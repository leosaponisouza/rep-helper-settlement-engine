package com.rephelper.settlement.algorithm;

import com.saponi.guardrails.SpecContract;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic Min-Cash-Flow debt simplification engine operating in integer cents
 * to guarantee exact zero-sum conservation without floating-point drift.
 */
@Component
@SpecContract(
        specId = "SPEC-001",
        specFile = "SPEC-001-settlement-and-outbox.md",
        invariant = "Net balances sum to zero and optimized Pix transfers never exceed N - 1"
)
public class DebtSimplificationEngine {

    public record ExpenseShareInput(UUID payerId, BigDecimal amount, List<UUID> participantIds) {}

    public record PlannedTransfer(UUID debtorId, UUID creditorId, BigDecimal amount) {}

    public record SimplificationPlan(
            BigDecimal totalExpenses,
            int rawPairwiseTransfers,
            List<PlannedTransfer> transfers
    ) {}

    private record BalanceNode(UUID residentId, long cents) {}

    public SimplificationPlan simplify(List<ExpenseShareInput> expenses) {
        if (expenses == null || expenses.isEmpty()) {
            return new SimplificationPlan(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), 0, List.of());
        }

        Map<UUID, Long> netCents = new HashMap<>();
        Set<String> rawEdges = new HashSet<>();
        long totalExpenseCents = 0L;

        for (ExpenseShareInput expense : expenses) {
            long amountCents = expense.amount()
                    .movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
            if (amountCents <= 0) {
                throw new IllegalArgumentException("Expense amount must be positive");
            }
            List<UUID> sortedParticipants = expense.participantIds().stream()
                    .distinct()
                    .sorted(Comparator.comparing(UUID::toString))
                    .toList();
            if (sortedParticipants.isEmpty()) {
                throw new IllegalArgumentException("Expense must have at least one participant");
            }

            totalExpenseCents += amountCents;
            netCents.merge(expense.payerId(), amountCents, Long::sum);

            long baseShare = amountCents / sortedParticipants.size();
            long remainder = amountCents % sortedParticipants.size();

            for (int i = 0; i < sortedParticipants.size(); i++) {
                UUID participant = sortedParticipants.get(i);
                long owed = baseShare + (i < remainder ? 1L : 0L);
                netCents.merge(participant, -owed, Long::sum);
                if (!participant.equals(expense.payerId()) && owed > 0) {
                    rawEdges.add(participant + "->" + expense.payerId());
                }
            }
        }

        long checksum = netCents.values().stream().mapToLong(Long::longValue).sum();
        if (checksum != 0L) {
            throw new IllegalStateException("Zero-sum conservation violated: checksum=" + checksum);
        }

        Comparator<BalanceNode> maxMagnitudeOrder = Comparator
                .comparingLong((BalanceNode n) -> Math.abs(n.cents())).reversed()
                .thenComparing(n -> n.residentId().toString());

        PriorityQueue<BalanceNode> creditors = new PriorityQueue<>(maxMagnitudeOrder);
        PriorityQueue<BalanceNode> debtors = new PriorityQueue<>(maxMagnitudeOrder);

        for (Map.Entry<UUID, Long> entry : netCents.entrySet()) {
            long value = entry.getValue();
            if (value > 0L) {
                creditors.add(new BalanceNode(entry.getKey(), value));
            } else if (value < 0L) {
                debtors.add(new BalanceNode(entry.getKey(), -value));
            }
        }

        List<PlannedTransfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            BalanceNode maxCreditor = creditors.poll();
            BalanceNode maxDebtor = debtors.poll();

            long settledCents = Math.min(maxCreditor.cents(), maxDebtor.cents());
            transfers.add(new PlannedTransfer(
                    maxDebtor.residentId(),
                    maxCreditor.residentId(),
                    BigDecimal.valueOf(settledCents, 2)
            ));

            long remainingCredit = maxCreditor.cents() - settledCents;
            long remainingDebt = maxDebtor.cents() - settledCents;

            if (remainingCredit > 0L) {
                creditors.add(new BalanceNode(maxCreditor.residentId(), remainingCredit));
            }
            if (remainingDebt > 0L) {
                debtors.add(new BalanceNode(maxDebtor.residentId(), remainingDebt));
            }
        }

        return new SimplificationPlan(
                BigDecimal.valueOf(totalExpenseCents, 2),
                rawEdges.size(),
                List.copyOf(transfers)
        );
    }
}

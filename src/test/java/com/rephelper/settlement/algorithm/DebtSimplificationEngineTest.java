package com.rephelper.settlement.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import com.rephelper.settlement.algorithm.DebtSimplificationEngine.ExpenseShareInput;
import com.rephelper.settlement.algorithm.DebtSimplificationEngine.PlannedTransfer;
import com.rephelper.settlement.algorithm.DebtSimplificationEngine.SimplificationPlan;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DebtSimplificationEngineTest {

    private final DebtSimplificationEngine engine = new DebtSimplificationEngine();

    @Test
    void shouldCollapseCircularAndTransitiveDebtsWithinNMinusOneTransfers() {
        UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bruno = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID carla = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID diego = UUID.fromString("00000000-0000-0000-0000-000000000004");
        UUID elena = UUID.fromString("00000000-0000-0000-0000-000000000005");
        List<UUID> allFive = List.of(alice, bruno, carla, diego, elena);

        List<ExpenseShareInput> expenses = List.of(
                new ExpenseShareInput(alice, new BigDecimal("500.00"), allFive),
                new ExpenseShareInput(bruno, new BigDecimal("250.00"), allFive),
                new ExpenseShareInput(carla, new BigDecimal("150.00"), List.of(carla, diego, elena)),
                new ExpenseShareInput(diego, new BigDecimal("90.00"), List.of(alice, bruno, diego))
        );

        SimplificationPlan plan = engine.simplify(expenses);

        assertThat(plan.totalExpenses()).isEqualByComparingTo("990.00");
        assertThat(plan.rawPairwiseTransfers()).isGreaterThan(plan.transfers().size());
        assertThat(plan.transfers().size()).isLessThanOrEqualTo(4); // N - 1 for 5 residents
    }

    @Test
    void shouldPreserveExactCentConservationWhenSplittingIndivisibleAmounts() {
        UUID r1 = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID r2 = UUID.fromString("00000000-0000-0000-0000-000000000020");
        UUID r3 = UUID.fromString("00000000-0000-0000-0000-000000000030");

        // R$ 100.00 split 3 ways -> 33.34 + 33.33 + 33.33 (r1 paid 100.00, owes 33.34 -> net +66.66)
        SimplificationPlan plan = engine.simplify(List.of(
                new ExpenseShareInput(r1, new BigDecimal("100.00"), List.of(r1, r2, r3))
        ));

        BigDecimal totalTransferred = plan.transfers().stream()
                .map(PlannedTransfer::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(plan.transfers()).hasSize(2);
        assertThat(totalTransferred).isEqualByComparingTo("66.66");
    }

    @Test
    void shouldProduceZeroTransfersWhenAllResidentsPaidEqualShares() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");

        SimplificationPlan plan = engine.simplify(List.of(
                new ExpenseShareInput(a, new BigDecimal("100.00"), List.of(a, b)),
                new ExpenseShareInput(b, new BigDecimal("100.00"), List.of(a, b))
        ));

        assertThat(plan.transfers()).isEmpty();
    }
}

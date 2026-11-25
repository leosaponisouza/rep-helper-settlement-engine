package com.rephelper.settlement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rephelper.settlement.algorithm.DebtSimplificationEngine;
import com.rephelper.settlement.algorithm.DebtSimplificationEngine.ExpenseShareInput;
import com.rephelper.settlement.algorithm.DebtSimplificationEngine.PlannedTransfer;
import com.rephelper.settlement.algorithm.DebtSimplificationEngine.SimplificationPlan;
import com.rephelper.settlement.api.BusinessException;
import com.rephelper.settlement.api.SettlementDTOs.CycleView;
import com.rephelper.settlement.api.SettlementDTOs.OpenCycleRequest;
import com.rephelper.settlement.api.SettlementDTOs.TransferView;
import com.rephelper.settlement.domain.OutboxEvent;
import com.rephelper.settlement.domain.OutboxEventRepository;
import com.rephelper.settlement.domain.SettlementCycle;
import com.rephelper.settlement.domain.SettlementCycleRepository;
import com.rephelper.settlement.domain.SettlementTransfer;
import com.saponi.guardrails.SpecContract;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SpecContract(
        specId = "SPEC-001",
        specFile = "SPEC-001-settlement-and-outbox.md",
        invariant = "Opening a settlement cycle persists simplified transfers and outbox event atomically"
)
public class SettlementCycleService {

    private final DebtSimplificationEngine engine;
    private final SettlementCycleRepository cycleRepo;
    private final OutboxEventRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public SettlementCycleService(
            DebtSimplificationEngine engine,
            SettlementCycleRepository cycleRepo,
            OutboxEventRepository outboxRepo,
            ObjectMapper objectMapper
    ) {
        this.engine = engine;
        this.cycleRepo = cycleRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CycleView openCycle(OpenCycleRequest request) {
        if (cycleRepo.existsByRepublicIdAndReferenceMonth(request.republicId(), request.referenceMonth())) {
            throw BusinessException.conflict(
                    "CYCLE_ALREADY_EXISTS",
                    "Settlement cycle already opened for republic " + request.republicId()
                            + " in " + request.referenceMonth()
            );
        }

        List<ExpenseShareInput> inputs = request.expenses().stream()
                .map(e -> new ExpenseShareInput(e.payerId(), e.amount(), e.participantIds()))
                .toList();

        SimplificationPlan plan = engine.simplify(inputs);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID cycleId = UUID.randomUUID();
        String initialStatus = plan.transfers().isEmpty() ? "SETTLED" : "OPEN";

        SettlementCycle cycle = new SettlementCycle(
                cycleId,
                request.republicId(),
                request.referenceMonth(),
                plan.totalExpenses(),
                plan.rawPairwiseTransfers(),
                plan.transfers().size(),
                initialStatus,
                now
        );
        if ("SETTLED".equals(initialStatus)) {
            cycle.markSettled(now);
        }

        for (PlannedTransfer pt : plan.transfers()) {
            String pixTxId = "PIX" + UUID.randomUUID().toString().replace("-", "").substring(0, 23).toUpperCase();
            cycle.addTransfer(new SettlementTransfer(
                    UUID.randomUUID(),
                    cycle,
                    pt.debtorId(),
                    pt.creditorId(),
                    pt.amount(),
                    pixTxId
            ));
        }

        SettlementCycle saved = cycleRepo.save(cycle);
        outboxRepo.save(new OutboxEvent(
                UUID.randomUUID(),
                "SETTLEMENT_CYCLE",
                saved.getId().toString(),
                "SETTLEMENT_CYCLE_OPENED",
                toJson(Map.of(
                        "cycleId", saved.getId().toString(),
                        "republicId", saved.getRepublicId().toString(),
                        "referenceMonth", saved.getReferenceMonth(),
                        "rawTransfers", saved.getRawTransferCount(),
                        "optimizedTransfers", saved.getOptimizedTransferCount()
                )),
                now
        ));

        return toView(saved);
    }

    @Transactional(readOnly = true)
    public CycleView getCycle(UUID cycleId) {
        SettlementCycle cycle = cycleRepo.findWithTransfersById(cycleId)
                .orElseThrow(() -> BusinessException.notFound("CYCLE_NOT_FOUND", "Cycle not found: " + cycleId));
        return toView(cycle);
    }

    private CycleView toView(SettlementCycle cycle) {
        List<TransferView> transfers = cycle.getTransfers().stream()
                .map(t -> new TransferView(
                        t.getId(),
                        t.getDebtorId(),
                        t.getCreditorId(),
                        t.getAmount(),
                        t.getPixTxId(),
                        t.getStatus(),
                        t.getSettledAt()
                ))
                .toList();

        return new CycleView(
                cycle.getId(),
                cycle.getRepublicId(),
                cycle.getReferenceMonth(),
                cycle.getTotalExpenses(),
                cycle.getRawTransferCount(),
                cycle.getOptimizedTransferCount(),
                cycle.getStatus(),
                cycle.getCreatedAt(),
                cycle.getSettledAt(),
                transfers
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }
}

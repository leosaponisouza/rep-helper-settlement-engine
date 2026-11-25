package com.rephelper.settlement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rephelper.settlement.api.BusinessException;
import com.rephelper.settlement.api.SettlementDTOs.PixWebhookPayload;
import com.rephelper.settlement.api.SettlementDTOs.WebhookAckView;
import com.rephelper.settlement.domain.OutboxEvent;
import com.rephelper.settlement.domain.OutboxEventRepository;
import com.rephelper.settlement.domain.SettlementCycle;
import com.rephelper.settlement.domain.SettlementCycleRepository;
import com.rephelper.settlement.domain.SettlementTransfer;
import com.rephelper.settlement.domain.SettlementTransferRepository;
import com.rephelper.settlement.domain.WebhookReceipt;
import com.rephelper.settlement.domain.WebhookReceiptRepository;
import com.saponi.guardrails.SpecContract;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@SpecContract(
        specId = "SPEC-001",
        specFile = "SPEC-001-settlement-and-outbox.md",
        invariant = "Duplicate or racing Pix webhooks settle each transfer at most once and record Outbox events atomically"
)
public class PixWebhookService {

    private final WebhookReceiptRepository receiptRepo;
    private final SettlementTransferRepository transferRepo;
    private final SettlementCycleRepository cycleRepo;
    private final OutboxEventRepository outboxRepo;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNewTx;
    private final Counter paidCounter;
    private final Counter deduplicatedCounter;

    public PixWebhookService(
            WebhookReceiptRepository receiptRepo,
            SettlementTransferRepository transferRepo,
            SettlementCycleRepository cycleRepo,
            OutboxEventRepository outboxRepo,
            ObjectMapper objectMapper,
            PlatformTransactionManager txManager,
            MeterRegistry meterRegistry
    ) {
        this.receiptRepo = receiptRepo;
        this.transferRepo = transferRepo;
        this.cycleRepo = cycleRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.paidCounter = Counter.builder("settlement.transfers.paid")
                .description("Pix transfers settled exactly once")
                .register(meterRegistry);
        this.deduplicatedCounter = Counter.builder("settlement.webhooks.deduplicated")
                .description("Duplicate or replayed Pix webhooks safely ignored")
                .register(meterRegistry);
    }

    public WebhookAckView processWebhook(String idempotencyKey, PixWebhookPayload payload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw BusinessException.badRequest("MISSING_IDEMPOTENCY_KEY", "Idempotency-Key header is required");
        }

        try {
            return requiresNewTx.execute(status -> settleInTransaction(idempotencyKey.trim(), payload));
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException raceEx) {
            deduplicatedCounter.increment();
            return requiresNewTx.execute(status -> readExistingStateAsDuplicate(idempotencyKey.trim(), payload.pixTxId()));
        }
    }

    private WebhookAckView settleInTransaction(String idempotencyKey, PixWebhookPayload payload) {
        if (receiptRepo.findByIdempotencyKey(idempotencyKey).isPresent()) {
            deduplicatedCounter.increment();
            return readExistingStateAsDuplicate(idempotencyKey, payload.pixTxId());
        }

        SettlementTransfer transfer = transferRepo.findByPixTxId(payload.pixTxId())
                .orElseThrow(() -> BusinessException.notFound(
                        "PIX_TRANSFER_NOT_FOUND",
                        "Unknown pixTxId: " + payload.pixTxId()
                ));

        if (transfer.getAmount().compareTo(payload.paidAmount()) != 0) {
            throw BusinessException.badRequest(
                    "AMOUNT_MISMATCH",
                    "Expected amount " + transfer.getAmount() + " but received " + payload.paidAmount()
            );
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        receiptRepo.saveAndFlush(new WebhookReceipt(
                UUID.randomUUID(),
                idempotencyKey,
                payload.pixTxId(),
                payload.endToEndId(),
                now
        ));

        if (transfer.isPaid()) {
            deduplicatedCounter.increment();
            SettlementCycle cycle = transfer.getCycle();
            return new WebhookAckView(
                    idempotencyKey,
                    transfer.getPixTxId(),
                    transfer.getStatus(),
                    cycle.getStatus(),
                    true,
                    now
            );
        }

        transfer.markPaid(payload.paidAt());
        transferRepo.saveAndFlush(transfer);
        paidCounter.increment();

        SettlementCycle cycle = transfer.getCycle();
        outboxRepo.save(new OutboxEvent(
                UUID.randomUUID(),
                "SETTLEMENT_TRANSFER",
                transfer.getId().toString(),
                "SETTLEMENT_TRANSFER_PAID",
                toJson(Map.of(
                        "transferId", transfer.getId().toString(),
                        "cycleId", cycle.getId().toString(),
                        "pixTxId", transfer.getPixTxId(),
                        "endToEndId", payload.endToEndId(),
                        "amount", transfer.getAmount()
                )),
                now
        ));

        long remainingUnpaid = transferRepo.countByCycleIdAndStatusNot(cycle.getId(), "PAID");
        if (remainingUnpaid == 0L && !"SETTLED".equals(cycle.getStatus())) {
            cycle.markSettled(now);
            cycleRepo.saveAndFlush(cycle);
            outboxRepo.save(new OutboxEvent(
                    UUID.randomUUID(),
                    "SETTLEMENT_CYCLE",
                    cycle.getId().toString(),
                    "SETTLEMENT_CYCLE_SETTLED",
                    toJson(Map.of(
                            "cycleId", cycle.getId().toString(),
                            "republicId", cycle.getRepublicId().toString(),
                            "referenceMonth", cycle.getReferenceMonth()
                    )),
                    now
            ));
        }

        return new WebhookAckView(
                idempotencyKey,
                transfer.getPixTxId(),
                transfer.getStatus(),
                cycle.getStatus(),
                false,
                now
        );
    }

    private WebhookAckView readExistingStateAsDuplicate(String idempotencyKey, String pixTxId) {
        SettlementTransfer transfer = transferRepo.findByPixTxId(pixTxId)
                .orElseThrow(() -> BusinessException.notFound("PIX_TRANSFER_NOT_FOUND", "Unknown pixTxId: " + pixTxId));
        return new WebhookAckView(
                idempotencyKey,
                transfer.getPixTxId(),
                transfer.getStatus(),
                transfer.getCycle().getStatus(),
                true,
                OffsetDateTime.now(ZoneOffset.UTC)
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

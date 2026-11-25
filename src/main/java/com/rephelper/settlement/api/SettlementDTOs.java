package com.rephelper.settlement.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SettlementDTOs {

    private SettlementDTOs() {}

    public record ExpenseItemRequest(
            @NotNull UUID payerId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotEmpty List<@NotNull UUID> participantIds
    ) {}

    public record OpenCycleRequest(
            @NotNull UUID republicId,
            @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}$") String referenceMonth,
            @NotEmpty List<@Valid ExpenseItemRequest> expenses
    ) {}

    public record TransferView(
            UUID id,
            UUID debtorId,
            UUID creditorId,
            BigDecimal amount,
            String pixTxId,
            String status,
            OffsetDateTime settledAt
    ) {}

    public record CycleView(
            UUID id,
            UUID republicId,
            String referenceMonth,
            BigDecimal totalExpenses,
            int rawTransferCount,
            int optimizedTransferCount,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime settledAt,
            List<TransferView> transfers
    ) {}

    public record PixWebhookPayload(
            @NotBlank String pixTxId,
            @NotBlank String endToEndId,
            @NotNull @DecimalMin("0.01") BigDecimal paidAmount,
            @NotNull OffsetDateTime paidAt
    ) {}

    public record WebhookAckView(
            String idempotencyKey,
            String pixTxId,
            String transferStatus,
            String cycleStatus,
            boolean duplicate,
            OffsetDateTime processedAt
    ) {}

    public record OutboxEventView(
            UUID id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String status,
            int attempts,
            OffsetDateTime createdAt,
            OffsetDateTime publishedAt
    ) {}

    public record RelayResultView(int publishedCount, OffsetDateTime executedAt) {}

    public record ErrorView(String code, String message, OffsetDateTime timestamp) {}
}

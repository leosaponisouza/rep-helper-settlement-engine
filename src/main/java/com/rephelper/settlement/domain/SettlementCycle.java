package com.rephelper.settlement.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "settlement_cycles")
public class SettlementCycle {

    @Id
    private UUID id;

    @Column(name = "republic_id", nullable = false)
    private UUID republicId;

    @Column(name = "reference_month", nullable = false, length = 7)
    private String referenceMonth;

    @Column(name = "total_expenses", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalExpenses;

    @Column(name = "raw_transfer_count", nullable = false)
    private int rawTransferCount;

    @Column(name = "optimized_transfer_count", nullable = false)
    private int optimizedTransferCount;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;

    @OneToMany(mappedBy = "cycle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("amount DESC")
    private List<SettlementTransfer> transfers = new ArrayList<>();

    protected SettlementCycle() {}

    public SettlementCycle(
            UUID id,
            UUID republicId,
            String referenceMonth,
            BigDecimal totalExpenses,
            int rawTransferCount,
            int optimizedTransferCount,
            String status,
            OffsetDateTime createdAt
    ) {
        this.id = id;
        this.republicId = republicId;
        this.referenceMonth = referenceMonth;
        this.totalExpenses = totalExpenses;
        this.rawTransferCount = rawTransferCount;
        this.optimizedTransferCount = optimizedTransferCount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void addTransfer(SettlementTransfer transfer) {
        this.transfers.add(transfer);
    }

    public void markSettled(OffsetDateTime when) {
        this.status = "SETTLED";
        this.settledAt = when;
    }

    public UUID getId() { return id; }
    public UUID getRepublicId() { return republicId; }
    public String getReferenceMonth() { return referenceMonth; }
    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public int getRawTransferCount() { return rawTransferCount; }
    public int getOptimizedTransferCount() { return optimizedTransferCount; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getSettledAt() { return settledAt; }
    public List<SettlementTransfer> getTransfers() { return transfers; }
}

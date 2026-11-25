package com.rephelper.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "settlement_transfers")
public class SettlementTransfer {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false)
    private SettlementCycle cycle;

    @Column(name = "debtor_id", nullable = false)
    private UUID debtorId;

    @Column(name = "creditor_id", nullable = false)
    private UUID creditorId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "pix_tx_id", nullable = false, unique = true, length = 64)
    private String pixTxId;

    @Column(nullable = false, length = 24)
    private String status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;

    protected SettlementTransfer() {}

    public SettlementTransfer(
            UUID id,
            SettlementCycle cycle,
            UUID debtorId,
            UUID creditorId,
            BigDecimal amount,
            String pixTxId
    ) {
        this.id = id;
        this.cycle = cycle;
        this.debtorId = debtorId;
        this.creditorId = creditorId;
        this.amount = amount;
        this.pixTxId = pixTxId;
        this.status = "PENDING";
    }

    public boolean isPaid() {
        return "PAID".equals(this.status);
    }

    public void markPaid(OffsetDateTime when) {
        this.status = "PAID";
        this.settledAt = when;
    }

    public UUID getId() { return id; }
    public SettlementCycle getCycle() { return cycle; }
    public UUID getDebtorId() { return debtorId; }
    public UUID getCreditorId() { return creditorId; }
    public BigDecimal getAmount() { return amount; }
    public String getPixTxId() { return pixTxId; }
    public String getStatus() { return status; }
    public OffsetDateTime getSettledAt() { return settledAt; }
}

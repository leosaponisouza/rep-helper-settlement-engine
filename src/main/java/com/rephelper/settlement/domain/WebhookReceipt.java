package com.rephelper.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_receipts")
public class WebhookReceipt {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "pix_tx_id", nullable = false, length = 64)
    private String pixTxId;

    @Column(name = "end_to_end_id", nullable = false, length = 64)
    private String endToEndId;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    protected WebhookReceipt() {}

    public WebhookReceipt(UUID id, String idempotencyKey, String pixTxId, String endToEndId, OffsetDateTime receivedAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.pixTxId = pixTxId;
        this.endToEndId = endToEndId;
        this.receivedAt = receivedAt;
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPixTxId() { return pixTxId; }
    public String getEndToEndId() { return endToEndId; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
}

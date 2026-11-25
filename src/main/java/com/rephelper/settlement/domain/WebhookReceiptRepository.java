package com.rephelper.settlement.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookReceiptRepository extends JpaRepository<WebhookReceipt, UUID> {

    Optional<WebhookReceipt> findByIdempotencyKey(String idempotencyKey);
}

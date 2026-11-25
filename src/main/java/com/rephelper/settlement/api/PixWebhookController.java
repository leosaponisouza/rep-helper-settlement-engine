package com.rephelper.settlement.api;

import com.rephelper.settlement.api.SettlementDTOs.PixWebhookPayload;
import com.rephelper.settlement.api.SettlementDTOs.WebhookAckView;
import com.rephelper.settlement.service.PixWebhookService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/pix")
public class PixWebhookController {

    private final PixWebhookService webhookService;

    public PixWebhookController(PixWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<WebhookAckView> receivePixWebhook(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PixWebhookPayload payload
    ) {
        return ResponseEntity.ok(webhookService.processWebhook(idempotencyKey, payload));
    }
}

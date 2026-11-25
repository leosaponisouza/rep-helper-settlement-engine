package com.rephelper.settlement.api;

import com.rephelper.settlement.api.SettlementDTOs.CycleView;
import com.rephelper.settlement.api.SettlementDTOs.OpenCycleRequest;
import com.rephelper.settlement.api.SettlementDTOs.OutboxEventView;
import com.rephelper.settlement.api.SettlementDTOs.RelayResultView;
import com.rephelper.settlement.service.OutboxRelayService;
import com.rephelper.settlement.service.SettlementCycleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private final SettlementCycleService cycleService;
    private final OutboxRelayService outboxRelayService;

    public SettlementController(SettlementCycleService cycleService, OutboxRelayService outboxRelayService) {
        this.cycleService = cycleService;
        this.outboxRelayService = outboxRelayService;
    }

    @PostMapping("/cycles")
    public ResponseEntity<CycleView> openCycle(@Valid @RequestBody OpenCycleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cycleService.openCycle(request));
    }

    @GetMapping("/cycles/{cycleId}")
    public ResponseEntity<CycleView> getCycle(@PathVariable UUID cycleId) {
        return ResponseEntity.ok(cycleService.getCycle(cycleId));
    }

    @GetMapping("/outbox/{aggregateId}")
    public ResponseEntity<List<OutboxEventView>> listOutbox(@PathVariable String aggregateId) {
        return ResponseEntity.ok(outboxRelayService.listByAggregate(aggregateId));
    }

    @PostMapping("/outbox/relay")
    public ResponseEntity<RelayResultView> triggerRelay() {
        return ResponseEntity.ok(outboxRelayService.relayPendingBatch());
    }
}

package com.rephelper.settlement.service;

import com.rephelper.settlement.api.SettlementDTOs.OutboxEventView;
import com.rephelper.settlement.api.SettlementDTOs.RelayResultView;
import com.rephelper.settlement.domain.OutboxEvent;
import com.rephelper.settlement.domain.OutboxEventRepository;
import com.saponi.guardrails.SpecContract;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SpecContract(
        specId = "SPEC-001",
        specFile = "SPEC-001-settlement-and-outbox.md",
        invariant = "Outbox relay drains PENDING events in FIFO order and transitions them to PUBLISHED"
)
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private final OutboxEventRepository outboxRepo;
    private final Counter publishedCounter;

    public OutboxRelayService(OutboxEventRepository outboxRepo, MeterRegistry meterRegistry) {
        this.outboxRepo = outboxRepo;
        this.publishedCounter = Counter.builder("settlement.outbox.published")
                .description("Outbox integration events relayed downstream")
                .register(meterRegistry);
    }

    @Transactional
    public RelayResultView relayPendingBatch() {
        List<OutboxEvent> pending = outboxRepo.findTop50ByStatusOrderByCreatedAtAsc("PENDING");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        for (OutboxEvent event : pending) {
            log.info("[OUTBOX RELAY] type={} aggregateId={} payload={}",
                    event.getEventType(), event.getAggregateId(), event.getPayload());
            event.markPublished(now);
            publishedCounter.increment();
        }
        outboxRepo.saveAll(pending);
        return new RelayResultView(pending.size(), now);
    }

    @Transactional(readOnly = true)
    public List<OutboxEventView> listByAggregate(String aggregateId) {
        return outboxRepo.findByAggregateIdOrderByCreatedAtAsc(aggregateId).stream()
                .map(e -> new OutboxEventView(
                        e.getId(),
                        e.getAggregateType(),
                        e.getAggregateId(),
                        e.getEventType(),
                        e.getStatus(),
                        e.getAttempts(),
                        e.getCreatedAt(),
                        e.getPublishedAt()
                ))
                .toList();
    }
}

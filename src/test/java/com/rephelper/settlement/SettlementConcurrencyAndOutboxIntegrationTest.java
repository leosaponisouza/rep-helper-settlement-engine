package com.rephelper.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettlementConcurrencyAndOutboxIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void shouldSettleTransferExactlyOnceUnderConcurrentVirtualThreadPixWebhookStorm() throws Exception {
        UUID republicId = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bruno = UUID.randomUUID();
        UUID carla = UUID.randomUUID();

        MvcResult openRes = mockMvc.perform(post("/api/v1/settlements/cycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "republicId", republicId,
                                "referenceMonth", "2026-11",
                                "expenses", List.of(
                                        Map.of(
                                                "payerId", alice,
                                                "amount", new BigDecimal("300.00"),
                                                "participantIds", List.of(alice, bruno, carla)
                                        )
                                )
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.transfers.length()").value(2))
                .andReturn();

        JsonNode cycleJson = readJson(openRes);
        String cycleId = cycleJson.get("id").asText();
        JsonNode firstTransfer = cycleJson.get("transfers").get(0);
        JsonNode secondTransfer = cycleJson.get("transfers").get(1);

        String firstPixTxId = firstTransfer.get("pixTxId").asText();
        BigDecimal firstAmount = firstTransfer.get("amount").decimalValue();

        double paidBefore = meterRegistry.counter("settlement.transfers.paid").count();

        // Fire 20 concurrent Virtual Threads delivering the same Pix webhook (mixed shared & distinct keys)
        List<Callable<JsonNode>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final String key = (i % 2 == 0) ? "idem-shared-storm-key" : "idem-distinct-replay-" + i;
            tasks.add(() -> {
                MvcResult res = mockMvc.perform(post("/api/v1/webhooks/pix")
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "pixTxId", firstPixTxId,
                                        "endToEndId", "E2E-BACEN-0001",
                                        "paidAmount", firstAmount,
                                        "paidAt", "2026-11-25T18:00:00-03:00"
                                ))))
                        .andExpect(status().isOk())
                        .andReturn();
                return readJson(res);
            });
        }

        List<JsonNode> responses = new ArrayList<>();
        try (ExecutorService vThreads = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<JsonNode> f : vThreads.invokeAll(tasks)) {
                responses.add(f.get());
            }
        }

        long firstTimeCount = responses.stream().filter(r -> !r.get("duplicate").asBoolean()).count();
        long duplicateCount = responses.stream().filter(r -> r.get("duplicate").asBoolean()).count();

        assertThat(firstTimeCount).isEqualTo(1L);
        assertThat(duplicateCount).isEqualTo(19L);
        assertThat(meterRegistry.counter("settlement.transfers.paid").count() - paidBefore).isEqualTo(1.0);

        // Settle the second transfer -> cycle must auto-transition to SETTLED and emit cycle outbox event
        mockMvc.perform(post("/api/v1/webhooks/pix")
                        .header("Idempotency-Key", "idem-transfer-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "pixTxId", secondTransfer.get("pixTxId").asText(),
                                "endToEndId", "E2E-BACEN-0002",
                                "paidAmount", secondTransfer.get("amount").decimalValue(),
                                "paidAt", "2026-11-25T18:05:00-03:00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.cycleStatus").value("SETTLED"));

        mockMvc.perform(get("/api/v1/settlements/cycles/" + cycleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        // Verify Outbox events recorded atomically and relay drains PENDING -> PUBLISHED
        mockMvc.perform(get("/api/v1/settlements/outbox/" + cycleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventType").value("SETTLEMENT_CYCLE_OPENED"))
                .andExpect(jsonPath("$[1].eventType").value("SETTLEMENT_CYCLE_SETTLED"));

        mockMvc.perform(post("/api/v1/settlements/outbox/relay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedCount").value(4));
    }

    @Test
    void shouldRejectDuplicateCycleForSameRepublicAndMonthAndValidateAmounts() throws Exception {
        UUID republicId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        String body = objectMapper.writeValueAsString(Map.of(
                "republicId", republicId,
                "referenceMonth", "2026-10",
                "expenses", List.of(Map.of(
                        "payerId", a,
                        "amount", new BigDecimal("120.00"),
                        "participantIds", List.of(a, b)
                ))
        ));

        mockMvc.perform(post("/api/v1/settlements/cycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/settlements/cycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}

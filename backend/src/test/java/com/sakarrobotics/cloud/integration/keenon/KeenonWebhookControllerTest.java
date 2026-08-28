package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.sakarrobotics.cloud.IntegrationTestSupport;

/** Idempotent callback processing (Master Requirements "Webhooks"). */
class KeenonWebhookControllerTest extends IntegrationTestSupport {

    private static final String SECRET = "test-webhook-secret-0123456789";

    @Autowired
    private VendorWebhookEventRepository repository;

    @Test
    void missingSecret_isRejected() throws Exception {
        mockMvc.perform(post("/integrations/keenon/webhooks/CleanRobotStatus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"robotSn\":\"94:BA:06:CA:99:F3\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void retriedDeliveryOfTheSamePayload_isIdempotent() throws Exception {
        long before = repository.count();
        String payload = "{\"robotSn\":\"94:BA:06:CA:99:F3\",\"event\":\"CleanRobotRechargeTask\"}";

        mockMvc.perform(post("/integrations/keenon/webhooks/CleanRobotRechargeTask")
                        .header("X-Keenon-Webhook-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        // Exact same delivery retried (as a real webhook sender would on a timeout/retry).
        mockMvc.perform(post("/integrations/keenon/webhooks/CleanRobotRechargeTask")
                        .header("X-Keenon-Webhook-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(repository.count()).isEqualTo(before + 1); // not +2 — the retry was a no-op
    }
}

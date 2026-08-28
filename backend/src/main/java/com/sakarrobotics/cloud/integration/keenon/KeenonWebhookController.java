package com.sakarrobotics.cloud.integration.keenon;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Vendor callback intake (Master Requirements "Webhooks"). Not part of the
 * Sakar public API surface — this endpoint authenticates the CALLER
 * (Keenon) via a shared secret header, not a Sakar user JWT, and is
 * explicitly excluded from the vendor-neutrality rule that governs
 * {@code /api/v1/**} (SAKAR_ROBOT_PLATFORM_API_SPEC.md "Vendor integration
 * boundary") because it is Keenon calling Sakar, not the reverse.
 *
 * <p>Idempotent by design: {@code dedupKey} is unique-constrained, so a
 * retried delivery of the same callback is a no-op rather than a duplicate
 * event.
 */
@RestController
@RequestMapping("/integrations/keenon/webhooks")
@RequiredArgsConstructor
@Tag(name = "Keenon Webhooks (vendor-facing, not part of the public API)")
class KeenonWebhookController {

    private static final String SIGNATURE_HEADER = "X-Keenon-Webhook-Secret";

    private final KeenonProperties properties;
    private final VendorWebhookEventRepository repository;

    @PostMapping("/{eventType}")
    @Operation(summary = "Receive a Keenon Open Platform callback (RobotOnlineStatus, CleanRobotStatus, CleanStrategyTemporary, ...)")
    @Transactional
    public ResponseEntity<Void> receive(
            @PathVariable String eventType,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String presentedSecret,
            @RequestBody String rawPayload) {

        String configuredSecret = properties.getWebhookSecret();
        if (configuredSecret == null || configuredSecret.isBlank() || !configuredSecret.equals(presentedSecret)) {
            throw new ApiException(SakarErrorCode.UNAUTHENTICATED, "Invalid or missing webhook secret");
        }

        String dedupKey = eventType + ":" + sha256(rawPayload);
        if (repository.findByDedupKey(dedupKey).isPresent()) {
            return ResponseEntity.ok().build(); // already processed — idempotent no-op
        }

        VendorWebhookEvent event = new VendorWebhookEvent();
        event.setEventType(eventType);
        event.setDedupKey(dedupKey);
        event.setRawPayload(rawPayload);
        // Normalization into typed robot_events rows is deferred past Phase 1 (Final Report).
        repository.save(event);

        return ResponseEntity.ok().build();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

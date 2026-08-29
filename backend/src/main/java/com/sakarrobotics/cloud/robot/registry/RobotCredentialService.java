package com.sakarrobotics.cloud.robot.registry;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.audit.AuditService;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.robot.registry.dto.RobotMqttCredentialResponse;
import com.sakarrobotics.cloud.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

/**
 * Provisions/rotates the per-robot MQTT credential (Phase 3 Part 4/5 —
 * "MQTT client identity must map to registered robot identity", "Credential
 * rotation capability"). Backed by the pre-existing {@code
 * robot_credentials} table/entity, previously unused by any code path.
 *
 * <p><strong>Honest scope note</strong> (see {@code
 * SAKAR_MQTT_ARCHITECTURE.md} "Security — DEVELOPMENT vs PRODUCTION
 * REQUIRED"): this credential is intended for the agent's MQTT {@code
 * CONNECT} username/password. The dev broker
 * ({@code backend/docker/mosquitto.conf}, {@code allow_anonymous true})
 * does not enforce it today — broker-side per-client authentication/ACL is
 * a separate, explicitly deferred production requirement. Issuing and
 * hashing a real, rotatable, per-robot secret here is still meaningful
 * work: it is the identity {@link com.sakarrobotics.cloud.mqtt.MqttInboundMessageService}
 * cross-checks robot/tenant ownership against, independent of whatever the
 * broker itself enforces.
 */
@Service
@RequiredArgsConstructor
public class RobotCredentialService {

    private static final int SECRET_BYTES = 32;

    private final RobotRepository robotRepository;
    private final RobotCredentialRepository robotCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public RobotMqttCredentialResponse provisionOrRotate(UserPrincipal principal, UUID robotId) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_NOT_FOUND, "Robot not found: " + robotId));

        String rawSecret = generateSecret();
        RobotCredential credential = robotCredentialRepository.findByRobotId(robotId).orElseGet(RobotCredential::new);
        boolean rotating = credential.getId() != null;

        credential.setRobotId(robot.getId());
        credential.setCredentialType("MQTT");
        credential.setCredentialValueHash(passwordEncoder.encode(rawSecret));
        Instant now = Instant.now();
        if (rotating) {
            credential.setRotatedAt(now);
        } else {
            credential.setProvisionedAt(now);
        }
        robotCredentialRepository.save(credential);

        // Audit the ACTION only — never the raw secret (Phase 3 Security Hardening Part 9/16:
        // "Never log the credential value").
        auditService.record(principal, robot.getOrganizationId(), robot.getId(),
                rotating ? "MQTT_CREDENTIAL_ROTATED" : "MQTT_CREDENTIAL_PROVISIONED", "SUCCESS", null, null, null);

        return new RobotMqttCredentialResponse(robot.getId(), robot.getId().toString(), rawSecret, now);
    }

    /**
     * Deletes the stored credential hash so no future connection attempt
     * can be verified against it (Phase 3 Security Hardening Part 4/9 —
     * "revoked credentials cannot reconnect"). This is the software-side
     * half of revocation: today, nothing on the dev broker actually checks
     * this table at CONNECT time (see class Javadoc), so this alone does
     * not yet terminate a currently-open connection or block a future
     * broker that hasn't been wired to check it — see
     * {@code SAKAR_MQTT_ARCHITECTURE.md} §7 for the production requirement
     * this remains gated on.
     */
    @Transactional
    public void revoke(UserPrincipal principal, UUID robotId) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_NOT_FOUND, "Robot not found: " + robotId));
        robotCredentialRepository.findByRobotId(robotId).ifPresent(robotCredentialRepository::delete);
        auditService.record(principal, robot.getOrganizationId(), robot.getId(), "MQTT_CREDENTIAL_REVOKED", "SUCCESS", null, null, null);
    }

    private static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

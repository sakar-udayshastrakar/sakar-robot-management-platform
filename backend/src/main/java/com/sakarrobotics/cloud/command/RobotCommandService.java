package com.sakarrobotics.cloud.command;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.audit.AuditService;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.mqtt.MqttGatewayService;
import com.sakarrobotics.cloud.mqtt.MqttTopicKind;
import com.sakarrobotics.cloud.mqtt.MqttTopicResolver;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityService;
import com.sakarrobotics.cloud.robot.registry.RobotService;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Real, non-lock robot command issuance (Roadmap Phase 6). Validates
 * capability/tenant/permission, persists a signed-envelope-shaped {@link
 * RobotCommand} row, and makes a real, best-effort MQTT publish attempt on
 * the outbound {@link MqttTopicKind#COMMANDS} topic.
 *
 * <p>It does <strong>not</strong> itself claim delivery or execution: a
 * successful publish here only means the broker accepted the message, not
 * that any agent received or executed it. As of Roadmap Phase 6/7 ("Robot
 * Agent Command Loop"), {@code SakarC40Agent} <em>can</em> consume this
 * topic and report lifecycle results back (see {@link
 * CommandResultIngestionService}), which is what advances {@link
 * RobotCommand#getStatus()} past {@link CommandStatus#SENT} — but that
 * result is only ever as trustworthy as the agent's own command executor.
 * SakarC40Agent's current {@code START_TASK} executor is an explicitly
 * labeled software simulation (no Peanut SDK cleaning-control API exists
 * to call — see PEANUT_CLEAN_V3.7.6_INTERNAL_OPERATION_ANALYSIS.md and
 * PEANUT_SDK_C40_API_MATRIX.md), so a {@code COMMAND_SUCCESS} status for
 * that command type does not mean a physical robot did anything. A
 * failed/disabled MQTT publish is reported back to the caller honestly
 * rather than silently swallowed or faked as successful.
 */
@Service
@RequiredArgsConstructor
public class RobotCommandService {

    /**
     * Previously hardcoded to 30 with {@code sakar.command.default-expiration-seconds}
     * (application.yml, value 45) defined but never read anywhere — Roadmap
     * Phase 6/7 wires it up for real.
     */
    @Value("${sakar.command.default-expiration-seconds:30}")
    private long expirySeconds;

    private final RobotCommandRepository robotCommandRepository;
    private final RobotService robotService;
    private final RobotCapabilityService robotCapabilityService;
    private final TenantAccessGuard tenantAccessGuard;
    private final AuditService auditService;
    private final MqttGatewayService mqttGatewayService;
    private final MqttTopicResolver mqttTopicResolver;
    private final ObjectMapper objectMapper;

    public record Issued(RobotCommand command, boolean dispatched, String dispatchNote) {
    }

    @Transactional
    public Issued issue(UserPrincipal principal, UUID robotId, String commandTypeRaw, Map<String, Object> params) {
        NonLockCommandType commandType = parseCommandType(commandTypeRaw);
        validateParams(commandType, params);
        Robot robot = robotService.getAccessibleOrThrow(principal, robotId);
        robotCapabilityService.assertSupported(robot.getRobotModelId(), commandType.requiredCapability());

        RobotCommand command = new RobotCommand();
        command.setRobotId(robotId);
        command.setIssuedBy(principal.getUserId());
        command.setOrganizationId(robot.getOrganizationId());
        command.setCommandType(commandType.name());
        command.setNonce(UUID.randomUUID().toString());
        command.setExpiresAt(Instant.now().plusSeconds(expirySeconds));
        command.setStatus(CommandStatus.AUTHORIZED);
        command.setPayload(serializePayload(params));
        RobotCommand saved = robotCommandRepository.save(command);

        boolean dispatched;
        String dispatchNote;
        try {
            byte[] envelope = buildEnvelope(saved, params);
            String topic = mqttTopicResolver.topic(robot.getOrganizationId(), robot.getSiteId(), robotId, MqttTopicKind.COMMANDS);
            mqttGatewayService.publish(topic, envelope, 1, false);
            saved.setStatus(CommandStatus.SENT);
            saved.setSentAt(Instant.now());
            robotCommandRepository.save(saved);
            dispatched = true;
            dispatchNote = "Published to MQTT — awaiting the agent's own report of receipt/execution; "
                    + "see GET .../commands/{commandId} for the current status.";
        } catch (ApiException ex) {
            dispatched = false;
            dispatchNote = "Not dispatched: " + ex.getMessage();
        }

        auditService.record(principal, robot.getOrganizationId(), robotId, "COMMAND_ISSUED",
                dispatched ? "SENT" : "NOT_DISPATCHED", null, null, null);
        return new Issued(saved, dispatched, dispatchNote);
    }

    public RobotCommand getAccessibleOrThrow(UserPrincipal principal, UUID commandId) {
        RobotCommand command = robotCommandRepository.findById(commandId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.COMMAND_NOT_FOUND, "Command not found: " + commandId));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, command.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.COMMAND_NOT_FOUND, "Command not found: " + commandId);
        }
        return command;
    }

    public Page<RobotCommand> listByRobot(UserPrincipal principal, UUID robotId, int page, int pageSize) {
        robotService.getAccessibleOrThrow(principal, robotId);
        return robotCommandRepository.findByRobotIdOrderByIdDesc(robotId, PageRequest.of(page, pageSize));
    }

    private static NonLockCommandType parseCommandType(String raw) {
        try {
            return NonLockCommandType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(SakarErrorCode.VALIDATION_FAILED,
                    "Unsupported or lock/unlock command type: " + raw
                            + " — only non-lock commands may be issued through this endpoint");
        }
    }

    /**
     * Per-command-type payload shape checks (Roadmap Phase 8 "GO_TO_POINT",
     * {@code C40_S_GO_TO_POINT_SDK_INVESTIGATION.md}). {@code GO_TO_POINT} is
     * the only command type with such a check today: it requires an integer
     * {@code destinationId} — a pre-registered destination id on the
     * robot's own currently-loaded map (verified SDK contract:
     * {@code NavigationComponent.setTarget(IDataCallback, int)} takes a
     * plain destination id, not raw coordinates). This backend never
     * invents or defaults that id; a missing or malformed one fails the
     * request here rather than being forwarded to the agent. Negative
     * values are rejected defensively — the verified SDK request body has
     * no documented meaning for a negative destination id (unlike, e.g.,
     * its unrelated {@code taskType}/{@code takeControl} fields, which use
     * {@code -1} as their own "unset" sentinel) — so this project treats
     * negative as invalid rather than guessing it is safe to forward.
     */
    private static void validateParams(NonLockCommandType commandType, Map<String, Object> params) {
        if (commandType != NonLockCommandType.GO_TO_POINT) {
            return;
        }
        Object destinationId = params == null ? null : params.get("destinationId");
        if (destinationId == null) {
            throw new ApiException(SakarErrorCode.VALIDATION_FAILED,
                    "GO_TO_POINT requires a 'destinationId' in params — a pre-registered destination id on "
                            + "the robot's currently loaded map. This backend does not invent one.");
        }
        long value;
        try {
            value = destinationId instanceof Number
                    ? ((Number) destinationId).longValue()
                    : Long.parseLong(destinationId.toString());
        } catch (NumberFormatException ex) {
            throw new ApiException(SakarErrorCode.VALIDATION_FAILED,
                    "GO_TO_POINT 'destinationId' must be an integer, got: " + destinationId);
        }
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new ApiException(SakarErrorCode.VALIDATION_FAILED,
                    "GO_TO_POINT 'destinationId' must be a non-negative 32-bit integer, got: " + value);
        }
    }

    private String serializePayload(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(params);
    }

    private byte[] buildEnvelope(RobotCommand command, Map<String, Object> params) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("schemaVersion", "1.0");
        envelope.put("messageId", command.getId().toString());
        envelope.put("robotId", command.getRobotId().toString());
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("messageType", "COMMAND");
        envelope.put("commandType", command.getCommandType());
        envelope.put("nonce", command.getNonce());
        envelope.put("expiresAt", command.getExpiresAt().toString());
        envelope.put("params", params != null ? params : Map.of());
        return objectMapper.writeValueAsBytes(envelope);
    }
}

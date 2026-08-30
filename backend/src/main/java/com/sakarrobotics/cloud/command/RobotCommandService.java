package com.sakarrobotics.cloud.command;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
 * Real, non-lock robot command issuance (Roadmap Phase 6). This is
 * deliberately the full extent of "command dispatch" this codebase
 * implements: it validates capability/tenant/permission, persists a
 * signed-envelope-shaped {@link RobotCommand} row, and makes a real,
 * best-effort MQTT publish attempt on the outbound {@link
 * MqttTopicKind#COMMANDS} topic.
 *
 * <p>It does <strong>not</strong> claim delivery or execution:
 * {@code SakarC40Agent} has no command-consuming code (this task
 * deliberately does not modify the agent — see the Keenon-parity
 * requirements document), so no ack/result ever arrives and {@link
 * RobotCommand#getStatus()} never advances past {@link
 * CommandStatus#SENT}. A failed/disabled MQTT publish is reported back to
 * the caller honestly rather than silently swallowed or faked as
 * successful.
 */
@Service
@RequiredArgsConstructor
public class RobotCommandService {

    private static final long EXPIRY_SECONDS = 30;

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
        Robot robot = robotService.getAccessibleOrThrow(principal, robotId);
        robotCapabilityService.assertSupported(robot.getRobotModelId(), commandType.requiredCapability());

        RobotCommand command = new RobotCommand();
        command.setRobotId(robotId);
        command.setIssuedBy(principal.getUserId());
        command.setOrganizationId(robot.getOrganizationId());
        command.setCommandType(commandType.name());
        command.setNonce(UUID.randomUUID().toString());
        command.setExpiresAt(Instant.now().plusSeconds(EXPIRY_SECONDS));
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
            dispatchNote = "Published to MQTT — SakarC40Agent does not yet consume commands, "
                    + "so delivery/execution is not confirmed.";
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

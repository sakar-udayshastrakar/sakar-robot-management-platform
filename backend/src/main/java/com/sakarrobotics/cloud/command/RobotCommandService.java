package com.sakarrobotics.cloud.command;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.sakarrobotics.cloud.robot.adapter.RobotAdapter;
import com.sakarrobotics.cloud.robot.adapter.RobotAdapterRegistry;
import com.sakarrobotics.cloud.robot.adapter.dto.AdapterOperationResult;
import com.sakarrobotics.cloud.robot.adapter.dto.AdapterTaskRequest;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityService;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
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
    private final CommandResultRepository commandResultRepository;
    private final RobotService robotService;
    private final RobotCapabilityService robotCapabilityService;
    private final RobotModelRepository robotModelRepository;
    private final RobotAdapterRegistry robotAdapterRegistry;
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

        // Vendor-neutral routing: a KEENON_CLOUD-adapter robot dispatches through the real
        // Keenon Open Platform adapter (see dispatchViaKeenonAdapter) instead of MQTT — no
        // Keenon robot runs a Sakar MQTT agent, so publishing to that topic for one would
        // never reach it. Every other adapter type (SAKAR_NATIVE and friends) keeps using
        // exactly the MQTT path below, completely unchanged.
        RobotModel model = robotModelRepository.findById(robot.getRobotModelId())
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_MODEL_NOT_FOUND, "Robot model not found"));
        if (model.getAdapterType() == AdapterType.KEENON_CLOUD) {
            return dispatchViaKeenonAdapter(principal, robot, commandType, params, saved);
        }

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

    /**
     * Dispatches a non-lock command through the real Keenon Open Platform
     * adapter instead of MQTT (Keenon integration audit, "Real Keenon
     * command dispatch" / "Fix command dispatch semantics" slices).
     * {@code dispatched} (and whether {@code sentAt} gets set) is driven
     * entirely by {@link AdapterOperationResult#vendorContacted()}, never by
     * whether the adapter call threw or what it returned:
     *
     * <ul>
     *   <li>Adapter throws (OAuth/network/timeout, an unsupported command
     *       for this adapter, a mapping lookup failure) — never contacted
     *       the vendor — {@code dispatched=false}, {@link
     *       CommandStatus#COMMAND_FAILED}.</li>
     *   <li>Adapter returns a pre-vendor-call rejection (e.g. {@link
     *       AdapterOperationResult#rejected}, "no area specified") — also
     *       never contacted the vendor — {@code dispatched=false}, {@link
     *       CommandStatus#COMMAND_FAILED}. Before this fix, any non-thrown
     *       rejection was incorrectly reported as {@code dispatched=true}.</li>
     *   <li>Adapter returns {@link AdapterOperationResult#rejectedByVendor}
     *       — the vendor was reached and gave a definitive non-accept
     *       answer — {@code dispatched=true}, {@link
     *       CommandStatus#COMMAND_FAILED}.</li>
     *   <li>Adapter returns {@link AdapterOperationResult#accepted} (Keenon
     *       code {@code 610000}) — {@code dispatched=true}, {@link
     *       CommandStatus#COMMAND_DISPATCHED} — this project's existing,
     *       honest "accepted but not confirmed complete" terminal state,
     *       never {@code COMMAND_SUCCESS}.</li>
     * </ul>
     *
     * <p>A Keenon API failure never marks the robot offline — that is a
     * separate concern (see {@code KeenonStatusSyncService}), not something
     * this method touches.
     */
    private Issued dispatchViaKeenonAdapter(UserPrincipal principal, Robot robot, NonLockCommandType commandType,
            Map<String, Object> params, RobotCommand command) {
        RobotAdapter adapter = robotAdapterRegistry.resolve(AdapterType.KEENON_CLOUD);

        AdapterOperationResult result;
        try {
            result = switch (commandType) {
                case START_TASK -> adapter.startTask(robot, buildTaskRequest(params));
                case STOP_TASK -> adapter.stopTask(robot);
                case PAUSE_TASK -> adapter.pauseTask(robot);
                case RESUME_TASK -> adapter.resumeTask(robot);
                case RETURN_TO_DOCK -> adapter.returnToDock(robot);
                // No Keenon Open Platform endpoint exists for this in the verified evidence
                // this codebase has (RobotAdapter has no goToPoint method at all) — reported
                // as a known contract gap, never invented. In practice this robot model's own
                // GET_TO_POINT capability row is unlikely to be granted for a KEENON_CLOUD
                // model, so assertSupported() above should already reject it before here.
                case GO_TO_POINT -> throw new ApiException(SakarErrorCode.UNSUPPORTED_CAPABILITY,
                        "GO_TO_POINT has no Keenon Open Platform equivalent in this adapter");
            };
        } catch (ApiException ex) {
            // Never completed a round trip to Keenon (disabled integration, OAuth/network
            // failure, or a rejected pre-vendor-call check inside the adapter itself) —
            // exactly like a failed MQTT publish, this stays "not dispatched".
            command.setStatus(CommandStatus.COMMAND_FAILED);
            command.setCompletedAt(Instant.now());
            RobotCommand saved = robotCommandRepository.save(command);
            recordResult(saved, CommandStatus.COMMAND_FAILED, ex.getMessage());
            auditService.record(principal, robot.getOrganizationId(), robot.getId(), "COMMAND_ISSUED",
                    "NOT_DISPATCHED", null, null, null);
            return new Issued(saved, false, "Not dispatched: " + ex.getMessage());
        }

        CommandStatus status = result.accepted() ? CommandStatus.COMMAND_DISPATCHED : CommandStatus.COMMAND_FAILED;
        command.setStatus(status);
        if (result.vendorContacted()) {
            // Only ever set when the vendor actually received the request (accepted or
            // definitively rejected) — never for a pre-vendor-call rejection, so this stays
            // consistent with dispatched=result.vendorContacted() below and with how
            // RobotCommandController later derives "dispatched" from sentAt on a GET.
            command.setSentAt(Instant.now());
        }
        command.setCompletedAt(Instant.now());
        RobotCommand saved = robotCommandRepository.save(command);
        recordResult(saved, status, result.message());

        String dispatchNote;
        String auditResult;
        if (result.accepted()) {
            dispatchNote = "Accepted by the Keenon Open Platform (not yet physically confirmed): " + result.message();
            auditResult = "SENT";
        } else if (result.vendorContacted()) {
            dispatchNote = "Rejected by the Keenon Open Platform: " + result.message();
            auditResult = "REJECTED";
        } else {
            // A pre-vendor-call rejection (e.g. no area specified) — the vendor was never
            // contacted, so this is "not dispatched" exactly like a failed MQTT publish or
            // an OAuth/network failure, not a vendor rejection.
            dispatchNote = "Not dispatched: " + result.message();
            auditResult = "NOT_DISPATCHED";
        }
        auditService.record(principal, robot.getOrganizationId(), robot.getId(), "COMMAND_ISSUED",
                auditResult, null, null, null);
        return new Issued(saved, result.vendorContacted(), dispatchNote);
    }

    private void recordResult(RobotCommand command, CommandStatus status, String detail) {
        CommandResult result = new CommandResult();
        result.setCommandId(command.getId());
        result.setResult(status.name());
        result.setDetail(detail);
        commandResultRepository.save(result);
    }

    /**
     * Maps {@code START_TASK}'s generic {@code params} onto Keenon's
     * required shape. {@code areaIds} must be the Sakar-side {@code
     * KeenonAreaMapping} row ids the adapter itself already resolves to a
     * vendor area id (see {@code KeenonRobotAdapter.startTask}) — never a
     * raw Keenon area id, and never invented here.
     *
     * <p><strong>Known contract gap (reported, not silently patched in this
     * slice):</strong> there is currently no endpoint that lets a caller
     * discover which {@code KeenonAreaMapping} row ids are valid for a
     * robot — {@code GET /robots/{id}/areas} returns vendor-facing {@code
     * AreaInfo} (vendor area id + display name) only, not the Sakar-side
     * mapping row id this method requires. A caller must already know the
     * id (e.g. from direct DB/API access to {@code keenon_area_mappings})
     * until a follow-up slice closes that gap.
     */
    private AdapterTaskRequest buildTaskRequest(Map<String, Object> params) {
        List<String> areaIds = optionalStringListParam(params, "areaIds");
        String mode = requiredStringParam(params, "mode");
        int repeatCount = optionalIntParam(params, "repeatCount", 1);
        // taskType/returnToDock are part of the generic AdapterTaskRequest shape but are not
        // read anywhere inside KeenonRobotAdapter.startTask today — "CLEANING" and false are
        // inert placeholders, not vendor-facing values.
        return new AdapterTaskRequest("CLEANING", areaIds, mode, repeatCount, false);
    }

    private static String requiredStringParam(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ApiException(SakarErrorCode.VALIDATION_FAILED,
                    "START_TASK requires a '" + key + "' in params");
        }
        return value.toString();
    }

    private static List<String> optionalStringListParam(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> rawList)) {
            throw new ApiException(SakarErrorCode.VALIDATION_FAILED,
                    "START_TASK '" + key + "' must be a list of Sakar Keenon-area-mapping ids");
        }
        return rawList.stream().map(String::valueOf).toList();
    }

    private static int optionalIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            throw new ApiException(SakarErrorCode.VALIDATION_FAILED,
                    "START_TASK '" + key + "' must be an integer, got: " + value);
        }
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

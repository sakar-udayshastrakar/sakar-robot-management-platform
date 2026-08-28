package com.sakarrobotics.cloud.robot.adapter;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.robot.adapter.dto.AdapterOperationResult;
import com.sakarrobotics.cloud.robot.adapter.dto.AdapterTaskRequest;
import com.sakarrobotics.cloud.robot.adapter.dto.AreaInfo;
import com.sakarrobotics.cloud.robot.adapter.dto.BatteryInfo;
import com.sakarrobotics.cloud.robot.adapter.dto.MapInfo;
import com.sakarrobotics.cloud.robot.adapter.dto.RobotStatusSnapshot;
import com.sakarrobotics.cloud.robot.adapter.dto.TelemetryReading;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;

/**
 * The target local path — Sakar Backend -&gt; MQTT -&gt; Sakar Robot Agent -&gt;
 * Peanut SDK / native SDK -&gt; Robot (SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md §7).
 *
 * <p><strong>This adapter is a deliberate stub.</strong> The MQTT command/
 * telemetry channel to the Sakar Robot Agent is not wired in Phase 1
 * (Master Requirements roadmap Phase 2/5). Every method throws
 * {@code FEATURE_NOT_YET_IMPLEMENTED} rather than returning fabricated
 * data — do not remove this behavior to "make it work" before the MQTT
 * integration actually exists.
 */
@Component
public class SakarRobotAdapter implements RobotAdapter {

    @Override
    public AdapterType adapterType() {
        return AdapterType.SAKAR_NATIVE;
    }

    @Override
    public Set<RobotCapabilityType> supportedCapabilities() {
        // Declared as the eventual full set once the local MQTT/agent path exists;
        // every call still fails closed with FEATURE_NOT_YET_IMPLEMENTED today.
        return Set.of(RobotCapabilityType.values());
    }

    @Override
    public RobotStatusSnapshot getStatus(Robot robot) {
        throw notYetImplemented();
    }

    @Override
    public BatteryInfo getBattery(Robot robot) {
        throw notYetImplemented();
    }

    @Override
    public List<TelemetryReading> getTelemetry(Robot robot) {
        throw notYetImplemented();
    }

    @Override
    public List<AreaInfo> getAreas(Robot robot) {
        throw notYetImplemented();
    }

    @Override
    public MapInfo getMap(Robot robot) {
        throw notYetImplemented();
    }

    @Override
    public AdapterOperationResult startTask(Robot robot, AdapterTaskRequest request) {
        throw notYetImplemented();
    }

    @Override
    public AdapterOperationResult stopTask(Robot robot) {
        throw notYetImplemented();
    }

    @Override
    public AdapterOperationResult pauseTask(Robot robot) {
        throw notYetImplemented();
    }

    @Override
    public AdapterOperationResult resumeTask(Robot robot) {
        throw notYetImplemented();
    }

    @Override
    public AdapterOperationResult returnToDock(Robot robot) {
        throw notYetImplemented();
    }

    private static ApiException notYetImplemented() {
        return new ApiException(SakarErrorCode.FEATURE_NOT_YET_IMPLEMENTED,
                "The local Sakar Robot Agent path (MQTT) is not yet implemented — "
                        + "this robot model cannot be served by the Sakar-native adapter in this phase");
    }
}

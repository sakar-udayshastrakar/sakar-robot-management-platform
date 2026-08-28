package com.sakarrobotics.cloud.robot.adapter;

import java.util.Set;

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

import java.util.List;

/**
 * The single abstraction every robot integration implements
 * (Master Requirements §6.A, SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md §8). The
 * Robot Command Service and read APIs call only this interface — never a
 * vendor SDK/HTTP client directly — so onboarding a new vendor never
 * requires touching the core backend.
 *
 * <p>Every write method returns {@link AdapterOperationResult}, which
 * deliberately cannot report "physically confirmed" on its own — see that
 * type's Javadoc and Master Requirements Part 40's governing rule.
 */
public interface RobotAdapter {

    AdapterType adapterType();

    /** The capabilities this adapter implementation is technically able to service at all — a ceiling, not a per-robot grant. Compare against {@code robot_capabilities} (DB) for the per-model, per-robot business decision. */
    Set<RobotCapabilityType> supportedCapabilities();

    RobotStatusSnapshot getStatus(Robot robot);

    BatteryInfo getBattery(Robot robot);

    List<TelemetryReading> getTelemetry(Robot robot);

    List<AreaInfo> getAreas(Robot robot);

    MapInfo getMap(Robot robot);

    AdapterOperationResult startTask(Robot robot, AdapterTaskRequest request);

    AdapterOperationResult stopTask(Robot robot);

    AdapterOperationResult pauseTask(Robot robot);

    AdapterOperationResult resumeTask(Robot robot);

    AdapterOperationResult returnToDock(Robot robot);
}

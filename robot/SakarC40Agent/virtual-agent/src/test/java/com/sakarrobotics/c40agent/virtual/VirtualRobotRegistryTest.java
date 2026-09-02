package com.sakarrobotics.c40agent.virtual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.sakarrobotics.c40agent.api.mqtt.RobotCommandResultReporter;

/**
 * Software-test-only coverage of {@link VirtualRobotRegistry} (Roadmap
 * Phase 9 "Multi-robot", see ../../VIRTUAL_C40_SIMULATOR.md). No
 * physical robot, no MQTT broker anywhere in this test.
 */
class VirtualRobotRegistryTest {

    @Test
    void withDefaultFleet_containsExactlyTheThreeNamedRobots() {
        VirtualRobotRegistry registry = VirtualRobotRegistry.withDefaultFleet();

        assertEquals(3, registry.all().size());
        assertEquals(VirtualRobotRegistry.DEFAULT_ROBOT_1, registry.get(VirtualRobotRegistry.DEFAULT_ROBOT_1).getRobotId());
        assertEquals(VirtualRobotRegistry.DEFAULT_ROBOT_2, registry.get(VirtualRobotRegistry.DEFAULT_ROBOT_2).getRobotId());
        assertEquals(VirtualRobotRegistry.DEFAULT_ROBOT_3, registry.get(VirtualRobotRegistry.DEFAULT_ROBOT_3).getRobotId());
    }

    @Test
    void get_unknownRobotId_returnsNullRatherThanFabricatingOne() {
        VirtualRobotRegistry registry = VirtualRobotRegistry.withDefaultFleet();

        assertNull(registry.get("NO-SUCH-ROBOT"));
    }

    @Test
    void oneRobotsCommand_neverAffectsAnotherRobotsState() {
        // Phase 15 #15 "multiple robots isolation".
        VirtualRobotRegistry registry = VirtualRobotRegistry.withDefaultFleet();
        VirtualRobotEngine robot1 = registry.get(VirtualRobotRegistry.DEFAULT_ROBOT_1);
        VirtualRobotEngine robot2 = registry.get(VirtualRobotRegistry.DEFAULT_ROBOT_2);
        VirtualRobotEngine robot3 = registry.get(VirtualRobotRegistry.DEFAULT_ROBOT_3);

        int robot2BatteryBefore = robot2.getState().getBatteryPercentage();
        int robot3BatteryBefore = robot3.getState().getBatteryPercentage();
        Integer robot2DestinationBefore = robot2.getState().getCurrentDestinationId();
        Integer robot3DestinationBefore = robot3.getState().getCurrentDestinationId();

        RobotCommandResultReporter reporter = mock(RobotCommandResultReporter.class);
        robot1.goToPoint(VirtualMap.DESTINATION_ID_ROOM_A, "cmd-isolation", reporter);
        verify(reporter, timeout(5000)).reportCompleted(org.mockito.ArgumentMatchers.any());

        assertEquals(VirtualMap.DESTINATION_ID_ROOM_A, robot1.getState().getCurrentDestinationId());
        assertEquals(robot2BatteryBefore, robot2.getState().getBatteryPercentage());
        assertEquals(robot3BatteryBefore, robot3.getState().getBatteryPercentage());
        assertEquals(robot2DestinationBefore, robot2.getState().getCurrentDestinationId());
        assertEquals(robot3DestinationBefore, robot3.getState().getCurrentDestinationId());
        assertEquals(NavigationState.IDLE, robot2.getState().getNavigationState());
        assertEquals(NavigationState.IDLE, robot3.getState().getNavigationState());
    }

    @Test
    void oneRobotGoingOffline_neverAffectsAnotherRobotsOnlineState() {
        VirtualRobotRegistry registry = VirtualRobotRegistry.withDefaultFleet();

        registry.get(VirtualRobotRegistry.DEFAULT_ROBOT_1).setOnline(false);

        assertEquals(false, registry.get(VirtualRobotRegistry.DEFAULT_ROBOT_1).isOnline());
        assertEquals(true, registry.get(VirtualRobotRegistry.DEFAULT_ROBOT_2).isOnline());
        assertEquals(true, registry.get(VirtualRobotRegistry.DEFAULT_ROBOT_3).isOnline());
    }
}

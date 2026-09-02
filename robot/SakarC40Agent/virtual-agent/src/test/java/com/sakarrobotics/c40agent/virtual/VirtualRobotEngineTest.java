package com.sakarrobotics.c40agent.virtual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.c40agent.api.mqtt.RobotCommandResultReporter;
import com.sakarrobotics.c40agent.telemetry.Destination;

/**
 * Software-test-only coverage of {@link VirtualRobotEngine} (Roadmap
 * Phase 9 "Virtual C40 Robot Simulator", see
 * ../../VIRTUAL_C40_SIMULATOR.md). No Android runtime, no Peanut SDK, no
 * MQTT broker, no physical robot anywhere in this test class — this is
 * SOFTWARE TEST VERIFIED coverage of the simulation's own logic only.
 */
@ExtendWith(MockitoExtension.class)
class VirtualRobotEngineTest {

    private static final String ROBOT_ID = "TEST-VIRTUAL-C40";

    @Mock
    private RobotCommandResultReporter reporter;

    private VirtualRobotEngine newEngine() {
        return new VirtualRobotEngine(ROBOT_ID, "Test Virtual C40");
    }

    // ---------------------------------------------------------------
    // Creation / initial state (Phase 15 #1)
    // ---------------------------------------------------------------

    @Test
    void newEngine_startsOnlineAtTheLobbyWithFullBatteryAndIdleNavigation() {
        VirtualRobotState state = newEngine().getState();

        assertEquals(ROBOT_ID, state.getRobotId());
        assertTrue(state.isOnline());
        assertEquals(100, state.getBatteryPercentage());
        assertFalse(state.isCharging());
        assertEquals(VirtualMap.MAP_ID, state.getCurrentMapId());
        assertEquals(VirtualMap.MAP_MD5, state.getCurrentMapMd5());
        assertEquals(VirtualMap.DESTINATION_ID_LOBBY, state.getCurrentDestinationId());
        assertEquals(NavigationState.IDLE, state.getNavigationState());
    }

    // ---------------------------------------------------------------
    // Destination discovery (Phase 15 #2)
    // ---------------------------------------------------------------

    @Test
    void getAllDestinations_returnsAllFourVirtualDestinations() {
        List<Destination> destinations = newEngine().getAllDestinations();

        assertEquals(4, destinations.size());
        assertTrue(destinations.stream().anyMatch(d -> d.getId() == VirtualMap.DESTINATION_ID_LOBBY && "Lobby".equals(d.getName())));
        assertTrue(destinations.stream().anyMatch(d -> d.getId() == VirtualMap.DESTINATION_ID_RECEPTION));
        assertTrue(destinations.stream().anyMatch(d -> d.getId() == VirtualMap.DESTINATION_ID_ROOM_A));
        assertTrue(destinations.stream().anyMatch(d -> d.getId() == VirtualMap.DESTINATION_ID_CHARGING_STATION));
    }

    @Test
    void getAllDestinations_everyDestinationBelongsToTheSameVirtualMap() {
        // Phase 15 #16 "map association"
        for (Destination destination : newEngine().getAllDestinations()) {
            assertEquals(VirtualMap.MAP_MD5, destination.getMapId());
        }
    }

    @Test
    void getAllDestinations_whileOffline_throwsRatherThanReturningData() {
        VirtualRobotEngine engine = newEngine();
        engine.setOnline(false);

        assertThrows(VirtualRobotOfflineException.class, engine::getAllDestinations);
    }

    // ---------------------------------------------------------------
    // GO_TO_POINT (Phase 15 #3-#8)
    // ---------------------------------------------------------------

    @Test
    void goToPoint_validDestination_progressesThroughMovingToArrivedAndUpdatesPosition() {
        VirtualRobotEngine engine = newEngine();

        engine.goToPoint(VirtualMap.DESTINATION_ID_ROOM_A, "cmd-1", reporter);

        verify(reporter).reportExecuting();
        assertEquals(NavigationState.MOVING, engine.getState().getNavigationState());

        verify(reporter, timeout(5000)).reportCompleted(contains("SIMULATED"));
        VirtualRobotState finalState = engine.getState();
        assertEquals(NavigationState.ARRIVED, finalState.getNavigationState());
        assertEquals(VirtualMap.DESTINATION_ID_ROOM_A, finalState.getCurrentDestinationId());
        Destination roomA = VirtualMap.byId(VirtualMap.DESTINATION_ID_ROOM_A);
        assertEquals(roomA.getPose().getPosition().getX(), finalState.getCurrentX());
        assertEquals(roomA.getPose().getPosition().getY(), finalState.getCurrentY());
        assertEquals("cmd-1", finalState.getLastCommandId());
    }

    @Test
    void goToPoint_invalidDestination_reportsFailedImmediatelyWithoutMoving() {
        VirtualRobotEngine engine = newEngine();

        engine.goToPoint(999999, "cmd-2", reporter);

        verify(reporter).reportFailed(contains("Invalid destinationId"));
        verify(reporter, never()).reportExecuting();
        assertEquals(NavigationState.IDLE, engine.getState().getNavigationState());
    }

    @Test
    void goToPoint_robotOffline_reportsFailedImmediatelyWithoutMoving() {
        VirtualRobotEngine engine = newEngine();
        engine.setOnline(false);

        engine.goToPoint(VirtualMap.DESTINATION_ID_ROOM_A, "cmd-3", reporter);

        verify(reporter).reportFailed(contains("OFFLINE"));
        verify(reporter, never()).reportExecuting();
    }

    @Test
    void goToPoint_insufficientBattery_reportsFailedImmediatelyWithoutMoving() {
        // VirtualRobotEngine intentionally has no public battery setter (a real robot doesn't let
        // you set its battery either) - a deliberately aggressive drain rate (60%/tick over 2
        // ticks = 100 -> 0, clamped) reaches a low-battery state in one real navigation round
        // trip, quickly and deterministically, instead of looping many slow round trips against
        // default rates.
        VirtualRobotEngine drained = new VirtualRobotEngine(ROBOT_ID, "Test Virtual C40",
                VirtualRobotEngine.DEFAULT_MINIMUM_BATTERY_TO_NAVIGATE, 60, 5, 2, 20L,
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor());
        drained.goToPoint(VirtualMap.DESTINATION_ID_RECEPTION, "drain-1", noOpReporter());
        waitForNavigationState(drained, NavigationState.ARRIVED);
        assertTrue(drained.getState().getBatteryPercentage() < VirtualRobotEngine.DEFAULT_MINIMUM_BATTERY_TO_NAVIGATE,
                "test setup should have drained the battery below the navigation threshold");

        drained.goToPoint(VirtualMap.DESTINATION_ID_ROOM_A, "cmd-low-battery", reporter);

        verify(reporter).reportFailed(contains("Insufficient battery"));
        verify(reporter, never()).reportExecuting();
    }

    @Test
    void goToPoint_navigationBlocked_reportsFailedAndIsOneShot() {
        VirtualRobotEngine engine = newEngine();
        engine.setBlocked(true);

        engine.goToPoint(VirtualMap.DESTINATION_ID_ROOM_A, "cmd-blocked", reporter);
        verify(reporter).reportFailed(contains("blocked"));
        assertEquals(NavigationState.BLOCKED, engine.getState().getNavigationState());

        // One-shot: the next attempt is not blocked.
        engine.goToPoint(VirtualMap.DESTINATION_ID_ROOM_A, "cmd-retry", reporter);
        verify(reporter).reportExecuting();
    }

    // ---------------------------------------------------------------
    // RETURN_TO_DOCK / charging (Phase 15 #9-#12)
    // ---------------------------------------------------------------

    @Test
    void returnToDock_arrivesAtChargingStationAndStartsCharging() {
        VirtualRobotEngine engine = newEngine();

        engine.returnToDock("cmd-dock", reporter);

        verify(reporter, timeout(5000)).reportCompleted(contains("charging"));
        VirtualRobotState state = engine.getState();
        assertEquals(VirtualMap.DESTINATION_ID_CHARGING_STATION, state.getCurrentDestinationId());
        assertTrue(state.isCharging());
    }

    @Test
    void tick_whileCharging_increasesBatteryUpToButNotBeyond100() {
        // Short interval so returnToDock's own simulated arrival completes within the wait below.
        // Safe from interference with the manual tick() calls that follow: simulateArrival
        // schedules exactly ticksToArrive tick() calls, all consumed DURING the movement phase -
        // by the time ARRIVED/charging is reached, nothing further is scheduled.
        VirtualRobotEngine engine = new VirtualRobotEngine(ROBOT_ID, "Test", 10, 2, 5, 3, 50L,
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor());
        engine.returnToDock("cmd-dock", noOpReporter());
        waitForNavigationState(engine, NavigationState.ARRIVED);
        assertTrue(engine.getState().isCharging());

        int before = engine.getState().getBatteryPercentage();
        engine.tick();
        assertEquals(Math.min(100, before + 5), engine.getState().getBatteryPercentage());

        for (int i = 0; i < 50; i++) {
            engine.tick();
        }
        assertEquals(100, engine.getState().getBatteryPercentage());
        assertFalse(engine.getState().isCharging(), "charging should stop once battery reaches 100%");
    }

    @Test
    void tick_whileMoving_decreasesBatteryButNeverBelowZero() {
        // Same very-large-interval reasoning as the charging test above.
        VirtualRobotEngine engine = new VirtualRobotEngine(ROBOT_ID, "Test", 0, 2, 5, 3, 3_600_000L,
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor());
        engine.goToPoint(VirtualMap.DESTINATION_ID_ROOM_A, "cmd-long-move", noOpReporter());
        assertEquals(NavigationState.MOVING, engine.getState().getNavigationState());

        int before = engine.getState().getBatteryPercentage();
        engine.tick();
        assertEquals(before - 2, engine.getState().getBatteryPercentage());

        for (int i = 0; i < 100; i++) {
            engine.tick();
        }
        assertEquals(0, engine.getState().getBatteryPercentage());
    }

    @Test
    void tick_whileIdleAndNotCharging_doesNotChangeBattery() {
        VirtualRobotEngine engine = newEngine();
        int before = engine.getState().getBatteryPercentage();

        engine.tick();

        assertEquals(before, engine.getState().getBatteryPercentage());
    }

    // ---------------------------------------------------------------

    private static RobotCommandResultReporter noOpReporter() {
        return new RobotCommandResultReporter() {
            @Override
            public void reportExecuting() {
            }

            @Override
            public void reportCompleted(String detail) {
            }

            @Override
            public void reportDispatched(String detail) {
            }

            @Override
            public void reportFailed(String detail) {
            }
        };
    }

    private static void waitForNavigationState(VirtualRobotEngine engine, NavigationState expected) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (engine.getState().getNavigationState() == expected) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}

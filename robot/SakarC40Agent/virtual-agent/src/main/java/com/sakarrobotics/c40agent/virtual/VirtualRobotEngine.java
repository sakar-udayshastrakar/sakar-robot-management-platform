package com.sakarrobotics.c40agent.virtual;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.sakarrobotics.c40agent.api.mqtt.RobotCommandResultReporter;
import com.sakarrobotics.c40agent.telemetry.Destination;

/**
 * The core simulation engine for one Virtual C40 Robot (Roadmap Phase 9,
 * see ../../VIRTUAL_C40_SIMULATOR.md). Behaves like a robot from the
 * perspective of the Sakar Backend / Web / Mobile applications, but
 * NEVER calls the real Peanut SDK, never opens a socket, and never
 * references a physical robot's network address - see {@code
 * VirtualAgentRealSdkIsolationTest} for the automated test that enforces
 * this at the source level (not just by this Javadoc's word).
 *
 * <p><strong>REAL ROBOT vs SIMULATED ROBOT:</strong> this class drives
 * {@link RobotCommandResultReporter} directly - the SAME lifecycle
 * contract {@code PeanutSdkReturnToDockExecutor}/{@code
 * PeanutSdkGoToPointExecutor} use for the real robot - but every {@link
 * RobotCommandResultReporter#reportCompleted} call here is prefixed
 * {@code "SIMULATED"} in its detail text, and completion here means
 * "the simulation's own internal state reached ARRIVED", never "a
 * physical robot moved." Unlike the real executors (which must report
 * {@code DISPATCHED}, not {@code COMPLETED}, because they cannot confirm
 * a physical robot's real-world effect), this engine has complete,
 * genuine ground truth over its own simulated world, so reporting
 * {@code COMPLETED} here is honest, not an overclaim - the {@code
 * "SIMULATED"} prefix is what prevents it from being confused with a
 * physical-robot completion.
 */
public final class VirtualRobotEngine {

    /** Below this, {@link #goToPoint}/{@link #returnToDock} fail with a deterministic "insufficient battery" result. */
    public static final int DEFAULT_MINIMUM_BATTERY_TO_NAVIGATE = 10;

    private static final int DEFAULT_BATTERY_DRAIN_PER_TICK = 2;
    private static final int DEFAULT_BATTERY_CHARGE_PER_TICK = 5;
    private static final int DEFAULT_TICKS_TO_ARRIVE = 3;
    private static final long DEFAULT_TICK_INTERVAL_MILLIS = 100L;

    private final String robotId;
    private final String robotName;
    private final int minimumBatteryToNavigate;
    private final int batteryDrainPerTick;
    private final int batteryChargePerTick;
    private final int ticksToArrive;
    private final long tickIntervalMillis;
    private final ScheduledExecutorService executor;

    private volatile boolean online = true;
    private volatile boolean blocked;
    private volatile int batteryPercentage = 100;
    private volatile boolean charging;
    private volatile Destination currentPosition;
    private volatile NavigationState navigationState = NavigationState.IDLE;
    private volatile String lastCommandId;

    public VirtualRobotEngine(String robotId, String robotName) {
        this(robotId, robotName, DEFAULT_MINIMUM_BATTERY_TO_NAVIGATE, DEFAULT_BATTERY_DRAIN_PER_TICK,
                DEFAULT_BATTERY_CHARGE_PER_TICK, DEFAULT_TICKS_TO_ARRIVE, DEFAULT_TICK_INTERVAL_MILLIS,
                newDaemonScheduledExecutor(robotId));
    }

    /** Full-control constructor for tests - an injectable executor lets a test observe every tick deterministically. */
    public VirtualRobotEngine(String robotId, String robotName, int minimumBatteryToNavigate, int batteryDrainPerTick,
            int batteryChargePerTick, int ticksToArrive, long tickIntervalMillis, ScheduledExecutorService executor) {
        this.robotId = robotId;
        this.robotName = robotName;
        this.minimumBatteryToNavigate = minimumBatteryToNavigate;
        this.batteryDrainPerTick = batteryDrainPerTick;
        this.batteryChargePerTick = batteryChargePerTick;
        this.ticksToArrive = ticksToArrive;
        this.tickIntervalMillis = tickIntervalMillis;
        this.executor = executor;
        this.currentPosition = VirtualMap.home();
    }

    // ---------------------------------------------------------------
    // State (Phase 2, Phase 12 "dashboard status")
    // ---------------------------------------------------------------

    public VirtualRobotState getState() {
        Destination position = currentPosition;
        double[] orientationQuaternion = position.getPose() != null && position.getPose().getOrientation() != null
                ? new double[] {position.getPose().getOrientation().getW(), position.getPose().getOrientation().getX(),
                        position.getPose().getOrientation().getY(), position.getPose().getOrientation().getZ()}
                : new double[] {1.0, 0.0, 0.0, 0.0};
        double x = position.getPose() != null && position.getPose().getPosition() != null ? position.getPose().getPosition().getX() : 0.0;
        double y = position.getPose() != null && position.getPose().getPosition() != null ? position.getPose().getPosition().getY() : 0.0;
        double z = position.getPose() != null && position.getPose().getPosition() != null ? position.getPose().getPosition().getZ() : 0.0;
        return new VirtualRobotState(robotId, robotName, online, batteryPercentage, charging, VirtualMap.MAP_ID,
                VirtualMap.MAP_MD5, position.getId(), x, y, z, orientationQuaternion, navigationState, lastCommandId);
    }

    public String getRobotId() {
        return robotId;
    }

    // ---------------------------------------------------------------
    // Simulation controls (Phase 8, Phase 10) - deterministic test/ops
    // hooks, not something a remote command can toggle.
    // ---------------------------------------------------------------

    public void setOnline(boolean online) {
        this.online = online;
    }

    public boolean isOnline() {
        return online;
    }

    /** Forces the next navigation command to fail with a deterministic "navigation blocked" result. */
    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    // ---------------------------------------------------------------
    // Destination discovery (Roadmap Phase 8/9's virtual equivalent of
    // getAllDestPose() - Phase 4). Read-only, exactly like the real
    // C40RobotController.getAllDestinations(): it is unaffected by
    // OperatingMode-style gating concepts because there is no actuation
    // here, but it DOES respect `online`, because a real robot cannot be
    // queried at all while offline either.
    // ---------------------------------------------------------------

    public List<Destination> getAllDestinations() {
        if (!online) {
            throw new VirtualRobotOfflineException(robotId);
        }
        return VirtualMap.allDestinations();
    }

    // ---------------------------------------------------------------
    // GO_TO_POINT / RETURN_TO_DOCK (Phase 5/6). Both drive the SAME
    // RobotCommandResultReporter contract the real executors use - see
    // class Javadoc for the COMPLETED-vs-DISPATCHED distinction.
    // ---------------------------------------------------------------

    public synchronized void goToPoint(int destinationId, String commandId, RobotCommandResultReporter reporter) {
        lastCommandId = commandId;
        if (!online) {
            reporter.reportFailed("Virtual robot " + robotId + " is OFFLINE - command not executed.");
            return;
        }
        Destination destination = VirtualMap.byId(destinationId);
        if (destination == null) {
            reporter.reportFailed("Invalid destinationId=" + destinationId + " - no such destination on " + VirtualMap.MAP_ID + ".");
            return;
        }
        if (batteryPercentage < minimumBatteryToNavigate) {
            reporter.reportFailed("Insufficient battery (" + batteryPercentage + "% < " + minimumBatteryToNavigate
                    + "% required) - navigation not started.");
            return;
        }
        if (blocked) {
            blocked = false; // one-shot, like a single obstacle the next command can retry past
            navigationState = NavigationState.BLOCKED;
            reporter.reportFailed("Navigation blocked (simulated obstacle) - destinationId=" + destinationId + ".");
            return;
        }

        navigationState = NavigationState.MOVING;
        reporter.reportExecuting();
        simulateArrival(destination, reporter);
    }

    public synchronized void returnToDock(String commandId, RobotCommandResultReporter reporter) {
        // goToPoint's simulateArrival already sets charging=true on arrival at the charging-station
        // destination id (see below) - this wrapper only adds RETURN_TO_DOCK-specific detail text.
        goToPoint(VirtualMap.chargingStation().getId(), commandId, new RobotCommandResultReporter() {
            @Override
            public void reportExecuting() {
                reporter.reportExecuting();
            }

            @Override
            public void reportCompleted(String detail) {
                reporter.reportCompleted("SIMULATED — virtual robot " + robotId
                        + " reached the virtual charging station and is now charging. " + detail);
            }

            @Override
            public void reportDispatched(String detail) {
                reporter.reportDispatched(detail);
            }

            @Override
            public void reportFailed(String detail) {
                reporter.reportFailed(detail);
            }
        });
    }

    /**
     * Applies one deterministic simulation step: battery drains while
     * {@link NavigationState#MOVING}, charges while {@link #charging}, and
     * is otherwise unchanged. Exposed publicly so battery arithmetic is
     * directly unit-testable without waiting on the asynchronous
     * arrival-simulation schedule used by {@link #goToPoint}.
     */
    public synchronized void tick() {
        if (navigationState == NavigationState.MOVING) {
            batteryPercentage = clampBattery(batteryPercentage - batteryDrainPerTick);
        } else if (charging) {
            batteryPercentage = clampBattery(batteryPercentage + batteryChargePerTick);
            if (batteryPercentage >= 100) {
                charging = false;
            }
        }
    }

    // ---------------------------------------------------------------

    private void simulateArrival(Destination destination, RobotCommandResultReporter reporter) {
        for (int i = 0; i < ticksToArrive; i++) {
            executor.schedule(this::tick, tickIntervalMillis * (i + 1), TimeUnit.MILLISECONDS);
        }
        executor.schedule(() -> {
            synchronized (this) {
                currentPosition = destination;
                navigationState = NavigationState.ARRIVED;
                if (destination.getId() == VirtualMap.DESTINATION_ID_CHARGING_STATION) {
                    charging = true;
                }
            }
            reporter.reportCompleted("SIMULATED — virtual robot " + robotId + " arrived at destinationId="
                    + destination.getId() + " (\"" + destination.getName() + "\") on " + VirtualMap.MAP_ID
                    + " after " + ticksToArrive + " simulated tick(s). No Peanut SDK call was made and no "
                    + "physical robot was contacted.");
        }, tickIntervalMillis * ticksToArrive, TimeUnit.MILLISECONDS);
    }

    private static int clampBattery(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static ScheduledExecutorService newDaemonScheduledExecutor(String robotId) {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "virtual-robot-" + robotId);
            thread.setDaemon(true);
            return thread;
        };
        return new ScheduledThreadPoolExecutor(1, threadFactory);
    }
}

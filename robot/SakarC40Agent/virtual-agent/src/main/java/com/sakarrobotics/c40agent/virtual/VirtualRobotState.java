package com.sakarrobotics.c40agent.virtual;

/**
 * Immutable snapshot of a {@link VirtualRobotEngine}'s current state
 * (Roadmap Phase 9, see ../../VIRTUAL_C40_SIMULATOR.md). This is
 * SIMULATION-ONLY data - it is never produced by, or fed into, the real
 * Peanut SDK path ({@code PeanutSdkBridge}/{@code C40RobotController}).
 *
 * <p>Field set matches the simulator spec's Phase 2 list exactly.
 */
public final class VirtualRobotState {

    private final String robotId;
    private final String robotName;
    private final boolean online;
    private final int batteryPercentage;
    private final boolean charging;
    private final String currentMapId;
    private final String currentMapMd5;
    private final Integer currentDestinationId;
    private final double currentX;
    private final double currentY;
    private final double currentZ;
    private final double[] orientation;
    private final NavigationState navigationState;
    private final String lastCommandId;

    public VirtualRobotState(String robotId, String robotName, boolean online, int batteryPercentage, boolean charging,
            String currentMapId, String currentMapMd5, Integer currentDestinationId, double currentX, double currentY,
            double currentZ, double[] orientation, NavigationState navigationState, String lastCommandId) {
        this.robotId = robotId;
        this.robotName = robotName;
        this.online = online;
        this.batteryPercentage = batteryPercentage;
        this.charging = charging;
        this.currentMapId = currentMapId;
        this.currentMapMd5 = currentMapMd5;
        this.currentDestinationId = currentDestinationId;
        this.currentX = currentX;
        this.currentY = currentY;
        this.currentZ = currentZ;
        this.orientation = orientation;
        this.navigationState = navigationState;
        this.lastCommandId = lastCommandId;
    }

    public String getRobotId() {
        return robotId;
    }

    public String getRobotName() {
        return robotName;
    }

    public boolean isOnline() {
        return online;
    }

    public int getBatteryPercentage() {
        return batteryPercentage;
    }

    public boolean isCharging() {
        return charging;
    }

    public String getCurrentMapId() {
        return currentMapId;
    }

    public String getCurrentMapMd5() {
        return currentMapMd5;
    }

    /** {@code null} until the robot has been sent to at least one destination. */
    public Integer getCurrentDestinationId() {
        return currentDestinationId;
    }

    public double getCurrentX() {
        return currentX;
    }

    public double getCurrentY() {
        return currentY;
    }

    public double getCurrentZ() {
        return currentZ;
    }

    /** {@code [w, x, y, z]} quaternion, matching the shape already used by the real getAllDestPose() integration. */
    public double[] getOrientation() {
        return orientation;
    }

    public NavigationState getNavigationState() {
        return navigationState;
    }

    /** {@code null} until at least one command has been processed. */
    public String getLastCommandId() {
        return lastCommandId;
    }
}

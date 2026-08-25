package com.sakarrobotics.c40agent.telemetry;

/**
 * Decoupled mirror of the fields exposed by the SDK's own
 * {@code com.keenon.sdk.component.runtime.RuntimeInfo} (verified by
 * decompiling peanut-sdk-release.aar - see COMPATIBILITY_REPORT.md).
 * Only the sdk module is allowed to import com.keenon.* types; this class
 * is how that data crosses the module boundary.
 */
public final class RuntimeSnapshot {

    private final int workMode;
    private final int syncStatus;
    private final int power;
    private final Double totalOdo;
    private final boolean emergencyEnable;
    private final boolean emergencyOpen;
    private final int motorStatus;
    private final String robotArmInfo;
    private final String robotStm32Info;
    private final String robotIp;
    private final String robotProperties;
    private final String destList;

    public RuntimeSnapshot(int workMode, int syncStatus, int power, Double totalOdo,
                            boolean emergencyEnable, boolean emergencyOpen, int motorStatus,
                            String robotArmInfo, String robotStm32Info, String robotIp,
                            String robotProperties, String destList) {
        this.workMode = workMode;
        this.syncStatus = syncStatus;
        this.power = power;
        this.totalOdo = totalOdo;
        this.emergencyEnable = emergencyEnable;
        this.emergencyOpen = emergencyOpen;
        this.motorStatus = motorStatus;
        this.robotArmInfo = robotArmInfo;
        this.robotStm32Info = robotStm32Info;
        this.robotIp = robotIp;
        this.robotProperties = robotProperties;
        this.destList = destList;
    }

    public int getWorkMode() {
        return workMode;
    }

    public int getSyncStatus() {
        return syncStatus;
    }

    public int getPower() {
        return power;
    }

    public Double getTotalOdo() {
        return totalOdo;
    }

    public boolean isEmergencyEnable() {
        return emergencyEnable;
    }

    public boolean isEmergencyOpen() {
        return emergencyOpen;
    }

    public int getMotorStatus() {
        return motorStatus;
    }

    public String getRobotArmInfo() {
        return robotArmInfo;
    }

    public String getRobotStm32Info() {
        return robotStm32Info;
    }

    public String getRobotIp() {
        return robotIp;
    }

    public String getRobotProperties() {
        return robotProperties;
    }

    public String getDestList() {
        return destList;
    }
}

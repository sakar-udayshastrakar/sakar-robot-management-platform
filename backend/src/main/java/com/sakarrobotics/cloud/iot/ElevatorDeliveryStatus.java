package com.sakarrobotics.cloud.iot;

/**
 * RECORDED means an operator logged that an elevator configuration was
 * delivered to a robot/elevator controller — never a real delivery
 * confirmation, since no delivery channel to a physical elevator controller
 * exists in this codebase (same convention as {@code ota.DeploymentStatus}).
 */
public enum ElevatorDeliveryStatus {
    RECORDED,
    FAILED
}

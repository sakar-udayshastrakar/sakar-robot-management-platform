package com.sakarrobotics.cloud.task;

/**
 * The real lifecycle {@link RobotTask#getStatus()} moves through — stored
 * as a plain string on the entity (unchanged, pre-existing column), this
 * enum exists only so {@link RobotTaskService} and its tests share one
 * source of truth for the exact values instead of repeating string
 * literals. Vendor-neutral by design (Master Requirements "Task Model").
 */
public enum TaskLifecycleStatus {
    CREATED,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED
}

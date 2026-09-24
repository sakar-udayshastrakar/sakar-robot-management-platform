package com.sakarrobotics.cloud.robot.registry;

/**
 * Commercial use-type (V20__robot_inventory_fields.sql) — independent of
 * {@link RobotLifecycleStatus}, which tracks deployment state
 * (registered/active/deactivated), not commercial terms.
 */
public enum RobotUseType {
    TRIAL,
    PRODUCTION
}

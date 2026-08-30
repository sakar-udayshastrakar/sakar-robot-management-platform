package com.sakarrobotics.cloud.alert;

/**
 * The alert types this backend actually generates today (Roadmap Phase 6/9).
 * {@link RobotAlert#getAlertType()} stores this as a plain string (matching
 * the entity's existing free-text column) rather than a JPA enum, so future
 * generators can add a type without a migration — this enum exists only to
 * give the current generators (and their tests) a single source of truth
 * for the exact string values in use.
 */
public enum AlertType {
    LOW_BATTERY,
    OFFLINE
}

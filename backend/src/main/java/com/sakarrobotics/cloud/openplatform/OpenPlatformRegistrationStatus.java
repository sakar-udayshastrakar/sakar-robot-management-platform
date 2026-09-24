package com.sakarrobotics.cloud.openplatform;

/**
 * A real review gate, not a fabricated "pass" — a registration starts
 * PENDING and only moves to APPROVED/REJECTED via an explicit ROLE_MANAGE
 * action (see {@link OpenPlatformService#review}), never automatically.
 */
public enum OpenPlatformRegistrationStatus {
    PENDING,
    APPROVED,
    REJECTED
}

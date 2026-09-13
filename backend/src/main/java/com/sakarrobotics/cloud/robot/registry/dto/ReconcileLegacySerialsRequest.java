package com.sakarrobotics.cloud.robot.registry.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * The caller must name the target organization explicitly — this is a bulk, cross-robot
 * administrative action, not a single-robot one, so it deliberately does not infer scope from a
 * path variable the way area/back-point sync does; mirrors {@code SyncRobotsRequest}'s identical
 * reasoning for Keenon robot discovery.
 */
public record ReconcileLegacySerialsRequest(@NotNull UUID organizationId) {
}

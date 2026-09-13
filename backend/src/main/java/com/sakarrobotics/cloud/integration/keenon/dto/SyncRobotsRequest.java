package com.sakarrobotics.cloud.integration.keenon.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The caller must supply both the Keenon store id and the target Sakar
 * organization explicitly — mirrors {@code SyncAreasRequest}'s "never
 * hardcode or infer the store id" rule, extended to the organization since
 * robot discovery (unlike area/back-point sync) has no already-registered
 * robot to scope a path variable to; it can create brand-new robots, so the
 * destination organization must be named the same way {@code POST
 * /api/v1/robots} (registration) already requires.
 */
public record SyncRobotsRequest(@NotBlank String storeId, @NotNull UUID organizationId) {
}

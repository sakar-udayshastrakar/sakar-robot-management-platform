package com.sakarrobotics.cloud.robot.adapter.dto;

/**
 * One cleanable area as currently reported by the adapter. {@code vendorAreaId}
 * is live vendor configuration and must never be hardcoded or cached
 * indefinitely by a caller — resync it (Master Requirements Part 40,
 * "current area IDs must not be hardcoded"). Resolving a Sakar-facing area
 * selection to the current {@code vendorAreaId} is
 * {@code com.sakarrobotics.cloud.integration.keenon.KeenonAreaMappingService}'s job.
 */
public record AreaInfo(String vendorAreaId, String displayName) {
}

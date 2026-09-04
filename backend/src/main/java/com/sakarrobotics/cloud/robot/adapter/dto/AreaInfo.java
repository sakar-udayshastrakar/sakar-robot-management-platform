package com.sakarrobotics.cloud.robot.adapter.dto;

/**
 * One cleanable area as currently reported by the adapter. {@code vendorAreaId}
 * is live vendor configuration and must never be hardcoded or cached
 * indefinitely by a caller — resync it (Master Requirements Part 40,
 * "current area IDs must not be hardcoded").
 *
 * <p>{@code sakarAreaId} is the Sakar-owned {@code KeenonAreaMapping} row id
 * that resolves this vendor area to a stable Sakar identity — the same
 * value a caller must supply (as one entry of {@code sakarAreaIds}) when
 * issuing a {@code START_TASK} command (see {@code
 * KeenonRobotAdapter.startTask} / {@code RobotCommandService}). It is
 * {@code null} when the vendor currently reports an area that has no
 * corresponding synced Sakar mapping row yet — never fabricated to fill
 * the gap.
 */
public record AreaInfo(String vendorAreaId, String displayName, String sakarAreaId) {
}

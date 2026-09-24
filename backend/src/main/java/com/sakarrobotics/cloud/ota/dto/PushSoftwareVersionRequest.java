package com.sakarrobotics.cloud.ota.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * "Push" (OTA Management, System Version Management) — records that an
 * operator pushed this version to this robot. {@code oldVersionNumber} is
 * caller-supplied (there is no on-robot version-reporting channel to read
 * it from); leaving it null is honest, not an error.
 */
public record PushSoftwareVersionRequest(@NotNull UUID robotId, String oldVersionNumber) {
}

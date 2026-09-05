package com.sakarrobotics.cloud.integration.keenon.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The caller must supply the Keenon store id explicitly — same rationale as
 * {@link SyncAreasRequest}: Sakar never hardcodes or infers a storeId, and
 * none is stored anywhere on {@code Robot} itself.
 */
public record SyncCleaningHistoryRequest(@NotBlank String storeId) {
}

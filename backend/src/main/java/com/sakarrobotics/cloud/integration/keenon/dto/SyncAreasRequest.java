package com.sakarrobotics.cloud.integration.keenon.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The caller must supply the Keenon store id explicitly — Sakar never
 * hardcodes or infers one (Master Requirements "current area IDs must not
 * be hardcoded" applies equally to the store id itself, which today has no
 * independent Sakar-side source; see the Keenon area-sync audit).
 */
public record SyncAreasRequest(@NotBlank String storeId) {
}

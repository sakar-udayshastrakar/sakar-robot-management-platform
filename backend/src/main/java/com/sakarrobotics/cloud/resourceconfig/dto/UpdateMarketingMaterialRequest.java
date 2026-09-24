package com.sakarrobotics.cloud.resourceconfig.dto;

import com.sakarrobotics.cloud.resourceconfig.SceneStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateMarketingMaterialRequest(@NotBlank String name, String materialType, @NotNull SceneStatus status) {
}

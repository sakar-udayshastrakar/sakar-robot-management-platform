package com.sakarrobotics.cloud.resourceconfig.dto;

import java.util.UUID;

import com.sakarrobotics.cloud.resourceconfig.SceneStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateResourceSceneRequest(
        UUID siteId,
        UUID robotId,
        @NotBlank String name,
        String resourcePackType,
        @NotNull SceneStatus status) {
}

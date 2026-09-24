package com.sakarrobotics.cloud.openplatform.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateOpenPlatformApplicationRequest(@NotBlank String applicationName, String businessType) {
}

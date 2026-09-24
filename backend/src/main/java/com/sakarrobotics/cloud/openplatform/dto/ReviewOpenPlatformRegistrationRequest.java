package com.sakarrobotics.cloud.openplatform.dto;

import com.sakarrobotics.cloud.openplatform.OpenPlatformRegistrationStatus;

import jakarta.validation.constraints.NotNull;

public record ReviewOpenPlatformRegistrationRequest(@NotNull OpenPlatformRegistrationStatus status) {
}

package com.sakarrobotics.cloud.iot.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateElevatorConfigurationRequest(@NotBlank String name, String notes) {
}

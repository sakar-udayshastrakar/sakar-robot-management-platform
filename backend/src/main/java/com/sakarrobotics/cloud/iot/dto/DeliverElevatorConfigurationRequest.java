package com.sakarrobotics.cloud.iot.dto;

import java.util.UUID;

import com.sakarrobotics.cloud.iot.ElevatorDeliveryStatus;

import jakarta.validation.constraints.NotNull;

public record DeliverElevatorConfigurationRequest(@NotNull UUID robotId, ElevatorDeliveryStatus status) {
}

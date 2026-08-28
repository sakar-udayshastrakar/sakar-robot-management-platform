package com.sakarrobotics.cloud.robot.adapter.dto;

import java.time.Instant;

public record BatteryInfo(int percentage, boolean charging, Instant observedAt) {
}

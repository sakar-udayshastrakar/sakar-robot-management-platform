package com.sakarrobotics.cloud.task.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTaskRequest(@NotBlank String taskType, String parameters) {
}

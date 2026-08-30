package com.sakarrobotics.cloud.command.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record IssueCommandRequest(@NotBlank String commandType, Map<String, Object> params) {
}

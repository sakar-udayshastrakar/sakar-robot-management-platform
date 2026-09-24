package com.sakarrobotics.cloud.iam.dto;

import jakarta.validation.constraints.NotBlank;

/** Also reused for rename (PUT) — a department has only ever had one field. */
public record CreateDepartmentRequest(@NotBlank String name) {
}

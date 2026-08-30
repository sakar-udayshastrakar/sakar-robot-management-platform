package com.sakarrobotics.cloud.task.dto;

import java.util.List;

public record TaskDetailResponse(TaskResponse task, List<TaskEventResponse> events) {
}

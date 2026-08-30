package com.sakarrobotics.cloud.task.dto;

import java.time.Instant;

import com.sakarrobotics.cloud.task.TaskEvent;

public record TaskEventResponse(Long id, String eventType, String detail, Instant createdAt) {

    public static TaskEventResponse from(TaskEvent event) {
        return new TaskEventResponse(event.getId(), event.getEventType(), event.getDetail(), event.getCreatedAt());
    }
}

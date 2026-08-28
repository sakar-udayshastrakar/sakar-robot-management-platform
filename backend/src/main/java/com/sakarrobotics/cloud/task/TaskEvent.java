package com.sakarrobotics.cloud.task;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.AppendOnlyEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Maps to {@code task_events} (Master Requirements Part 7 "Tasks"). Phase 1 scope: schema/entity only. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "task_events")
public class TaskEvent extends AppendOnlyEntity {

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column
    private String detail;
}

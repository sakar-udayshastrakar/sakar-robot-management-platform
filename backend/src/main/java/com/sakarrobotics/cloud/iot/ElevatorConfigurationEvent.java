package com.sakarrobotics.cloud.iot;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.AppendOnlyEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Maps to {@code elevator_configuration_events} — "set record", an immutable change history for {@link ElevatorConfiguration}. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "elevator_configuration_events")
public class ElevatorConfigurationEvent extends AppendOnlyEntity {

    @Column(name = "elevator_configuration_id", nullable = false)
    private UUID elevatorConfigurationId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column
    private String detail;
}

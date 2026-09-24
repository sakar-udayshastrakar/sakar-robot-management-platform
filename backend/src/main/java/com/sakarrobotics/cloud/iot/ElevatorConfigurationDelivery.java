package com.sakarrobotics.cloud.iot;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.AppendOnlyEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Maps to {@code elevator_configuration_deliveries} — "The elevator configuration is delivered" (bookkeeping only, see {@link ElevatorDeliveryStatus}). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "elevator_configuration_deliveries")
public class ElevatorConfigurationDelivery extends AppendOnlyEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "elevator_configuration_id", nullable = false)
    private UUID elevatorConfigurationId;

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(name = "delivered_by")
    private String deliveredBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ElevatorDeliveryStatus status = ElevatorDeliveryStatus.RECORDED;
}

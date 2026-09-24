package com.sakarrobotics.cloud.iot;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code elevator_configurations} — IoT Platform → Elevator Module
 * → Elevator configuration. Binds an existing {@link ElevatorDevice} to an
 * existing Robot for a store, so the robot can call that elevator — never a
 * separate copy of the device/robot/store identity.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "elevator_configurations")
public class ElevatorConfiguration extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "elevator_device_id", nullable = false)
    private UUID elevatorDeviceId;

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(nullable = false)
    private String name;

    @Column
    private String notes;

    @Column(name = "modified_by")
    private String modifiedBy;
}

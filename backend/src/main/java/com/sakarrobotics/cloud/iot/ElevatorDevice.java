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
 * Maps to {@code elevator_devices} — IoT Platform → Elevator Module →
 * Elevator management. {@code siteId} is the existing Site entity ("Store"),
 * never duplicated. There is no "online status" field: no elevator vendor
 * telemetry channel exists in this codebase (see {@link ElevatorController}'s
 * own Javadoc) — the frontend renders this as an honest "Unknown".
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "elevator_devices")
public class ElevatorDevice extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "device_name")
    private String deviceName;

    @Column
    private String building;

    @Column
    private String protocol;

    @Column(name = "networking_mode")
    private String networkingMode;

    @Column(name = "communication_mode")
    private String communicationMode;
}

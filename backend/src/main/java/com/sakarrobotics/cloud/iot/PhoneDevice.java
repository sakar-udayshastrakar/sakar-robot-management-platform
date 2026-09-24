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
 * Maps to {@code phone_devices} — IoT Platform → Phone Module → Device
 * management. Same "no fabricated online status" convention as {@link
 * ElevatorDevice} — no phone/intercom vendor telemetry channel exists here.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "phone_devices")
public class PhoneDevice extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "networking_mode")
    private String networkingMode;
}

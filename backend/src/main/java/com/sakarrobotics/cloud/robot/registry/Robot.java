package com.sakarrobotics.cloud.robot.registry;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code robots} (SAKAR_ROBOT_PLATFORM_DATABASE.md §7). Deliberately
 * has no {@code c40_*}-style column anywhere — everything robot-model-
 * specific is reached through {@link #robotModelId}. {@link #externalRobotId}
 * is the vendor's own identifier (e.g. a Keenon {@code robotSn}); it is
 * internal integration data and must never be the identifier a Web/Mobile
 * client sees (SAKAR_SECURITY_REQUIREMENTS.md §13.A).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "robots")
public class Robot extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "robot_model_id", nullable = false)
    private UUID robotModelId;

    @Column(nullable = false)
    private String name;

    @Column(name = "serial_number", nullable = false, unique = true)
    private String serialNumber;

    /** Vendor-side identifier (e.g. Keenon robotSn) — internal integration data only. */
    @Column(name = "external_robot_id")
    private String externalRobotId;

    /**
     * The vendor's own manufacturer serial (e.g. Keenon {@code mftCode}) —
     * distinct from {@link #serialNumber}, which is Sakar's own generated
     * identity ({@code SR-CB-YYYY-NNNNNN}, see {@code
     * SakarSerialNumberService}). Nullable: not every vendor/robot supplies
     * one.
     */
    @Column(name = "vendor_serial_number")
    private String vendorSerialNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RobotLifecycleStatus status = RobotLifecycleStatus.REGISTERED;
}

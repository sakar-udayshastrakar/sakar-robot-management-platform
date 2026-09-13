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
 * Maps to {@code robot_models} (SAKAR_ROBOT_PLATFORM_DATABASE.md §6). The
 * current Keenon C40 S / Sakar CleanBot 5000 Plus row is just one instance
 * of this table — the schema itself is vendor/model-agnostic
 * (Master Requirements §6.A).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "robot_models")
public class RobotModel extends BaseEntity {

    @Column(name = "manufacturer_id", nullable = false)
    private UUID manufacturerId;

    /** e.g. "C40 S" (vendor model name) — Sakar's own product identity lives on {@code Robot}/fleet metadata, not here. */
    @Column(nullable = false)
    private String name;

    /** e.g. "Sakar CleanBot 5000 Plus" — the Sakar-facing product name for this model. */
    @Column(name = "sakar_product_name")
    private String sakarProductName;

    /**
     * Sakar-owned model-family prefix used to build this model's serial numbers (e.g. {@code
     * "CB"} for C40 S — {@code SR-CB-YYYY-NNNNNN}) — see {@code SakarSerialNumberService}. {@code
     * null} means no prefix has been configured for this model yet; registering a robot of this
     * model then fails safely ({@code UNSUPPORTED_ROBOT_MODEL_SERIAL_PREFIX}) rather than
     * inventing one.
     */
    @Column(name = "serial_prefix", length = 8)
    private String serialPrefix;

    @Enumerated(EnumType.STRING)
    @Column(name = "adapter_type", nullable = false, length = 32)
    private AdapterType adapterType;

    @Enumerated(EnumType.STRING)
    @Column(name = "integration_path", nullable = false, length = 32)
    private IntegrationPath integrationPath;
}

package com.sakarrobotics.cloud.ota;

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

/**
 * Maps to {@code deployment_records} (OTA Management → Update record).
 * Append-only — one row per push, never edited in place. {@code
 * robotId}/{@code softwareVersionId} resolve "Machine SN"/"Whole package
 * name" by reusing the existing {@code robots}/{@code software_versions}
 * tables rather than duplicating those fields here.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "deployment_records")
public class DeploymentRecord extends AppendOnlyEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(name = "software_version_id", nullable = false)
    private UUID softwareVersionId;

    @Column(name = "old_version_number")
    private String oldVersionNumber;

    @Column(name = "new_version_number", nullable = false)
    private String newVersionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeploymentStatus status = DeploymentStatus.RECORDED;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(nullable = false)
    private boolean grayscale = false;
}

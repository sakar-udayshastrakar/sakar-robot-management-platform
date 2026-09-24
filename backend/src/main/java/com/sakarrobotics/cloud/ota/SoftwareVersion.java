package com.sakarrobotics.cloud.ota;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Maps to {@code software_versions} (OTA Management → System Version Management). A named, versioned package a robot could be updated to. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "software_versions")
public class SoftwareVersion extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    @Column(name = "whole_machine_software")
    private String wholeMachineSoftware;

    @Column(name = "package_version", nullable = false)
    private String packageVersion;

    @Column(name = "hardware_version")
    private String hardwareVersion;

    @Column(nullable = false)
    private boolean grayscale = false;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "created_by")
    private String createdBy;

    @Column
    private String notes;
}

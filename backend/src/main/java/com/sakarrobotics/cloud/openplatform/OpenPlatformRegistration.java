package com.sakarrobotics.cloud.openplatform;

import java.time.Instant;
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
 * Maps to {@code open_platform_registrations} — Open Platform → Customer
 * registration. One per organization (the existing Organization entity,
 * never duplicated as a tenant concept — {@code companyName} is a separate,
 * legal-paperwork field that may legitimately differ from the internal
 * {@code Organization.name}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "open_platform_registrations")
public class OpenPlatformRegistration extends BaseEntity {

    @Column(name = "organization_id", nullable = false, unique = true)
    private UUID organizationId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column
    private String area;

    @Column(name = "company_address")
    private String companyAddress;

    @Column(name = "system_matcher")
    private String systemMatcher;

    @Column(name = "contact_information")
    private String contactInformation;

    @Column(name = "docking_requirements")
    private String dockingRequirements;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OpenPlatformRegistrationStatus status = OpenPlatformRegistrationStatus.PENDING;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}

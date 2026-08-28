package com.sakarrobotics.cloud.org;

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
 * Maps to {@code organizations} (SAKAR_ROBOT_PLATFORM_DATABASE.md §4),
 * extended with a self-referencing hierarchy (see {@link OrganizationType})
 * so distributor/sub-distributor/client trees can be expressed.
 *
 * <p>{@code path} is a materialized ancestry path (e.g.
 * {@code /<sakarRootId>/<distributorId>/<clientId>/}) maintained by
 * {@code OrganizationService} on create, used by
 * {@code TenantAccessGuard} for O(1) descendant checks
 * ({@code childPath.startsWith(ancestorPath)}) instead of a recursive
 * query on every authorization check.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(name = "parent_organization_id")
    private UUID parentOrganizationId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "org_type", nullable = false, length = 32)
    private OrganizationType orgType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    /** Materialized ancestry path, e.g. {@code /root-id/dist-id/}. Set by the service layer. */
    @Column(nullable = false, length = 2048)
    private String path;
}

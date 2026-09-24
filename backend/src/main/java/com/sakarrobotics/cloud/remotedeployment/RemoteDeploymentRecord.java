package com.sakarrobotics.cloud.remotedeployment;

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
 * Maps to {@code remote_deployment_records} — Operation And Maintenance
 * Platform → Remote Deployment. See {@link RemoteDeploymentStatus}'s own
 * Javadoc for why this is bookkeeping only, never a fabricated delivery
 * confirmation.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "remote_deployment_records")
public class RemoteDeploymentRecord extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(name = "deployed_by")
    private String deployedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RemoteDeploymentStatus status = RemoteDeploymentStatus.RECORDED;

    @Column
    private String notes;

    @Column(name = "completed_at")
    private Instant completedAt;
}

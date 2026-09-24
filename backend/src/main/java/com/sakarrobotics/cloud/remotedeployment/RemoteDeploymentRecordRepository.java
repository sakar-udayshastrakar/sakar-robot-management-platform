package com.sakarrobotics.cloud.remotedeployment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RemoteDeploymentRecordRepository extends JpaRepository<RemoteDeploymentRecord, UUID> {

    List<RemoteDeploymentRecord> findByOrganizationIdInOrderByCreatedAtDesc(List<UUID> organizationIds);

    List<RemoteDeploymentRecord> findAllByOrderByCreatedAtDesc();
}

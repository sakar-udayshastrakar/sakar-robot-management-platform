package com.sakarrobotics.cloud.ota;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRecordRepository extends JpaRepository<DeploymentRecord, Long> {

    List<DeploymentRecord> findByOrganizationIdInOrderByCreatedAtDesc(List<UUID> organizationIds);

    List<DeploymentRecord> findAllByOrderByCreatedAtDesc();
}

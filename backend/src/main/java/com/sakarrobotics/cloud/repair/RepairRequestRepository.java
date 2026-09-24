package com.sakarrobotics.cloud.repair;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairRequestRepository extends JpaRepository<RepairRequest, UUID> {

    List<RepairRequest> findByOrganizationIdInOrderByCreatedAtDesc(List<UUID> organizationIds);

    List<RepairRequest> findAllByOrderByCreatedAtDesc();
}

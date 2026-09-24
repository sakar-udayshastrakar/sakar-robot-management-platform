package com.sakarrobotics.cloud.iot;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ElevatorDeviceRepository extends JpaRepository<ElevatorDevice, UUID> {

    List<ElevatorDevice> findByOrganizationIdInOrderByCreatedAtDesc(List<UUID> organizationIds);

    List<ElevatorDevice> findAllByOrderByCreatedAtDesc();

    List<ElevatorDevice> findBySiteIdIn(List<UUID> siteIds);
}

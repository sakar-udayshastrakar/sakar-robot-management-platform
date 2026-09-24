package com.sakarrobotics.cloud.iot;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ElevatorConfigurationRepository extends JpaRepository<ElevatorConfiguration, UUID> {

    List<ElevatorConfiguration> findByOrganizationIdInOrderByUpdatedAtDesc(List<UUID> organizationIds);

    List<ElevatorConfiguration> findAllByOrderByUpdatedAtDesc();
}

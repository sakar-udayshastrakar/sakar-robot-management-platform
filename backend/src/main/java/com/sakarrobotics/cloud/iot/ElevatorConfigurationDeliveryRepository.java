package com.sakarrobotics.cloud.iot;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ElevatorConfigurationDeliveryRepository extends JpaRepository<ElevatorConfigurationDelivery, Long> {

    List<ElevatorConfigurationDelivery> findByOrganizationIdInOrderByCreatedAtDesc(List<UUID> organizationIds);

    List<ElevatorConfigurationDelivery> findAllByOrderByCreatedAtDesc();
}

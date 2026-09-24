package com.sakarrobotics.cloud.iot;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ElevatorConfigurationEventRepository extends JpaRepository<ElevatorConfigurationEvent, Long> {

    List<ElevatorConfigurationEvent> findByElevatorConfigurationIdOrderByIdAsc(UUID elevatorConfigurationId);
}

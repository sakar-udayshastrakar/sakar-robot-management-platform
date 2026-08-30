package com.sakarrobotics.cloud.alert;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotAlertRepository extends JpaRepository<RobotAlert, UUID> {

    Page<RobotAlert> findByOrganizationIdInOrderByIdDesc(List<UUID> organizationIds, Pageable pageable);

    Page<RobotAlert> findAllByOrderByIdDesc(Pageable pageable);

    boolean existsByRobotIdAndAlertTypeAndStatus(UUID robotId, String alertType, String status);

    Optional<RobotAlert> findFirstByRobotIdAndAlertTypeAndStatus(UUID robotId, String alertType, String status);
}

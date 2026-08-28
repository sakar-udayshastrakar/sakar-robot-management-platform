package com.sakarrobotics.cloud.alert;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotAlertRepository extends JpaRepository<RobotAlert, UUID> {
}

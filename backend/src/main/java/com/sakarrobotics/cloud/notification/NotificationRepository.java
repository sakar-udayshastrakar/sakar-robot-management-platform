package com.sakarrobotics.cloud.notification;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}

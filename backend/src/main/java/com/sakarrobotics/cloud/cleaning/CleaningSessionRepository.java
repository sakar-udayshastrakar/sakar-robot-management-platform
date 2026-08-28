package com.sakarrobotics.cloud.cleaning;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CleaningSessionRepository extends JpaRepository<CleaningSession, UUID> {
}

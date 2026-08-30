package com.sakarrobotics.cloud.telemetry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotStatusRepository extends JpaRepository<RobotStatus, UUID> {

    /** Drives {@code com.sakarrobotics.cloud.alert.RobotOfflineWatcherService}'s stale-heartbeat sweep. */
    List<RobotStatus> findByOnlineTrueAndLastSeenAtBefore(Instant cutoff);
}

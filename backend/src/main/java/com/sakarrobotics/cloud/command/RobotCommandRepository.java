package com.sakarrobotics.cloud.command;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotCommandRepository extends JpaRepository<RobotCommand, UUID> {

    Page<RobotCommand> findByRobotIdOrderByIdDesc(UUID robotId, Pageable pageable);

    /** Used by {@link CommandExpiryService} to find commands overdue for a result. */
    List<RobotCommand> findByStatusInAndExpiresAtBefore(List<CommandStatus> statuses, Instant instant);
}

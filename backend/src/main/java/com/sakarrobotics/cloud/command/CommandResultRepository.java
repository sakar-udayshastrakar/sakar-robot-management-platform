package com.sakarrobotics.cloud.command;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandResultRepository extends JpaRepository<CommandResult, Long> {

    /** Newest-first lifecycle history for one command — see {@code RobotCommandService#listResultsByCommand}. */
    Page<CommandResult> findByCommandIdOrderByCreatedAtDesc(UUID commandId, Pageable pageable);
}

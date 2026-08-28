package com.sakarrobotics.cloud.command;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandResultRepository extends JpaRepository<CommandResult, Long> {
}

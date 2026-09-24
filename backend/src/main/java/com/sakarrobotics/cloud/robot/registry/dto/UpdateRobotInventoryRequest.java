package com.sakarrobotics.cloud.robot.registry.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * "Bind store" (Robot Management, Phase 2) — a full replace of these three
 * fields, not a sparse patch: {@code siteId == null} unassigns the robot
 * from any site, matching {@code returnToInventory}'s own semantics.
 */
public record UpdateRobotInventoryRequest(UUID siteId, LocalDate warrantyStartDate, LocalDate warrantyEndDate) {
}

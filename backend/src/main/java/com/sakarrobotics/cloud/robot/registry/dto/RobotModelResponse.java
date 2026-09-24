package com.sakarrobotics.cloud.robot.registry.dto;

import java.util.UUID;

import com.sakarrobotics.cloud.robot.registry.RobotModel;

/**
 * Previously nothing exposed {@code RobotModel}/{@code RobotManufacturer} to
 * a client at all — {@code RobotResponse.robotModelId} was a bare UUID the
 * frontend could only truncate and display raw. This is a read-only lookup
 * list, not a registry-write surface.
 */
public record RobotModelResponse(UUID id, String manufacturerName, String name, String sakarProductName) {

    public static RobotModelResponse from(RobotModel model, String manufacturerName) {
        return new RobotModelResponse(model.getId(), manufacturerName, model.getName(), model.getSakarProductName());
    }
}

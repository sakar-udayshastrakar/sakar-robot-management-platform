package com.sakarrobotics.cloud.robot.adapter.dto;

import java.util.List;

/**
 * A generic (vendor-neutral) task request handed to an adapter. The public
 * Sakar API never carries a Keenon {@code cleanModelId}/{@code areaId}/
 * {@code backPointId} — the adapter is responsible for resolving
 * {@code sakarAreaIds}/{@code mode} to whatever the underlying vendor needs
 * (Master Requirements "Task Model"/"Cleaning" sections).
 */
public record AdapterTaskRequest(
        String taskType,
        List<String> sakarAreaIds,
        String mode,
        int repeatCount,
        boolean returnToDock) {
}

package com.sakarrobotics.cloud.dashboard.dto;

import java.util.List;

/** Operational Dashboard → Store Real-Time Data Statistics. See {@link OperationRankingResponse}'s Javadoc for the null-means-not-tracked convention. */
public record StoreRealtimeStatsResponse(
        long tasksToday,
        List<TaskTypeShare> taskModeProportionToday,
        Long callsToday,
        long activeMachines,
        Long mileageToday,
        Double averageSpeedMetersPerSecond) {
}

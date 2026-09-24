package com.sakarrobotics.cloud.dashboard.dto;

import java.util.List;

/** Operational Dashboard → Hotel Task Record. See {@link OperationRankingResponse}'s Javadoc for the null-means-not-tracked convention. */
public record HotelTaskRecordResponse(
        long totalVolumeOfTask,
        Long cumulativeMileage,
        long cumulativeDurationSeconds,
        Long numberOfRooms,
        List<DailyTaskCount> dailyBreakdown) {
}

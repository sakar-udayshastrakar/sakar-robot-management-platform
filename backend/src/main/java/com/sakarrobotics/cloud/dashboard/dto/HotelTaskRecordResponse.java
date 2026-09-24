package com.sakarrobotics.cloud.dashboard.dto;

import java.util.List;

/**
 * Operational Dashboard → Hotel Task Record; also reused by the main
 * Dashboard's "Seven-day Overview" / "Task Data Details" cards (same real
 * filtered-task-list computation, just called with a 7-day range and no
 * other filters — never a second, duplicate aggregation). See {@link
 * OperationRankingResponse}'s Javadoc for the null-means-not-tracked
 * convention.
 */
public record HotelTaskRecordResponse(
        long totalVolumeOfTask,
        Long cumulativeMileage,
        long cumulativeDurationSeconds,
        Long numberOfRooms,
        List<DailyTaskCount> dailyBreakdown,
        List<TaskTypeShare> taskTypeBreakdown) {
}

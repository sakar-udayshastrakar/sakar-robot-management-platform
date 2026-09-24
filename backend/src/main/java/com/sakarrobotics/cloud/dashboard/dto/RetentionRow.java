package com.sakarrobotics.cloud.dashboard.dto;

import java.time.LocalDate;

/**
 * One day's retention snapshot — {@code usedN} is the number of stores with
 * at least one task on every one of the N days ending on {@code date};
 * {@code unusedN} is the number with no task on any of those N days. Both
 * are computed straight from real {@code robot_tasks.created_at} rows, never
 * simulated.
 */
public record RetentionRow(
        LocalDate date,
        long used3, long used7, long used15,
        long unused3, long unused7, long unused15) {
}

package com.sakarrobotics.cloud.dashboard.dto;

import java.util.List;

/**
 * Operational Dashboard → Operation Ranking. {@code totalMileage}/{@code
 * totalCalls} (and their ranking lists) are always {@code null}: no
 * distance/odometer or call/summon concept exists anywhere in this
 * codebase, so this never reports a fabricated number for them — the
 * frontend renders "Not tracked" instead of "0", which would misrepresent
 * an absence of data as a real zero count.
 */
public record OperationRankingResponse(
        long totalTasks,
        Long totalMileage,
        Long totalCalls,
        List<RankingEntry> storeRankingsByTasks,
        List<RankingEntry> robotRankingsByTasks,
        List<RankingEntry> storeRankingsByMileage,
        List<RankingEntry> robotRankingsByMileage) {
}

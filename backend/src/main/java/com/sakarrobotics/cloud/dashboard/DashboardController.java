package com.sakarrobotics.cloud.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.dashboard.dto.HotelTaskRecordResponse;
import com.sakarrobotics.cloud.dashboard.dto.OperationRankingResponse;
import com.sakarrobotics.cloud.dashboard.dto.RetentionRow;
import com.sakarrobotics.cloud.dashboard.dto.StoreRealtimeStatsResponse;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Operational Dashboard — see {@link DashboardService}'s own Javadoc for the real-data-only convention. */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Operational Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/operation-ranking")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Operation Ranking — cumulative task counts and store/robot rankings within the caller's organization scope")
    public ApiResponse<OperationRankingResponse> operationRanking(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(dashboardService.operationRanking(principal));
    }

    @GetMapping("/store-realtime")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Store Real-Time Data Statistics — today's task counts, mode proportions, and active machines")
    public ApiResponse<StoreRealtimeStatsResponse> storeRealtimeStats(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam(required = false) UUID siteId) {
        return ApiResponse.ok(dashboardService.storeRealtimeStats(principal, siteId));
    }

    @GetMapping("/retention")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Use Retention Analytics — consecutive-day store usage/non-usage streaks, newest first")
    public ApiResponse<List<RetentionRow>> retentionAnalytics(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam(defaultValue = "8") int days) {
        return ApiResponse.ok(dashboardService.retentionAnalytics(principal, days));
    }

    @GetMapping("/hotel-task-record")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Hotel Task Record — filtered task volume, cumulative duration, and daily breakdown")
    public ApiResponse<HotelTaskRecordResponse> hotelTaskRecord(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) UUID robotId,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Instant fromInstant = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return ApiResponse.ok(dashboardService.hotelTaskRecord(principal, siteId, robotId, taskType, fromInstant, toInstant));
    }
}

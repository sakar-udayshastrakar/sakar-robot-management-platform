package com.sakarrobotics.cloud.alert;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.alert.dto.AlertResponse;
import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Real alert read/acknowledge/resolve API (Roadmap Phase 6/9). Alerts
 * themselves are generated only by {@link AlertGenerationService} (low
 * battery, offline) — this controller never creates one.
 */
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts")
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List alerts within the caller's organization scope")
    public ApiResponse<Page<AlertResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        return ApiResponse.ok(alertService.listAccessible(principal, page, pageSize).map(AlertResponse::from));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('ROBOT_CONTROL')")
    @Operation(summary = "Acknowledge an alert")
    public ApiResponse<AlertResponse> acknowledge(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.ok(AlertResponse.from(alertService.acknowledge(principal, id)));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('ROBOT_CONTROL')")
    @Operation(summary = "Resolve an alert")
    public ApiResponse<AlertResponse> resolve(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.ok(AlertResponse.from(alertService.resolve(principal, id)));
    }
}

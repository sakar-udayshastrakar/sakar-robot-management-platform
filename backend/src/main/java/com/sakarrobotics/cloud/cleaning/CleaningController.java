package com.sakarrobotics.cloud.cleaning;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.cleaning.dto.CleaningSessionResponse;
import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.robot.registry.RobotService;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Real cleaning-history read path (Roadmap Phase 6/9,
 * SAKAR_ROBOT_PLATFORM_API_SPEC.md §1.8 shape). Empty by design until a
 * robot's operator actually completes a {@code CLEANING}-type task through
 * the real task API ({@link com.sakarrobotics.cloud.task.RobotTaskController})
 * — see {@link CleaningSessionService}'s Javadoc for why nothing else
 * populates this table.
 */
@RestController
@RequestMapping("/api/v1/robots/{robotId}/cleaning")
@RequiredArgsConstructor
@Tag(name = "Cleaning")
public class CleaningController {

    private final CleaningSessionService cleaningSessionService;
    private final RobotService robotService;

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List a robot's real cleaning session history")
    public ApiResponse<Page<CleaningSessionResponse>> history(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID robotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        robotService.getAccessibleOrThrow(principal, robotId); // tenant check before any read
        return ApiResponse.ok(cleaningSessionService.listByRobot(robotId, page, pageSize).map(CleaningSessionResponse::from));
    }
}

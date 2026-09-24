package com.sakarrobotics.cloud.iot;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.iot.dto.CreateLadderControlBindingRequest;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** IoT Platform → Elevator Module → Cloud ladder control configuration → Store binding. */
@RestController
@RequestMapping("/api/v1/iot/ladder-control-bindings")
@RequiredArgsConstructor
@Tag(name = "IoT Platform — Cloud Ladder Control")
public class LadderControlController {

    private final LadderControlService ladderControlService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List store-to-ladder-control-vendor bindings within the caller's organization scope")
    public ApiResponse<List<LadderControlStoreBinding>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ladderControlService.list(principal));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Bind a store to a third-party (\"tripartite\") ladder control vendor account")
    public ResponseEntity<ApiResponse<LadderControlStoreBinding>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateLadderControlBindingRequest request) {
        LadderControlStoreBinding binding = ladderControlService.create(principal, request.organizationId(),
                request.siteId(), request.manufacturer(), request.buildingId(), request.clientId());
        return ResponseEntity.status(201).body(ApiResponse.ok(binding));
    }
}

package com.sakarrobotics.cloud.resourceconfig;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.resourceconfig.dto.CreateResourceSceneRequest;
import com.sakarrobotics.cloud.resourceconfig.dto.UpdateResourceSceneRequest;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * New Resource Configuration → Scene list (Robot Management sidebar group).
 * Gated on {@code ROBOT_CONFIGURE} for writes / {@code ROBOT_VIEW} for
 * reads — the same permissions this codebase already uses for adjacent
 * robot-configuration actions; no new permission code was introduced for
 * this pass's scope.
 */
@RestController
@RequestMapping("/api/v1/scenes")
@RequiredArgsConstructor
@Tag(name = "Resource Scenes")
public class ResourceSceneController {

    private final ResourceSceneService resourceSceneService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List resource-configuration scenes within the caller's organization scope")
    public ApiResponse<List<ResourceScene>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(resourceSceneService.listAccessible(principal));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Add a scene")
    public ResponseEntity<ApiResponse<ResourceScene>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateResourceSceneRequest request) {
        ResourceScene scene = resourceSceneService.create(principal, request.organizationId(), request.siteId(),
                request.robotId(), request.name(), request.resourcePackType());
        return ResponseEntity.status(201).body(ApiResponse.ok(scene));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Edit a scene (including publishing/un-publishing it)")
    public ApiResponse<ResourceScene> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateResourceSceneRequest request) {
        ResourceScene scene = resourceSceneService.update(principal, id, request.siteId(), request.robotId(),
                request.name(), request.resourcePackType(), request.status());
        return ApiResponse.ok(scene);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Delete a scene")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        resourceSceneService.delete(principal, id);
        return ApiResponse.ok(null);
    }
}

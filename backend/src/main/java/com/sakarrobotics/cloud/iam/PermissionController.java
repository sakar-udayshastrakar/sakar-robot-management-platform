package com.sakarrobotics.cloud.iam;

import java.util.List;
import java.util.Comparator;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.iam.dto.PermissionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Read-only list of the fixed {@link PermissionCode} set (V9__seed_rbac.sql)
 * — previously only mirrored, hardcoded, in the frontend's own {@code
 * types/permissions.ts}; this is the real source the new role-permission
 * editor reads from. Gated by {@code USER_MANAGE}, matching {@link
 * RoleController}'s existing rationale for the same gate.
 */
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions")
public class PermissionController {

    private final PermissionRepository permissionRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "List the fixed set of platform permissions")
    public ApiResponse<List<PermissionResponse>> list() {
        return ApiResponse.ok(permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(p -> p.getCode().name()))
                .map(PermissionResponse::from)
                .toList());
    }
}

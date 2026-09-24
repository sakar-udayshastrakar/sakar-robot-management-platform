package com.sakarrobotics.cloud.iam;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.iam.dto.RoleResponse;
import com.sakarrobotics.cloud.iam.dto.UpdateRoleRequest;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Role reference list (V9__seed_rbac.sql), plus editing of an existing
 * role's description/permission set (Account Permission Platform, Phase 1).
 * {@code GET} stays gated by {@code USER_MANAGE} because its main consumer
 * is the user-management role picker; {@code PUT} is gated by the stronger
 * {@code ROLE_MANAGE} (SUPER_ADMIN-only per V9's seed matrix), matching the
 * permission this codebase already defined for exactly this purpose.
 * Creating a brand-new, arbitrarily-named role remains out of scope — see
 * {@link com.sakarrobotics.cloud.iam.dto.UpdateRoleRequest}'s Javadoc.
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles")
public class RoleController {

    private final RoleRepository roleRepository;
    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "List the fixed set of platform roles and their permissions")
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.ok(roleRepository.findAll().stream().map(RoleResponse::from).toList());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Update a role's description and permission set (the role name itself is fixed)")
    public ApiResponse<RoleResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        Role role = roleService.update(principal, id, request.description(), request.permissionCodes());
        return ApiResponse.ok(RoleResponse.from(role));
    }
}

package com.sakarrobotics.cloud.iam;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.iam.dto.RoleResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Read-only role reference list (V9__seed_rbac.sql). Gated by {@code
 * USER_MANAGE} because its only consumer today is the user-management role
 * picker — editing roles/permissions themselves is not implemented and is
 * out of this batch's approved scope.
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles")
public class RoleController {

    private final RoleRepository roleRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "List the fixed set of platform roles and their permissions")
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.ok(roleRepository.findAll().stream().map(RoleResponse::from).toList());
    }
}

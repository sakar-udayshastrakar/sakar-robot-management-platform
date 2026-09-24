package com.sakarrobotics.cloud.iam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.iam.dto.ChangeUserRoleRequest;
import com.sakarrobotics.cloud.iam.dto.CreateUserRequest;
import com.sakarrobotics.cloud.iam.dto.UpdateUserRequest;
import com.sakarrobotics.cloud.iam.dto.UserResponse;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Real user administration API (Roadmap approved scope, Master Requirements
 * Part 19). Gated by {@code USER_MANAGE} throughout — per the seeded RBAC
 * matrix (V9__seed_rbac.sql) only SUPER_ADMIN/ORG_ADMIN hold it.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Create a user within the caller's organization scope")
    public ResponseEntity<ApiResponse<UserResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateUserRequest request) {
        UserType userType = request.userType() != null ? request.userType() : UserType.EXTERNAL;
        User user = userService.create(principal, request.organizationId(), request.email(), request.password(),
                request.fullName(), request.roleName(), userType, request.departmentId());
        return ResponseEntity.status(201).body(ApiResponse.ok(respond(user)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "List users within the caller's organization scope, optionally filtered by userType")
    public ApiResponse<Page<UserResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize,
            @RequestParam(required = false) UserType userType) {
        Page<User> users = userService.listAccessible(principal, page, pageSize, userType);
        // One batch department-name lookup for the whole page, not one per row.
        Map<UUID, String> departmentNames = userService.departmentNamesFor(users.getContent());
        return ApiResponse.ok(users.map(u -> UserResponse.from(u, departmentNames.get(u.getDepartmentId()))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Get a user the caller is authorized to see")
    public ApiResponse<UserResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.ok(respond(userService.getAccessibleOrThrow(principal, id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Edit a user's full name and department assignment")
    public ApiResponse<UserResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(respond(userService.update(principal, id, request.fullName(), request.departmentId())));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Suspend a user, blocking further login")
    public ApiResponse<UserResponse> suspend(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.ok(respond(userService.suspend(principal, id)));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Re-activate a suspended or lockout-affected user")
    public ApiResponse<UserResponse> activate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.ok(respond(userService.activate(principal, id)));
    }

    @PostMapping("/{id}/role")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Change a user's assigned role")
    public ApiResponse<UserResponse> changeRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeUserRoleRequest request) {
        return ApiResponse.ok(respond(userService.changeRole(principal, id, request.roleName())));
    }

    private UserResponse respond(User user) {
        Map<UUID, String> departmentNames = userService.departmentNamesFor(List.of(user));
        return UserResponse.from(user, departmentNames.get(user.getDepartmentId()));
    }
}

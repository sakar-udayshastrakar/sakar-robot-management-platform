package com.sakarrobotics.cloud.iam;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.iam.dto.CreateDepartmentRequest;
import com.sakarrobotics.cloud.iam.dto.DepartmentResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * CRUD for the flat, Sakar-wide department list that groups INTERNAL users
 * (Account Permission Platform, Phase 1). Gated by {@code USER_MANAGE} —
 * the same permission that already gates every other user-administration
 * endpoint in {@link UserController}.
 */
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Tag(name = "Departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "List all departments")
    public ApiResponse<List<DepartmentResponse>> list() {
        return ApiResponse.ok(departmentService.listAll().stream().map(DepartmentResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Create a department")
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(@Valid @RequestBody CreateDepartmentRequest request) {
        Department department = departmentService.create(request.name());
        return ResponseEntity.status(201).body(ApiResponse.ok(DepartmentResponse.from(department)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Rename a department")
    public ApiResponse<DepartmentResponse> rename(@PathVariable UUID id, @Valid @RequestBody CreateDepartmentRequest request) {
        return ApiResponse.ok(DepartmentResponse.from(departmentService.rename(id, request.name())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Delete a department (rejected if any user is still assigned to it)")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        departmentService.delete(id);
        return ApiResponse.ok(null);
    }
}

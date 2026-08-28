package com.sakarrobotics.cloud.audit;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit")
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final TenantAccessGuard tenantAccessGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    @Operation(summary = "List audit log records within the caller's organization scope")
    public ApiResponse<Page<AuditLog>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        List<java.util.UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        Page<AuditLog> result = orgIds == null
                ? auditLogRepository.findAllByOrderByIdDesc(PageRequest.of(page, pageSize))
                : auditLogRepository.findByOrganizationIdIn(orgIds, PageRequest.of(page, pageSize));
        return ApiResponse.ok(result);
    }
}

package com.sakarrobotics.cloud.org;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.org.dto.CreateSiteRequest;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Site management (Master Requirements Part 7 "Site Management"). */
@RestController
@RequestMapping("/api/v1/sites")
@RequiredArgsConstructor
@Tag(name = "Sites")
public class SiteController {

    private final SiteRepository siteRepository;
    private final TenantAccessGuard tenantAccessGuard;

    @PostMapping
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Create a site under an organization the caller may configure")
    public ResponseEntity<ApiResponse<Site>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateSiteRequest request) {
        tenantAccessGuard.assertOrganizationAccess(principal, request.organizationId());
        Site site = new Site();
        site.setOrganizationId(request.organizationId());
        site.setName(request.name());
        site.setAddress(request.address());
        site.setTimezone(request.timezone());
        Site saved = siteRepository.save(site);
        return ResponseEntity.status(201).body(ApiResponse.ok(saved));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List sites under an organization the caller may view")
    public ApiResponse<List<Site>> listByOrganization(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam UUID organizationId) {
        tenantAccessGuard.assertOrganizationAccess(principal, organizationId);
        return ApiResponse.ok(siteRepository.findByOrganizationId(organizationId));
    }
}

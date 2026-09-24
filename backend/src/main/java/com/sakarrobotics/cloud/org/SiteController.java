package com.sakarrobotics.cloud.org;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.org.dto.CreateSiteRequest;
import com.sakarrobotics.cloud.org.dto.UpdateSiteRequest;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Site management (Master Requirements Part 7 "Site Management") — also
 * this platform's "Store Management" (Robot Management sidebar group); see
 * {@link Site}'s own Javadoc for why no separate Store entity exists.
 */
@RestController
@RequestMapping("/api/v1/sites")
@RequiredArgsConstructor
@Tag(name = "Sites")
public class SiteController {

    private final SiteRepository siteRepository;
    private final TenantAccessGuard tenantAccessGuard;

    @PostMapping
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Create a site (\"store\") under an organization the caller may configure")
    public ResponseEntity<ApiResponse<Site>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateSiteRequest request) {
        tenantAccessGuard.assertOrganizationAccess(principal, request.organizationId());
        Site site = new Site();
        site.setOrganizationId(request.organizationId());
        applyFields(site, request.name(), request.address(), request.timezone(), request.area(), request.contactName(),
                request.phone(), request.email(), request.sceneType(), request.chainBrand());
        Site saved = siteRepository.save(site);
        return ResponseEntity.status(201).body(ApiResponse.ok(saved));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List sites (\"stores\") — within one organization if organizationId is given, "
            + "otherwise every site across the caller's accessible organizations (Store Management's store list)")
    public ApiResponse<List<Site>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID organizationId) {
        if (organizationId != null) {
            tenantAccessGuard.assertOrganizationAccess(principal, organizationId);
            return ApiResponse.ok(siteRepository.findByOrganizationId(organizationId));
        }
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return ApiResponse.ok(orgIds == null ? siteRepository.findAll() : siteRepository.findByOrganizationIdIn(orgIds));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Edit a site (\"store\")")
    public ApiResponse<Site> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSiteRequest request) {
        Site site = getAccessibleOrThrow(principal, id);
        applyFields(site, request.name(), request.address(), request.timezone(), request.area(), request.contactName(),
                request.phone(), request.email(), request.sceneType(), request.chainBrand());
        return ApiResponse.ok(siteRepository.save(site));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Delete a site (\"store\")")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        Site site = getAccessibleOrThrow(principal, id);
        siteRepository.delete(site);
        return ApiResponse.ok(null);
    }

    private Site getAccessibleOrThrow(UserPrincipal principal, UUID id) {
        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new ApiException(SakarErrorCode.SITE_NOT_FOUND, "Site not found: " + id));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, site.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.SITE_NOT_FOUND, "Site not found: " + id);
        }
        return site;
    }

    private void applyFields(Site site, String name, String address, String timezone, String area, String contactName,
            String phone, String email, String sceneType, Boolean chainBrand) {
        site.setName(name);
        site.setAddress(address);
        site.setTimezone(timezone);
        site.setArea(area);
        site.setContactName(contactName);
        site.setPhone(phone);
        site.setEmail(email);
        site.setSceneType(sceneType);
        site.setChainBrand(chainBrand != null && chainBrand);
    }
}

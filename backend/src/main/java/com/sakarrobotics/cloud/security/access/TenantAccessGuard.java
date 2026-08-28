package com.sakarrobotics.cloud.security.access;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.org.OrganizationRepository;
import com.sakarrobotics.cloud.org.OrganizationService;
import com.sakarrobotics.cloud.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

/**
 * The single, mandatory chokepoint for organization-hierarchy authorization
 * (Master Requirements Part 19.B: "Organization -> Site -> Robot", "every
 * API call and UI list filters through this hierarchy server-side — a
 * client-supplied id is never trusted without a server-side authorization
 * check"). Every controller/service method that resolves an object by id
 * must call one of these before returning or acting on it — this is the
 * platform's IDOR/BOLA/tenant-escape defense.
 *
 * <p>Known limitation (Phase 1): scoping stops at the organization level.
 * Per-user site/robot assignment narrower than "everything in my
 * organization and its descendants" is not yet implemented — see the
 * Phase 1 final report.
 */
@Component
@RequiredArgsConstructor
public class TenantAccessGuard {

    private final OrganizationService organizationService;
    private final OrganizationRepository organizationRepository;

    /**
     * Throws {@link SakarErrorCode#TENANT_ACCESS_DENIED} (403) unless the
     * principal may reach {@code targetOrganizationId}. Use this only where
     * revealing "this organization exists but you can't see it" is
     * acceptable — for a robot/site/other child resource looked up by id,
     * prefer {@link #hasOrganizationAccess} and let the caller raise its own
     * resource-specific NOT_FOUND-shaped error instead, per
     * SAKAR_ROBOT_PLATFORM_API_SPEC.md §1.12's rule against distinguishing
     * "not found" from "not visible to your scope."
     */
    public void assertOrganizationAccess(UserPrincipal principal, UUID targetOrganizationId) {
        if (!hasOrganizationAccess(principal, targetOrganizationId)) {
            throw new ApiException(SakarErrorCode.TENANT_ACCESS_DENIED,
                    "You are not authorized to access this organization's resources");
        }
    }

    /** Non-throwing check — see {@link #assertOrganizationAccess} for when to prefer this. */
    public boolean hasOrganizationAccess(UserPrincipal principal, UUID targetOrganizationId) {
        if (principal.isSuperAdmin()) {
            return true;
        }
        return principal.getOrganizationId() != null
                && organizationService.isSameOrDescendant(principal.getOrganizationId(), targetOrganizationId);
    }

    /**
     * Every organization id the principal may act on: {@code null} means "no
     * restriction" (SUPER_ADMIN only) — callers must branch on this rather than
     * ever treating {@code null} as an empty list.
     */
    public List<UUID> accessibleOrganizationIds(UserPrincipal principal) {
        if (principal.isSuperAdmin()) {
            return null;
        }
        if (principal.getOrganizationId() == null) {
            throw new ApiException(SakarErrorCode.TENANT_ACCESS_DENIED, "This account has no organization scope");
        }
        return organizationRepository.findByPathStartingWith(principal.getOrganizationPath())
                .stream()
                .map(org -> org.getId())
                .toList();
    }
}

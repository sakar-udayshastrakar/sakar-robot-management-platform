package com.sakarrobotics.cloud.iot;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.org.Site;
import com.sakarrobotics.cloud.org.SiteRepository;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import lombok.RequiredArgsConstructor;

/** IoT Platform → Elevator Module → Cloud ladder control configuration → Store binding. */
@Service
@RequiredArgsConstructor
public class LadderControlService {

    private final LadderControlStoreBindingRepository ladderControlStoreBindingRepository;
    private final SiteRepository siteRepository;
    private final TenantAccessGuard tenantAccessGuard;

    @Transactional
    public LadderControlStoreBinding create(UserPrincipal principal, UUID organizationId, UUID siteId,
            String manufacturer, String buildingId, String clientId) {
        tenantAccessGuard.assertOrganizationAccess(principal, organizationId);
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.SITE_NOT_FOUND, "Site not found: " + siteId));
        if (!site.getOrganizationId().equals(organizationId)) {
            throw new ApiException(SakarErrorCode.SITE_NOT_FOUND, "Site not found: " + siteId);
        }
        if (ladderControlStoreBindingRepository.existsBySiteId(siteId)) {
            throw new ApiException(SakarErrorCode.CONFLICT, "Store is already bound to a ladder control vendor: " + siteId);
        }

        LadderControlStoreBinding binding = new LadderControlStoreBinding();
        binding.setOrganizationId(organizationId);
        binding.setSiteId(siteId);
        binding.setManufacturer(manufacturer);
        binding.setBuildingId(buildingId);
        binding.setClientId(clientId);
        return ladderControlStoreBindingRepository.save(binding);
    }

    public List<LadderControlStoreBinding> list(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null
                ? ladderControlStoreBindingRepository.findAllByOrderByCreatedAtDesc()
                : ladderControlStoreBindingRepository.findByOrganizationIdInOrderByCreatedAtDesc(orgIds);
    }
}

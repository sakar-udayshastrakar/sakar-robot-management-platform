package com.sakarrobotics.cloud.resourceconfig;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import lombok.RequiredArgsConstructor;

/** New Resource Configuration → Marketing materials. Same tenant-scoping chokepoint (TenantAccessGuard) every other resource in this codebase uses. */
@Service
@RequiredArgsConstructor
public class MarketingMaterialService {

    private final MarketingMaterialRepository marketingMaterialRepository;
    private final TenantAccessGuard tenantAccessGuard;

    public List<MarketingMaterial> listAccessible(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null ? marketingMaterialRepository.findAll() : marketingMaterialRepository.findByOrganizationIdIn(orgIds);
    }

    @Transactional
    public MarketingMaterial create(UserPrincipal principal, UUID organizationId, String name, String materialType) {
        tenantAccessGuard.assertOrganizationAccess(principal, organizationId);
        MarketingMaterial material = new MarketingMaterial();
        material.setOrganizationId(organizationId);
        material.setName(name);
        material.setMaterialType(materialType == null || materialType.isBlank() ? "GENERAL" : materialType);
        return marketingMaterialRepository.save(material);
    }

    @Transactional
    public MarketingMaterial update(UserPrincipal principal, UUID id, String name, String materialType, SceneStatus status) {
        MarketingMaterial material = getAccessibleOrThrow(principal, id);
        material.setName(name);
        material.setMaterialType(materialType == null || materialType.isBlank() ? "GENERAL" : materialType);
        material.setStatus(status);
        return marketingMaterialRepository.save(material);
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID id) {
        MarketingMaterial material = getAccessibleOrThrow(principal, id);
        marketingMaterialRepository.delete(material);
    }

    private MarketingMaterial getAccessibleOrThrow(UserPrincipal principal, UUID id) {
        MarketingMaterial material = marketingMaterialRepository.findById(id)
                .orElseThrow(() -> new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Marketing material not found: " + id));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, material.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Marketing material not found: " + id);
        }
        return material;
    }
}

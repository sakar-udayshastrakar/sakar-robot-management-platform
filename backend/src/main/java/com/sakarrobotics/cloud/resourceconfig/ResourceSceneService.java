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

/** New Resource Configuration → Scene list. Same tenant-scoping chokepoint (TenantAccessGuard) every other resource in this codebase uses. */
@Service
@RequiredArgsConstructor
public class ResourceSceneService {

    private final ResourceSceneRepository resourceSceneRepository;
    private final TenantAccessGuard tenantAccessGuard;

    public List<ResourceScene> listAccessible(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null ? resourceSceneRepository.findAll() : resourceSceneRepository.findByOrganizationIdIn(orgIds);
    }

    @Transactional
    public ResourceScene create(UserPrincipal principal, UUID organizationId, UUID siteId, UUID robotId, String name,
            String resourcePackType) {
        tenantAccessGuard.assertOrganizationAccess(principal, organizationId);
        ResourceScene scene = new ResourceScene();
        scene.setOrganizationId(organizationId);
        scene.setSiteId(siteId);
        scene.setRobotId(robotId);
        scene.setName(name);
        scene.setResourcePackType(resourcePackType == null || resourcePackType.isBlank() ? "STANDARD" : resourcePackType);
        return resourceSceneRepository.save(scene);
    }

    @Transactional
    public ResourceScene update(UserPrincipal principal, UUID id, UUID siteId, UUID robotId, String name,
            String resourcePackType, SceneStatus status) {
        ResourceScene scene = getAccessibleOrThrow(principal, id);
        scene.setSiteId(siteId);
        scene.setRobotId(robotId);
        scene.setName(name);
        scene.setResourcePackType(resourcePackType == null || resourcePackType.isBlank() ? "STANDARD" : resourcePackType);
        scene.setStatus(status);
        return resourceSceneRepository.save(scene);
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID id) {
        ResourceScene scene = getAccessibleOrThrow(principal, id);
        resourceSceneRepository.delete(scene);
    }

    private ResourceScene getAccessibleOrThrow(UserPrincipal principal, UUID id) {
        ResourceScene scene = resourceSceneRepository.findById(id)
                .orElseThrow(() -> new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Scene not found: " + id));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, scene.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Scene not found: " + id);
        }
        return scene;
    }
}

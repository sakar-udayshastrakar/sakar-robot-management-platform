package com.sakarrobotics.cloud.org;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Transactional
    public Organization create(String name, OrganizationType orgType, UUID parentOrganizationId) {
        Organization org = new Organization();
        org.setName(name);
        org.setOrgType(orgType);
        org.setStatus(OrganizationStatus.ACTIVE);
        org.setParentOrganizationId(parentOrganizationId);

        String parentPath = "/";
        if (parentOrganizationId != null) {
            Organization parent = organizationRepository.findById(parentOrganizationId)
                    .orElseThrow(() -> new ApiException(SakarErrorCode.ORGANIZATION_NOT_FOUND,
                            "Parent organization not found: " + parentOrganizationId));
            parentPath = parent.getPath();
        }
        // Path is finalized once the row has an id (materialized ancestry path).
        org.setPath(parentPath);
        Organization saved = organizationRepository.save(org);
        saved.setPath(parentPath + saved.getId() + "/");
        return organizationRepository.save(saved);
    }

    public Organization getOrThrow(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.ORGANIZATION_NOT_FOUND,
                        "Organization not found: " + organizationId));
    }

    /** True if {@code candidateDescendantId} is {@code ancestorId} itself or a descendant of it. */
    public boolean isSameOrDescendant(UUID ancestorId, UUID candidateDescendantId) {
        if (ancestorId.equals(candidateDescendantId)) {
            return true;
        }
        Organization ancestor = getOrThrow(ancestorId);
        Organization candidate = getOrThrow(candidateDescendantId);
        return candidate.getPath().startsWith(ancestor.getPath());
    }
}

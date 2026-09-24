package com.sakarrobotics.cloud.openplatform.dto;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.openplatform.OpenPlatformRegistration;
import com.sakarrobotics.cloud.openplatform.OpenPlatformRegistrationStatus;

public record OpenPlatformRegistrationResponse(
        UUID id,
        UUID organizationId,
        String companyName,
        String area,
        String companyAddress,
        String systemMatcher,
        String contactInformation,
        String dockingRequirements,
        OpenPlatformRegistrationStatus status,
        String submittedBy,
        String reviewedBy,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static OpenPlatformRegistrationResponse from(OpenPlatformRegistration r) {
        return new OpenPlatformRegistrationResponse(r.getId(), r.getOrganizationId(), r.getCompanyName(), r.getArea(),
                r.getCompanyAddress(), r.getSystemMatcher(), r.getContactInformation(), r.getDockingRequirements(),
                r.getStatus(), r.getSubmittedBy(), r.getReviewedBy(), r.getReviewedAt(), r.getCreatedAt(), r.getUpdatedAt());
    }
}

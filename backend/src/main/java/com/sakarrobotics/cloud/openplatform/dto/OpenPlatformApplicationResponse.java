package com.sakarrobotics.cloud.openplatform.dto;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.openplatform.OpenPlatformApplication;

/**
 * The public shape of an application — deliberately has no field for the
 * secret key at all (not even hashed); {@code secretKeyMasked} is the only
 * hint ever shown again after creation. See {@link OpenPlatformApplication}'s
 * own Javadoc for why.
 */
public record OpenPlatformApplicationResponse(
        UUID id,
        UUID organizationId,
        String appId,
        String applicationName,
        String businessType,
        String accessKey,
        String secretKeyMasked,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static OpenPlatformApplicationResponse from(OpenPlatformApplication a) {
        return new OpenPlatformApplicationResponse(a.getId(), a.getOrganizationId(), a.getAppId(), a.getApplicationName(),
                a.getBusinessType(), a.getAccessKey(), "••••••••" + a.getSecretKeyLastFour(), a.getCreatedBy(),
                a.getCreatedAt(), a.getUpdatedAt());
    }
}

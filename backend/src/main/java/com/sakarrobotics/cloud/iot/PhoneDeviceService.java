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

/** IoT Platform → Phone Module → Device management. Same "no fabricated online status" convention as {@link ElevatorDevice}. */
@Service
@RequiredArgsConstructor
public class PhoneDeviceService {

    private final PhoneDeviceRepository phoneDeviceRepository;
    private final SiteRepository siteRepository;
    private final TenantAccessGuard tenantAccessGuard;

    @Transactional
    public PhoneDevice register(UserPrincipal principal, UUID organizationId, UUID siteId, String deviceId,
            String deviceName, String networkingMode) {
        tenantAccessGuard.assertOrganizationAccess(principal, organizationId);
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.SITE_NOT_FOUND, "Site not found: " + siteId));
        if (!site.getOrganizationId().equals(organizationId)) {
            throw new ApiException(SakarErrorCode.SITE_NOT_FOUND, "Site not found: " + siteId);
        }

        PhoneDevice device = new PhoneDevice();
        device.setOrganizationId(organizationId);
        device.setSiteId(siteId);
        device.setDeviceId(deviceId);
        device.setDeviceName(deviceName);
        device.setNetworkingMode(networkingMode);
        return phoneDeviceRepository.save(device);
    }

    public List<PhoneDevice> list(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null
                ? phoneDeviceRepository.findAllByOrderByCreatedAtDesc()
                : phoneDeviceRepository.findByOrganizationIdInOrderByCreatedAtDesc(orgIds);
    }
}

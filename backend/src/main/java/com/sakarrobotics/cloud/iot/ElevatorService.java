package com.sakarrobotics.cloud.iot;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.org.Site;
import com.sakarrobotics.cloud.org.SiteRepository;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import lombok.RequiredArgsConstructor;

/**
 * IoT Platform → Elevator Module. {@link ElevatorDevice} is a real device
 * registry; {@link ElevatorConfiguration} binds one of those devices to an
 * existing Robot for a Site. Nothing here reports a live "online status" or
 * a real elevator-call delivery — see {@link ElevatorDeliveryStatus}'s own
 * Javadoc for why "delivered" is operator-recorded bookkeeping only.
 */
@Service
@RequiredArgsConstructor
public class ElevatorService {

    private final ElevatorDeviceRepository elevatorDeviceRepository;
    private final ElevatorConfigurationRepository elevatorConfigurationRepository;
    private final ElevatorConfigurationEventRepository elevatorConfigurationEventRepository;
    private final ElevatorConfigurationDeliveryRepository elevatorConfigurationDeliveryRepository;
    private final SiteRepository siteRepository;
    private final RobotRepository robotRepository;
    private final TenantAccessGuard tenantAccessGuard;

    private Site siteOrThrow(UUID siteId, UUID organizationId) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.SITE_NOT_FOUND, "Site not found: " + siteId));
        if (!site.getOrganizationId().equals(organizationId)) {
            throw new ApiException(SakarErrorCode.SITE_NOT_FOUND, "Site not found: " + siteId);
        }
        return site;
    }

    @Transactional
    public ElevatorDevice registerDevice(UserPrincipal principal, UUID organizationId, UUID siteId, String deviceId,
            String deviceName, String building, String protocol, String networkingMode, String communicationMode) {
        tenantAccessGuard.assertOrganizationAccess(principal, organizationId);
        siteOrThrow(siteId, organizationId);

        ElevatorDevice device = new ElevatorDevice();
        device.setOrganizationId(organizationId);
        device.setSiteId(siteId);
        device.setDeviceId(deviceId);
        device.setDeviceName(deviceName);
        device.setBuilding(building);
        device.setProtocol(protocol);
        device.setNetworkingMode(networkingMode);
        device.setCommunicationMode(communicationMode);
        return elevatorDeviceRepository.save(device);
    }

    public List<ElevatorDevice> listDevices(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null
                ? elevatorDeviceRepository.findAllByOrderByCreatedAtDesc()
                : elevatorDeviceRepository.findByOrganizationIdInOrderByCreatedAtDesc(orgIds);
    }

    private ElevatorDevice deviceOrThrow(UUID id, UUID organizationId) {
        ElevatorDevice device = elevatorDeviceRepository.findById(id)
                .orElseThrow(() -> new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Elevator device not found: " + id));
        if (!device.getOrganizationId().equals(organizationId)) {
            throw new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Elevator device not found: " + id);
        }
        return device;
    }

    @Transactional
    public ElevatorConfiguration createConfiguration(UserPrincipal principal, UUID organizationId, UUID siteId,
            UUID elevatorDeviceId, UUID robotId, String name, String notes) {
        tenantAccessGuard.assertOrganizationAccess(principal, organizationId);
        siteOrThrow(siteId, organizationId);
        deviceOrThrow(elevatorDeviceId, organizationId);
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_NOT_FOUND, "Robot not found: " + robotId));
        if (!robot.getOrganizationId().equals(organizationId)) {
            throw new ApiException(SakarErrorCode.ROBOT_NOT_FOUND, "Robot not found: " + robotId);
        }

        ElevatorConfiguration configuration = new ElevatorConfiguration();
        configuration.setOrganizationId(organizationId);
        configuration.setSiteId(siteId);
        configuration.setElevatorDeviceId(elevatorDeviceId);
        configuration.setRobotId(robotId);
        configuration.setName(name);
        configuration.setNotes(notes);
        configuration.setModifiedBy(principal.getEmail());
        ElevatorConfiguration saved = elevatorConfigurationRepository.save(configuration);
        recordEvent(saved.getId(), "CREATED", null);
        return saved;
    }

    public List<ElevatorConfiguration> listConfigurations(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null
                ? elevatorConfigurationRepository.findAllByOrderByUpdatedAtDesc()
                : elevatorConfigurationRepository.findByOrganizationIdInOrderByUpdatedAtDesc(orgIds);
    }

    private ElevatorConfiguration configurationOrThrow(UserPrincipal principal, UUID id) {
        ElevatorConfiguration configuration = elevatorConfigurationRepository.findById(id)
                .orElseThrow(() -> new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Elevator configuration not found: " + id));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, configuration.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Elevator configuration not found: " + id);
        }
        return configuration;
    }

    @Transactional
    public ElevatorConfiguration updateConfiguration(UserPrincipal principal, UUID id, String name, String notes) {
        ElevatorConfiguration configuration = configurationOrThrow(principal, id);
        configuration.setName(name);
        configuration.setNotes(notes);
        configuration.setModifiedBy(principal.getEmail());
        ElevatorConfiguration saved = elevatorConfigurationRepository.save(configuration);
        recordEvent(saved.getId(), "UPDATED", null);
        return saved;
    }

    public List<ElevatorConfigurationEvent> events(UserPrincipal principal, UUID configurationId) {
        configurationOrThrow(principal, configurationId);
        return elevatorConfigurationEventRepository.findByElevatorConfigurationIdOrderByIdAsc(configurationId);
    }

    private void recordEvent(UUID configurationId, String eventType, String detail) {
        ElevatorConfigurationEvent event = new ElevatorConfigurationEvent();
        event.setElevatorConfigurationId(configurationId);
        event.setEventType(eventType);
        event.setDetail(detail);
        elevatorConfigurationEventRepository.save(event);
    }

    /** See {@link ElevatorDeliveryStatus}'s own Javadoc: RECORDED, never a fabricated delivery confirmation. */
    @Transactional
    public ElevatorConfigurationDelivery deliver(UserPrincipal principal, UUID configurationId, UUID robotId, ElevatorDeliveryStatus status) {
        ElevatorConfiguration configuration = configurationOrThrow(principal, configurationId);
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_NOT_FOUND, "Robot not found: " + robotId));
        if (!robot.getOrganizationId().equals(configuration.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.ROBOT_NOT_FOUND, "Robot not found: " + robotId);
        }

        ElevatorConfigurationDelivery delivery = new ElevatorConfigurationDelivery();
        delivery.setOrganizationId(configuration.getOrganizationId());
        delivery.setElevatorConfigurationId(configurationId);
        delivery.setRobotId(robotId);
        delivery.setDeliveredBy(principal.getEmail());
        delivery.setStatus(status != null ? status : ElevatorDeliveryStatus.RECORDED);
        recordEvent(configurationId, "DELIVERED", "robotId=" + robotId);
        return elevatorConfigurationDeliveryRepository.save(delivery);
    }

    public List<ElevatorConfigurationDelivery> listDeliveries(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null
                ? elevatorConfigurationDeliveryRepository.findAllByOrderByCreatedAtDesc()
                : elevatorConfigurationDeliveryRepository.findByOrganizationIdInOrderByCreatedAtDesc(orgIds);
    }
}

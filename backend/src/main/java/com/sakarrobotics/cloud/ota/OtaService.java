package com.sakarrobotics.cloud.ota;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import lombok.RequiredArgsConstructor;

/**
 * OTA Management (System Version Management + Update record). "Push" only
 * ever records that an operator initiated a push — see {@link
 * DeploymentStatus}'s own Javadoc for why this never claims real device
 * delivery/confirmation; no MQTT OTA channel or agent-side updater exists
 * anywhere in this codebase.
 */
@Service
@RequiredArgsConstructor
public class OtaService {

    private final SoftwareVersionRepository softwareVersionRepository;
    private final DeploymentRecordRepository deploymentRecordRepository;
    private final RobotRepository robotRepository;
    private final TenantAccessGuard tenantAccessGuard;

    public List<SoftwareVersion> listVersions(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null ? softwareVersionRepository.findAll() : softwareVersionRepository.findByOrganizationIdIn(orgIds);
    }

    @Transactional
    public SoftwareVersion createVersion(UserPrincipal principal, UUID organizationId, String packageName,
            String wholeMachineSoftware, String packageVersion, String hardwareVersion, boolean grayscale,
            Long sizeBytes, String notes) {
        tenantAccessGuard.assertOrganizationAccess(principal, organizationId);
        SoftwareVersion version = new SoftwareVersion();
        version.setOrganizationId(organizationId);
        version.setPackageName(packageName);
        version.setWholeMachineSoftware(wholeMachineSoftware);
        version.setPackageVersion(packageVersion);
        version.setHardwareVersion(hardwareVersion);
        version.setGrayscale(grayscale);
        version.setSizeBytes(sizeBytes);
        version.setCreatedBy(principal.getEmail());
        version.setNotes(notes);
        return softwareVersionRepository.save(version);
    }

    /** See this class's own Javadoc: RECORDED, never a fabricated delivery confirmation. */
    @Transactional
    public DeploymentRecord push(UserPrincipal principal, UUID versionId, UUID robotId, String oldVersionNumber) {
        SoftwareVersion version = softwareVersionRepository.findById(versionId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Software version not found: " + versionId));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, version.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Software version not found: " + versionId);
        }
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_NOT_FOUND, "Robot not found: " + robotId));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, robot.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.ROBOT_NOT_FOUND, "Robot not found: " + robotId);
        }

        DeploymentRecord record = new DeploymentRecord();
        record.setOrganizationId(robot.getOrganizationId());
        record.setRobotId(robotId);
        record.setSoftwareVersionId(versionId);
        record.setOldVersionNumber(oldVersionNumber);
        record.setNewVersionNumber(version.getPackageVersion());
        record.setGrayscale(version.isGrayscale());
        record.setStatus(DeploymentStatus.RECORDED);
        return deploymentRecordRepository.save(record);
    }

    public List<DeploymentRecord> listDeploymentRecords(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null
                ? deploymentRecordRepository.findAllByOrderByCreatedAtDesc()
                : deploymentRecordRepository.findByOrganizationIdInOrderByCreatedAtDesc(orgIds);
    }
}

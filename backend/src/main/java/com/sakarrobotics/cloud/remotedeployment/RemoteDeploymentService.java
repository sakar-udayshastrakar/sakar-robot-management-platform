package com.sakarrobotics.cloud.remotedeployment;

import java.time.Instant;
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
 * Operation And Maintenance Platform → Remote Deployment. See {@link
 * RemoteDeploymentStatus}'s own Javadoc: this only ever records that an
 * operator manually deployed a configuration, never a real remote push.
 */
@Service
@RequiredArgsConstructor
public class RemoteDeploymentService {

    private final RemoteDeploymentRecordRepository remoteDeploymentRecordRepository;
    private final RobotRepository robotRepository;
    private final TenantAccessGuard tenantAccessGuard;

    @Transactional
    public RemoteDeploymentRecord create(UserPrincipal principal, UUID robotId, String notes) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_NOT_FOUND, "Robot not found: " + robotId));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, robot.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.ROBOT_NOT_FOUND, "Robot not found: " + robotId);
        }

        RemoteDeploymentRecord record = new RemoteDeploymentRecord();
        record.setOrganizationId(robot.getOrganizationId());
        record.setSiteId(robot.getSiteId());
        record.setRobotId(robotId);
        record.setDeployedBy(principal.getEmail());
        record.setNotes(notes);
        return remoteDeploymentRecordRepository.save(record);
    }

    public List<RemoteDeploymentRecord> listAccessible(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null
                ? remoteDeploymentRecordRepository.findAllByOrderByCreatedAtDesc()
                : remoteDeploymentRecordRepository.findByOrganizationIdInOrderByCreatedAtDesc(orgIds);
    }

    @Transactional
    public RemoteDeploymentRecord updateStatus(UserPrincipal principal, UUID id, RemoteDeploymentStatus status) {
        RemoteDeploymentRecord record = remoteDeploymentRecordRepository.findById(id)
                .orElseThrow(() -> new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Deployment record not found: " + id));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, record.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Deployment record not found: " + id);
        }
        record.setStatus(status);
        if (status != RemoteDeploymentStatus.RECORDED && record.getCompletedAt() == null) {
            record.setCompletedAt(Instant.now());
        }
        return remoteDeploymentRecordRepository.save(record);
    }
}

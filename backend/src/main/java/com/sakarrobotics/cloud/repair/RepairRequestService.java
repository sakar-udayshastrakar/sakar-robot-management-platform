package com.sakarrobotics.cloud.repair;

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
 * Operation And Maintenance Platform → Customer Repair Requests. A real
 * work-order log a staff member fills in on a customer's behalf (the
 * reference product's own "Construction order on behalf of others" —
 * there is no customer-facing self-report channel in this codebase, so
 * every request here is staff-entered).
 */
@Service
@RequiredArgsConstructor
public class RepairRequestService {

    private final RepairRequestRepository repairRequestRepository;
    private final RobotRepository robotRepository;
    private final TenantAccessGuard tenantAccessGuard;

    @Transactional
    public RepairRequest create(UserPrincipal principal, UUID organizationId, UUID siteId, UUID robotId,
            String symptom, String reportedBy, String notes) {
        tenantAccessGuard.assertOrganizationAccess(principal, organizationId);
        if (robotId != null) {
            Robot robot = robotRepository.findById(robotId)
                    .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_NOT_FOUND, "Robot not found: " + robotId));
            if (!robot.getOrganizationId().equals(organizationId)) {
                throw new ApiException(SakarErrorCode.ROBOT_NOT_FOUND, "Robot not found: " + robotId);
            }
        }

        RepairRequest request = new RepairRequest();
        request.setOrganizationId(organizationId);
        request.setSiteId(siteId);
        request.setRobotId(robotId);
        request.setWorkOrderNumber("WO" + Long.toString(System.nanoTime(), 36).toUpperCase());
        request.setSymptom(symptom);
        request.setReportedBy(reportedBy != null ? reportedBy : principal.getEmail());
        request.setNotes(notes);
        return repairRequestRepository.save(request);
    }

    public List<RepairRequest> listAccessible(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null
                ? repairRequestRepository.findAllByOrderByCreatedAtDesc()
                : repairRequestRepository.findByOrganizationIdInOrderByCreatedAtDesc(orgIds);
    }

    @Transactional
    public RepairRequest updateStatus(UserPrincipal principal, UUID id, RepairRequestStatus status, String notes) {
        RepairRequest request = repairRequestRepository.findById(id)
                .orElseThrow(() -> new ApiException(SakarErrorCode.REPAIR_REQUEST_NOT_FOUND, "Repair request not found: " + id));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, request.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.REPAIR_REQUEST_NOT_FOUND, "Repair request not found: " + id);
        }
        request.setStatus(status);
        if (notes != null) {
            request.setNotes(notes);
        }
        if ((status == RepairRequestStatus.RESOLVED || status == RepairRequestStatus.CLOSED) && request.getResolvedAt() == null) {
            request.setResolvedAt(Instant.now());
        }
        return repairRequestRepository.save(request);
    }
}

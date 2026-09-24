package com.sakarrobotics.cloud.robot.registry;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.org.OrganizationService;
import com.sakarrobotics.cloud.org.OrganizationType;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RobotService {

    private final RobotRepository robotRepository;
    private final RobotModelRepository robotModelRepository;
    private final OrganizationService organizationService;
    private final TenantAccessGuard tenantAccessGuard;

    @Transactional
    public Robot register(UUID organizationId, UUID siteId, UUID robotModelId, String name,
            String serialNumber, String externalRobotId) {
        return register(organizationId, siteId, robotModelId, name, serialNumber, externalRobotId, null);
    }

    /**
     * Same as {@link #register(UUID, UUID, UUID, String, String, String)},
     * additionally recording the vendor's own manufacturer serial (e.g.
     * Keenon {@code mftCode}) — distinct from {@code serialNumber}, which is
     * always Sakar's own generated identity. {@code vendorSerialNumber} is
     * never validated for uniqueness: it is vendor-owned reference data, not
     * a Sakar identity field.
     */
    @Transactional
    public Robot register(UUID organizationId, UUID siteId, UUID robotModelId, String name,
            String serialNumber, String externalRobotId, String vendorSerialNumber) {
        if (robotRepository.existsBySerialNumber(serialNumber)) {
            throw new ApiException(SakarErrorCode.DUPLICATE_SERIAL_NUMBER,
                    "A robot with serial number " + serialNumber + " is already registered");
        }
        robotModelRepository.findById(robotModelId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_MODEL_NOT_FOUND,
                        "Robot model not found: " + robotModelId));
        // The Sakar-serial <-> vendor-serial mapping is Sakar-Robotics-owned data — a
        // non-Sakar-Robotics organization's robot must never carry a vendor identifier,
        // even though that org may otherwise fully own/manage the robot itself.
        if (externalRobotId != null && organizationService.getOrThrow(organizationId).getOrgType() != OrganizationType.SAKAR_ROOT) {
            throw new ApiException(SakarErrorCode.EXTERNAL_ROBOT_ID_NOT_ALLOWED,
                    "A vendor (Keenon) identifier may only be set for a robot registered under the Sakar Robotics organization");
        }
        // Duplicate-vendor-serial guard, scoped per organization (not per org-type) so it
        // stays correct even if more than one SAKAR_ROOT-type organization ever exists —
        // see RobotRepository.existsByOrganizationIdAndExternalRobotId for why.
        if (externalRobotId != null && robotRepository.existsByOrganizationIdAndExternalRobotId(organizationId, externalRobotId)) {
            throw new ApiException(SakarErrorCode.DUPLICATE_EXTERNAL_ROBOT_ID,
                    "A robot with external (Keenon) id " + externalRobotId + " is already registered in this organization");
        }

        Robot robot = new Robot();
        robot.setOrganizationId(organizationId);
        robot.setSiteId(siteId);
        robot.setRobotModelId(robotModelId);
        robot.setName(name);
        robot.setSerialNumber(serialNumber);
        robot.setExternalRobotId(externalRobotId);
        robot.setVendorSerialNumber(vendorSerialNumber);
        robot.setStatus(RobotLifecycleStatus.REGISTERED);
        return robotRepository.save(robot);
    }

    /**
     * Resolves a robot the caller may see. Deliberately raises the same
     * {@code ROBOT_NOT_FOUND} (404) whether the robot doesn't exist at all
     * or exists in an organization outside the caller's scope — never a 403
     * — so a client cannot distinguish "not found" from "not yours" and
     * enumerate cross-tenant robot ids (SAKAR_ROBOT_PLATFORM_API_SPEC.md
     * §1.12).
     */
    public Robot getAccessibleOrThrow(UserPrincipal principal, UUID robotId) {
        Robot robot = robotRepository.findById(robotId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROBOT_NOT_FOUND, "Robot not found: " + robotId));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, robot.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.ROBOT_NOT_FOUND, "Robot not found: " + robotId);
        }
        return robot;
    }

    public Page<Robot> listAccessible(UserPrincipal principal, int page, int pageSize) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        PageRequest pageRequest = PageRequest.of(page, pageSize);
        return orgIds == null ? robotRepository.findAll(pageRequest) : robotRepository.findByOrganizationIdIn(orgIds, pageRequest);
    }

    @Transactional
    public Robot activate(UserPrincipal principal, UUID robotId) {
        Robot robot = getAccessibleOrThrow(principal, robotId);
        robot.setStatus(RobotLifecycleStatus.ACTIVE);
        return robotRepository.save(robot);
    }

    @Transactional
    public Robot deactivate(UserPrincipal principal, UUID robotId) {
        Robot robot = getAccessibleOrThrow(principal, robotId);
        robot.setStatus(RobotLifecycleStatus.DEACTIVATED);
        return robotRepository.save(robot);
    }

    /** "Bind store" (Robot Management, Phase 2) — see {@code UpdateRobotInventoryRequest}'s own Javadoc for the full-replace semantics. */
    @Transactional
    public Robot updateInventory(UserPrincipal principal, UUID robotId, UUID siteId, LocalDate warrantyStartDate,
            LocalDate warrantyEndDate) {
        Robot robot = getAccessibleOrThrow(principal, robotId);
        robot.setSiteId(siteId);
        robot.setWarrantyStartDate(warrantyStartDate);
        robot.setWarrantyEndDate(warrantyEndDate);
        return robotRepository.save(robot);
    }

    /**
     * "Allocate to lower level agent" (Robot Management, Phase 2) — moves a robot exactly one
     * level down the distributor/sub-distributor/client tree ({@link
     * com.sakarrobotics.cloud.org.OrganizationService#isDirectChildOf}), never several levels
     * at once or sideways; rejected before any write if the target is not a direct child of
     * the robot's own current organization.
     */
    @Transactional
    public Robot allocateToChildOrganization(UserPrincipal principal, UUID robotId, UUID targetOrganizationId) {
        Robot robot = getAccessibleOrThrow(principal, robotId);
        if (!organizationService.isDirectChildOf(robot.getOrganizationId(), targetOrganizationId)) {
            throw new ApiException(SakarErrorCode.VALIDATION_FAILED,
                    "Target organization is not a direct child of this robot's current organization");
        }
        robot.setOrganizationId(targetOrganizationId);
        return robotRepository.save(robot);
    }

    /**
     * "Returning inventory" (Robot Management, Phase 2) — unassigns the robot from any site and
     * resets it to {@code REGISTERED}, the same state a freshly-registered, not-yet-deployed
     * robot starts in. Deliberately does not touch {@code organizationId}: returning a robot to
     * inventory is a deployment-state change, not a re-allocation up the agent hierarchy.
     */
    @Transactional
    public Robot returnToInventory(UserPrincipal principal, UUID robotId) {
        Robot robot = getAccessibleOrThrow(principal, robotId);
        robot.setSiteId(null);
        robot.setStatus(RobotLifecycleStatus.REGISTERED);
        return robotRepository.save(robot);
    }
}

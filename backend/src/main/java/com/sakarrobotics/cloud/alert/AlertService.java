package com.sakarrobotics.cloud.alert;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.audit.AuditService;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final RobotAlertRepository robotAlertRepository;
    private final TenantAccessGuard tenantAccessGuard;
    private final AuditService auditService;

    public Page<RobotAlert> listAccessible(UserPrincipal principal, int page, int pageSize) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        PageRequest pageRequest = PageRequest.of(page, pageSize);
        return orgIds == null
                ? robotAlertRepository.findAllByOrderByIdDesc(pageRequest)
                : robotAlertRepository.findByOrganizationIdInOrderByIdDesc(orgIds, pageRequest);
    }

    public RobotAlert getAccessibleOrThrow(UserPrincipal principal, UUID alertId) {
        RobotAlert alert = robotAlertRepository.findById(alertId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.ALERT_NOT_FOUND, "Alert not found: " + alertId));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, alert.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.ALERT_NOT_FOUND, "Alert not found: " + alertId);
        }
        return alert;
    }

    @Transactional
    public RobotAlert acknowledge(UserPrincipal principal, UUID alertId) {
        RobotAlert alert = getAccessibleOrThrow(principal, alertId);
        alert.setStatus(AlertStatus.ACKNOWLEDGED.name());
        alert.setAcknowledgedBy(principal.getUserId());
        alert.setAcknowledgedAt(Instant.now());
        RobotAlert saved = robotAlertRepository.save(alert);
        auditService.record(principal, alert.getOrganizationId(), alert.getRobotId(),
                "ALERT_ACKNOWLEDGED", "SUCCESS", null, null, null);
        return saved;
    }

    @Transactional
    public RobotAlert resolve(UserPrincipal principal, UUID alertId) {
        RobotAlert alert = getAccessibleOrThrow(principal, alertId);
        alert.setStatus(AlertStatus.RESOLVED.name());
        RobotAlert saved = robotAlertRepository.save(alert);
        auditService.record(principal, alert.getOrganizationId(), alert.getRobotId(),
                "ALERT_RESOLVED", "SUCCESS", null, null, null);
        return saved;
    }
}

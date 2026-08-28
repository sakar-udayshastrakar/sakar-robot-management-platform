package com.sakarrobotics.cloud.audit;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.web.RequestIdFilter;
import com.sakarrobotics.cloud.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

/**
 * The one place every sensitive action in the platform is recorded
 * (Master Requirements Part 12.E). Callers pass the currently authenticated
 * {@link UserPrincipal} (or {@code null} for system-initiated actions) —
 * this service never re-derives "who did this" from ambient state.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void record(UserPrincipal actor, UUID organizationId, UUID robotId, String action, String result,
            String reason, String ipAddress, String device) {
        AuditLog log = new AuditLog();
        log.setUserId(actor != null ? actor.getUserId() : null);
        log.setOrganizationId(organizationId);
        log.setRobotId(robotId);
        log.setAction(action);
        log.setResult(result);
        log.setReason(reason);
        log.setIpAddress(ipAddress);
        log.setDevice(device);
        log.setRequestId(RequestIdFilter.currentOrNew());
        auditLogRepository.save(log);
    }
}

package com.sakarrobotics.cloud.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.sakarrobotics.cloud.IntegrationTestSupport;

class AuditServiceTest extends IntegrationTestSupport {

    @Autowired
    private AuditService auditService;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void record_persistsActionAndScope_withoutRequiringAnAuthenticatedActor() {
        UUID orgId = UUID.randomUUID();
        UUID robotId = UUID.randomUUID();

        auditService.record(null, orgId, robotId, "ROBOT_REGISTERED", "SUCCESS", null, "127.0.0.1", "junit");

        AuditLog last = auditLogRepository.findAllByOrderByIdDesc(org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0);
        assertThat(last.getAction()).isEqualTo("ROBOT_REGISTERED");
        assertThat(last.getResult()).isEqualTo("SUCCESS");
        assertThat(last.getOrganizationId()).isEqualTo(orgId);
        assertThat(last.getRobotId()).isEqualTo(robotId);
        assertThat(last.getUserId()).isNull(); // system-initiated action, no actor
    }
}

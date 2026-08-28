package com.sakarrobotics.cloud.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByOrganizationIdIn(List<UUID> organizationIds, Pageable pageable);

    Page<AuditLog> findAllByOrderByIdDesc(Pageable pageable);
}

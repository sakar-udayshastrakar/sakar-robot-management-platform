package com.sakarrobotics.cloud.robot.registry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotRepository extends JpaRepository<Robot, UUID> {

    boolean existsBySerialNumber(String serialNumber);

    Optional<Robot> findByExternalRobotId(String externalRobotId);

    /**
     * Scoped (not global) duplicate check for the vendor identifier — deliberately
     * per-organization rather than per-org-type, since it stays correct even if more
     * than one SAKAR_ROOT-type organization ever exists (see {@code RobotService.register}).
     */
    boolean existsByOrganizationIdAndExternalRobotId(UUID organizationId, String externalRobotId);

    /** Tenant-scoped listing: pass the caller's own org id plus every accessible descendant id. */
    Page<Robot> findByOrganizationIdIn(List<UUID> organizationIds, Pageable pageable);

    /** Unpaged variant — Operational Dashboard aggregation (see DashboardService), never a paged UI listing. */
    List<Robot> findByOrganizationIdIn(List<UUID> organizationIds);

    /** Unpaged, single-organization listing — used by bulk/administrative operations (e.g. {@code RobotSerialReconciliationService}) that must process every matching row deterministically, not one page at a time. */
    List<Robot> findByOrganizationId(UUID organizationId);
}

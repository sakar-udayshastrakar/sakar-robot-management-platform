package com.sakarrobotics.cloud.integration.keenon;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KeenonAreaMappingRepository extends JpaRepository<KeenonAreaMapping, UUID> {

    List<KeenonAreaMapping> findByRobotIdAndActiveTrue(UUID robotId);

    Optional<KeenonAreaMapping> findByIdAndActiveTrue(UUID id);

    /** Upsert lookup key for {@code KeenonAreaSyncService} — regardless of {@code active}, so a
     * previously-deactivated area that reappears in a later sync is reactivated in place rather
     * than creating a second row for the same vendor area id. */
    Optional<KeenonAreaMapping> findByRobotIdAndKeenonAreaId(UUID robotId, String keenonAreaId);

    /**
     * Every currently-active mapping, for {@code KeenonAreaSyncScheduler} to discover which
     * (robot, storeId) pairs already have an established mapping worth periodically refreshing —
     * the scheduler never invents a storeId for a robot that has never been synced before.
     */
    List<KeenonAreaMapping> findByActiveTrue();
}

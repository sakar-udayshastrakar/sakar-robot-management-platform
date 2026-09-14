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

    /**
     * Every mapping for a robot regardless of {@code active} — used by {@code
     * KeenonRobotAdapter#getAreas} to recover a previously-known Keenon store id when every
     * active mapping has been deactivated (e.g. a prior sync's live response reported zero
     * areas), so an on-demand re-sync can still be attempted. Never used to fabricate a store
     * id for a robot with no prior mapping row at all.
     */
    List<KeenonAreaMapping> findByRobotId(UUID robotId);
}

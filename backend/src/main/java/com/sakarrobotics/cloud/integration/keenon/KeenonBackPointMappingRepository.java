package com.sakarrobotics.cloud.integration.keenon;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KeenonBackPointMappingRepository extends JpaRepository<KeenonBackPointMapping, UUID> {

    List<KeenonBackPointMapping> findByRobotIdAndActiveTrue(UUID robotId);

    /** Upsert lookup key for {@code KeenonBackPointSyncService} — regardless of
     * {@code active}, so a previously-deactivated point that reappears in a later
     * sync is reactivated in place rather than creating a second row for the same
     * vendor back-point id. */
    Optional<KeenonBackPointMapping> findByRobotIdAndKeenonBackPointId(UUID robotId, String keenonBackPointId);
}

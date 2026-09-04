package com.sakarrobotics.cloud.integration.keenon;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KeenonCleaningModeMappingRepository extends JpaRepository<KeenonCleaningModeMapping, UUID> {

    List<KeenonCleaningModeMapping> findByRobotIdAndActiveTrue(UUID robotId);

    /** Upsert lookup key for {@code KeenonCleaningModeSyncService} — regardless of
     * {@code active}, so a previously-deactivated mode that reappears in a later
     * sync is reactivated in place rather than creating a second row for the same
     * vendor mode id. */
    Optional<KeenonCleaningModeMapping> findByRobotIdAndKeenonModeId(UUID robotId, String keenonModeId);
}

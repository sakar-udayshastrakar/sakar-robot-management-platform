package com.sakarrobotics.cloud.map;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MapPointRepository extends JpaRepository<MapPoint, UUID> {

    List<MapPoint> findByMapIdAndActiveTrue(UUID mapId);

    /**
     * Upsert lookup key for {@code KeenonMapPointSyncService} — {@code
     * map_points} has no vendor point-id column, so {@code name} (scoped to
     * the map) is the closest schema-supported approximation of vendor
     * identity. Documented limitation: a vendor-side rename of a point
     * creates a new row here rather than updating the existing one, since
     * there is no stable id to match on instead.
     */
    Optional<MapPoint> findByMapIdAndName(UUID mapId, String name);
}

package com.sakarrobotics.cloud.integration.keenon;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps a Sakar-owned return/charging-point identity to the CURRENT Keenon
 * vendor back-point id for one robot (mirrors {@link
 * KeenonCleaningModeMapping} for the same reason: never compile a vendor
 * point id — such as the observed {@code 39} — into application code).
 * {@code GET .../clean/robot/strategy/back/point} is keyed only by {@code
 * robotSn}, so there is no {@code keenon_store_id}/{@code keenon_map_id}
 * here either.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "keenon_back_point_mappings")
public class KeenonBackPointMapping extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(name = "keenon_back_point_id", nullable = false)
    private String keenonBackPointId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}

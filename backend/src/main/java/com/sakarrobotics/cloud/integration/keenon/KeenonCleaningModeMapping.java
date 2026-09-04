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
 * Maps a Sakar-owned cleaning-mode identity to the CURRENT Keenon vendor
 * mode id for one robot (mirrors {@link KeenonAreaMapping} for the same
 * reason: never compile a vendor mode id into application code). Unlike
 * area mappings, there is no {@code keenon_store_id}/{@code keenon_map_id}
 * — {@code GET .../clean/robot/strategy/clean/model} is keyed only by
 * {@code robotSn}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "keenon_cleaning_mode_mappings")
public class KeenonCleaningModeMapping extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(name = "keenon_mode_id", nullable = false)
    private String keenonModeId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}

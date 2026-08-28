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
 * Maps a Sakar-owned area identity to the CURRENT Keenon vendor area id.
 * Mandatory per Master Requirements Part 40 / the live evidence: Keenon
 * area ids are live configuration and change over time (the Conference
 * carpet area id observed in testing had already gone stale once) — the
 * platform must resolve "the area the user selected" through this table on
 * every use, never through a hardcoded id anywhere in application code.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "keenon_area_mappings")
public class KeenonAreaMapping extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(name = "keenon_store_id", nullable = false)
    private String keenonStoreId;

    @Column(name = "keenon_map_id")
    private String keenonMapId;

    @Column(name = "keenon_area_id", nullable = false)
    private String keenonAreaId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}

package com.sakarrobotics.cloud.integration.keenon;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sakar-owned, per-robot Keenon {@code sceneCode} configuration (Phase 1I).
 * Exists because no live Keenon endpoint currently resolves a C-series
 * robot's CURRENT sceneCode for this account — see {@link
 * KeenonMapMetadataSyncService}'s own Javadoc for the full evidence trail.
 * One row per robot ({@code robot_id} is unique); set only via {@code PUT
 * /api/v1/robots/{id}/keenon/scene-config}, never defaulted, never inferred
 * from another robot's row.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "keenon_robot_scene_configs")
public class KeenonRobotSceneConfig extends BaseEntity {

    @Column(name = "robot_id", nullable = false, unique = true)
    private UUID robotId;

    @Column(name = "scene_code", nullable = false)
    private String sceneCode;

    @Column(name = "scene_name")
    private String sceneName;
}

package com.sakarrobotics.cloud.map;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code maps} (SAKAR_ROBOT_PLATFORM_DATABASE.md §20). Named
 * {@code RobotMap} to avoid colliding with {@code java.util.Map}. Phase 1
 * scope: schema/entity only.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "maps")
public class RobotMap extends BaseEntity {

    @Column(name = "robot_id", nullable = false)
    private UUID robotId;

    @Column(name = "vendor_map_id")
    private String vendorMapId;

    @Column(name = "image_url")
    private String imageUrl;

    @Column
    private String name;
}

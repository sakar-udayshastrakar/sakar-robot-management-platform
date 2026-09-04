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
 * {@code RobotMap} to avoid colliding with {@code java.util.Map}.
 *
 * <p>{@code width}/{@code height}/{@code mapMd5} were added by the raw
 * Keenon PNG map-storage slice — see {@code KeenonMapImageSyncService}.
 * {@code imageUrl} currently holds a local filesystem path, not a public
 * URL — no image-serving controller exists yet (a later slice's concern);
 * nothing exposes this value to any client today. Deliberately no
 * {@code resolution}/{@code originX}/{@code originY}/{@code yaw}/occupancy
 * fields — none are evidenced by any captured Keenon response.
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

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column(name = "map_md5")
    private String mapMd5;
}

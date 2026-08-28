package com.sakarrobotics.cloud.map;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Maps to {@code map_points}. Phase 1 scope: schema/entity only. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "map_points")
public class MapPoint extends BaseEntity {

    @Column(name = "map_id", nullable = false)
    private UUID mapId;

    @Column(nullable = false)
    private String name;

    @Column(name = "point_type")
    private String pointType;

    @Column
    private Double x;

    @Column
    private Double y;
}

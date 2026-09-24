package com.sakarrobotics.cloud.resourceconfig;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code resource_scenes} (Robot Management → New Resource
 * Configuration). A named configuration profile — map points, voice, route,
 * and business resources — optionally bound to one store ({@link #siteId},
 * reusing the existing {@code sites} table, never a new "Store" concept)
 * and/or one specific robot ({@link #robotId}). {@code resourcePackType} is
 * a plain, Sakar-owned free-text classification, not a copy of any vendor's
 * business-specific vocabulary (e.g. food-delivery "dining" categories,
 * which do not apply to Sakar's own robot lineup).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "resource_scenes")
public class ResourceScene extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "robot_id")
    private UUID robotId;

    @Column(nullable = false)
    private String name;

    @Column(name = "resource_pack_type", nullable = false)
    private String resourcePackType = "STANDARD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SceneStatus status = SceneStatus.DRAFT;
}

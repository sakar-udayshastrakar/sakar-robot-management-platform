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

/** Maps to {@code marketing_materials} (Robot Management → New Resource Configuration). A named material package, no file storage yet — see this pass's own scope note. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "marketing_materials")
public class MarketingMaterial extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column(name = "material_type", nullable = false)
    private String materialType = "GENERAL";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SceneStatus status = SceneStatus.DRAFT;
}

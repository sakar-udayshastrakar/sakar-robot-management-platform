package com.sakarrobotics.cloud.iot;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code ladder_control_store_bindings} — IoT Platform → Elevator
 * Module → Cloud ladder control configuration → Store binding. One binding
 * per Site to a third-party ("tripartite") ladder-control vendor account.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ladder_control_store_bindings")
public class LadderControlStoreBinding extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id", nullable = false, unique = true)
    private UUID siteId;

    @Column(nullable = false)
    private String manufacturer;

    @Column(name = "building_id")
    private String buildingId;

    @Column(name = "client_id")
    private String clientId;
}

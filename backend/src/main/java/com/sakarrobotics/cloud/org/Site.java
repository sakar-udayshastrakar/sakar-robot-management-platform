package com.sakarrobotics.cloud.org;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code sites} (SAKAR_ROBOT_PLATFORM_DATABASE.md §5) — also this
 * platform's "Store" (Store Management, Robot Management sidebar group).
 * "Affiliated agent" needs no column here: it is {@link #organizationId}'s
 * own organization, resolved by the caller.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sites")
public class Site extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column
    private String address;

    @Column
    private String timezone;

    /** The region/country the store is located in (e.g. "INDIA") — distinct from {@link #timezone}. */
    @Column
    private String area;

    @Column(name = "contact_name")
    private String contactName;

    @Column
    private String phone;

    @Column
    private String email;

    /** Business/venue classification (e.g. "Hotel") — plain, Sakar-owned free text, no fixed vocabulary imposed. */
    @Column(name = "scene_type")
    private String sceneType;

    @Column(name = "is_chain_brand", nullable = false)
    private boolean chainBrand = false;
}

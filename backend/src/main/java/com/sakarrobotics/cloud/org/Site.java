package com.sakarrobotics.cloud.org;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Maps to {@code sites} (SAKAR_ROBOT_PLATFORM_DATABASE.md §5). */
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
}

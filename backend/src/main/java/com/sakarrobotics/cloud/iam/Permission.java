package com.sakarrobotics.cloud.iam;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Maps 1:1 to {@code permissions} (SAKAR_ROBOT_PLATFORM_DATABASE.md §3). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "permissions")
public class Permission extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 64)
    private PermissionCode code;

    @Column
    private String description;

    public Permission(PermissionCode code, String description) {
        this.code = code;
        this.description = description;
    }
}

package com.sakarrobotics.cloud.iam;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code departments} (V19__departments_and_user_type.sql). A flat,
 * Sakar-wide list used to group internal (Sakar-staff) users — deliberately
 * not organization-scoped, see the migration's own header comment.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "departments")
public class Department extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    public Department(String name) {
        this.name = name;
    }
}

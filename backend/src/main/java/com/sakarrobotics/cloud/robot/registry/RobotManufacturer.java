package com.sakarrobotics.cloud.robot.registry;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code robot_manufacturers}
 * (SAKAR_ROBOT_PLATFORM_DATABASE.md Appendix A.1). Normalizes what would
 * otherwise be a free-text {@code vendor} column on {@code robot_models}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "robot_manufacturers")
public class RobotManufacturer extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    public RobotManufacturer(String name) {
        this.name = name;
    }
}

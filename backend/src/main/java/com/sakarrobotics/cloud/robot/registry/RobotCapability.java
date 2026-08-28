package com.sakarrobotics.cloud.robot.registry;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code robot_capabilities}
 * (SAKAR_ROBOT_PLATFORM_DATABASE.md Appendix A.2). One row per
 * (robot model, capability) pair. A capability absent here — or present
 * with {@code supported = false} — must be rejected before it ever reaches
 * an adapter (Master Requirements §6.A's {@code UNSUPPORTED_CAPABILITY} rule).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "robot_capabilities", uniqueConstraints = @UniqueConstraint(columnNames = { "robot_model_id", "capability" }))
public class RobotCapability extends BaseEntity {

    @Column(name = "robot_model_id", nullable = false)
    private UUID robotModelId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RobotCapabilityType capability;

    @Column(nullable = false)
    private boolean supported = false;

    public RobotCapability(UUID robotModelId, RobotCapabilityType capability, boolean supported) {
        this.robotModelId = robotModelId;
        this.capability = capability;
        this.supported = supported;
    }
}

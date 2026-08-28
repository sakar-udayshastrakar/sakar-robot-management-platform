package com.sakarrobotics.cloud.robot.registry;

import java.time.Instant;
import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code robot_credentials} (SAKAR_ROBOT_PLATFORM_DATABASE.md §8) —
 * the Sakar Robot Agent's own device credential for the future local path
 * (MQTT client identity, Part 15/23), distinct from any vendor credential.
 * {@code credentialValue} must never be the raw secret at rest in a real
 * deployment — Phase 1 stores an opaque, already-hashed/encrypted value
 * only; the encryption-at-rest mechanism itself is a secrets-manager
 * integration deferred past Phase 1 (see Final Report "known limitations").
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "robot_credentials")
public class RobotCredential extends BaseEntity {

    @Column(name = "robot_id", nullable = false, unique = true)
    private UUID robotId;

    @Column(name = "credential_type", nullable = false)
    private String credentialType;

    @Column(name = "credential_value_hash", nullable = false)
    private String credentialValueHash;

    @Column(name = "provisioned_at", nullable = false)
    private Instant provisionedAt = Instant.now();

    @Column(name = "rotated_at")
    private Instant rotatedAt;
}

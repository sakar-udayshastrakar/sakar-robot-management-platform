package com.sakarrobotics.cloud.openplatform;

import java.util.UUID;

import com.sakarrobotics.cloud.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps to {@code open_platform_applications} — Open Platform → Application
 * management, a real API client/key registry. {@code secretKeyHash} is
 * BCrypt-hashed (same {@code PasswordEncoder} bean as user passwords) and is
 * never serialized back to a client — see {@code
 * dto.OpenPlatformApplicationResponse}, which deliberately has no field for
 * it. The plaintext secret is returned exactly once, from the create
 * endpoint's own response, and never again — same convention as a real
 * cloud provider's access-key UX (AWS, Stripe, etc.), not a security
 * regression from the reference product's own plaintext-forever table.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "open_platform_applications")
public class OpenPlatformApplication extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "app_id", nullable = false, unique = true)
    private String appId;

    @Column(name = "application_name", nullable = false)
    private String applicationName;

    @Column(name = "business_type")
    private String businessType;

    @Column(name = "access_key", nullable = false, unique = true)
    private String accessKey;

    @Column(name = "secret_key_hash", nullable = false)
    private String secretKeyHash;

    @Column(name = "secret_key_last_four", nullable = false)
    private String secretKeyLastFour;

    @Column(name = "created_by")
    private String createdBy;
}

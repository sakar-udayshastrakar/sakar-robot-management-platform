package com.sakarrobotics.cloud.iam;

/**
 * Distinguishes Sakar-internal staff from customer/external accounts
 * (V19__departments_and_user_type.sql). Independent of {@code
 * organizationId} nullability, which remains a separate,
 * SUPER_ADMIN-only concept ({@link UserService#create}).
 */
public enum UserType {
    INTERNAL,
    EXTERNAL
}

package com.sakarrobotics.cloud.iam;

/**
 * The authoritative role set, verbatim from Master Requirements Part 19.
 * {@code SUPER_ADMIN} is the only role with cross-organization scope;
 * every other role is bounded by the organization hierarchy
 * (com.sakarrobotics.cloud.org).
 */
public enum RoleName {
    SUPER_ADMIN,
    ORG_ADMIN,
    SITE_ADMIN,
    OPERATOR,
    TECHNICIAN,
    VIEWER
}

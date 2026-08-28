package com.sakarrobotics.cloud.org;

/**
 * Extends SAKAR_ROBOT_PLATFORM_DATABASE.md §4's {@code type}
 * (customer/internal) into the distributor hierarchy explicitly required
 * for multi-tenancy: Sakar root -> distributor -> sub-distributor ->
 * client -> direct client. This is an additive refinement, not a
 * replacement of the original two-value classification — {@link #INTERNAL}
 * corresponds to the original "internal" value and every other constant is
 * a "customer" in the original vocabulary.
 */
public enum OrganizationType {
    SAKAR_ROOT,
    INTERNAL,
    DISTRIBUTOR,
    SUB_DISTRIBUTOR,
    CLIENT,
    DIRECT_CLIENT
}

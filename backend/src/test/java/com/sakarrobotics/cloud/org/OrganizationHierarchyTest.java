package com.sakarrobotics.cloud.org;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sakarrobotics.cloud.IntegrationTestSupport;

/**
 * The materialized-path descendant logic that every tenant-isolation check
 * in the platform ultimately relies on (TenantAccessGuard).
 */
class OrganizationHierarchyTest extends IntegrationTestSupport {

    @Test
    void grandchildOrganization_isADescendantOfTheRoot_butUnrelatedOrgIsNot() {
        Organization root = createOrganization("Root " + UUID.randomUUID(), OrganizationType.SAKAR_ROOT, null);
        Organization distributor = createOrganization("Distributor " + UUID.randomUUID(), OrganizationType.DISTRIBUTOR, root.getId());
        Organization client = createOrganization("Client " + UUID.randomUUID(), OrganizationType.CLIENT, distributor.getId());
        Organization unrelated = createOrganization("Unrelated " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);

        assertThat(organizationService.isSameOrDescendant(root.getId(), client.getId())).isTrue();
        assertThat(organizationService.isSameOrDescendant(distributor.getId(), client.getId())).isTrue();
        assertThat(organizationService.isSameOrDescendant(client.getId(), root.getId())).isFalse(); // not upward
        assertThat(organizationService.isSameOrDescendant(root.getId(), unrelated.getId())).isFalse();
        assertThat(organizationService.isSameOrDescendant(root.getId(), root.getId())).isTrue(); // reflexive
    }
}

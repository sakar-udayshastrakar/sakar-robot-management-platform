package com.sakarrobotics.cloud.org;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    List<Organization> findByParentOrganizationId(UUID parentOrganizationId);

    List<Organization> findByPathStartingWith(String pathPrefix);
}

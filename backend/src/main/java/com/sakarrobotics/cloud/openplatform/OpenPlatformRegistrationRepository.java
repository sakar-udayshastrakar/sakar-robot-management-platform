package com.sakarrobotics.cloud.openplatform;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OpenPlatformRegistrationRepository extends JpaRepository<OpenPlatformRegistration, UUID> {

    Optional<OpenPlatformRegistration> findByOrganizationId(UUID organizationId);

    List<OpenPlatformRegistration> findByOrganizationIdInOrderByCreatedAtDesc(List<UUID> organizationIds);

    List<OpenPlatformRegistration> findAllByOrderByCreatedAtDesc();
}

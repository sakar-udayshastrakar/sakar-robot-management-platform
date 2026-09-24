package com.sakarrobotics.cloud.ota;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SoftwareVersionRepository extends JpaRepository<SoftwareVersion, UUID> {

    List<SoftwareVersion> findByOrganizationIdIn(List<UUID> organizationIds);
}

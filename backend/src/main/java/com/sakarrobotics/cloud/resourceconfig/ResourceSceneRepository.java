package com.sakarrobotics.cloud.resourceconfig;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceSceneRepository extends JpaRepository<ResourceScene, UUID> {

    List<ResourceScene> findByOrganizationIdIn(List<UUID> organizationIds);
}

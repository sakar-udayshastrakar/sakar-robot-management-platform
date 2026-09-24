package com.sakarrobotics.cloud.resourceconfig;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketingMaterialRepository extends JpaRepository<MarketingMaterial, UUID> {

    List<MarketingMaterial> findByOrganizationIdIn(List<UUID> organizationIds);
}

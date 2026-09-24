package com.sakarrobotics.cloud.openplatform;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OpenPlatformApplicationRepository extends JpaRepository<OpenPlatformApplication, UUID> {

    List<OpenPlatformApplication> findByOrganizationIdInOrderByCreatedAtDesc(List<UUID> organizationIds);

    List<OpenPlatformApplication> findAllByOrderByCreatedAtDesc();

    boolean existsByAppId(String appId);

    boolean existsByAccessKey(String accessKey);
}

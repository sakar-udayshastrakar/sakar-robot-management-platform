package com.sakarrobotics.cloud.iot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LadderControlStoreBindingRepository extends JpaRepository<LadderControlStoreBinding, UUID> {

    List<LadderControlStoreBinding> findByOrganizationIdInOrderByCreatedAtDesc(List<UUID> organizationIds);

    List<LadderControlStoreBinding> findAllByOrderByCreatedAtDesc();

    Optional<LadderControlStoreBinding> findBySiteId(UUID siteId);

    boolean existsBySiteId(UUID siteId);
}

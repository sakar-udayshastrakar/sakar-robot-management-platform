package com.sakarrobotics.cloud.cleaning;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CleaningSessionRepository extends JpaRepository<CleaningSession, UUID> {

    Page<CleaningSession> findByRobotIdOrderByIdDesc(UUID robotId, Pageable pageable);

    /**
     * Dedup check for Keenon-sourced history rows — see {@link
     * CleaningSessionService#recordFromKeenonHistory} for why {@code
     * vendorReference} (not a vendor-issued id, since none is evidenced for a
     * single cleaning-log entry) is the lookup key.
     */
    boolean existsByVendorReference(String vendorReference);
}

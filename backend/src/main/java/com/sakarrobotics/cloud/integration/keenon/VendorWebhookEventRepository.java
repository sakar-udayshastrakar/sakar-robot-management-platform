package com.sakarrobotics.cloud.integration.keenon;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorWebhookEventRepository extends JpaRepository<VendorWebhookEvent, Long> {

    Optional<VendorWebhookEvent> findByDedupKey(String dedupKey);
}

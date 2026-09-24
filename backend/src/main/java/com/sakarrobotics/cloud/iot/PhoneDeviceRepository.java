package com.sakarrobotics.cloud.iot;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PhoneDeviceRepository extends JpaRepository<PhoneDevice, UUID> {

    List<PhoneDevice> findByOrganizationIdInOrderByCreatedAtDesc(List<UUID> organizationIds);

    List<PhoneDevice> findAllByOrderByCreatedAtDesc();
}

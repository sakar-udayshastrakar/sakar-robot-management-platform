package com.sakarrobotics.cloud.iam;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Page<User> findByOrganizationIdIn(List<UUID> organizationIds, Pageable pageable);

    Page<User> findByOrganizationIdInAndUserType(List<UUID> organizationIds, UserType userType, Pageable pageable);

    Page<User> findByUserType(UserType userType, Pageable pageable);

    boolean existsByDepartmentId(UUID departmentId);
}

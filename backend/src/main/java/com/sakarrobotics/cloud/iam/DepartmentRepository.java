package com.sakarrobotics.cloud.iam;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Optional<Department> findByNameIgnoreCase(String name);

    List<Department> findAllByOrderByNameAsc();
}

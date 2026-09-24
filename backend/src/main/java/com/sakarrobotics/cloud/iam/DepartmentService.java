package com.sakarrobotics.cloud.iam;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Departments are a flat, Sakar-wide list (V19__departments_and_user_type.sql)
 * used only to group INTERNAL users — no organization scoping, no
 * {@link com.sakarrobotics.cloud.security.access.TenantAccessGuard}
 * involvement, unlike every tenant-owned resource elsewhere in this codebase.
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public List<Department> listAll() {
        return departmentRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public Department create(String name) {
        assertNameAvailable(name, null);
        return departmentRepository.save(new Department(name));
    }

    @Transactional
    public Department rename(UUID id, String name) {
        Department department = getOrThrow(id);
        assertNameAvailable(name, id);
        department.setName(name);
        return departmentRepository.save(department);
    }

    @Transactional
    public void delete(UUID id) {
        Department department = getOrThrow(id);
        if (userRepository.existsByDepartmentId(id)) {
            throw new ApiException(SakarErrorCode.DEPARTMENT_IN_USE,
                    "Cannot delete a department that still has users assigned to it");
        }
        departmentRepository.delete(department);
    }

    private Department getOrThrow(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ApiException(SakarErrorCode.DEPARTMENT_NOT_FOUND, "Department not found: " + id));
    }

    private void assertNameAvailable(String name, UUID excludingId) {
        departmentRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (excludingId == null || !existing.getId().equals(excludingId)) {
                throw new ApiException(SakarErrorCode.DUPLICATE_DEPARTMENT_NAME,
                        "A department named '" + name + "' already exists");
            }
        });
    }
}

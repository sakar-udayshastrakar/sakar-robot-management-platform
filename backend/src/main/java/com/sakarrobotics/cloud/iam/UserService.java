package com.sakarrobotics.cloud.iam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.audit.AuditService;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import lombok.RequiredArgsConstructor;

/**
 * Real user administration (Master Requirements Part 19, approved Decision
 * 1 item 1). Reuses the existing organization-hierarchy authorization
 * ({@link TenantAccessGuard}) and audit ({@link AuditService}) chokepoints
 * rather than introducing parallel ones.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantAccessGuard tenantAccessGuard;
    private final AuditService auditService;

    @Transactional
    public User create(UserPrincipal principal, UUID organizationId, String email, String rawPassword,
            String fullName, RoleName roleName, UserType userType, UUID departmentId) {
        if (organizationId != null) {
            tenantAccessGuard.assertOrganizationAccess(principal, organizationId);
        } else if (!principal.isSuperAdmin()) {
            throw new ApiException(SakarErrorCode.FORBIDDEN,
                    "Only SUPER_ADMIN may create a user with no organization scope");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(SakarErrorCode.DUPLICATE_EMAIL, "A user with this email already exists");
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROLE_NOT_FOUND, "Role not found: " + roleName));
        assertDepartmentAssignmentValid(userType, departmentId);

        User user = new User();
        user.setOrganizationId(organizationId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setUserType(userType);
        user.setDepartmentId(departmentId);
        User saved = userRepository.save(user);

        auditService.record(principal, organizationId, null, "USER_CREATED", "SUCCESS", saved.getId().toString(), null, null);
        return saved;
    }

    @Transactional
    public User update(UserPrincipal principal, UUID userId, String fullName, UUID departmentId) {
        User user = getAccessibleOrThrow(principal, userId);
        assertDepartmentAssignmentValid(user.getUserType(), departmentId);
        user.setFullName(fullName);
        user.setDepartmentId(departmentId);
        User saved = userRepository.save(user);
        auditService.record(principal, user.getOrganizationId(), null, "USER_UPDATED", "SUCCESS", null, null, null);
        return saved;
    }

    /** {@code departmentId} is only meaningful for INTERNAL users, and must reference a real department. */
    private void assertDepartmentAssignmentValid(UserType userType, UUID departmentId) {
        if (departmentId == null) {
            return;
        }
        if (userType != UserType.INTERNAL) {
            throw new ApiException(SakarErrorCode.VALIDATION_FAILED, "departmentId is only valid for INTERNAL users");
        }
        if (!departmentRepository.existsById(departmentId)) {
            throw new ApiException(SakarErrorCode.DEPARTMENT_NOT_FOUND, "Department not found: " + departmentId);
        }
    }

    /**
     * One batch query for a page of users rather than N+1 per-row department lookups. Always a
     * plain {@link HashMap}, never {@link Map#of()} — most users have a {@code null}
     * departmentId, and {@code Map.of().get(null)} throws (immutable maps reject a null key),
     * whereas {@code HashMap.get(null)} safely returns {@code null}.
     */
    public Map<UUID, String> departmentNamesFor(List<User> users) {
        List<UUID> ids = users.stream().map(User::getDepartmentId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            departmentRepository.findAllById(ids).forEach(d -> names.put(d.getId(), d.getName()));
        }
        return names;
    }

    /**
     * Same not-found-not-forbidden pattern as {@link
     * com.sakarrobotics.cloud.robot.registry.RobotService#getAccessibleOrThrow}.
     * A cross-organization SUPER_ADMIN-scoped user ({@code organizationId ==
     * null}) is only visible to another SUPER_ADMIN.
     */
    public User getAccessibleOrThrow(UserPrincipal principal, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.USER_NOT_FOUND, "User not found: " + userId));
        boolean visible = user.getOrganizationId() != null
                ? tenantAccessGuard.hasOrganizationAccess(principal, user.getOrganizationId())
                : principal.isSuperAdmin();
        if (!visible) {
            throw new ApiException(SakarErrorCode.USER_NOT_FOUND, "User not found: " + userId);
        }
        return user;
    }

    public Page<User> listAccessible(UserPrincipal principal, int page, int pageSize) {
        return listAccessible(principal, page, pageSize, null);
    }

    public Page<User> listAccessible(UserPrincipal principal, int page, int pageSize, UserType userTypeFilter) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        PageRequest pageRequest = PageRequest.of(page, pageSize);
        if (userTypeFilter == null) {
            return orgIds == null ? userRepository.findAll(pageRequest) : userRepository.findByOrganizationIdIn(orgIds, pageRequest);
        }
        return orgIds == null
                ? userRepository.findByUserType(userTypeFilter, pageRequest)
                : userRepository.findByOrganizationIdInAndUserType(orgIds, userTypeFilter, pageRequest);
    }

    @Transactional
    public User suspend(UserPrincipal principal, UUID userId) {
        User user = getAccessibleOrThrow(principal, userId);
        user.setStatus(UserStatus.SUSPENDED);
        User saved = userRepository.save(user);
        auditService.record(principal, user.getOrganizationId(), null, "USER_SUSPENDED", "SUCCESS", null, null, null);
        return saved;
    }

    @Transactional
    public User activate(UserPrincipal principal, UUID userId) {
        User user = getAccessibleOrThrow(principal, userId);
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        User saved = userRepository.save(user);
        auditService.record(principal, user.getOrganizationId(), null, "USER_ACTIVATED", "SUCCESS", null, null, null);
        return saved;
    }

    @Transactional
    public User changeRole(UserPrincipal principal, UUID userId, RoleName roleName) {
        User user = getAccessibleOrThrow(principal, userId);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROLE_NOT_FOUND, "Role not found: " + roleName));
        user.setRole(role);
        User saved = userRepository.save(user);
        auditService.record(principal, user.getOrganizationId(), null, "USER_ROLE_CHANGED", "SUCCESS", roleName.name(), null, null);
        return saved;
    }
}

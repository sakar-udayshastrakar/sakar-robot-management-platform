package com.sakarrobotics.cloud.iam;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.audit.AuditService;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

/**
 * Editing surface for the 6 fixed, seeded roles (V9__seed_rbac.sql) — the
 * role's {@code name} is never editable here, only its {@code description}
 * and its {@code role_permissions} membership (see {@link
 * com.sakarrobotics.cloud.iam.dto.UpdateRoleRequest}'s own Javadoc for why).
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;

    @Transactional
    public Role update(UserPrincipal principal, UUID roleId, String description, Set<PermissionCode> permissionCodes) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ApiException(SakarErrorCode.ROLE_NOT_FOUND, "Role not found: " + roleId));

        Set<Permission> permissions = permissionCodes.stream()
                .map(code -> permissionRepository.findByCode(code)
                        .orElseThrow(() -> new ApiException(SakarErrorCode.PERMISSION_NOT_FOUND,
                                "Permission not found: " + code)))
                .collect(Collectors.toSet());

        role.setDescription(description);
        role.getPermissions().clear();
        role.getPermissions().addAll(permissions);
        Role saved = roleRepository.save(role);

        auditService.record(principal, null, null, "ROLE_PERMISSIONS_UPDATED", "SUCCESS",
                role.getName().name() + " -> " + permissionCodes, null, null);
        return saved;
    }
}

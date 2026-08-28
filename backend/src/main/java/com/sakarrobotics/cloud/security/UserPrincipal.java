package com.sakarrobotics.cloud.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.RoleName;

import lombok.Getter;

/**
 * The authenticated principal carried in the {@code SecurityContext} for
 * every request. Built entirely from the JWT's own claims (user id,
 * organization id, role, permission set) — the backend never re-hits the
 * database on every request just to know who is calling, which is what
 * makes the stateless-JWT design in Master Requirements Part 19 tractable
 * at scale. Authorization decisions that need current DB state (e.g. "is
 * this organization still active") are the responsibility of the service
 * layer, not this principal.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final UUID userId;
    private final String email;
    private final UUID organizationId;
    private final String organizationPath;
    private final RoleName role;
    private final Set<PermissionCode> permissions;

    public UserPrincipal(UUID userId, String email, UUID organizationId, String organizationPath,
            RoleName role, Set<PermissionCode> permissions) {
        this.userId = userId;
        this.email = email;
        this.organizationId = organizationId;
        this.organizationPath = organizationPath;
        this.role = role;
        this.permissions = permissions;
    }

    public boolean isSuperAdmin() {
        return role == RoleName.SUPER_ADMIN;
    }

    public boolean hasPermission(PermissionCode permission) {
        return permissions.contains(permission);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = permissions.stream()
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p.name()))
                .collect(Collectors.toSet());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }
}

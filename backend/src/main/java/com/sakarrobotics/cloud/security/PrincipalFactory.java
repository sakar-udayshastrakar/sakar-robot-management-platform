package com.sakarrobotics.cloud.security;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.User;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationService;

import lombok.RequiredArgsConstructor;

/** Builds the JWT-bound {@link UserPrincipal} from a persisted {@link User}. */
@Component
@RequiredArgsConstructor
public class PrincipalFactory {

    private final OrganizationService organizationService;

    public UserPrincipal from(User user) {
        String orgPath = null;
        if (user.getOrganizationId() != null) {
            Organization org = organizationService.getOrThrow(user.getOrganizationId());
            orgPath = org.getPath();
        }
        Set<PermissionCode> permissions = user.getRole().getPermissions().stream()
                .map(p -> p.getCode())
                .collect(Collectors.toSet());
        return new UserPrincipal(user.getId(), user.getEmail(), user.getOrganizationId(), orgPath,
                user.getRole().getName(), permissions);
    }
}

package com.sakarrobotics.cloud.openplatform;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.security.UserPrincipal;
import com.sakarrobotics.cloud.security.access.TenantAccessGuard;

import lombok.RequiredArgsConstructor;

/**
 * Open Platform → Customer registration + Application management. A
 * registration's {@link OpenPlatformRegistrationStatus} starts PENDING and
 * only ever moves via {@link #review}, an explicit ROLE_MANAGE-gated action
 * — never a fabricated "pass". An application's secret key is generated
 * once, hashed with the same {@link PasswordEncoder} bean as user passwords,
 * and returned in plaintext only from {@link #createApplication}'s own
 * response — see {@code dto.OpenPlatformApplicationResponse}'s Javadoc.
 */
@Service
@RequiredArgsConstructor
public class OpenPlatformService {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final SecureRandom secureRandom = new SecureRandom();

    private final OpenPlatformRegistrationRepository registrationRepository;
    private final OpenPlatformApplicationRepository applicationRepository;
    private final TenantAccessGuard tenantAccessGuard;
    private final PasswordEncoder passwordEncoder;

    private String randomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(secureRandom.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    @Transactional
    public OpenPlatformRegistration submitRegistration(UserPrincipal principal, UUID organizationId, String companyName,
            String area, String companyAddress, String systemMatcher, String contactInformation, String dockingRequirements) {
        tenantAccessGuard.assertOrganizationAccess(principal, organizationId);
        OpenPlatformRegistration registration = registrationRepository.findByOrganizationId(organizationId)
                .orElseGet(OpenPlatformRegistration::new);
        if (registration.getStatus() == OpenPlatformRegistrationStatus.APPROVED) {
            throw new ApiException(SakarErrorCode.CONFLICT, "This organization's registration is already approved");
        }

        registration.setOrganizationId(organizationId);
        registration.setCompanyName(companyName);
        registration.setArea(area);
        registration.setCompanyAddress(companyAddress);
        registration.setSystemMatcher(systemMatcher);
        registration.setContactInformation(contactInformation);
        registration.setDockingRequirements(dockingRequirements);
        registration.setStatus(OpenPlatformRegistrationStatus.PENDING);
        registration.setSubmittedBy(principal.getEmail());
        registration.setReviewedBy(null);
        registration.setReviewedAt(null);
        return registrationRepository.save(registration);
    }

    public Optional<OpenPlatformRegistration> getRegistration(UserPrincipal principal, UUID organizationId) {
        tenantAccessGuard.assertOrganizationAccess(principal, organizationId);
        return registrationRepository.findByOrganizationId(organizationId);
    }

    public List<OpenPlatformRegistration> listRegistrations(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null
                ? registrationRepository.findAllByOrderByCreatedAtDesc()
                : registrationRepository.findByOrganizationIdInOrderByCreatedAtDesc(orgIds);
    }

    @Transactional
    public OpenPlatformRegistration review(UserPrincipal principal, UUID id, OpenPlatformRegistrationStatus status) {
        OpenPlatformRegistration registration = registrationRepository.findById(id)
                .orElseThrow(() -> new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Registration not found: " + id));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, registration.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Registration not found: " + id);
        }
        registration.setStatus(status);
        registration.setReviewedBy(principal.getEmail());
        registration.setReviewedAt(Instant.now());
        return registrationRepository.save(registration);
    }

    /** The one and only moment a caller ever sees {@code secretKey} in plaintext. */
    public record CreatedApplication(OpenPlatformApplication application, String secretKey) {
    }

    @Transactional
    public CreatedApplication createApplication(UserPrincipal principal, UUID organizationId, String applicationName, String businessType) {
        tenantAccessGuard.assertOrganizationAccess(principal, organizationId);

        String appId;
        do {
            appId = String.valueOf(100000000000000L + Math.abs(secureRandom.nextLong() % 900000000000000L));
        } while (applicationRepository.existsByAppId(appId));

        String accessKey;
        do {
            accessKey = randomToken(20);
        } while (applicationRepository.existsByAccessKey(accessKey));

        String secretKey = randomToken(40);

        OpenPlatformApplication application = new OpenPlatformApplication();
        application.setOrganizationId(organizationId);
        application.setAppId(appId);
        application.setApplicationName(applicationName);
        application.setBusinessType(businessType);
        application.setAccessKey(accessKey);
        application.setSecretKeyHash(passwordEncoder.encode(secretKey));
        application.setSecretKeyLastFour(secretKey.substring(secretKey.length() - 4));
        application.setCreatedBy(principal.getEmail());
        return new CreatedApplication(applicationRepository.save(application), secretKey);
    }

    public List<OpenPlatformApplication> listApplications(UserPrincipal principal) {
        List<UUID> orgIds = tenantAccessGuard.accessibleOrganizationIds(principal);
        return orgIds == null
                ? applicationRepository.findAllByOrderByCreatedAtDesc()
                : applicationRepository.findByOrganizationIdInOrderByCreatedAtDesc(orgIds);
    }

    private OpenPlatformApplication applicationOrThrow(UserPrincipal principal, UUID id) {
        OpenPlatformApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Application not found: " + id));
        if (!tenantAccessGuard.hasOrganizationAccess(principal, application.getOrganizationId())) {
            throw new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Application not found: " + id);
        }
        return application;
    }

    public OpenPlatformApplication getApplication(UserPrincipal principal, UUID id) {
        return applicationOrThrow(principal, id);
    }

    @Transactional
    public OpenPlatformApplication updateApplication(UserPrincipal principal, UUID id, String applicationName, String businessType) {
        OpenPlatformApplication application = applicationOrThrow(principal, id);
        application.setApplicationName(applicationName);
        application.setBusinessType(businessType);
        return applicationRepository.save(application);
    }
}

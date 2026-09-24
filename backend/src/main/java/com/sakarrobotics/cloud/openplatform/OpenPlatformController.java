package com.sakarrobotics.cloud.openplatform;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.openplatform.dto.CreateOpenPlatformApplicationRequest;
import com.sakarrobotics.cloud.openplatform.dto.CreateOpenPlatformApplicationResponse;
import com.sakarrobotics.cloud.openplatform.dto.OpenPlatformApplicationResponse;
import com.sakarrobotics.cloud.openplatform.dto.OpenPlatformRegistrationResponse;
import com.sakarrobotics.cloud.openplatform.dto.ReviewOpenPlatformRegistrationRequest;
import com.sakarrobotics.cloud.openplatform.dto.SubmitOpenPlatformRegistrationRequest;
import com.sakarrobotics.cloud.openplatform.dto.UpdateOpenPlatformApplicationRequest;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Open Platform → Customer registration + Application management. "File
 * download" needs no endpoint here — it points at this backend's own live
 * OpenAPI spec ({@code /api-docs}, {@code /swagger-ui.html}, already served
 * by springdoc), not a fabricated document history.
 */
@RestController
@RequestMapping("/api/v1/open-platform")
@RequiredArgsConstructor
@Tag(name = "Open Platform")
public class OpenPlatformController {

    private final OpenPlatformService openPlatformService;

    @GetMapping("/registration")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Get an organization's own Open Platform customer registration, if it has submitted one")
    public ApiResponse<OpenPlatformRegistrationResponse> getRegistration(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam UUID organizationId) {
        Optional<OpenPlatformRegistration> registration = openPlatformService.getRegistration(principal, organizationId);
        return ApiResponse.ok(registration.map(OpenPlatformRegistrationResponse::from).orElse(null));
    }

    @GetMapping("/registrations")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "List every Open Platform customer registration within the caller's organization scope")
    public ApiResponse<List<OpenPlatformRegistrationResponse>> listRegistrations(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(openPlatformService.listRegistrations(principal).stream().map(OpenPlatformRegistrationResponse::from).toList());
    }

    @PostMapping("/registration")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Submit (or resubmit, while PENDING/REJECTED) an organization's Open Platform customer registration")
    public ResponseEntity<ApiResponse<OpenPlatformRegistrationResponse>> submitRegistration(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SubmitOpenPlatformRegistrationRequest request) {
        OpenPlatformRegistration registration = openPlatformService.submitRegistration(principal, request.organizationId(),
                request.companyName(), request.area(), request.companyAddress(), request.systemMatcher(),
                request.contactInformation(), request.dockingRequirements());
        return ResponseEntity.status(201).body(ApiResponse.ok(OpenPlatformRegistrationResponse.from(registration)));
    }

    @PostMapping("/registrations/{id}/review")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Approve or reject a customer registration — a real gate, never an automatic \"pass\"")
    public ApiResponse<OpenPlatformRegistrationResponse> review(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ReviewOpenPlatformRegistrationRequest request) {
        OpenPlatformRegistration registration = openPlatformService.review(principal, id, request.status());
        return ApiResponse.ok(OpenPlatformRegistrationResponse.from(registration));
    }

    @GetMapping("/applications")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "List API applications within the caller's organization scope (secret keys always masked)")
    public ApiResponse<List<OpenPlatformApplicationResponse>> listApplications(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(openPlatformService.listApplications(principal).stream().map(OpenPlatformApplicationResponse::from).toList());
    }

    @GetMapping("/applications/{id}")
    @PreAuthorize("hasAuthority('ROBOT_VIEW')")
    @Operation(summary = "Get an API application's details (secret key always masked)")
    public ApiResponse<OpenPlatformApplicationResponse> getApplication(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.ok(OpenPlatformApplicationResponse.from(openPlatformService.getApplication(principal, id)));
    }

    @PostMapping("/applications")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Create an API application — the plaintext secret key is returned ONLY in this response, never again")
    public ResponseEntity<ApiResponse<CreateOpenPlatformApplicationResponse>> createApplication(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateOpenPlatformApplicationRequest request) {
        OpenPlatformService.CreatedApplication created = openPlatformService.createApplication(principal,
                request.organizationId(), request.applicationName(), request.businessType());
        CreateOpenPlatformApplicationResponse body = new CreateOpenPlatformApplicationResponse(
                OpenPlatformApplicationResponse.from(created.application()), created.secretKey());
        return ResponseEntity.status(201).body(ApiResponse.ok(body));
    }

    @PutMapping("/applications/{id}")
    @PreAuthorize("hasAuthority('ROBOT_CONFIGURE')")
    @Operation(summary = "Update an API application's name/business type")
    public ApiResponse<OpenPlatformApplicationResponse> updateApplication(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOpenPlatformApplicationRequest request) {
        OpenPlatformApplication application = openPlatformService.updateApplication(principal, id, request.applicationName(), request.businessType());
        return ApiResponse.ok(OpenPlatformApplicationResponse.from(application));
    }
}

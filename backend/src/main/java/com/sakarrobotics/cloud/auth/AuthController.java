package com.sakarrobotics.cloud.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakarrobotics.cloud.auth.dto.LoginRequest;
import com.sakarrobotics.cloud.auth.dto.RefreshRequest;
import com.sakarrobotics.cloud.auth.dto.TokenResponse;
import com.sakarrobotics.cloud.common.web.ApiResponse;
import com.sakarrobotics.cloud.security.UserPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email/password and receive an access + refresh token pair")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        TokenResponse response = authService.login(
                request.email(), request.password(), clientIp(httpRequest), userAgent(httpRequest));
        return ApiResponse.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a valid refresh token for a new access + refresh token pair (rotation)")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        TokenResponse response = authService.refresh(
                request.refreshToken(), clientIp(httpRequest), userAgent(httpRequest));
        return ApiResponse.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the given refresh token (server-side session invalidation)")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        authService.logout(request.refreshToken(), principal);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }

    private static String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}

package com.sakarrobotics.cloud.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        boolean mfaRequired) {

    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, expiresIn, false);
    }
}

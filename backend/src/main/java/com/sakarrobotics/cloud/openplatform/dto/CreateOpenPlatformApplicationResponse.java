package com.sakarrobotics.cloud.openplatform.dto;

/**
 * Returned ONLY from the create endpoint — carries the one and only time the
 * plaintext secret key is ever shown. Callers must copy it now; every later
 * read (list/details) sees {@link OpenPlatformApplicationResponse#secretKeyMasked} instead.
 */
public record CreateOpenPlatformApplicationResponse(OpenPlatformApplicationResponse application, String secretKey) {
}

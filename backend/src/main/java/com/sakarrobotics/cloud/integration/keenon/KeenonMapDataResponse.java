package com.sakarrobotics.cloud.integration.keenon;

/**
 * The one deliberate exception to {@link KeenonApiClient}'s "always return a
 * raw {@code JsonNode}" convention (see that class's Javadoc) — {@code
 * KeenonMapImageSyncService} needs exactly these three evidenced fields
 * (nothing more) and nothing about a base64 PNG blob benefits from staying
 * as a raw JSON tree. {@code contentBase64} is the untouched base64 string
 * from the vendor response — no base64/image decoding happens here or
 * anywhere in {@link KeenonApiClient}; that is entirely
 * {@code KeenonMapImageSyncService}'s responsibility.
 */
record KeenonMapDataResponse(String contentBase64, Integer width, Integer height) {
}

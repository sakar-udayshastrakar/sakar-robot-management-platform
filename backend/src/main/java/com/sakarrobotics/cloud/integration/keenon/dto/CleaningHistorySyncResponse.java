package com.sakarrobotics.cloud.integration.keenon.dto;

/**
 * {@code newRecords} is exactly {@link
 * com.sakarrobotics.cloud.integration.keenon.KeenonCleaningHistorySyncService#sync}'s
 * own return value — the count of rows actually appended this call, after
 * dedup. Zero is a normal, successful result (nothing new since the last
 * sync), not an error.
 */
public record CleaningHistorySyncResponse(int newRecords) {
}

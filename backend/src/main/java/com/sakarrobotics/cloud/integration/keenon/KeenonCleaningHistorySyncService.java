package com.sakarrobotics.cloud.integration.keenon;

import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sakarrobotics.cloud.cleaning.CleaningSessionService;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.robot.registry.Robot;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * Appends Sakar-owned {@code cleaning_sessions} rows from Keenon's own
 * {@code GET .../clean/log/list} — reuses the already-real (now
 * page-parameterized) {@link KeenonApiClient#getCleaningLogs} call that
 * previously had no caller anywhere in the codebase.
 *
 * <p><strong>This is structurally different from area/cleaning-mode/
 * back-point sync</strong>: those replace a robot's "current state" list
 * (upsert + deactivate-what's-gone). Cleaning history is append-only —
 * a completed vendor cleaning run is an immutable historical fact, never
 * updated once recorded, and never deactivated/deleted (Keenon-side
 * deletion, if it ever happens, is simply never observed by a
 * point-in-time page fetch and is not treated as "erase from Sakar").
 *
 * <p><strong>Deduplication limitation (documented, not invented around):
 * </strong> no per-record vendor identifier (e.g. a {@code logId}) is
 * documented or evidenced anywhere for a single {@code clean/log/list}
 * entry — {@code SAKAR_LIVE_API_VALIDATION_MATRIX.md} and the Keenon Cloud
 * audit both stop at field-level evidence ({@code cleanArea}, {@code
 * cleanEfficiency}/reported as the {@code cleanEfficiency} value, {@code
 * cleanTiming}, {@code mState}, {@code failDescCode}, {@code failDesc},
 * {@code mapInfo[].taskSnapshot}), never a record-level id. Rather than
 * inventing an unevidenced field name, deduplication uses a deterministic
 * composite of every evidenced field as the lookup key (see {@link
 * #vendorReferenceOf}). This is a best-effort identity, not a
 * cryptographic guarantee: two genuinely distinct real-world runs that
 * happened to produce byte-identical values across every evidenced field
 * would be deduplicated into one row. No stronger key is available without
 * fabricating one.
 *
 * <p><strong>Pagination:</strong> the vendor's own confirmed envelope is
 * {@code {count, currentPage, pageSize, entities: [...]}} (not {@code
 * data}, unlike every other Keenon list endpoint this codebase calls —
 * confirmed live in the Keenon Cloud audit for this exact endpoint). This
 * service pages forward from 1, stopping at the first page that returns
 * fewer than {@code pageSize} entities (the natural end-of-data signal) or
 * at {@code maxPages}, whichever comes first — never an unbounded fetch.
 *
 * <p><strong>Incremental sync:</strong> not supported. No start/end-time,
 * cursor, or "since last sync" parameter is documented or evidenced for
 * this endpoint (unlike the separate food/hotel task-record endpoints,
 * which do document optional {@code startTime}/{@code endTime} params —
 * a different endpoint family). Every run re-scans up to {@code maxPages}
 * from the beginning; the {@code vendorReference} dedup check is what
 * keeps repeated runs from creating duplicate rows for records already
 * seen. A very old record beyond the page cap will never be synced by
 * this service — this is a real, reported limitation, not silently papered
 * over.
 */
@Service
@RequiredArgsConstructor
public class KeenonCleaningHistorySyncService {

    private static final Logger log = LoggerFactory.getLogger(KeenonCleaningHistorySyncService.class);

    private final KeenonApiClient client;
    private final CleaningSessionService cleaningSessionService;

    @Value("${sakar.integration.keenon.cleaning-history-sync.page-size:50}")
    private int pageSize;

    @Value("${sakar.integration.keenon.cleaning-history-sync.max-pages:5}")
    private int maxPages;

    public int sync(Robot robot, String storeId) {
        if (robot.getExternalRobotId() == null) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE,
                    "Robot " + robot.getId() + " has no external (Keenon) identifier configured");
        }
        String robotSn = robot.getExternalRobotId();

        int newRecords = 0;
        for (int page = 1; page <= maxPages; page++) {
            JsonNode response = client.getCleaningLogs(storeId, robotSn, page, pageSize);
            JsonNode entities = response != null ? response.get("entities") : null;
            var pageEntries = entities != null && entities.isArray()
                    ? StreamSupport.stream(entities.spliterator(), false).toList()
                    : java.util.List.<JsonNode>of();

            for (JsonNode entry : pageEntries) {
                if (recordEntry(robot, entry)) {
                    newRecords++;
                }
            }

            if (pageEntries.size() < pageSize) {
                // Fewer than a full page — this was the last page of vendor data.
                break;
            }
            if (page == maxPages) {
                log.info("Keenon cleaning-history sync: reached the {}-page bound for robot {} — older records, "
                        + "if any remain beyond this page, were not fetched this run", maxPages, robot.getId());
            }
        }
        return newRecords;
    }

    private boolean recordEntry(Robot robot, JsonNode entry) {
        Double cleanArea = doubleOrNull(entry, "cleanArea");
        Double cleanEfficiency = doubleOrNull(entry, "cleanEfficiency");
        Long cleanTiming = longOrNull(entry, "cleanTiming");
        String mState = textOrNull(entry, "mState");
        String failDescCode = textOrNull(entry, "failDescCode");
        String failDesc = textOrNull(entry, "failDesc");
        String taskSnapshot = firstMapInfoTaskSnapshot(entry);

        String vendorReference = vendorReferenceOf(cleanArea, cleanEfficiency, cleanTiming, mState, failDescCode);
        // mState has no documented/evidenced enum mapping (only one anecdotal example is on
        // record: mState 1 alongside a successful run) — store the raw vendor value verbatim
        // rather than inventing a translation like "COMPLETED"/"FAILED".
        String result = mState != null ? "mState:" + mState : null;
        String failureReason = failDesc != null ? failDesc : failDescCode;

        return cleaningSessionService
                .recordFromKeenonHistory(robot, cleanArea, cleanEfficiency, cleanTiming, result, failureReason,
                        taskSnapshot, vendorReference)
                .isPresent();
    }

    /**
     * Deterministic composite key from every evidenced field — see the class
     * Javadoc's "Deduplication limitation" section for why this exists
     * instead of a vendor-issued record id.
     */
    private static String vendorReferenceOf(Double cleanArea, Double cleanEfficiency, Long cleanTiming,
            String mState, String failDescCode) {
        return "keenon-log:area=" + cleanArea + ";efficiency=" + cleanEfficiency + ";duration=" + cleanTiming
                + ";mState=" + mState + ";failDescCode=" + failDescCode;
    }

    private static String firstMapInfoTaskSnapshot(JsonNode entry) {
        JsonNode mapInfo = entry != null ? entry.get("mapInfo") : null;
        if (mapInfo != null && mapInfo.isArray() && !mapInfo.isEmpty()) {
            return textOrNull(mapInfo.get(0), "taskSnapshot");
        }
        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asDouble() : null;
    }

    private static Long longOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asLong() : null;
    }
}

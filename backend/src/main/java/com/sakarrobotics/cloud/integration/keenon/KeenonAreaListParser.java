package com.sakarrobotics.cloud.integration.keenon;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * Shared parser for the Keenon cleaning-area-list response envelope
 * ({@code GET /api/open/custom/clean/robot/area/list}) — confirmed live by
 * a raw, unparsed capture of this exact endpoint for this exact account:
 *
 * <pre>{@code
 * {
 *   "code": 610000, "msg": "success", "errorMsg": "success",
 *   "data": {
 *     "currentPage": 1, "pageSize": 100, "count": 1,
 *     "entities": [
 *       {
 *         "storeId": "C00715655", "robotSn": "94:BA:06:CA:99:F3",
 *         "mapId": "4c0075859805496eb452187b3cd91107", "floor": 1,
 *         "areaIdList": ["8a7bd155598342d08158d34d5a07007d"],
 *         "areaNameList": ["Area5"]
 *       }
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <p>Two previously-tried assumptions about this shape were both wrong and
 * both silently produced zero areas rather than failing loudly: reading a
 * flat array directly under {@code data} (the original code), and reading a
 * top-level {@code entities} array with no {@code data} wrapper (a
 * subsequent fix, based on this endpoint's sibling {@code clean/log/list}
 * envelope, which turned out not to generalize to this endpoint). Do not
 * change this parsing again without a fresh raw capture — see the
 * investigation trail in this project's session history for both failed
 * attempts.
 *
 * <p>Each {@code entities} element is a per-map/floor GROUP, never a single
 * area: {@code mapId}/{@code floor} apply to the whole group, and the
 * group's areas are two PARALLEL arrays, {@code areaIdList}/{@code
 * areaNameList}, paired strictly by index. Used identically by {@link
 * KeenonRobotAdapter#getAreas} (a live, unpersisted read) and {@link
 * KeenonAreaSyncService#sync} (the same read, persisted) — both need the
 * exact same flattening, so it lives here once rather than being
 * duplicated.
 */
final class KeenonAreaListParser {

    private KeenonAreaListParser() {
    }

    /**
     * One area flattened out of its {@code entities[]} map/floor group.
     * {@code areaId} is always non-blank (an entry with no id is dropped,
     * never fabricated — see {@link #flatten}). {@code areaName} is the
     * vendor's own paired name entry, verbatim, and may be {@code null} —
     * each caller applies its own existing "no display name" convention
     * (this parser makes no business-logic decision about that).
     */
    record VendorArea(String mapId, Integer floor, String areaId, String areaName) {
    }

    /**
     * Flattens {@code response.data.entities[].{areaIdList,areaNameList}}
     * into one {@link VendorArea} per id/name pair.
     *
     * <p>Never throws on malformed/absent vendor data — a missing or
     * non-object {@code data}, a missing or non-array {@code entities}, or
     * a missing/non-array {@code areaIdList}/{@code areaNameList} on a
     * given entity all resolve to "no areas from that part" rather than an
     * exception, mirroring this codebase's existing "malformed/absent
     * vendor data is treated as empty, not a hard failure" convention (see
     * {@code KeenonCleaningHistorySyncService}). This intentionally does
     * NOT distinguish "field absent" from "field present but empty" — the
     * same convention that sibling service already uses — since Keenon's
     * documented contract gives no way to tell a genuinely-empty result
     * apart from an unrecognized/older response shape.
     *
     * <p>{@code areaIdList}/{@code areaNameList} are paired strictly by
     * index up to {@code min(areaIdList.size(), areaNameList.size())} — a
     * trailing, unpaired entry on either side (a real vendor length
     * mismatch, never expected but not assumed impossible) is dropped
     * rather than guessed at: there is no way to know which id a stray
     * trailing name belongs to (or vice versa) without fabricating a
     * pairing, and this codebase never fabricates vendor identity data.
     */
    static List<VendorArea> flatten(JsonNode response) {
        List<VendorArea> result = new ArrayList<>();
        JsonNode data = response != null ? response.get("data") : null;
        JsonNode entities = data != null ? data.get("entities") : null;
        if (entities == null || !entities.isArray()) {
            return result;
        }
        for (JsonNode entity : entities) {
            JsonNode areaIdList = entity != null ? entity.get("areaIdList") : null;
            JsonNode areaNameList = entity != null ? entity.get("areaNameList") : null;
            if (areaIdList == null || !areaIdList.isArray() || areaNameList == null || !areaNameList.isArray()) {
                continue;
            }
            String mapId = textOrNull(entity, "mapId");
            Integer floor = intOrNull(entity, "floor");
            int pairCount = Math.min(areaIdList.size(), areaNameList.size());
            for (int i = 0; i < pairCount; i++) {
                String areaId = textOrNullAt(areaIdList, i);
                if (areaId == null || areaId.isBlank()) {
                    // No id — nothing to key a mapping on, skip rather than fabricate one.
                    continue;
                }
                String areaName = textOrNullAt(areaNameList, i);
                result.add(new VendorArea(mapId, floor, areaId, areaName));
            }
        }
        return result;
    }

    private static String textOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    /** Same "absent/null is null, never a fabricated default" convention as {@link #textOrNull}, for one element of a JSON array rather than one field of a JSON object. */
    private static String textOrNullAt(JsonNode arrayNode, int index) {
        JsonNode element = arrayNode.get(index);
        return element != null && !element.isNull() ? element.asText() : null;
    }
}

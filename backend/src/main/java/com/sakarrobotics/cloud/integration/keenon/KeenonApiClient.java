package com.sakarrobotics.cloud.integration.keenon;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;

import lombok.RequiredArgsConstructor;

// Built via RestClient.builder() directly (not an injected RestClient.Builder bean) —
// no RestClient auto-configuration is on this project's classpath in this Spring Boot
// version, and a hand-built client is sufficient for this simple, single-vendor use.

/**
 * Thin, read/control HTTP client for the exact Keenon Open Platform
 * endpoints live-tested in Master Requirements Part 40 /
 * {@code SAKAR_LIVE_API_VALIDATION_MATRIX.md}. Every response is returned
 * as a raw {@link JsonNode} deliberately — the vendor's field-level schema
 * beyond what was actually observed in that live test is graded
 * {@code UNKNOWN} in the docs, and this client does not invent typed DTOs
 * for fields nobody has confirmed. Only {@link KeenonRobotAdapter} calls
 * this class; nothing in this class is ever called directly from a
 * controller.
 *
 * <p><strong>One deliberate exception:</strong> {@link
 * #getMapData(String, String)} returns a typed {@link KeenonMapDataResponse}
 * rather than a raw {@code JsonNode} — the raw Keenon PNG map-storage slice
 * needs exactly three evidenced fields and nothing about a base64 image
 * blob benefits from staying as a JSON tree. No base64/image decoding
 * happens in this class either way — that stays entirely {@code
 * KeenonMapImageSyncService}'s responsibility.
 */
@Component
@RequiredArgsConstructor
class KeenonApiClient {

    private final KeenonProperties properties;
    private final KeenonOAuthTokenService tokenService;

    private RestClient client() {
        return RestClient.builder().baseUrl(properties.getBaseUrl()).build();
    }

    private RestClient.RequestHeadersSpec<?> authorizedGet(String uri) {
        // uri(String) re-runs its argument through RestClient's own URI-template encoding —
        // since every caller here already passes a pre-encoded query string (see encode()
        // below), that doubly percent-encodes it (e.g. "%3A" becomes "%253A"), corrupting
        // every identifier sent to Keenon. uri(URI) takes the URI as already encoded and
        // performs no further encoding — see https://github.com/spring-projects/spring-framework
        // "RestClient/RestTemplate URI encoding" for the documented distinction.
        return client().get().uri(java.net.URI.create(uri)).header("Authorization", "Bearer " + tokenService.currentAccessToken());
    }

    // SEC-2026 Keenon status-endpoint migration: /api/open/scene/v1/robot/status (robotId)
    // is documented and was live-confirmed working in the original Master Requirements
    // Part 40 audit, but a later live re-test found it returning 610403 "Insufficient
    // operation permission" for this account while the cleaning-family equivalent below
    // succeeded (610000) for the exact same robot at the exact same time. Live-verified
    // to return the same mainState/subState fields (plus hardwareState/globalState/
    // childState) this adapter already reads - see KeenonRobotAdapter#getStatus.
    JsonNode getRobotStatus(String robotSn) {
        return get("/api/open/custom/clean/robot/status?robotSn=" + encode(robotSn));
    }

    JsonNode getBatteryLevel(String robotSn) {
        return get("/api/open/custom/robot/battery/level?robotSn=" + encode(robotSn));
    }

    JsonNode getCleaningStatus(String robotSn) {
        return get("/api/open/custom/clean/robot/status?robotSn=" + encode(robotSn));
    }

    JsonNode getAreaList(String storeId, String robotSn) {
        return get("/api/open/custom/clean/robot/area/list?storeId=" + encode(storeId)
                + "&robotSn=" + encode(robotSn) + "&currentPage=1&pageSize=100");
    }

    JsonNode getCleaningModes(String robotSn) {
        return get("/api/open/custom/clean/robot/strategy/clean/model?robotSn=" + encode(robotSn));
    }

    JsonNode getBackPoints(String robotSn) {
        return get("/api/open/custom/clean/robot/strategy/back/point?robotSn=" + encode(robotSn));
    }

    JsonNode getCleaningLogs(String storeId, String robotSn, int currentPage, int pageSize) {
        return get("/api/open/custom/clean/log/list?storeId=" + encode(storeId)
                + "&robotSn=" + encode(robotSn) + "&currentPage=" + currentPage + "&pageSize=" + pageSize);
    }

    KeenonMapDataResponse getMapData(String sceneCode, String floorInfo) {
        JsonNode response = get("/api/open/custom/robot/map?sceneCode=" + encode(sceneCode) + "&floorInfo=" + encode(floorInfo));
        JsonNode data = response != null ? response.get("data") : null;
        JsonNode originPosition = data != null ? data.get("originPosition") : null;
        String content = data != null && data.hasNonNull("content") ? data.get("content").asText() : null;
        Integer width = originPosition != null && originPosition.hasNonNull("width") ? originPosition.get("width").asInt() : null;
        Integer height = originPosition != null && originPosition.hasNonNull("height") ? originPosition.get("height").asInt() : null;
        return new KeenonMapDataResponse(content, width, height);
    }

    JsonNode getMapPosition(String sceneCode, String floorInfo) {
        return get("/api/open/custom/robot/map/position?sceneCode=" + encode(sceneCode) + "&floorInfo=" + encode(floorInfo));
    }

    JsonNode postTemporaryTask(Object body) {
        return post("/api/open/custom/clean/robot/strategy/temporary/task", body);
    }

    JsonNode postFinishTask(Object body) {
        return post("/api/open/custom/clean/robot/finish/task", body);
    }

    JsonNode postPauseTask(Object body) {
        return post("/api/open/custom/clean/robot/pause/task", body);
    }

    JsonNode postRechargeTask(Object body) {
        return post("/api/open/custom/clean/robot/recharge/task", body);
    }

    private JsonNode get(String uri) {
        try {
            return authorizedGet(uri).retrieve().body(JsonNode.class);
        } catch (Exception ex) {
            throw new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed: " + uri, ex);
        }
    }

    private JsonNode post(String uri, Object body) {
        try {
            return client().post().uri(uri)
                    .header("Authorization", "Bearer " + tokenService.currentAccessToken())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            throw new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed: " + uri, ex);
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}

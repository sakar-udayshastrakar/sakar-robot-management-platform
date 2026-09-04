package com.sakarrobotics.cloud.integration.keenon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.map.RobotMap;
import com.sakarrobotics.cloud.map.RobotMapRepository;
import com.sakarrobotics.cloud.robot.registry.Robot;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * Fetches the real Keenon map PNG ({@code GET .../custom/robot/map}) and
 * stores it as a raw file on a persistent volume — deliberately mechanical
 * storage only. No PGM/YAML/occupancy-grid conversion, no resolution/origin/
 * yaw fields, no robot control: none of that is evidenced by any captured
 * Keenon response (see {@link KeenonMapMetadataSyncService}'s own Javadoc
 * for the same gap), so none of it is attempted here.
 *
 * <p><strong>Where {@code mapMd5} comes from:</strong> {@code
 * GET .../custom/robot/map} never returns it — only {@code
 * GET .../custom/robot/map/position}'s {@code targetList[]} entries do
 * (every point sharing one map reports the same value). Rather than
 * refactor the already-shipped {@link KeenonMapPointSyncService} to expose
 * it, this service makes its own {@link KeenonApiClient#getMapPosition}
 * call — an accepted, documented duplicate call in exchange for not
 * touching a tested class.
 *
 * <p><strong>Change detection:</strong> the current vendor {@code mapMd5}
 * is compared against the stored value; if they match AND the PNG file
 * already exists on disk, the (large, base64-encoded) map-image call is
 * skipped entirely. Any mismatch, missing file, or undeterminable current
 * {@code mapMd5} (e.g. an empty {@code targetList}) causes a fresh fetch —
 * never a silent skip when we can't actually prove nothing changed.
 *
 * <p><strong>Storage layout:</strong> {@code
 * <storage-root>/<robotId>/<mapId>/map.png}, built only from Sakar-owned
 * {@link Robot#getId()}/{@link RobotMap#getId()} UUIDs — no vendor-supplied
 * string ({@code sceneCode}, {@code sceneName}, point names) ever
 * participates in path construction. A {@code startsWith} check against the
 * resolved storage root is kept as defense-in-depth even though the UUID
 * construction already makes escaping the root structurally impossible.
 *
 * <p><strong>Write ordering:</strong> fetch → validate (non-blank base64,
 * decodable, real PNG magic bytes) → write to a temp file in the same
 * target directory → atomic move into place → only then update and save
 * {@link RobotMap}. A vendor failure or validation failure leaves the
 * existing file and DB row completely untouched. The one accepted edge case
 * this ordering cannot fully close: a successful file write followed by a
 * DB save failure leaves the file "ahead" of the DB row; the next sync run
 * self-corrects it (the file will simply be overwritten again), so no
 * additional cross-system transaction is attempted.
 */
@Service
@RequiredArgsConstructor
public class KeenonMapImageSyncService {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final String MAP_FILE_NAME = "map.png";

    private final KeenonApiClient client;
    private final KeenonMapMetadataSyncService keenonMapMetadataSyncService;
    private final RobotMapRepository robotMapRepository;

    @Value("${sakar.maps.storage-root:./data/maps}")
    private String storageRoot;

    @Value("${sakar.integration.keenon.map-image-sync.default-floor-info:1}")
    private String defaultFloorInfo;

    @Transactional
    public Optional<RobotMap> sync(Robot robot) {
        if (robot.getExternalRobotId() == null) {
            throw new ApiException(SakarErrorCode.INTEGRATION_UNAVAILABLE,
                    "Robot " + robot.getId() + " has no external (Keenon) identifier configured");
        }

        Optional<RobotMap> map = keenonMapMetadataSyncService.sync(robot);
        if (map.isEmpty()) {
            // No current sceneCode this run (e.g. robot offline) — nothing to fetch an
            // image against; never invent a scene, never touch an existing image either.
            return Optional.empty();
        }
        RobotMap robotMap = map.get();
        String sceneCode = robotMap.getVendorMapId();

        Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
        Path targetDir = root.resolve(robot.getId().toString()).resolve(robotMap.getId().toString());
        Path targetFile = targetDir.resolve(MAP_FILE_NAME).normalize();
        if (!targetFile.startsWith(root)) {
            // Structurally unreachable given the UUID-only path construction above —
            // kept as defense-in-depth, never bypassed.
            throw new ApiException(SakarErrorCode.VENDOR_API_ERROR,
                    "Resolved map storage path escaped the configured storage root");
        }

        String currentMapMd5 = fetchCurrentMapMd5(sceneCode);
        if (currentMapMd5 != null && currentMapMd5.equals(robotMap.getMapMd5()) && Files.exists(targetFile)) {
            // Vendor itself reports no change and we already have the file — the
            // (large, base64-encoded) map-image call would be entirely wasted.
            return Optional.of(robotMap);
        }

        KeenonMapDataResponse mapData = client.getMapData(sceneCode, defaultFloorInfo);
        byte[] pngBytes = decodeAndValidatePng(mapData.contentBase64());

        writeAtomically(targetDir, targetFile, pngBytes);

        robotMap.setWidth(mapData.width());
        robotMap.setHeight(mapData.height());
        robotMap.setMapMd5(currentMapMd5);
        robotMap.setImageUrl(targetFile.toString());
        return Optional.of(robotMapRepository.save(robotMap));
    }

    private String fetchCurrentMapMd5(String sceneCode) {
        JsonNode response = client.getMapPosition(sceneCode, defaultFloorInfo);
        JsonNode data = response != null ? response.get("data") : null;
        JsonNode targetList = data != null ? data.get("targetList") : null;
        if (targetList == null || !targetList.isArray() || targetList.isEmpty()) {
            // No point in this scene reports a mapMd5 right now — cannot prove
            // "unchanged", so the caller always falls through to a fresh fetch.
            return null;
        }
        JsonNode first = targetList.get(0);
        return first != null && first.hasNonNull("mapMd5") ? first.get("mapMd5").asText() : null;
    }

    private byte[] decodeAndValidatePng(String contentBase64) {
        if (contentBase64 == null || contentBase64.isBlank()) {
            throw new ApiException(SakarErrorCode.VENDOR_API_ERROR,
                    "Keenon map data response had no image content");
        }
        byte[] pngBytes;
        try {
            pngBytes = Base64.getDecoder().decode(contentBase64);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(SakarErrorCode.VENDOR_API_ERROR,
                    "Keenon map data content was not valid Base64", ex);
        }
        if (!isPng(pngBytes)) {
            throw new ApiException(SakarErrorCode.VENDOR_API_ERROR,
                    "Keenon map data content was not a valid PNG image");
        }
        return pngBytes;
    }

    private static boolean isPng(byte[] bytes) {
        if (bytes.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static void writeAtomically(Path targetDir, Path targetFile, byte[] pngBytes) {
        try {
            Files.createDirectories(targetDir);
            Path tempFile = targetDir.resolve(MAP_FILE_NAME + "." + System.nanoTime() + ".tmp");
            try {
                Files.write(tempFile, pngBytes);
                Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write Keenon map image to " + targetFile, ex);
        }
    }
}

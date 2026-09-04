package com.sakarrobotics.cloud.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;

/**
 * Reads the raw Keenon map PNG {@code KeenonMapImageSyncService} already
 * wrote to disk, for the authenticated map-image-serving API
 * ({@code RobotController#mapImage}). Deliberately independent of that sync
 * service (only the {@code sakar.maps.storage-root} config key and the
 * {@code <storage-root>/<robotId>/<mapId>/map.png} layout convention are
 * shared) — a small, accepted duplication rather than coupling a read-facing
 * API concern to a write-facing sync concern.
 *
 * <p><strong>Never trusts {@link RobotMap#getImageUrl()}</strong> for the
 * actual file open — the path is always recomputed fresh from {@code
 * robotId}/{@code mapId} (both server-resolved UUIDs, never client input),
 * so "what file do we open" depends only on that invariant, not on a
 * previously-stored string column. The {@code startsWith} check against the
 * resolved storage root is kept as defense-in-depth even though no
 * client-supplied value ever participates in path construction here at all.
 *
 * <p>Missing file, unreadable file, empty file, and a file whose bytes don't
 * even start with the PNG magic number are all treated identically — {@link
 * SakarErrorCode#RESOURCE_NOT_FOUND} — never a 500, never a leaked
 * filesystem path or stack trace. The PNG-signature check should be
 * unreachable in practice ({@code KeenonMapImageSyncService} validates it on
 * every write) but is never trusted blindly on the read side either.
 */
@Service
public class RobotMapImageService {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    /** Must match {@code KeenonMapImageSyncService}'s own file name. */
    private static final String MAP_FILE_NAME = "map.png";

    @Value("${sakar.maps.storage-root:./data/maps}")
    private String storageRoot;

    public byte[] readImageBytes(UUID robotId, UUID mapId) {
        Path file = resolveImagePath(robotId, mapId);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException ex) {
            // Missing file / permission error / anything else — indistinguishable, from the
            // caller's perspective, from "no image was ever synced."
            throw notFound();
        }
        if (!isPng(bytes)) {
            throw notFound();
        }
        return bytes;
    }

    /** Quoted ETag value derived from the vendor's own map hash, or {@code null} if unset. */
    public String etagFor(RobotMap robotMap) {
        String mapMd5 = robotMap.getMapMd5();
        return mapMd5 == null ? null : "\"" + mapMd5 + "\"";
    }

    private Path resolveImagePath(UUID robotId, UUID mapId) {
        Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
        Path file = root.resolve(robotId.toString()).resolve(mapId.toString()).resolve(MAP_FILE_NAME).normalize();
        if (!file.startsWith(root)) {
            // Structurally unreachable given the UUID-only construction above — kept as
            // defense-in-depth, never bypassed.
            throw notFound();
        }
        return file;
    }

    private static ApiException notFound() {
        return new ApiException(SakarErrorCode.RESOURCE_NOT_FOUND, "Map image not found");
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
}

package com.sakarrobotics.cloud.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;

/**
 * {@link RobotMapImageService} — the read side of the raw Keenon PNG map
 * storage slice. Confirms the path is resolved only from UUIDs (never a
 * client-supplied value, never {@code RobotMap#getImageUrl()}), and that
 * missing/empty/corrupt files on disk all fail the same safe way —
 * {@link SakarErrorCode#RESOURCE_NOT_FOUND}, never a leaked path or a 500.
 */
class RobotMapImageServiceTest {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @TempDir
    Path tempDir;

    private RobotMapImageService service() throws ReflectiveOperationException {
        RobotMapImageService service = new RobotMapImageService();
        var field = RobotMapImageService.class.getDeclaredField("storageRoot");
        field.setAccessible(true);
        field.set(service, tempDir.toString());
        return service;
    }

    private static byte[] validPngBytes() {
        byte[] bytes = new byte[PNG_SIGNATURE.length + 4];
        System.arraycopy(PNG_SIGNATURE, 0, bytes, 0, PNG_SIGNATURE.length);
        bytes[8] = 1;
        bytes[9] = 2;
        bytes[10] = 3;
        bytes[11] = 4;
        return bytes;
    }

    private Path fileFor(UUID robotId, UUID mapId) {
        return tempDir.resolve(robotId.toString()).resolve(mapId.toString()).resolve("map.png");
    }

    @Test
    void readImageBytes_validStoredPng_returnsExactBytes() throws Exception {
        UUID robotId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        byte[] png = validPngBytes();
        Path file = fileFor(robotId, mapId);
        Files.createDirectories(file.getParent());
        Files.write(file, png);

        byte[] result = service().readImageBytes(robotId, mapId);

        assertThat(result).isEqualTo(png);
    }

    @Test
    void readImageBytes_differentRobotOrMapId_resolvesToADifferentPath_neverCrossesOver() throws Exception {
        UUID robotId = UUID.randomUUID();
        UUID mapIdA = UUID.randomUUID();
        UUID mapIdB = UUID.randomUUID();
        byte[] pngA = validPngBytes();
        Path fileA = fileFor(robotId, mapIdA);
        Files.createDirectories(fileA.getParent());
        Files.write(fileA, pngA);
        // No file at all for mapIdB under the same robotId.

        assertThat(service().readImageBytes(robotId, mapIdA)).isEqualTo(pngA);
        assertThatThrownBy(() -> service().readImageBytes(robotId, mapIdB))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void readImageBytes_missingFile_throwsResourceNotFound_neverLeaksPath() throws Exception {
        UUID robotId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();

        assertThatThrownBy(() -> service().readImageBytes(robotId, mapId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> {
                            assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.RESOURCE_NOT_FOUND);
                            assertThat(ex.getMessage()).doesNotContain(tempDir.toString());
                        });
    }

    @Test
    void readImageBytes_emptyFile_throwsResourceNotFound() throws Exception {
        UUID robotId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        Path file = fileFor(robotId, mapId);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[0]);

        assertThatThrownBy(() -> service().readImageBytes(robotId, mapId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void readImageBytes_corruptNonPngFile_throwsResourceNotFound() throws Exception {
        UUID robotId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        Path file = fileFor(robotId, mapId);
        Files.createDirectories(file.getParent());
        Files.write(file, "this is not a png file at all".getBytes());

        assertThatThrownBy(() -> service().readImageBytes(robotId, mapId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void etagFor_nullMapMd5_returnsNull() throws Exception {
        RobotMap map = new RobotMap();

        assertThat(service().etagFor(map)).isNull();
    }

    @Test
    void etagFor_presentMapMd5_returnsQuotedValue() throws Exception {
        RobotMap map = new RobotMap();
        map.setMapMd5("abc123");

        assertThat(service().etagFor(map)).isEqualTo("\"abc123\"");
    }
}

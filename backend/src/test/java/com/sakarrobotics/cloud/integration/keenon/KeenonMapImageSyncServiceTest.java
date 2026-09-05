package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.map.RobotMap;
import com.sakarrobotics.cloud.map.RobotMapRepository;
import com.sakarrobotics.cloud.robot.registry.Robot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Raw Keenon PNG map-storage slice. Verifies mapMd5-based change detection
 * (comparing {@link KeenonApiClient#getMapPosition}'s current value against
 * the stored one, only skipping the image call when both match AND the file
 * already exists), base64/PNG-magic-byte validation, atomic write-then-move
 * ordering, UUID-only storage paths, and that a vendor or validation
 * failure leaves any existing file/row completely untouched.
 */
@ExtendWith(MockitoExtension.class)
class KeenonMapImageSyncServiceTest {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @TempDir
    Path tempDir;

    @Mock
    private KeenonApiClient client;
    @Mock
    private KeenonMapMetadataSyncService keenonMapMetadataSyncService;
    @Mock
    private RobotMapRepository robotMapRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KeenonMapImageSyncService service(String floorInfo) throws ReflectiveOperationException {
        KeenonMapImageSyncService service = new KeenonMapImageSyncService(client, keenonMapMetadataSyncService, robotMapRepository);
        setField(service, "storageRoot", tempDir.toString());
        setField(service, "defaultFloorInfo", floorInfo);
        return service;
    }

    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        var field = KeenonMapImageSyncService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Robot aKeenonRobot() {
        Robot robot = new Robot();
        robot.setId(UUID.randomUUID());
        robot.setExternalRobotId("94:BA:06:CA:99:F3");
        return robot;
    }

    private RobotMap aRobotMap(String sceneCode) {
        RobotMap map = new RobotMap();
        map.setId(UUID.randomUUID());
        map.setVendorMapId(sceneCode);
        return map;
    }

    private JsonNode mapPositionResponse(String mapMd5) throws IOException {
        if (mapMd5 == null) {
            return objectMapper.readTree("{\"data\":{\"targetList\":[]}}");
        }
        return objectMapper.readTree("{\"data\":{\"targetList\":[{\"name\":\"p\",\"mapMd5\":\"" + mapMd5 + "\"}]}}");
    }

    private static byte[] validPngBytes(int marker) {
        byte[] bytes = new byte[PNG_SIGNATURE.length + 1];
        System.arraycopy(PNG_SIGNATURE, 0, bytes, 0, PNG_SIGNATURE.length);
        bytes[PNG_SIGNATURE.length] = (byte) marker;
        return bytes;
    }

    private static String base64Of(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private Path targetFile(UUID robotId, UUID mapId) {
        return tempDir.resolve(robotId.toString()).resolve(mapId.toString()).resolve("map.png");
    }

    private void stubSaveEchoesArgument() {
        when(robotMapRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void sync_missingExternalRobotId_throwsIntegrationUnavailable_neverCallsAnything() throws Exception {
        Robot robot = aKeenonRobot();
        robot.setExternalRobotId(null);

        assertThatThrownBy(() -> service("1").sync(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.INTEGRATION_UNAVAILABLE));
        verifyNoInteractions(keenonMapMetadataSyncService, client, robotMapRepository);
    }

    @Test
    void sync_noSceneResolvable_returnsEmpty_neverCallsVendorMapEndpoints() throws Exception {
        Robot robot = aKeenonRobot();
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.empty());

        Optional<RobotMap> result = service("1").sync(robot);

        assertThat(result).isEmpty();
        verifyNoInteractions(client, robotMapRepository);
    }

    @Test
    void sync_successfulFetch_decodesAndStoresPngAndPersistsMetadata() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        when(client.getMapPosition("7ClJPR", "1")).thenReturn(mapPositionResponse("md5-a"));
        byte[] png = validPngBytes(1);
        when(client.getMapData("7ClJPR", "1")).thenReturn(new KeenonMapDataResponse(base64Of(png), 800, 600));
        stubSaveEchoesArgument();

        Optional<RobotMap> result = service("1").sync(robot);

        assertThat(result).isPresent();
        assertThat(result.get().getWidth()).isEqualTo(800);
        assertThat(result.get().getHeight()).isEqualTo(600);
        assertThat(result.get().getMapMd5()).isEqualTo("md5-a");
        Path expected = targetFile(robot.getId(), robotMap.getId());
        assertThat(result.get().getImageUrl()).isEqualTo(expected.toString());
        assertThat(Files.readAllBytes(expected)).isEqualTo(png);
    }

    @Test
    void sync_unchangedMapMd5AndFileAlreadyExists_skipsImageDownloadEntirely() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        robotMap.setMapMd5("md5-a");
        Path existing = targetFile(robot.getId(), robotMap.getId());
        Files.createDirectories(existing.getParent());
        Files.write(existing, validPngBytes(9));
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        when(client.getMapPosition("7ClJPR", "1")).thenReturn(mapPositionResponse("md5-a"));

        Optional<RobotMap> result = service("1").sync(robot);

        assertThat(result).contains(robotMap);
        verify(client, never()).getMapData(anyString(), anyString());
        verifyNoInteractions(robotMapRepository);
    }

    @Test
    void sync_changedMapMd5_downloadsFreshImageAndReplacesExistingFile() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        robotMap.setMapMd5("md5-old");
        Path existing = targetFile(robot.getId(), robotMap.getId());
        Files.createDirectories(existing.getParent());
        Files.write(existing, validPngBytes(9));
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        when(client.getMapPosition("7ClJPR", "1")).thenReturn(mapPositionResponse("md5-new"));
        byte[] newPng = validPngBytes(2);
        when(client.getMapData("7ClJPR", "1")).thenReturn(new KeenonMapDataResponse(base64Of(newPng), 10, 20));
        stubSaveEchoesArgument();

        service("1").sync(robot);

        assertThat(Files.readAllBytes(existing)).isEqualTo(newPng);
        assertThat(robotMap.getMapMd5()).isEqualTo("md5-new");
    }

    @Test
    void sync_currentMapMd5Undeterminable_alwaysDownloadsRatherThanAssumingUnchanged() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        robotMap.setMapMd5("md5-a");
        Path existing = targetFile(robot.getId(), robotMap.getId());
        Files.createDirectories(existing.getParent());
        Files.write(existing, validPngBytes(9));
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        when(client.getMapPosition("7ClJPR", "1")).thenReturn(mapPositionResponse(null));
        byte[] newPng = validPngBytes(3);
        when(client.getMapData("7ClJPR", "1")).thenReturn(new KeenonMapDataResponse(base64Of(newPng), 1, 1));
        stubSaveEchoesArgument();

        service("1").sync(robot);

        verify(client).getMapData("7ClJPR", "1");
        assertThat(Files.readAllBytes(existing)).isEqualTo(newPng);
    }

    @Test
    void sync_matchingMapMd5ButFileMissing_stillDownloads() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        robotMap.setMapMd5("md5-a");
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        when(client.getMapPosition("7ClJPR", "1")).thenReturn(mapPositionResponse("md5-a"));
        byte[] png = validPngBytes(4);
        when(client.getMapData("7ClJPR", "1")).thenReturn(new KeenonMapDataResponse(base64Of(png), 1, 1));
        stubSaveEchoesArgument();

        service("1").sync(robot);

        verify(client).getMapData("7ClJPR", "1");
        assertThat(Files.readAllBytes(targetFile(robot.getId(), robotMap.getId()))).isEqualTo(png);
    }

    @Test
    void sync_invalidBase64Content_rejectedAndExistingDataPreserved() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        robotMap.setMapMd5("md5-old");
        Path existing = targetFile(robot.getId(), robotMap.getId());
        Files.createDirectories(existing.getParent());
        byte[] originalBytes = validPngBytes(9);
        Files.write(existing, originalBytes);
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        when(client.getMapPosition("7ClJPR", "1")).thenReturn(mapPositionResponse("md5-new"));
        when(client.getMapData("7ClJPR", "1")).thenReturn(new KeenonMapDataResponse("not-valid-base64!!!", 1, 1));

        assertThatThrownBy(() -> service("1").sync(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VENDOR_API_ERROR));

        assertThat(Files.readAllBytes(existing)).isEqualTo(originalBytes);
        assertThat(robotMap.getMapMd5()).isEqualTo("md5-old");
        verify(robotMapRepository, never()).save(any());
    }

    @Test
    void sync_invalidPngMagicBytes_rejectedAndExistingDataPreserved() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        robotMap.setMapMd5("md5-old");
        Path existing = targetFile(robot.getId(), robotMap.getId());
        Files.createDirectories(existing.getParent());
        byte[] originalBytes = validPngBytes(9);
        Files.write(existing, originalBytes);
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        when(client.getMapPosition("7ClJPR", "1")).thenReturn(mapPositionResponse("md5-new"));
        String notPng = base64Of("this is not a png file at all".getBytes());
        when(client.getMapData("7ClJPR", "1")).thenReturn(new KeenonMapDataResponse(notPng, 1, 1));

        assertThatThrownBy(() -> service("1").sync(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VENDOR_API_ERROR));

        assertThat(Files.readAllBytes(existing)).isEqualTo(originalBytes);
        verify(robotMapRepository, never()).save(any());
    }

    @Test
    void sync_nullOrBlankContent_rejected() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        when(client.getMapPosition("7ClJPR", "1")).thenReturn(mapPositionResponse("md5-new"));
        when(client.getMapData("7ClJPR", "1")).thenReturn(new KeenonMapDataResponse("  ", 1, 1));

        assertThatThrownBy(() -> service("1").sync(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VENDOR_API_ERROR));
        verify(robotMapRepository, never()).save(any());
    }

    @Test
    void sync_vendorGetMapDataThrows_propagatesAndPreservesExistingFileAndMetadata() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        robotMap.setMapMd5("md5-old");
        Path existing = targetFile(robot.getId(), robotMap.getId());
        Files.createDirectories(existing.getParent());
        byte[] originalBytes = validPngBytes(9);
        Files.write(existing, originalBytes);
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        when(client.getMapPosition("7ClJPR", "1")).thenReturn(mapPositionResponse("md5-new"));
        when(client.getMapData("7ClJPR", "1"))
                .thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        assertThatThrownBy(() -> service("1").sync(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VENDOR_API_ERROR));

        assertThat(Files.readAllBytes(existing)).isEqualTo(originalBytes);
        assertThat(robotMap.getMapMd5()).isEqualTo("md5-old");
        verify(robotMapRepository, never()).save(any());
    }

    @Test
    void sync_vendorGetMapPositionThrows_propagatesWithoutMutatingAnything() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        when(client.getMapPosition(anyString(), anyString()))
                .thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        assertThatThrownBy(() -> service("1").sync(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VENDOR_API_ERROR));

        verify(client, never()).getMapData(anyString(), anyString());
        verifyNoInteractions(robotMapRepository);
    }

    @Test
    void sync_liveVerifiedDemoPieceScene_persistsRealEvidencedMapMetadata() throws Exception {
        // Regression/documentation test (Phase 1I): the exact sceneCode/mapMd5/dimensions
        // live-verified for Demo Piece (robotSn 94:BA:06:CA:99:F3) via a Sakar-configured
        // KeenonRobotSceneConfig row resolving to sceneCode "7ClJPR" — never the historical
        // "dTW2N7" or the vendor mapId "4c0075859805496eb452187b3cd91107".
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("7ClJPR");
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        when(client.getMapPosition("7ClJPR", "1"))
                .thenReturn(mapPositionResponse("a3cb0d75faa17c9ab12b9a6434173b42"));
        byte[] png = validPngBytes(1);
        when(client.getMapData("7ClJPR", "1")).thenReturn(new KeenonMapDataResponse(base64Of(png), 570, 763));
        stubSaveEchoesArgument();

        Optional<RobotMap> result = service("1").sync(robot);

        assertThat(result).isPresent();
        assertThat(result.get().getVendorMapId()).isEqualTo("7ClJPR");
        assertThat(result.get().getWidth()).isEqualTo(570);
        assertThat(result.get().getHeight()).isEqualTo(763);
        assertThat(result.get().getMapMd5()).isEqualTo("a3cb0d75faa17c9ab12b9a6434173b42");
    }

    @Test
    void sync_storagePathIsBuiltOnlyFromRobotAndMapUuids_vendorSceneCodeNeverAppearsInPath() throws Exception {
        Robot robot = aKeenonRobot();
        RobotMap robotMap = aRobotMap("../../etc/passwd");
        when(keenonMapMetadataSyncService.sync(robot)).thenReturn(Optional.of(robotMap));
        when(client.getMapPosition("../../etc/passwd", "1")).thenReturn(mapPositionResponse("md5-a"));
        byte[] png = validPngBytes(5);
        when(client.getMapData("../../etc/passwd", "1")).thenReturn(new KeenonMapDataResponse(base64Of(png), 1, 1));
        stubSaveEchoesArgument();

        service("1").sync(robot);

        Path expected = targetFile(robot.getId(), robotMap.getId());
        assertThat(Files.readAllBytes(expected)).isEqualTo(png);
        assertThat(expected.normalize().startsWith(tempDir.toAbsolutePath().normalize())).isTrue();
    }
}

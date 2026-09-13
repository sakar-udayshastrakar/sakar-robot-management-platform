package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.robot.adapter.dto.AreaInfo;
import com.sakarrobotics.cloud.robot.registry.Robot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Keenon area-sync slice. Verifies the sync writer this codebase never had
 * before this slice: upsert-not-duplicate, deactivate-only-on-real-evidence,
 * and never inventing an area id/map id/display name.
 *
 * <p>Every mocked response here uses the REAL, raw-captured live envelope
 * (see {@link KeenonAreaListParser}'s Javadoc) — {@code
 * {code, msg, data: {count, currentPage, pageSize, entities: [...]}}},
 * where each {@code entities} element is a per-map/floor group carrying
 * {@code mapId}/{@code floor} plus two PARALLEL arrays, {@code
 * areaIdList}/{@code areaNameList}. Two earlier, wrong assumptions about
 * this shape are explicitly regression-tested against (see
 * {@code sync_oldTopLevelEntitiesShape_...} and {@code
 * sync_oldDataAsArrayShape_...} below) so neither can silently reappear.
 */
@ExtendWith(MockitoExtension.class)
class KeenonAreaSyncServiceTest {

    @Mock
    private KeenonApiClient client;
    @Mock
    private KeenonAreaMappingRepository areaMappingRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KeenonAreaSyncService service() {
        return new KeenonAreaSyncService(client, areaMappingRepository);
    }

    private Robot aKeenonRobot() {
        Robot robot = new Robot();
        robot.setId(UUID.randomUUID());
        robot.setOrganizationId(UUID.randomUUID());
        robot.setSiteId(UUID.randomUUID());
        robot.setExternalRobotId("94:BA:06:CA:99:F3");
        return robot;
    }

    private void stubSaveEchoesArgumentWithGeneratedId() {
        when(areaMappingRepository.save(any())).thenAnswer(inv -> {
            KeenonAreaMapping mapping = inv.getArgument(0);
            if (mapping.getId() == null) {
                mapping.setId(UUID.randomUUID());
            }
            return mapping;
        });
    }

    /** Builds the real, confirmed-live envelope shape around a hand-supplied {@code entities} JSON array literal. */
    private JsonNode envelope(String entitiesJsonArray) throws Exception {
        return objectMapper.readTree(
                "{\"code\":610000,\"msg\":\"success\",\"errorMsg\":\"success\",\"data\":{\"currentPage\":1,\"pageSize\":100,\"count\":1,\"entities\":"
                        + entitiesJsonArray + "}}");
    }

    // ------------------------------------------------------------------
    // 1. Real captured response: one map/floor group, one area.
    // ------------------------------------------------------------------

    @Test
    void sync_realCapturedResponse_producesTheExactLiveEvidencedArea() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        when(areaMappingRepository.findByRobotIdAndKeenonAreaId(robot.getId(), "8a7bd155598342d08158d34d5a07007d"))
                .thenReturn(Optional.empty());
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        // The exact raw body captured live for storeId=C00715655, robotSn=94:BA:06:CA:99:F3.
        JsonNode response = objectMapper.readTree("{\"msg\":\"success\",\"code\":610000,\"data\":{\"currentPage\":1,"
                + "\"pageSize\":100,\"count\":1,\"entities\":[{\"storeId\":\"C00715655\",\"robotSn\":\"94:BA:06:CA:99:F3\","
                + "\"mapId\":\"4c0075859805496eb452187b3cd91107\",\"floor\":1,"
                + "\"areaIdList\":[\"8a7bd155598342d08158d34d5a07007d\"],\"areaNameList\":[\"Area5\"]}]},"
                + "\"errorMsg\":\"success\"}");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).vendorAreaId()).isEqualTo("8a7bd155598342d08158d34d5a07007d");
        assertThat(result.get(0).displayName()).isEqualTo("Area5");
        assertThat(result.get(0).sakarAreaId()).isNotBlank();

        verify(areaMappingRepository).save(argThatMapping(m ->
                "8a7bd155598342d08158d34d5a07007d".equals(m.getKeenonAreaId())
                        && "Area5".equals(m.getDisplayName())
                        && "4c0075859805496eb452187b3cd91107".equals(m.getKeenonMapId())
                        && "C00715655".equals(m.getKeenonStoreId())
                        && m.getRobotId().equals(robot.getId()) && m.isActive() && m.getLastSyncedAt() != null));
    }

    // ------------------------------------------------------------------
    // 2. One entity, multiple areas (parallel arrays).
    // ------------------------------------------------------------------

    @Test
    void sync_oneEntityWithMultipleAreas_pairsEachIdWithItsNameByIndex() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        when(areaMappingRepository.findByRobotIdAndKeenonAreaId(any(), any())).thenReturn(Optional.empty());
        JsonNode response = envelope("[{\"mapId\":\"map-1\",\"floor\":1,"
                + "\"areaIdList\":[\"area-a\",\"area-b\",\"area-c\"],\"areaNameList\":[\"AreaA\",\"AreaB\",\"AreaC\"]}]");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).hasSize(3);
        assertThat(result).extracting(AreaInfo::vendorAreaId).containsExactly("area-a", "area-b", "area-c");
        assertThat(result).extracting(AreaInfo::displayName).containsExactly("AreaA", "AreaB", "AreaC");
        verify(areaMappingRepository).save(argThatMapping(m -> "area-a".equals(m.getKeenonAreaId()) && "map-1".equals(m.getKeenonMapId())));
        verify(areaMappingRepository).save(argThatMapping(m -> "area-b".equals(m.getKeenonAreaId()) && "map-1".equals(m.getKeenonMapId())));
        verify(areaMappingRepository).save(argThatMapping(m -> "area-c".equals(m.getKeenonAreaId()) && "map-1".equals(m.getKeenonMapId())));
    }

    // ------------------------------------------------------------------
    // 3. Multiple map/floor entities.
    // ------------------------------------------------------------------

    @Test
    void sync_multipleMapFloorEntities_processesAreasFromEveryGroup() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        when(areaMappingRepository.findByRobotIdAndKeenonAreaId(any(), any())).thenReturn(Optional.empty());
        JsonNode response = envelope("["
                + "{\"mapId\":\"map-1\",\"floor\":1,\"areaIdList\":[\"area-a\"],\"areaNameList\":[\"AreaA\"]},"
                + "{\"mapId\":\"map-2\",\"floor\":2,\"areaIdList\":[\"area-b\"],\"areaNameList\":[\"AreaB\"]}"
                + "]");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AreaInfo::vendorAreaId).containsExactlyInAnyOrder("area-a", "area-b");
        verify(areaMappingRepository).save(argThatMapping(m -> "area-a".equals(m.getKeenonAreaId()) && "map-1".equals(m.getKeenonMapId())));
        verify(areaMappingRepository).save(argThatMapping(m -> "area-b".equals(m.getKeenonAreaId()) && "map-2".equals(m.getKeenonMapId())));
    }

    // ------------------------------------------------------------------
    // 4/5/6. Empty entities / missing data / missing entities.
    // ------------------------------------------------------------------

    @Test
    void sync_emptyEntitiesArray_producesZeroAreas_andDeactivatesEveryPreviouslyActiveMapping() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        KeenonAreaMapping stale = new KeenonAreaMapping();
        stale.setId(UUID.randomUUID());
        stale.setRobotId(robot.getId());
        stale.setKeenonAreaId("area-old");
        stale.setActive(true);
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of(stale));
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(envelope("[]"));

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).isEmpty();
        verify(areaMappingRepository).save(argThatMapping(m -> !m.isActive()));
    }

    @Test
    void sync_missingDataField_isTreatedAsEmpty_neverThrows() throws Exception {
        Robot robot = aKeenonRobot();
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(objectMapper.readTree("{\"code\":610000,\"msg\":\"success\"}"));

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).isEmpty();
        verify(areaMappingRepository, never()).save(any());
    }

    @Test
    void sync_missingEntitiesField_isTreatedAsEmpty_neverThrows() throws Exception {
        Robot robot = aKeenonRobot();
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3"))
                .thenReturn(objectMapper.readTree("{\"code\":610000,\"data\":{\"currentPage\":1,\"pageSize\":100,\"count\":0}}"));

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).isEmpty();
        verify(areaMappingRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // 7/8. Missing/null areaIdList / areaNameList on one entity.
    // ------------------------------------------------------------------

    @Test
    void sync_entityWithMissingAreaIdList_isSkipped_noInvalidMapping() throws Exception {
        Robot robot = aKeenonRobot();
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = envelope("[{\"mapId\":\"map-1\",\"floor\":1,\"areaNameList\":[\"AreaA\"]}]");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).isEmpty();
        verify(areaMappingRepository, never()).save(any());
    }

    @Test
    void sync_entityWithNullAreaIdList_isSkipped_noInvalidMapping() throws Exception {
        Robot robot = aKeenonRobot();
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = envelope("[{\"mapId\":\"map-1\",\"floor\":1,\"areaIdList\":null,\"areaNameList\":[\"AreaA\"]}]");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).isEmpty();
        verify(areaMappingRepository, never()).save(any());
    }

    @Test
    void sync_entityWithMissingAreaNameList_isSkipped_noInvalidMapping() throws Exception {
        Robot robot = aKeenonRobot();
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = envelope("[{\"mapId\":\"map-1\",\"floor\":1,\"areaIdList\":[\"area-a\"]}]");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).isEmpty();
        verify(areaMappingRepository, never()).save(any());
    }

    @Test
    void sync_entityWithNullAreaNameList_isSkipped_noInvalidMapping() throws Exception {
        Robot robot = aKeenonRobot();
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = envelope("[{\"mapId\":\"map-1\",\"floor\":1,\"areaIdList\":[\"area-a\"],\"areaNameList\":null}]");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).isEmpty();
        verify(areaMappingRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // 9. Mismatched array lengths — explicit chosen behavior: pair strictly
    // by index up to the SHORTER list; a trailing, unpaired entry (on
    // either side) is dropped rather than guessed at.
    // ------------------------------------------------------------------

    @Test
    void sync_moreIdsThanNames_onlyPairsUpToTheShorterList_dropsTheUnpairedTrailingId() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        when(areaMappingRepository.findByRobotIdAndKeenonAreaId(any(), any())).thenReturn(Optional.empty());
        JsonNode response = envelope("[{\"mapId\":\"map-1\",\"floor\":1,"
                + "\"areaIdList\":[\"area-a\",\"area-b\",\"area-c\"],\"areaNameList\":[\"AreaA\",\"AreaB\"]}]");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AreaInfo::vendorAreaId).containsExactly("area-a", "area-b");
        verify(areaMappingRepository, never()).save(argThatMapping(m -> "area-c".equals(m.getKeenonAreaId())));
    }

    @Test
    void sync_moreNamesThanIds_onlyPairsUpToTheShorterList_dropsTheUnpairedTrailingName() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        when(areaMappingRepository.findByRobotIdAndKeenonAreaId(any(), any())).thenReturn(Optional.empty());
        JsonNode response = envelope("[{\"mapId\":\"map-1\",\"floor\":1,"
                + "\"areaIdList\":[\"area-a\"],\"areaNameList\":[\"AreaA\",\"AreaB\",\"AreaC\"]}]");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).vendorAreaId()).isEqualTo("area-a");
        assertThat(result.get(0).displayName()).isEqualTo("AreaA");
    }

    // ------------------------------------------------------------------
    // 10/11. The two previously-tried, now-confirmed-WRONG shapes must
    // never be silently treated as valid again.
    // ------------------------------------------------------------------

    @Test
    void sync_oldTopLevelEntitiesShape_isNotTreatedAsAValidAreaResponse() throws Exception {
        // A prior (wrong) fix attempt assumed a top-level "entities" array with no "data"
        // wrapper. The real envelope nests entities under "data" — a response shaped like
        // that wrong assumption must still resolve to zero areas, not silently succeed.
        Robot robot = aKeenonRobot();
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = objectMapper.readTree("{\"entities\":[{\"areaId\":\"area-1\",\"areaName\":\"Lobby\"}]}");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).isEmpty();
        verify(areaMappingRepository, never()).save(any());
    }

    @Test
    void sync_oldDataAsArrayShape_isNotTreatedAsAValidAreaResponse() throws Exception {
        // The ORIGINAL (also wrong) assumption: "data" itself is a flat array of
        // {areaId, areaName} objects. The real "data" is an OBJECT containing
        // "entities" — a flat-array "data" must still resolve to zero areas.
        Robot robot = aKeenonRobot();
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = objectMapper.readTree("{\"data\":[{\"areaId\":\"area-1\",\"areaName\":\"Lobby\"}]}");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).isEmpty();
        verify(areaMappingRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Pre-existing behavior — upsert / deactivate / no-fabrication — all
    // re-verified against the real envelope shape.
    // ------------------------------------------------------------------

    @Test
    void sync_usesExternalRobotId_neverTheSakarSerialNumber() throws Exception {
        // A robot with BOTH identifiers set to deliberately different values — the Sakar
        // serial must never leak into the vendor request, only the Keenon (vendor) identifier.
        Robot robot = aKeenonRobot();
        robot.setSerialNumber("SR-CB-2026-000001");
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        when(client.getAreaList(anyString(), anyString())).thenReturn(envelope("[]"));

        service().sync(robot, "C00715655");

        ArgumentCaptor<String> storeIdArg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> robotSnArg = ArgumentCaptor.forClass(String.class);
        verify(client).getAreaList(storeIdArg.capture(), robotSnArg.capture());
        assertThat(storeIdArg.getValue()).isEqualTo("C00715655");
        assertThat(robotSnArg.getValue()).isEqualTo("94:BA:06:CA:99:F3");
    }

    @Test
    void sync_repeatedSync_updatesTheSameRowRatherThanCreatingADuplicate() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        JsonNode response = envelope("[{\"mapId\":\"map-1\",\"floor\":1,\"areaIdList\":[\"area-1\"],\"areaNameList\":[\"Lobby\"]}]");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        // First sync: nothing exists yet.
        when(areaMappingRepository.findByRobotIdAndKeenonAreaId(robot.getId(), "area-1")).thenReturn(Optional.empty());
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        String firstSakarAreaId = service().sync(robot, "C00715655").get(0).sakarAreaId();

        // Second sync: the same vendor area now already has a mapping row.
        KeenonAreaMapping existing = new KeenonAreaMapping();
        existing.setId(UUID.fromString(firstSakarAreaId));
        existing.setRobotId(robot.getId());
        existing.setKeenonAreaId("area-1");
        existing.setDisplayName("Lobby");
        existing.setActive(true);
        when(areaMappingRepository.findByRobotIdAndKeenonAreaId(robot.getId(), "area-1")).thenReturn(Optional.of(existing));
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of(existing));

        List<AreaInfo> secondResult = service().sync(robot, "C00715655");

        assertThat(secondResult).hasSize(1);
        // Same Sakar id both times — the second sync updated the existing row, it did not
        // create a second one for the same vendor area.
        assertThat(secondResult.get(0).sakarAreaId()).isEqualTo(firstSakarAreaId);
    }

    @Test
    void sync_secondSyncWithANewlyAddedArea_createsOnlyTheNewMapping_leavesTheExistingOneUnchanged() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();

        // First sync: one existing, already-active mapping for area-1.
        KeenonAreaMapping existing = new KeenonAreaMapping();
        existing.setId(UUID.randomUUID());
        existing.setRobotId(robot.getId());
        existing.setKeenonAreaId("area-1");
        existing.setDisplayName("Lobby");
        existing.setActive(true);
        when(areaMappingRepository.findByRobotIdAndKeenonAreaId(robot.getId(), "area-1")).thenReturn(Optional.of(existing));
        when(areaMappingRepository.findByRobotIdAndKeenonAreaId(robot.getId(), "area-2")).thenReturn(Optional.empty());
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of(existing));

        // Vendor's live list now reports the existing area PLUS a newly added one.
        JsonNode response = envelope("[{\"mapId\":\"map-1\",\"floor\":1,"
                + "\"areaIdList\":[\"area-1\",\"area-2\"],\"areaNameList\":[\"Lobby\",\"Kitchen\"]}]");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).hasSize(2);
        // The pre-existing area's own Sakar id is unchanged — the row was updated, not duplicated.
        assertThat(result.get(0).sakarAreaId()).isEqualTo(existing.getId().toString());
        // The newly added area gets its own freshly created mapping (a real, generated id, not
        // invented by the sync code itself).
        assertThat(result.get(1).vendorAreaId()).isEqualTo("area-2");
        assertThat(result.get(1).sakarAreaId()).isNotBlank().isNotEqualTo(existing.getId().toString());

        verify(areaMappingRepository).save(argThatMapping(m -> "area-2".equals(m.getKeenonAreaId())
                && m.getDisplayName().equals("Kitchen") && m.isActive()));
        // Nothing was deactivated — both areas are present in the latest successful response.
        verify(areaMappingRepository, never()).save(argThatMapping(m -> !m.isActive()));
    }

    @Test
    void sync_areaAbsentFromLatestVendorResponse_isDeactivated() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        KeenonAreaMapping stale = new KeenonAreaMapping();
        stale.setId(UUID.randomUUID());
        stale.setRobotId(robot.getId());
        stale.setKeenonAreaId("area-old");
        stale.setDisplayName("Old Area");
        stale.setActive(true);
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of(stale));
        when(areaMappingRepository.findByRobotIdAndKeenonAreaId(robot.getId(), "area-new")).thenReturn(Optional.empty());
        JsonNode response = envelope("[{\"mapId\":\"map-1\",\"floor\":1,\"areaIdList\":[\"area-new\"],\"areaNameList\":[\"New Area\"]}]");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        service().sync(robot, "C00715655");

        verify(areaMappingRepository).save(argThatMapping(m -> "area-old".equals(m.getKeenonAreaId()) && !m.isActive()));
    }

    @Test
    void sync_vendorApiThrows_propagatesWithoutDeactivatingOrMutatingAnything() {
        Robot robot = aKeenonRobot();
        when(client.getAreaList(anyString(), anyString()))
                .thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        assertThatThrownBy(() -> service().sync(robot, "C00715655"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VENDOR_API_ERROR));

        // A failed call carries no evidence about anything — nothing should ever be saved.
        verify(areaMappingRepository, never()).save(any());
    }

    @Test
    void sync_areaWithNoDisplayName_fallsBackToTheVendorAreaId_neverInventsAName() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        when(areaMappingRepository.findByRobotIdAndKeenonAreaId(robot.getId(), "area-1")).thenReturn(Optional.empty());
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = envelope("[{\"mapId\":\"map-1\",\"floor\":1,\"areaIdList\":[\"area-1\"],\"areaNameList\":[null]}]");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result.get(0).displayName()).isEqualTo("area-1");
    }

    @Test
    void sync_robotWithoutExternalId_throwsIntegrationUnavailable_neverCallsTheVendor() {
        Robot robot = aKeenonRobot();
        robot.setExternalRobotId(null);

        assertThatThrownBy(() -> service().sync(robot, "C00715655"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.INTEGRATION_UNAVAILABLE));
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    private static KeenonAreaMapping argThatMapping(java.util.function.Predicate<KeenonAreaMapping> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}

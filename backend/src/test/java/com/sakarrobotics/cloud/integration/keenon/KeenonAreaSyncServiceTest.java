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

    @Test
    void sync_newAreas_createsMappingsAndReturnsThemInTheVendorNeutralShape() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        when(areaMappingRepository.findByRobotIdAndKeenonAreaId(robot.getId(), "area-1")).thenReturn(Optional.empty());
        when(areaMappingRepository.findByRobotIdAndKeenonAreaId(robot.getId(), "area-2")).thenReturn(Optional.empty());
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = objectMapper.readTree(
                "{\"data\":[{\"areaId\":\"area-1\",\"areaName\":\"Lobby\"},{\"areaId\":\"area-2\",\"areaName\":\"Conference Room\"}]}");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).vendorAreaId()).isEqualTo("area-1");
        assertThat(result.get(0).displayName()).isEqualTo("Lobby");
        assertThat(result.get(0).sakarAreaId()).isNotBlank();
        assertThat(result.get(1).vendorAreaId()).isEqualTo("area-2");
        assertThat(result.get(1).displayName()).isEqualTo("Conference Room");

        verify(areaMappingRepository).save(argThatMapping(m ->
                m.getKeenonAreaId().equals("area-1") && m.getDisplayName().equals("Lobby")
                        && m.getKeenonStoreId().equals("C00715655") && m.getRobotId().equals(robot.getId())
                        && m.getOrganizationId().equals(robot.getOrganizationId()) && m.getSiteId().equals(robot.getSiteId())
                        && m.isActive() && m.getLastSyncedAt() != null));
    }

    @Test
    void sync_usesExternalRobotId_neverTheSakarSerialNumber() throws Exception {
        // A robot with BOTH identifiers set to deliberately different values — the Sakar
        // serial must never leak into the vendor request, only the Keenon (vendor) identifier.
        Robot robot = aKeenonRobot();
        robot.setSerialNumber("SR-CB-2026-000001");
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        when(client.getAreaList(anyString(), anyString())).thenReturn(objectMapper.readTree("{\"data\":[]}"));

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
        JsonNode response = objectMapper.readTree("{\"data\":[{\"areaId\":\"area-1\",\"areaName\":\"Lobby\"}]}");
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
        JsonNode response = objectMapper.readTree(
                "{\"data\":[{\"areaId\":\"area-1\",\"areaName\":\"Lobby\"},{\"areaId\":\"area-2\",\"areaName\":\"Kitchen\"}]}");
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
        JsonNode response = objectMapper.readTree("{\"data\":[{\"areaId\":\"area-new\",\"areaName\":\"New Area\"}]}");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        service().sync(robot, "C00715655");

        verify(areaMappingRepository).save(argThatMapping(m -> "area-old".equals(m.getKeenonAreaId()) && !m.isActive()));
    }

    @Test
    void sync_emptyVendorResponse_deactivatesEveryPreviouslyActiveMapping() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        KeenonAreaMapping stale = new KeenonAreaMapping();
        stale.setId(UUID.randomUUID());
        stale.setRobotId(robot.getId());
        stale.setKeenonAreaId("area-old");
        stale.setActive(true);
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of(stale));
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(objectMapper.readTree("{\"data\":[]}"));

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).isEmpty();
        verify(areaMappingRepository).save(argThatMapping(m -> !m.isActive()));
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
    void sync_malformedVendorEntryMissingAreaId_isSkippedNeverFabricated() throws Exception {
        Robot robot = aKeenonRobot();
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = objectMapper.readTree("{\"data\":[{\"areaName\":\"No id here\"}]}");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        List<AreaInfo> result = service().sync(robot, "C00715655");

        assertThat(result).isEmpty();
        verify(areaMappingRepository, never()).save(any());
    }

    @Test
    void sync_areaWithNoDisplayName_fallsBackToTheVendorAreaId_neverInventsAName() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        when(areaMappingRepository.findByRobotIdAndKeenonAreaId(robot.getId(), "area-1")).thenReturn(Optional.empty());
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = objectMapper.readTree("{\"data\":[{\"areaId\":\"area-1\"}]}");
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

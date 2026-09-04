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
import com.sakarrobotics.cloud.robot.registry.Robot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Keenon cleaning-mode-sync slice. Mirrors {@code KeenonAreaSyncServiceTest}:
 * upsert-not-duplicate, deactivate-only-on-real-evidence, never inventing a
 * mode id or display name.
 */
@ExtendWith(MockitoExtension.class)
class KeenonCleaningModeSyncServiceTest {

    @Mock
    private KeenonApiClient client;
    @Mock
    private KeenonCleaningModeMappingRepository modeMappingRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KeenonCleaningModeSyncService service() {
        return new KeenonCleaningModeSyncService(client, modeMappingRepository);
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
        when(modeMappingRepository.save(any())).thenAnswer(inv -> {
            KeenonCleaningModeMapping mapping = inv.getArgument(0);
            if (mapping.getId() == null) {
                mapping.setId(UUID.randomUUID());
            }
            return mapping;
        });
    }

    @Test
    void sync_newModes_createsMappings() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        when(modeMappingRepository.findByRobotIdAndKeenonModeId(robot.getId(), "101")).thenReturn(Optional.empty());
        when(modeMappingRepository.findByRobotIdAndKeenonModeId(robot.getId(), "105")).thenReturn(Optional.empty());
        when(modeMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = objectMapper.readTree(
                "{\"data\":[{\"cleanModelId\":\"101\",\"cleanModelName\":\"Sweep & Mop\"},"
                        + "{\"cleanModelId\":\"105\",\"cleanModelName\":\"Sweep\"}]}");
        when(client.getCleaningModes("94:BA:06:CA:99:F3")).thenReturn(response);

        List<KeenonCleaningModeMapping> result = service().sync(robot);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getKeenonModeId()).isEqualTo("101");
        assertThat(result.get(0).getDisplayName()).isEqualTo("Sweep & Mop");
        assertThat(result.get(1).getKeenonModeId()).isEqualTo("105");
        assertThat(result.get(1).getDisplayName()).isEqualTo("Sweep");

        verify(modeMappingRepository).save(argThatMapping(m ->
                m.getKeenonModeId().equals("101") && m.getDisplayName().equals("Sweep & Mop")
                        && m.getRobotId().equals(robot.getId()) && m.getOrganizationId().equals(robot.getOrganizationId())
                        && m.getSiteId().equals(robot.getSiteId()) && m.isActive() && m.getLastSyncedAt() != null));
    }

    @Test
    void sync_usesExternalRobotId_neverTheSakarSerialNumber() throws Exception {
        Robot robot = aKeenonRobot();
        robot.setSerialNumber("SR-CB-2026-000001");
        when(modeMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        when(client.getCleaningModes(anyString())).thenReturn(objectMapper.readTree("{\"data\":[]}"));

        service().sync(robot);

        ArgumentCaptor<String> robotSnArg = ArgumentCaptor.forClass(String.class);
        verify(client).getCleaningModes(robotSnArg.capture());
        assertThat(robotSnArg.getValue()).isEqualTo("94:BA:06:CA:99:F3");
    }

    @Test
    void sync_repeatedSync_updatesTheSameRowRatherThanCreatingADuplicate() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        JsonNode response = objectMapper.readTree("{\"data\":[{\"cleanModelId\":\"105\",\"cleanModelName\":\"Sweep\"}]}");
        when(client.getCleaningModes("94:BA:06:CA:99:F3")).thenReturn(response);

        when(modeMappingRepository.findByRobotIdAndKeenonModeId(robot.getId(), "105")).thenReturn(Optional.empty());
        when(modeMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        String firstId = service().sync(robot).get(0).getId().toString();

        KeenonCleaningModeMapping existing = new KeenonCleaningModeMapping();
        existing.setId(UUID.fromString(firstId));
        existing.setRobotId(robot.getId());
        existing.setKeenonModeId("105");
        existing.setDisplayName("Sweep");
        existing.setActive(true);
        when(modeMappingRepository.findByRobotIdAndKeenonModeId(robot.getId(), "105")).thenReturn(Optional.of(existing));
        when(modeMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of(existing));

        List<KeenonCleaningModeMapping> secondResult = service().sync(robot);

        assertThat(secondResult).hasSize(1);
        assertThat(secondResult.get(0).getId().toString()).isEqualTo(firstId);
    }

    @Test
    void sync_modeAbsentFromLatestVendorResponse_isDeactivated() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        KeenonCleaningModeMapping stale = new KeenonCleaningModeMapping();
        stale.setId(UUID.randomUUID());
        stale.setRobotId(robot.getId());
        stale.setKeenonModeId("102");
        stale.setDisplayName("Water Suction");
        stale.setActive(true);
        when(modeMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of(stale));
        when(modeMappingRepository.findByRobotIdAndKeenonModeId(robot.getId(), "105")).thenReturn(Optional.empty());
        JsonNode response = objectMapper.readTree("{\"data\":[{\"cleanModelId\":\"105\",\"cleanModelName\":\"Sweep\"}]}");
        when(client.getCleaningModes("94:BA:06:CA:99:F3")).thenReturn(response);

        service().sync(robot);

        verify(modeMappingRepository).save(argThatMapping(m -> "102".equals(m.getKeenonModeId()) && !m.isActive()));
    }

    @Test
    void sync_emptyVendorResponse_deactivatesEveryPreviouslyActiveMapping() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        KeenonCleaningModeMapping stale = new KeenonCleaningModeMapping();
        stale.setId(UUID.randomUUID());
        stale.setRobotId(robot.getId());
        stale.setKeenonModeId("105");
        stale.setActive(true);
        when(modeMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of(stale));
        when(client.getCleaningModes("94:BA:06:CA:99:F3")).thenReturn(objectMapper.readTree("{\"data\":[]}"));

        List<KeenonCleaningModeMapping> result = service().sync(robot);

        assertThat(result).isEmpty();
        verify(modeMappingRepository).save(argThatMapping(m -> !m.isActive()));
    }

    @Test
    void sync_vendorApiThrows_propagatesWithoutDeactivatingOrMutatingAnything() {
        Robot robot = aKeenonRobot();
        when(client.getCleaningModes(anyString()))
                .thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        assertThatThrownBy(() -> service().sync(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VENDOR_API_ERROR));

        verify(modeMappingRepository, never()).save(any());
    }

    @Test
    void sync_malformedVendorEntryMissingModeId_isSkippedNeverFabricated() throws Exception {
        Robot robot = aKeenonRobot();
        when(modeMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = objectMapper.readTree("{\"data\":[{\"cleanModelName\":\"No id here\"}]}");
        when(client.getCleaningModes("94:BA:06:CA:99:F3")).thenReturn(response);

        List<KeenonCleaningModeMapping> result = service().sync(robot);

        assertThat(result).isEmpty();
        verify(modeMappingRepository, never()).save(any());
    }

    @Test
    void sync_modeWithNoDisplayName_fallsBackToTheVendorModeId_neverInventsAName() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        when(modeMappingRepository.findByRobotIdAndKeenonModeId(robot.getId(), "105")).thenReturn(Optional.empty());
        when(modeMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = objectMapper.readTree("{\"data\":[{\"cleanModelId\":\"105\"}]}");
        when(client.getCleaningModes("94:BA:06:CA:99:F3")).thenReturn(response);

        List<KeenonCleaningModeMapping> result = service().sync(robot);

        assertThat(result.get(0).getDisplayName()).isEqualTo("105");
    }

    @Test
    void sync_robotWithoutExternalId_throwsIntegrationUnavailable_neverCallsTheVendor() {
        Robot robot = aKeenonRobot();
        robot.setExternalRobotId(null);

        assertThatThrownBy(() -> service().sync(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.INTEGRATION_UNAVAILABLE));
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    private static KeenonCleaningModeMapping argThatMapping(java.util.function.Predicate<KeenonCleaningModeMapping> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}

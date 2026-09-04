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
 * Keenon back/charging-point-sync slice. Mirrors
 * {@code KeenonCleaningModeSyncServiceTest}: upsert-not-duplicate,
 * deactivate-only-on-real-evidence, never inventing a point id or
 * display name — and never hardcoding the observed point id {@code 39}.
 */
@ExtendWith(MockitoExtension.class)
class KeenonBackPointSyncServiceTest {

    @Mock
    private KeenonApiClient client;
    @Mock
    private KeenonBackPointMappingRepository backPointMappingRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KeenonBackPointSyncService service() {
        return new KeenonBackPointSyncService(client, backPointMappingRepository);
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
        when(backPointMappingRepository.save(any())).thenAnswer(inv -> {
            KeenonBackPointMapping mapping = inv.getArgument(0);
            if (mapping.getId() == null) {
                mapping.setId(UUID.randomUUID());
            }
            return mapping;
        });
    }

    @Test
    void sync_newBackPoints_createsMappings() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        when(backPointMappingRepository.findByRobotIdAndKeenonBackPointId(robot.getId(), "39")).thenReturn(Optional.empty());
        when(backPointMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = objectMapper.readTree(
                "{\"data\":[{\"backPointId\":\"39\",\"backPointName\":\"1_Charging pile\"}]}");
        when(client.getBackPoints("94:BA:06:CA:99:F3")).thenReturn(response);

        List<KeenonBackPointMapping> result = service().sync(robot);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKeenonBackPointId()).isEqualTo("39");
        assertThat(result.get(0).getDisplayName()).isEqualTo("1_Charging pile");

        verify(backPointMappingRepository).save(argThatMapping(m ->
                m.getKeenonBackPointId().equals("39") && m.getDisplayName().equals("1_Charging pile")
                        && m.getRobotId().equals(robot.getId()) && m.getOrganizationId().equals(robot.getOrganizationId())
                        && m.getSiteId().equals(robot.getSiteId()) && m.isActive() && m.getLastSyncedAt() != null));
    }

    @Test
    void sync_usesExternalRobotId_neverTheSakarSerialNumber() throws Exception {
        Robot robot = aKeenonRobot();
        robot.setSerialNumber("SR-CB-2026-000001");
        when(backPointMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        when(client.getBackPoints(anyString())).thenReturn(objectMapper.readTree("{\"data\":[]}"));

        service().sync(robot);

        ArgumentCaptor<String> robotSnArg = ArgumentCaptor.forClass(String.class);
        verify(client).getBackPoints(robotSnArg.capture());
        assertThat(robotSnArg.getValue()).isEqualTo("94:BA:06:CA:99:F3");
    }

    @Test
    void sync_repeatedSync_updatesTheSameRowRatherThanCreatingADuplicate() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        JsonNode response = objectMapper.readTree("{\"data\":[{\"backPointId\":\"39\",\"backPointName\":\"1_Charging pile\"}]}");
        when(client.getBackPoints("94:BA:06:CA:99:F3")).thenReturn(response);

        when(backPointMappingRepository.findByRobotIdAndKeenonBackPointId(robot.getId(), "39")).thenReturn(Optional.empty());
        when(backPointMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        String firstId = service().sync(robot).get(0).getId().toString();

        KeenonBackPointMapping existing = new KeenonBackPointMapping();
        existing.setId(UUID.fromString(firstId));
        existing.setRobotId(robot.getId());
        existing.setKeenonBackPointId("39");
        existing.setDisplayName("1_Charging pile");
        existing.setActive(true);
        when(backPointMappingRepository.findByRobotIdAndKeenonBackPointId(robot.getId(), "39")).thenReturn(Optional.of(existing));
        when(backPointMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of(existing));

        List<KeenonBackPointMapping> secondResult = service().sync(robot);

        assertThat(secondResult).hasSize(1);
        assertThat(secondResult.get(0).getId().toString()).isEqualTo(firstId);
    }

    @Test
    void sync_pointAbsentFromLatestVendorResponse_isDeactivated() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        KeenonBackPointMapping stale = new KeenonBackPointMapping();
        stale.setId(UUID.randomUUID());
        stale.setRobotId(robot.getId());
        stale.setKeenonBackPointId("40");
        stale.setDisplayName("Old dock");
        stale.setActive(true);
        when(backPointMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of(stale));
        when(backPointMappingRepository.findByRobotIdAndKeenonBackPointId(robot.getId(), "39")).thenReturn(Optional.empty());
        JsonNode response = objectMapper.readTree("{\"data\":[{\"backPointId\":\"39\",\"backPointName\":\"1_Charging pile\"}]}");
        when(client.getBackPoints("94:BA:06:CA:99:F3")).thenReturn(response);

        service().sync(robot);

        verify(backPointMappingRepository).save(argThatMapping(m -> "40".equals(m.getKeenonBackPointId()) && !m.isActive()));
    }

    @Test
    void sync_emptyVendorResponse_deactivatesEveryPreviouslyActiveMapping() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        KeenonBackPointMapping stale = new KeenonBackPointMapping();
        stale.setId(UUID.randomUUID());
        stale.setRobotId(robot.getId());
        stale.setKeenonBackPointId("39");
        stale.setActive(true);
        when(backPointMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of(stale));
        when(client.getBackPoints("94:BA:06:CA:99:F3")).thenReturn(objectMapper.readTree("{\"data\":[]}"));

        List<KeenonBackPointMapping> result = service().sync(robot);

        assertThat(result).isEmpty();
        verify(backPointMappingRepository).save(argThatMapping(m -> !m.isActive()));
    }

    @Test
    void sync_vendorApiThrows_propagatesWithoutDeactivatingOrMutatingAnything() {
        Robot robot = aKeenonRobot();
        when(client.getBackPoints(anyString()))
                .thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        assertThatThrownBy(() -> service().sync(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VENDOR_API_ERROR));

        verify(backPointMappingRepository, never()).save(any());
    }

    @Test
    void sync_malformedVendorEntryMissingBackPointId_isSkippedNeverFabricated() throws Exception {
        Robot robot = aKeenonRobot();
        when(backPointMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = objectMapper.readTree("{\"data\":[{\"backPointName\":\"No id here\"}]}");
        when(client.getBackPoints("94:BA:06:CA:99:F3")).thenReturn(response);

        List<KeenonBackPointMapping> result = service().sync(robot);

        assertThat(result).isEmpty();
        verify(backPointMappingRepository, never()).save(any());
    }

    @Test
    void sync_pointWithNoDisplayName_fallsBackToTheVendorPointId_neverInventsAName() throws Exception {
        Robot robot = aKeenonRobot();
        stubSaveEchoesArgumentWithGeneratedId();
        when(backPointMappingRepository.findByRobotIdAndKeenonBackPointId(robot.getId(), "39")).thenReturn(Optional.empty());
        when(backPointMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(List.of());
        JsonNode response = objectMapper.readTree("{\"data\":[{\"backPointId\":\"39\"}]}");
        when(client.getBackPoints("94:BA:06:CA:99:F3")).thenReturn(response);

        List<KeenonBackPointMapping> result = service().sync(robot);

        assertThat(result.get(0).getDisplayName()).isEqualTo("39");
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

    private static KeenonBackPointMapping argThatMapping(java.util.function.Predicate<KeenonBackPointMapping> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}

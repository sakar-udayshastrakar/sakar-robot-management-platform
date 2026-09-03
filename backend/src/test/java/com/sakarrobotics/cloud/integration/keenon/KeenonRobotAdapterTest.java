package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.robot.adapter.dto.AdapterOperationResult;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.Robot;

/**
 * Verifies the Keenon Open Platform response mapping against the exact
 * codes observed in the live evidence (Master Requirements Part 40):
 * {@code 610000} accepted, anything else rejected, and — critically — that
 * "accepted" is never conflated with "physically confirmed."
 */
@ExtendWith(MockitoExtension.class)
class KeenonRobotAdapterTest {

    @Mock
    private KeenonApiClient client;
    @Mock
    private KeenonAreaMappingRepository areaMappingRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KeenonRobotAdapter adapter() {
        return new KeenonRobotAdapter(client, areaMappingRepository);
    }

    private Robot robotWithExternalId(String externalId) {
        Robot robot = new Robot();
        robot.setExternalRobotId(externalId);
        return robot;
    }

    @Test
    void adapterType_isKeenonCloud() {
        assertThat(adapter().adapterType()).isEqualTo(AdapterType.KEENON_CLOUD);
    }

    @Test
    void returnToDock_code610000_isAcceptedButNotPhysicallyConfirmed() throws Exception {
        JsonNode response = objectMapper.readTree("{\"code\":\"610000\",\"bizType\":\"CleanRobotRechargeTask\"}");
        when(client.postRechargeTask(org.mockito.ArgumentMatchers.any())).thenReturn(response);

        AdapterOperationResult result = adapter().returnToDock(robotWithExternalId("94:BA:06:CA:99:F3"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.physicallyConfirmed()).isFalse();
        assertThat(result.vendorReference()).isEqualTo("610000");
    }

    @Test
    void returnToDock_nonAcceptedCode_isRejected() throws Exception {
        JsonNode response = objectMapper.readTree("{\"code\":\"500001\",\"message\":\"failure\"}");
        when(client.postRechargeTask(org.mockito.ArgumentMatchers.any())).thenReturn(response);

        AdapterOperationResult result = adapter().returnToDock(robotWithExternalId("94:BA:06:CA:99:F3"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.physicallyConfirmed()).isFalse();
    }

    @Test
    void resumeTask_throwsUnsupportedCapability_becauseKeenonHasNoResumeApi() {
        assertThatThrownBy(() -> adapter().resumeTask(robotWithExternalId("94:BA:06:CA:99:F3")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.UNSUPPORTED_CAPABILITY));
    }

    @Test
    void getBattery_mapsBatteryLevelField() throws Exception {
        JsonNode response = objectMapper.readTree("{\"data\":{\"batteryLevel\":83}}");
        when(client.getBatteryLevel(anyString())).thenReturn(response);

        var battery = adapter().getBattery(robotWithExternalId("94:BA:06:CA:99:F3"));

        assertThat(battery.percentage()).isEqualTo(83);
    }

    @Test
    void anyOperation_withoutExternalRobotId_failsClosedRatherThanCallingVendorWithNull() {
        Robot robotWithoutExternalId = new Robot();
        assertThatThrownBy(() -> adapter().getBattery(robotWithoutExternalId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.INTEGRATION_UNAVAILABLE));
    }

    @Test
    void startTask_withNoAreasSpecified_isRejectedBeforeCallingVendor() {
        AdapterOperationResult result = adapter().startTask(robotWithExternalId("SN-1"),
                new com.sakarrobotics.cloud.robot.adapter.dto.AdapterTaskRequest("CLEANING", java.util.List.of(), "SWEEP", 1, true));

        assertThat(result.accepted()).isFalse();
    }

    @Test
    void getAreas_mapsAreaIdAndAreaNameFields() throws Exception {
        Robot robot = robotWithExternalId("94:BA:06:CA:99:F3");
        robot.setId(UUID.randomUUID());
        KeenonAreaMapping mapping = new KeenonAreaMapping();
        mapping.setKeenonStoreId("C00715655");
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(java.util.List.of(mapping));

        JsonNode response = objectMapper.readTree(
                "{\"data\":[{\"areaId\":\"area-1\",\"areaName\":\"Lobby\"},{\"areaId\":\"area-2\",\"areaName\":\"Conference Room\"}]}");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        var areas = adapter().getAreas(robot);

        assertThat(areas).hasSize(2);
        assertThat(areas.get(0).vendorAreaId()).isEqualTo("area-1");
        assertThat(areas.get(0).displayName()).isEqualTo("Lobby");
        assertThat(areas.get(1).vendorAreaId()).isEqualTo("area-2");
        assertThat(areas.get(1).displayName()).isEqualTo("Conference Room");
    }

    @Test
    void getAreas_withoutSyncedStoreMapping_throwsResourceNotFound() {
        Robot robot = robotWithExternalId("94:BA:06:CA:99:F3");
        robot.setId(UUID.randomUUID());
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> adapter().getAreas(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void getAreas_whenVendorApiFails_propagatesTheFailureRatherThanReturningEmptyAreas() {
        Robot robot = robotWithExternalId("94:BA:06:CA:99:F3");
        robot.setId(UUID.randomUUID());
        KeenonAreaMapping mapping = new KeenonAreaMapping();
        mapping.setKeenonStoreId("C00715655");
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(java.util.List.of(mapping));
        when(client.getAreaList(anyString(), anyString()))
                .thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        assertThatThrownBy(() -> adapter().getAreas(robot))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VENDOR_API_ERROR));
    }

    @Test
    void getMap_isNotYetImplemented_failsClosedRatherThanGuessingAResponseShape() {
        assertThatThrownBy(() -> adapter().getMap(robotWithExternalId("94:BA:06:CA:99:F3")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.FEATURE_NOT_YET_IMPLEMENTED));
    }

    @Test
    void startTask_withUnknownMode_throwsValidationFailed() {
        java.util.UUID areaMappingId = UUID.randomUUID();
        KeenonAreaMapping mapping = new KeenonAreaMapping();
        mapping.setKeenonAreaId("live-area-id");
        mapping.setKeenonStoreId("C00715655");
        when(areaMappingRepository.findByIdAndActiveTrue(areaMappingId)).thenReturn(java.util.Optional.of(mapping));

        var request = new com.sakarrobotics.cloud.robot.adapter.dto.AdapterTaskRequest(
                "CLEANING", java.util.List.of(areaMappingId.toString()), "NOT_A_REAL_MODE", 1, true);

        assertThatThrownBy(() -> adapter().startTask(robotWithExternalId("SN-1"), request))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VALIDATION_FAILED));
    }
}

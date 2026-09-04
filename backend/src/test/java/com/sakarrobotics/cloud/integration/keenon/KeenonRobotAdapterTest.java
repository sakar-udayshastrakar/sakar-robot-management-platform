package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        assertThat(result.vendorContacted()).isTrue();
    }

    @Test
    void returnToDock_nonAcceptedCode_isRejectedByVendor_vendorWasContacted() throws Exception {
        JsonNode response = objectMapper.readTree("{\"code\":\"500001\",\"message\":\"failure\"}");
        when(client.postRechargeTask(org.mockito.ArgumentMatchers.any())).thenReturn(response);

        AdapterOperationResult result = adapter().returnToDock(robotWithExternalId("94:BA:06:CA:99:F3"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.physicallyConfirmed()).isFalse();
        // "Fix command dispatch semantics" slice: Keenon was genuinely reached and gave a
        // definitive answer, so this must be distinguishable from a pre-vendor-call rejection.
        assertThat(result.vendorContacted()).isTrue();
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
    void getStatus_mapsMainStateSubStateAndOnlineFields() throws Exception {
        JsonNode response = objectMapper.readTree("{\"data\":{\"mainState\":\"IDLE\",\"subState\":\"STANDBY\"}}");
        when(client.getRobotStatus(anyString())).thenReturn(response);

        var status = adapter().getStatus(robotWithExternalId("94:BA:06:CA:99:F3"));

        assertThat(status.mainState()).isEqualTo("IDLE");
        assertThat(status.subState()).isEqualTo("STANDBY");
        assertThat(status.online()).isTrue();
    }

    @Test
    void getStatus_noDataField_isReportedAsOffline_neverInventingAState() throws Exception {
        JsonNode response = objectMapper.readTree("{\"code\":\"200\"}");
        when(client.getRobotStatus(anyString())).thenReturn(response);

        var status = adapter().getStatus(robotWithExternalId("94:BA:06:CA:99:F3"));

        assertThat(status.online()).isFalse();
        assertThat(status.mainState()).isNull();
    }

    @Test
    void getStatusAndGetBattery_useExternalRobotId_neverTheSakarSerialNumber() throws Exception {
        // A robot with BOTH identifiers set to deliberately different values — the Sakar
        // serial must never leak into a vendor call, only the Keenon (vendor) identifier.
        Robot robot = robotWithExternalId("94:BA:06:CA:99:F3");
        robot.setSerialNumber("SR-CB-2026-000001");
        when(client.getRobotStatus(anyString())).thenReturn(objectMapper.readTree("{\"data\":{}}"));
        when(client.getBatteryLevel(anyString())).thenReturn(objectMapper.readTree("{\"data\":{\"batteryLevel\":50}}"));

        adapter().getStatus(robot);
        adapter().getBattery(robot);

        ArgumentCaptor<String> statusArg = ArgumentCaptor.forClass(String.class);
        verify(client).getRobotStatus(statusArg.capture());
        assertThat(statusArg.getValue()).isEqualTo("94:BA:06:CA:99:F3");

        ArgumentCaptor<String> batteryArg = ArgumentCaptor.forClass(String.class);
        verify(client).getBatteryLevel(batteryArg.capture());
        assertThat(batteryArg.getValue()).isEqualTo("94:BA:06:CA:99:F3");
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
        // "Fix command dispatch semantics" slice: this is a pre-vendor-call rejection —
        // the vendor was never contacted, so callers must not treat it as "dispatched".
        assertThat(result.vendorContacted()).isFalse();
    }

    @Test
    void getAreas_mapsAreaIdAndAreaNameFields_andResolvesTheSakarMappingIdForASyncedArea() throws Exception {
        Robot robot = robotWithExternalId("94:BA:06:CA:99:F3");
        robot.setId(UUID.randomUUID());
        KeenonAreaMapping mapping = new KeenonAreaMapping();
        mapping.setId(UUID.randomUUID());
        mapping.setKeenonStoreId("C00715655");
        // Only "area-1" has a synced Sakar mapping row — "area-2" below is a real vendor
        // area Keenon currently reports that has no corresponding Sakar row yet.
        mapping.setKeenonAreaId("area-1");
        when(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).thenReturn(java.util.List.of(mapping));

        JsonNode response = objectMapper.readTree(
                "{\"data\":[{\"areaId\":\"area-1\",\"areaName\":\"Lobby\"},{\"areaId\":\"area-2\",\"areaName\":\"Conference Room\"}]}");
        when(client.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(response);

        var areas = adapter().getAreas(robot);

        assertThat(areas).hasSize(2);
        assertThat(areas.get(0).vendorAreaId()).isEqualTo("area-1");
        assertThat(areas.get(0).displayName()).isEqualTo("Lobby");
        assertThat(areas.get(0).sakarAreaId()).isEqualTo(mapping.getId().toString());
        assertThat(areas.get(1).vendorAreaId()).isEqualTo("area-2");
        assertThat(areas.get(1).displayName()).isEqualTo("Conference Room");
        // No synced mapping row for "area-2" — null, never fabricated.
        assertThat(areas.get(1).sakarAreaId()).isNull();
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

    // ------------------------------------------------------------------
    // "Controlled START_TASK end-to-end validation" slice — the exact
    // vendor request body sent to Keenon, captured and asserted field by
    // field, plus the startTask-specific failure cases not covered above.
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    void startTask_acceptedResponse_translatesSakarAreaIdToVendorAreaId_andSendsTheExactVendorRequest() throws Exception {
        UUID areaMappingId = UUID.randomUUID();
        KeenonAreaMapping mapping = new KeenonAreaMapping();
        mapping.setKeenonAreaId("live-area-42"); // the current vendor area id — NOT areaMappingId
        mapping.setKeenonStoreId("C00715655");
        when(areaMappingRepository.findByIdAndActiveTrue(areaMappingId)).thenReturn(Optional.of(mapping));

        JsonNode backPoints = objectMapper.readTree("{\"data\":[{\"backPointId\":\"BP-1\"}]}");
        when(client.getBackPoints("94:BA:06:CA:99:F3")).thenReturn(backPoints);

        JsonNode accepted = objectMapper.readTree("{\"code\":\"610000\",\"bizType\":\"CleanRobotTemporaryTask\"}");
        when(client.postTemporaryTask(any())).thenReturn(accepted);

        var request = new com.sakarrobotics.cloud.robot.adapter.dto.AdapterTaskRequest(
                "CLEANING", List.of(areaMappingId.toString()), "SWEEP_MOP", 3, false);

        AdapterOperationResult result = adapter().startTask(robotWithExternalId("94:BA:06:CA:99:F3"), request);

        assertThat(result.accepted()).isTrue();
        assertThat(result.physicallyConfirmed()).isFalse();
        assertThat(result.vendorReference()).isEqualTo("610000");

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(client).postTemporaryTask(bodyCaptor.capture());
        Map<String, Object> body = bodyCaptor.getValue();

        assertThat(body.get("robotSn")).isEqualTo("94:BA:06:CA:99:F3");
        // The vendor request carries the CURRENT Keenon vendor area id, never the Sakar
        // KeenonAreaMapping row id the caller supplied.
        assertThat(body.get("areaIdList")).isEqualTo(List.of("live-area-42"));
        assertThat(body.get("cleanModelId")).isEqualTo(101); // SWEEP_MOP -> 101 (verified mapping)
        assertThat(body.get("cleanTimes")).isEqualTo(3);
        assertThat(body.get("backPointId")).isEqualTo("BP-1");
    }

    @Test
    void startTask_unknownSakarAreaMappingId_throwsResourceNotFound_neverCallsTheVendor() {
        UUID unknownMappingId = UUID.randomUUID();
        when(areaMappingRepository.findByIdAndActiveTrue(unknownMappingId)).thenReturn(Optional.empty());

        var request = new com.sakarrobotics.cloud.robot.adapter.dto.AdapterTaskRequest(
                "CLEANING", List.of(unknownMappingId.toString()), "SWEEP", 1, false);

        assertThatThrownBy(() -> adapter().startTask(robotWithExternalId("SN-1"), request))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.RESOURCE_NOT_FOUND));
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    void startTask_vendorRejection_isReportedAsRejectedNotThrown() throws Exception {
        UUID areaMappingId = UUID.randomUUID();
        KeenonAreaMapping mapping = new KeenonAreaMapping();
        mapping.setKeenonAreaId("live-area-42");
        when(areaMappingRepository.findByIdAndActiveTrue(areaMappingId)).thenReturn(Optional.of(mapping));
        when(client.getBackPoints(anyString())).thenReturn(objectMapper.readTree("{\"data\":[{\"backPointId\":\"BP-1\"}]}"));
        when(client.postTemporaryTask(any())).thenReturn(objectMapper.readTree("{\"code\":\"500001\",\"message\":\"failure\"}"));

        var request = new com.sakarrobotics.cloud.robot.adapter.dto.AdapterTaskRequest(
                "CLEANING", List.of(areaMappingId.toString()), "SWEEP", 1, false);

        AdapterOperationResult result = adapter().startTask(robotWithExternalId("94:BA:06:CA:99:F3"), request);

        assertThat(result.accepted()).isFalse();
        assertThat(result.physicallyConfirmed()).isFalse();
        assertThat(result.vendorContacted()).isTrue();
    }

    @Test
    void startTask_vendorApiException_propagatesRatherThanBeingSwallowedOrFabricatedAsSuccess() throws Exception {
        UUID areaMappingId = UUID.randomUUID();
        KeenonAreaMapping mapping = new KeenonAreaMapping();
        mapping.setKeenonAreaId("live-area-42");
        when(areaMappingRepository.findByIdAndActiveTrue(areaMappingId)).thenReturn(Optional.of(mapping));
        when(client.getBackPoints(anyString())).thenReturn(objectMapper.readTree("{\"data\":[{\"backPointId\":\"BP-1\"}]}"));
        when(client.postTemporaryTask(any()))
                .thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        var request = new com.sakarrobotics.cloud.robot.adapter.dto.AdapterTaskRequest(
                "CLEANING", List.of(areaMappingId.toString()), "SWEEP", 1, false);

        assertThatThrownBy(() -> adapter().startTask(robotWithExternalId("94:BA:06:CA:99:F3"), request))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VENDOR_API_ERROR));
    }

    @Test
    void startTask_noBackPointConfigured_throwsResourceNotFound_neverInventsOne() throws Exception {
        UUID areaMappingId = UUID.randomUUID();
        KeenonAreaMapping mapping = new KeenonAreaMapping();
        mapping.setKeenonAreaId("live-area-42");
        when(areaMappingRepository.findByIdAndActiveTrue(areaMappingId)).thenReturn(Optional.of(mapping));
        when(client.getBackPoints(anyString())).thenReturn(objectMapper.readTree("{\"data\":[]}"));

        var request = new com.sakarrobotics.cloud.robot.adapter.dto.AdapterTaskRequest(
                "CLEANING", List.of(areaMappingId.toString()), "SWEEP", 1, false);

        assertThatThrownBy(() -> adapter().startTask(robotWithExternalId("94:BA:06:CA:99:F3"), request))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.RESOURCE_NOT_FOUND));
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).postTemporaryTask(any());
    }
}

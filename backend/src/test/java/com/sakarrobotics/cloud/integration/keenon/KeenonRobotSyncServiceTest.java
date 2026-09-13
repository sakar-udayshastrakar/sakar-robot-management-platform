package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.integration.keenon.dto.KeenonRobotSyncResult;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationService;
import com.sakarrobotics.cloud.org.OrganizationType;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturer;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturerRepository;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;
import com.sakarrobotics.cloud.robot.registry.RobotService;
import com.sakarrobotics.cloud.robot.registry.SakarSerialNumberService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Keenon robot-discovery sync slice. Every mocked response here uses the
 * REAL, raw-captured live envelope (see {@link KeenonRobotSyncService}'s
 * Javadoc) — {@code {code, msg, data: [...]}}, where {@code data} is a flat
 * array of robot records, not an object wrapping a nested list like
 * area-list/back-point.
 *
 * <p>{@link SakarSerialNumberService} is mocked here — its own format/
 * uniqueness/concurrency-safety guarantees are this class's own
 * responsibility and are proven against a real database in {@code
 * SakarSerialNumberServiceTest} instead. What THIS class proves is that
 * {@link KeenonRobotSyncService} calls it (never Keenon's own {@code
 * mftCode}) for a new robot's {@code serialNumber}, and preserves {@code
 * mftCode} separately as {@code vendorSerialNumber}.
 */
@ExtendWith(MockitoExtension.class)
class KeenonRobotSyncServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();

    @Mock
    private KeenonApiClient client;
    @Mock
    private RobotRepository robotRepository;
    @Mock
    private RobotModelRepository robotModelRepository;
    @Mock
    private RobotManufacturerRepository robotManufacturerRepository;
    @Mock
    private RobotService robotService;
    @Mock
    private OrganizationService organizationService;
    @Mock
    private SakarSerialNumberService sakarSerialNumberService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KeenonRobotSyncService service() {
        return new KeenonRobotSyncService(client, robotRepository, robotModelRepository,
                robotManufacturerRepository, robotService, organizationService, sakarSerialNumberService);
    }

    private Organization sakarRootOrg() {
        Organization org = new Organization();
        org.setId(ORG_ID);
        org.setOrgType(OrganizationType.SAKAR_ROOT);
        return org;
    }

    private void stubSakarRootOrg() {
        when(organizationService.getOrThrow(ORG_ID)).thenReturn(sakarRootOrg());
    }

    private void stubExistingManufacturer(UUID manufacturerId) {
        RobotManufacturer manufacturer = new RobotManufacturer("Keenon");
        manufacturer.setId(manufacturerId);
        when(robotManufacturerRepository.findByNameIgnoreCase("Keenon")).thenReturn(Optional.of(manufacturer));
    }

    private void stubNewModelIsCreated(UUID manufacturerId, String modelName, UUID modelId) {
        when(robotModelRepository.findByManufacturerIdAndName(manufacturerId, modelName)).thenReturn(Optional.empty());
        when(robotModelRepository.save(any())).thenAnswer(inv -> {
            RobotModel model = inv.getArgument(0);
            model.setId(modelId);
            return model;
        });
    }

    private JsonNode envelope(String dataJsonArray) throws Exception {
        return objectMapper.readTree("{\"code\":610000,\"msg\":\"Request successful\",\"data\":" + dataJsonArray + "}");
    }

    // 1 + 7: discovery returns multiple robots, all handled
    @Test
    void sync_multipleVendorRobots_allDiscoveredAndCounted() throws Exception {
        stubSakarRootOrg();
        UUID manufacturerId = UUID.randomUUID();
        stubExistingManufacturer(manufacturerId);
        stubNewModelIsCreated(manufacturerId, "C40 S", UUID.randomUUID());
        when(robotRepository.findByExternalRobotId(anyString())).thenReturn(Optional.empty());
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn("SR-CB-2026-000001", "SR-CB-2026-000002");
        when(client.getRobotList("C00715655")).thenReturn(envelope(
                "[{\"robotId\":\"94:BA:06:CA:9A:23\",\"robotCode\":\"a\",\"mftCode\":\"QC1\",\"robotName\":\"Robot A\","
                        + "\"onlineStatus\":0,\"power\":71,\"robotModel\":\"C40 S\"},"
                        + "{\"robotId\":\"94:BA:06:CA:9A:05\",\"robotCode\":\"b\",\"mftCode\":\"QC2\",\"robotName\":\"Robot B\","
                        + "\"onlineStatus\":0,\"power\":17,\"robotModel\":\"C40 S\"}]"));

        KeenonRobotSyncResult result = service().sync(ORG_ID, "C00715655");

        assertThat(result.discovered()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        verify(robotService, times(2)).register(eq(ORG_ID), isNull(), any(), anyString(), anyString(), anyString(), anyString());
    }

    // 1 + 6: new robot receives a Sakar-generated serial; mftCode preserved separately, never as the Sakar serial
    @Test
    void sync_unknownVendorRobot_createsSakarRobot_withSakarSerial_mftCodePreservedSeparately() throws Exception {
        stubSakarRootOrg();
        UUID manufacturerId = UUID.randomUUID();
        stubExistingManufacturer(manufacturerId);
        UUID modelId = UUID.randomUUID();
        stubNewModelIsCreated(manufacturerId, "C40 S", modelId);
        when(robotRepository.findByExternalRobotId("94:BA:06:CA:99:F3")).thenReturn(Optional.empty());
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn("SR-CB-2026-000001");
        when(client.getRobotList("C00715655")).thenReturn(envelope(
                "[{\"robotId\":\"94:BA:06:CA:99:F3\",\"mftCode\":\"QC402602X00002\",\"robotName\":\"Demo Piece\","
                        + "\"robotModel\":\"C40 S\",\"onlineStatus\":1}]"));

        KeenonRobotSyncResult result = service().sync(ORG_ID, "C00715655");

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.unchanged()).isZero();
        // serialNumber = the Sakar-generated value; externalRobotId unchanged; vendorSerialNumber = mftCode,
        // never the other way around.
        verify(robotService).register(ORG_ID, null, modelId, "Demo Piece", "SR-CB-2026-000001",
                "94:BA:06:CA:99:F3", "QC402602X00002");
    }

    // 3: two newly created robots receive different Sakar serials
    @Test
    void sync_twoNewRobots_receiveDifferentSakarSerials() throws Exception {
        stubSakarRootOrg();
        UUID manufacturerId = UUID.randomUUID();
        stubExistingManufacturer(manufacturerId);
        stubNewModelIsCreated(manufacturerId, "C40 S", UUID.randomUUID());
        when(robotRepository.findByExternalRobotId(anyString())).thenReturn(Optional.empty());
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn("SR-CB-2026-000001", "SR-CB-2026-000002");
        when(client.getRobotList("C00715655")).thenReturn(envelope(
                "[{\"robotId\":\"94:BA:06:CA:9A:23\",\"mftCode\":\"QC1\",\"robotName\":\"A\",\"robotModel\":\"C40 S\"},"
                        + "{\"robotId\":\"94:BA:06:CA:9A:05\",\"mftCode\":\"QC2\",\"robotName\":\"B\",\"robotModel\":\"C40 S\"}]"));

        service().sync(ORG_ID, "C00715655");

        ArgumentCaptor<String> serialCaptor = ArgumentCaptor.forClass(String.class);
        verify(robotService, times(2)).register(eq(ORG_ID), isNull(), any(), anyString(), serialCaptor.capture(),
                anyString(), anyString());
        assertThat(serialCaptor.getAllValues()).containsExactly("SR-CB-2026-000001", "SR-CB-2026-000002");
        assertThat(serialCaptor.getAllValues()).doesNotHaveDuplicates();
    }

    // 4 + 5 + 11: second sync with the same data does NOT create duplicates and keeps the existing Sakar serial
    @Test
    void sync_repeatedSyncWithSameData_doesNotCreateDuplicateRobots_keepsExistingSerial() throws Exception {
        stubSakarRootOrg();
        UUID manufacturerId = UUID.randomUUID();
        stubExistingManufacturer(manufacturerId);
        UUID modelId = UUID.randomUUID();
        when(robotModelRepository.findByManufacturerIdAndName(manufacturerId, "C40 S")).thenReturn(Optional.of(existingModel(modelId, manufacturerId)));
        when(client.getRobotList("C00715655")).thenReturn(envelope(
                "[{\"robotId\":\"94:BA:06:CA:99:F3\",\"mftCode\":\"QC402602X00002\",\"robotName\":\"Demo Piece\","
                        + "\"robotModel\":\"C40 S\",\"onlineStatus\":1}]"));

        // vendorSerialNumber already matches the mftCode in the response, and the model already
        // matches — nothing at all should change on this sync.
        Robot alreadyRegistered = existingRobot(modelId, "SR-CB-2026-000005", "QC402602X00002");
        when(robotRepository.findByExternalRobotId("94:BA:06:CA:99:F3")).thenReturn(Optional.of(alreadyRegistered));

        KeenonRobotSyncResult result = service().sync(ORG_ID, "C00715655");

        assertThat(result.created()).isZero();
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(alreadyRegistered.getSerialNumber()).isEqualTo("SR-CB-2026-000005");
        verify(robotService, never()).register(any(), any(), any(), anyString(), anyString(), anyString(), anyString());
        verify(robotRepository, never()).save(any());
        verifyNoInteractions(sakarSerialNumberService);
    }

    // 4 + 5 + 12: existing robot is updated (model + mftCode changed), Sakar UUID and Sakar serial preserved
    @Test
    void sync_existingRobotWithChangedModelAndMftCode_updatesOnlyThoseFields_preservesSakarIdAndSerial() throws Exception {
        stubSakarRootOrg();
        UUID manufacturerId = UUID.randomUUID();
        stubExistingManufacturer(manufacturerId);
        UUID oldModelId = UUID.randomUUID();
        UUID newModelId = UUID.randomUUID();
        when(robotModelRepository.findByManufacturerIdAndName(manufacturerId, "S100")).thenReturn(Optional.of(existingModel(newModelId, manufacturerId)));
        when(client.getRobotList("C00715655")).thenReturn(envelope(
                "[{\"robotId\":\"88:49:2D:5F:29:55\",\"mftCode\":\"QS1\",\"robotName\":\"Delivery Bot\",\"robotModel\":\"S100\"}]"));

        Robot existing = existingRobot(oldModelId, "SR-CB-2026-000009", "OLD-MFT-CODE");
        UUID sakarRobotId = existing.getId();
        when(robotRepository.findByExternalRobotId("88:49:2D:5F:29:55")).thenReturn(Optional.of(existing));
        when(robotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KeenonRobotSyncResult result = service().sync(ORG_ID, "C00715655");

        assertThat(result.updated()).isEqualTo(1);
        ArgumentCaptor<Robot> savedCaptor = ArgumentCaptor.forClass(Robot.class);
        verify(robotRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getId()).isEqualTo(sakarRobotId);
        assertThat(savedCaptor.getValue().getSerialNumber()).isEqualTo("SR-CB-2026-000009");
        assertThat(savedCaptor.getValue().getRobotModelId()).isEqualTo(newModelId);
        assertThat(savedCaptor.getValue().getVendorSerialNumber()).isEqualTo("QS1");
        verify(robotService, never()).register(any(), any(), any(), anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(sakarSerialNumberService);
    }

    // 7: identity uses Keenon robotId (robotSn), never the robot name
    @Test
    void sync_looksUpByVendorRobotId_notByRobotName() throws Exception {
        stubSakarRootOrg();
        UUID manufacturerId = UUID.randomUUID();
        stubExistingManufacturer(manufacturerId);
        stubNewModelIsCreated(manufacturerId, "C40 S", UUID.randomUUID());
        when(robotRepository.findByExternalRobotId(anyString())).thenReturn(Optional.empty());
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn("SR-CB-2026-000001");
        when(client.getRobotList("C00715655")).thenReturn(envelope(
                "[{\"robotId\":\"94:BA:06:CA:99:F3\",\"mftCode\":\"QC1\",\"robotName\":\"Totally Different Display Name\","
                        + "\"robotModel\":\"C40 S\"}]"));

        service().sync(ORG_ID, "C00715655");

        verify(robotRepository).findByExternalRobotId("94:BA:06:CA:99:F3");
        verify(robotRepository, never()).findByExternalRobotId("Totally Different Display Name");
        verify(robotService).register(eq(ORG_ID), isNull(), any(), eq("Totally Different Display Name"),
                eq("SR-CB-2026-000001"), eq("94:BA:06:CA:99:F3"), eq("QC1"));
    }

    // 13: offline robot still receives a Sakar serial when first registered (onlineStatus never used to skip/deactivate)
    @Test
    void sync_offlineVendorRobot_isStillCreated_withASakarSerial() throws Exception {
        stubSakarRootOrg();
        UUID manufacturerId = UUID.randomUUID();
        stubExistingManufacturer(manufacturerId);
        stubNewModelIsCreated(manufacturerId, "W3", UUID.randomUUID());
        when(robotRepository.findByExternalRobotId(anyString())).thenReturn(Optional.empty());
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn("SR-CB-2026-000003");
        when(client.getRobotList("C00715655")).thenReturn(envelope(
                "[{\"robotId\":\"A8:B5:8E:B5:E3:E7\",\"mftCode\":\"KRW1\",\"robotName\":null,"
                        + "\"onlineStatus\":0,\"robotModel\":\"W3\"}]"));

        KeenonRobotSyncResult result = service().sync(ORG_ID, "C00715655");

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        // Vendor gave no robotName — falls back to the vendor id itself, never invented.
        verify(robotService).register(eq(ORG_ID), isNull(), any(), eq("A8:B5:8E:B5:E3:E7"),
                eq("SR-CB-2026-000003"), eq("A8:B5:8E:B5:E3:E7"), eq("KRW1"));
    }

    // 12: model records are not duplicated across multiple robots sharing one model
    @Test
    void sync_twoRobotsSharingOneNewModel_createsTheModelOnlyOnce() throws Exception {
        stubSakarRootOrg();
        UUID manufacturerId = UUID.randomUUID();
        stubExistingManufacturer(manufacturerId);
        UUID modelId = UUID.randomUUID();
        RobotModel persisted = existingModel(modelId, manufacturerId);
        when(robotModelRepository.findByManufacturerIdAndName(manufacturerId, "C40 S"))
                .thenReturn(Optional.empty(), Optional.of(persisted));
        when(robotModelRepository.save(any())).thenAnswer(inv -> {
            RobotModel model = inv.getArgument(0);
            model.setId(modelId);
            return model;
        });
        when(robotRepository.findByExternalRobotId(anyString())).thenReturn(Optional.empty());
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn("SR-CB-2026-000001", "SR-CB-2026-000002");
        when(client.getRobotList("C00715655")).thenReturn(envelope(
                "[{\"robotId\":\"94:BA:06:CA:9A:23\",\"mftCode\":\"QC1\",\"robotName\":\"A\",\"robotModel\":\"C40 S\"},"
                        + "{\"robotId\":\"94:BA:06:CA:9A:05\",\"mftCode\":\"QC2\",\"robotName\":\"B\",\"robotModel\":\"C40 S\"}]"));

        KeenonRobotSyncResult result = service().sync(ORG_ID, "C00715655");

        assertThat(result.created()).isEqualTo(2);
        verify(robotModelRepository, times(1)).save(any());
    }

    // 10: tenant isolation — a non-SAKAR_ROOT organization is rejected before any vendor call
    @Test
    void sync_nonSakarRootOrganization_rejectedBeforeAnyVendorCall() {
        Organization directClient = new Organization();
        directClient.setId(ORG_ID);
        directClient.setOrgType(OrganizationType.DIRECT_CLIENT);
        when(organizationService.getOrThrow(ORG_ID)).thenReturn(directClient);

        assertThatThrownBy(() -> service().sync(ORG_ID, "C00715655"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.EXTERNAL_ROBOT_ID_NOT_ALLOWED));
        verifyNoInteractions(client);
        verifyNoInteractions(robotRepository);
        verifyNoInteractions(sakarSerialNumberService);
    }

    // a Keenon API error is propagated, not swallowed into a partial/failed count
    @Test
    void sync_vendorApiThrows_propagatesWithoutMutatingAnything() {
        stubSakarRootOrg();
        when(client.getRobotList("C00715655"))
                .thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        assertThatThrownBy(() -> service().sync(ORG_ID, "C00715655"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.VENDOR_API_ERROR));
        verifyNoInteractions(robotRepository);
        verifyNoInteractions(robotService);
        verifyNoInteractions(sakarSerialNumberService);
    }

    // empty Keenon robot list
    @Test
    void sync_emptyVendorRobotList_returnsAllZeroCounts_neverThrows() throws Exception {
        stubSakarRootOrg();
        when(client.getRobotList("C00715655")).thenReturn(envelope("[]"));

        KeenonRobotSyncResult result = service().sync(ORG_ID, "C00715655");

        assertThat(result.discovered()).isZero();
        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.unchanged()).isZero();
        assertThat(result.failed()).isZero();
        verifyNoInteractions(robotRepository);
        verifyNoInteractions(robotService);
    }

    // missing "data" field entirely is treated as empty, never throws
    @Test
    void sync_missingDataField_isTreatedAsEmpty_neverThrows() throws Exception {
        stubSakarRootOrg();
        when(client.getRobotList("C00715655")).thenReturn(objectMapper.readTree("{\"code\":610000,\"msg\":\"ok\"}"));

        KeenonRobotSyncResult result = service().sync(ORG_ID, "C00715655");

        assertThat(result.discovered()).isZero();
        verifyNoInteractions(robotRepository);
    }

    // malformed/missing stable robot identity (no robotId) is handled safely
    @Test
    void sync_vendorRecordMissingRobotId_isSkippedAsFailure_neverFabricated() throws Exception {
        stubSakarRootOrg();
        when(client.getRobotList("C00715655")).thenReturn(envelope(
                "[{\"mftCode\":\"QC1\",\"robotName\":\"No id here\",\"robotModel\":\"C40 S\"}]"));

        KeenonRobotSyncResult result = service().sync(ORG_ID, "C00715655");

        assertThat(result.discovered()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(result.failures().get(0).vendorRobotId()).isNull();
        verifyNoInteractions(robotRepository);
        verifyNoInteractions(robotService);
    }

    // 8: missing mftCode does NOT prevent Sakar serial generation / registration; vendorSerialNumber is simply null
    @Test
    void sync_newVendorRobotMissingMftCode_stillReceivesASakarSerial_vendorSerialNumberIsNull() throws Exception {
        stubSakarRootOrg();
        UUID manufacturerId = UUID.randomUUID();
        stubExistingManufacturer(manufacturerId);
        UUID modelId = UUID.randomUUID();
        stubNewModelIsCreated(manufacturerId, "C40 S", modelId);
        when(robotRepository.findByExternalRobotId("94:BA:06:CA:99:F3")).thenReturn(Optional.empty());
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn("SR-CB-2026-000007");
        when(client.getRobotList("C00715655")).thenReturn(envelope(
                "[{\"robotId\":\"94:BA:06:CA:99:F3\",\"robotName\":\"Demo Piece\",\"robotModel\":\"C40 S\"}]"));

        KeenonRobotSyncResult result = service().sync(ORG_ID, "C00715655");

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(robotService).register(ORG_ID, null, modelId, "Demo Piece", "SR-CB-2026-000007",
                "94:BA:06:CA:99:F3", null);
    }

    // Missing robotModel on a new robot: never invent a model
    @Test
    void sync_newVendorRobotMissingRobotModel_isCountedAsFailure() throws Exception {
        stubSakarRootOrg();
        when(robotRepository.findByExternalRobotId("94:BA:06:CA:99:F3")).thenReturn(Optional.empty());
        when(client.getRobotList("C00715655")).thenReturn(envelope(
                "[{\"robotId\":\"94:BA:06:CA:99:F3\",\"mftCode\":\"QC1\",\"robotName\":\"Demo Piece\"}]"));

        KeenonRobotSyncResult result = service().sync(ORG_ID, "C00715655");

        assertThat(result.failed()).isEqualTo(1);
        verifyNoInteractions(robotService);
        verifyNoInteractions(sakarSerialNumberService);
    }

    // One malformed robot (still missing robotModel — genuinely un-registerable) must not abort sync of the rest
    @Test
    void sync_oneFailingRobot_doesNotAbortSyncOfOthers() throws Exception {
        stubSakarRootOrg();
        UUID manufacturerId = UUID.randomUUID();
        stubExistingManufacturer(manufacturerId);
        stubNewModelIsCreated(manufacturerId, "C40 S", UUID.randomUUID());
        when(robotRepository.findByExternalRobotId(anyString())).thenReturn(Optional.empty());
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn("SR-CB-2026-000001");
        when(client.getRobotList("C00715655")).thenReturn(envelope(
                "[{\"robotId\":\"bad-one\",\"robotName\":\"No model here\",\"mftCode\":\"QCX\"},"
                        + "{\"robotId\":\"94:BA:06:CA:99:F3\",\"mftCode\":\"QC1\",\"robotName\":\"Good one\",\"robotModel\":\"C40 S\"}]"));

        KeenonRobotSyncResult result = service().sync(ORG_ID, "C00715655");

        assertThat(result.discovered()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        verify(robotService).register(eq(ORG_ID), isNull(), any(), eq("Good one"), eq("SR-CB-2026-000001"),
                eq("94:BA:06:CA:99:F3"), eq("QC1"));
    }

    // Requirement 5: a model with no configured Sakar serial prefix fails that one robot safely
    // (never aborts the rest of the batch) — proves KeenonRobotSyncService propagates
    // SakarSerialNumberService's own fail-safe rather than swallowing or working around it.
    @Test
    void sync_modelWithNoConfiguredSerialPrefix_failsThatRobotOnly_doesNotAbortTheBatch() throws Exception {
        stubSakarRootOrg();
        UUID manufacturerId = UUID.randomUUID();
        stubExistingManufacturer(manufacturerId);
        UUID unsupportedModelId = UUID.randomUUID();
        UUID supportedModelId = UUID.randomUUID();
        when(robotModelRepository.findByManufacturerIdAndName(manufacturerId, "Mystery Model"))
                .thenReturn(Optional.of(existingModel(unsupportedModelId, manufacturerId)));
        when(robotModelRepository.findByManufacturerIdAndName(manufacturerId, "C40 S"))
                .thenReturn(Optional.of(existingModel(supportedModelId, manufacturerId)));
        when(robotRepository.findByExternalRobotId(anyString())).thenReturn(Optional.empty());
        when(sakarSerialNumberService.nextSerialNumber(any()))
                .thenThrow(new ApiException(SakarErrorCode.UNSUPPORTED_ROBOT_MODEL_SERIAL_PREFIX,
                        "Robot model 'Mystery Model' has no configured Sakar serial prefix"))
                .thenReturn("SR-CB-2026-000001");
        when(client.getRobotList("C00715655")).thenReturn(envelope(
                "[{\"robotId\":\"unsupported-1\",\"mftCode\":\"MX1\",\"robotName\":\"Unsupported\",\"robotModel\":\"Mystery Model\"},"
                        + "{\"robotId\":\"94:BA:06:CA:99:F3\",\"mftCode\":\"QC1\",\"robotName\":\"Good one\",\"robotModel\":\"C40 S\"}]"));

        KeenonRobotSyncResult result = service().sync(ORG_ID, "C00715655");

        assertThat(result.discovered()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failures().get(0).vendorRobotId()).isEqualTo("unsupported-1");
        assertThat(result.failures().get(0).reason()).contains("no configured Sakar serial prefix");
        verify(robotService).register(eq(ORG_ID), isNull(), eq(supportedModelId), eq("Good one"),
                eq("SR-CB-2026-000001"), eq("94:BA:06:CA:99:F3"), eq("QC1"));
    }

    // Wiring proof: the exact resolved RobotModel (carrying whatever serialPrefix it has) is
    // what is handed to SakarSerialNumberService — never a raw model name/id substitute.
    @Test
    void sync_newRobot_passesTheResolvedRobotModelItselfToSerialGeneration() throws Exception {
        stubSakarRootOrg();
        UUID manufacturerId = UUID.randomUUID();
        stubExistingManufacturer(manufacturerId);
        UUID modelId = UUID.randomUUID();
        RobotModel persistedModel = existingModel(modelId, manufacturerId);
        persistedModel.setName("C40 S");
        persistedModel.setSerialPrefix("CB");
        when(robotModelRepository.findByManufacturerIdAndName(manufacturerId, "C40 S")).thenReturn(Optional.of(persistedModel));
        when(robotRepository.findByExternalRobotId(anyString())).thenReturn(Optional.empty());
        when(sakarSerialNumberService.nextSerialNumber(any())).thenReturn("SR-CB-2026-000001");
        when(client.getRobotList("C00715655")).thenReturn(envelope(
                "[{\"robotId\":\"94:BA:06:CA:99:F3\",\"mftCode\":\"QC1\",\"robotName\":\"Demo Piece\",\"robotModel\":\"C40 S\"}]"));

        service().sync(ORG_ID, "C00715655");

        ArgumentCaptor<RobotModel> modelCaptor = ArgumentCaptor.forClass(RobotModel.class);
        verify(sakarSerialNumberService).nextSerialNumber(modelCaptor.capture());
        assertThat(modelCaptor.getValue().getId()).isEqualTo(modelId);
        assertThat(modelCaptor.getValue().getSerialPrefix()).isEqualTo("CB");
    }

    private static RobotModel existingModel(UUID id, UUID manufacturerId) {
        RobotModel model = new RobotModel();
        model.setId(id);
        model.setManufacturerId(manufacturerId);
        return model;
    }

    private static Robot existingRobot(UUID robotModelId, String sakarSerialNumber, String vendorSerialNumber) {
        Robot robot = new Robot();
        robot.setId(UUID.randomUUID());
        robot.setOrganizationId(ORG_ID);
        robot.setRobotModelId(robotModelId);
        robot.setName("Existing Name");
        robot.setSerialNumber(sakarSerialNumber);
        robot.setVendorSerialNumber(vendorSerialNumber);
        robot.setExternalRobotId("94:BA:06:CA:99:F3");
        return robot;
    }
}

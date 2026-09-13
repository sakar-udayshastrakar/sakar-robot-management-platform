package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.Role;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.IntegrationPath;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturer;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturerRepository;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;

/**
 * Keenon robot-discovery sync slice — full Spring context, real {@link
 * KeenonRobotSyncService}/{@code RobotService}/tenant guard, with only
 * {@link KeenonApiClient} (the one real network boundary) replaced by a
 * mock — no real Keenon HTTP call is possible from this test.
 */
class KeenonRobotSyncControllerTest extends IntegrationTestSupport {

    @Autowired
    private RobotRepository robotRepository;
    @Autowired
    private RobotModelRepository robotModelRepository;
    @Autowired
    private RobotManufacturerRepository robotManufacturerRepository;
    @MockitoBean
    private KeenonApiClient keenonApiClient;

    private String vendorRobotListJson(String robotId, String mftCode, String robotName, String robotModel) {
        return "{\"code\":610000,\"msg\":\"Request successful\",\"data\":[{\"robotId\":\"" + robotId
                + "\",\"robotCode\":\"code-1\",\"mftCode\":\"" + mftCode + "\",\"robotName\":"
                + (robotName == null ? "null" : "\"" + robotName + "\"")
                + ",\"onlineStatus\":1,\"power\":100,\"robotModel\":\"" + robotModel + "\"}]}";
    }

    /**
     * Pre-seeds a Keenon-manufacturer robot model with a configured Sakar serial prefix — the
     * production migration (V18) does this for the real "C40 S"/"W3"/"S100" models, but that
     * migration never runs in tests (Flyway disabled, H2 schema generated from JPA entities), so
     * each test that expects a robot to be successfully CREATED must seed its own model here
     * first; a model discovered fresh by the sync itself always has a null prefix by design (see
     * Requirement 5 — never invented).
     */
    private RobotModel preSeedKeenonModel(String modelName, String serialPrefix) {
        RobotManufacturer manufacturer = robotManufacturerRepository.findByNameIgnoreCase("Keenon")
                .orElseGet(() -> robotManufacturerRepository.save(new RobotManufacturer("Keenon")));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName(modelName);
        model.setSerialPrefix(serialPrefix);
        model.setAdapterType(AdapterType.KEENON_CLOUD);
        model.setIntegrationPath(IntegrationPath.KEENON_CLOUD_DEPENDENT);
        return robotModelRepository.save(model);
    }

    @Test
    void authorizedUser_syncsRobotsIntoOwnSakarRootOrganization_createsSakarRobot() throws Exception {
        Role superAdmin = ensureRole(RoleName.SUPER_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization sakarRoot = createOrganization("Sakar Robotics " + UUID.randomUUID(), OrganizationType.SAKAR_ROOT, null);
        String email = "root-admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", superAdmin, sakarRoot.getId());
        String token = login(email, "Password1!");

        String vendorRobotId = "TEST-" + UUID.randomUUID();
        String mftCode = "MFT-" + UUID.randomUUID();
        String modelName = "TestC40S-" + UUID.randomUUID();
        preSeedKeenonModel(modelName, "CB");
        when(keenonApiClient.getRobotList("C00715655")).thenReturn(objectMapper.readTree(
                vendorRobotListJson(vendorRobotId, mftCode, "Demo Piece", modelName)));

        mockMvc.perform(post("/api/v1/keenon/robots/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\",\"organizationId\":\"" + sakarRoot.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discovered").value(1))
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.failed").value(0));

        Optional<Robot> saved = robotRepository.findByExternalRobotId(vendorRobotId);
        assertThat(saved).isPresent();
        // serialNumber is Sakar's own generated identity (SR-CB-YYYY-NNNNNN) — never the vendor's
        // mftCode, which is preserved separately as vendorSerialNumber.
        assertThat(saved.get().getSerialNumber()).matches("SR-CB-\\d{4}-\\d{6}");
        assertThat(saved.get().getSerialNumber()).isNotEqualTo(mftCode);
        assertThat(saved.get().getVendorSerialNumber()).isEqualTo(mftCode);
        assertThat(saved.get().getOrganizationId()).isEqualTo(sakarRoot.getId());
        assertThat(robotModelRepository.findAll()).anyMatch(m -> m.getName().equals(modelName));
        assertThat(robotManufacturerRepository.findByNameIgnoreCase("Keenon")).isPresent();
    }

    @Test
    void twoNewVendorRobots_receiveDifferentSakarSerials() throws Exception {
        Role superAdmin = ensureRole(RoleName.SUPER_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization sakarRoot = createOrganization("Sakar Robotics " + UUID.randomUUID(), OrganizationType.SAKAR_ROOT, null);
        String email = "root-admin-twoserials-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", superAdmin, sakarRoot.getId());
        String token = login(email, "Password1!");

        String robotIdA = "TEST-" + UUID.randomUUID();
        String robotIdB = "TEST-" + UUID.randomUUID();
        String modelName = "TestC40S-" + UUID.randomUUID();
        preSeedKeenonModel(modelName, "CB");
        when(keenonApiClient.getRobotList("C00715655")).thenReturn(objectMapper.readTree(
                "{\"code\":610000,\"msg\":\"ok\",\"data\":["
                        + "{\"robotId\":\"" + robotIdA + "\",\"mftCode\":\"MFT-A\",\"robotName\":\"A\",\"robotModel\":\"" + modelName + "\"},"
                        + "{\"robotId\":\"" + robotIdB + "\",\"mftCode\":\"MFT-B\",\"robotName\":\"B\",\"robotModel\":\"" + modelName + "\"}]}"));

        mockMvc.perform(post("/api/v1/keenon/robots/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\",\"organizationId\":\"" + sakarRoot.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(2));

        String serialA = robotRepository.findByExternalRobotId(robotIdA).orElseThrow().getSerialNumber();
        String serialB = robotRepository.findByExternalRobotId(robotIdB).orElseThrow().getSerialNumber();
        assertThat(serialA).matches("SR-CB-\\d{4}-\\d{6}");
        assertThat(serialB).matches("SR-CB-\\d{4}-\\d{6}");
        assertThat(serialA).isNotEqualTo(serialB);
    }

    @Test
    void repeatedSync_isIdempotent_doesNotCreateDuplicateRobotsOrModels() throws Exception {
        Role superAdmin = ensureRole(RoleName.SUPER_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization sakarRoot = createOrganization("Sakar Robotics " + UUID.randomUUID(), OrganizationType.SAKAR_ROOT, null);
        String email = "root-admin-repeat-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", superAdmin, sakarRoot.getId());
        String token = login(email, "Password1!");

        String vendorRobotId = "TEST-" + UUID.randomUUID();
        String vendorModel = "TestModel-" + UUID.randomUUID();
        preSeedKeenonModel(vendorModel, "CB");
        when(keenonApiClient.getRobotList("C00715655")).thenReturn(objectMapper.readTree(
                vendorRobotListJson(vendorRobotId, "QC1", "Gujrat Robot", vendorModel)));

        String body = "{\"storeId\":\"C00715655\",\"organizationId\":\"" + sakarRoot.getId() + "\"}";
        mockMvc.perform(post("/api/v1/keenon/robots/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(1));
        String serialAfterFirstSync = robotRepository.findByExternalRobotId(vendorRobotId).orElseThrow().getSerialNumber();

        mockMvc.perform(post("/api/v1/keenon/robots/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.unchanged").value(1));

        assertThat(robotRepository.findAll()).filteredOn(r -> vendorRobotId.equals(r.getExternalRobotId())).hasSize(1);
        assertThat(robotModelRepository.findAll()).filteredOn(m -> vendorModel.equals(m.getName())).hasSize(1);
        // The repeated sync must not regenerate a new Sakar serial for the already-registered robot.
        String serialAfterSecondSync = robotRepository.findByExternalRobotId(vendorRobotId).orElseThrow().getSerialNumber();
        assertThat(serialAfterSecondSync).isEqualTo(serialAfterFirstSync);
    }

    @Test
    void nonSakarRootOrganization_returnsExternalRobotIdNotAllowed_neverCallsVendor() throws Exception {
        Role superAdmin = ensureRole(RoleName.SUPER_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization sakarRoot = createOrganization("Sakar Robotics " + UUID.randomUUID(), OrganizationType.SAKAR_ROOT, null);
        Organization distributor = createOrganization("Distributor " + UUID.randomUUID(), OrganizationType.DISTRIBUTOR, sakarRoot.getId());
        String email = "root-admin-nonroot-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", superAdmin, sakarRoot.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(post("/api/v1/keenon/robots/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\",\"organizationId\":\"" + distributor.getId() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_ROBOT_ID_NOT_ALLOWED"));
        verifyNoInteractions(keenonApiClient);
    }

    @Test
    void crossOrganizationCaller_returnsTenantAccessDenied_neverCallsVendor() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization sakarRoot = createOrganization("Sakar Robotics " + UUID.randomUUID(), OrganizationType.SAKAR_ROOT, null);
        Organization otherOrg = createOrganization("Other Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "other-org-admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, otherOrg.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(post("/api/v1/keenon/robots/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\",\"organizationId\":\"" + sakarRoot.getId() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TENANT_ACCESS_DENIED"));
        verifyNoInteractions(keenonApiClient);
    }

    @Test
    void missingRobotConfigurePermission_returnsForbidden() throws Exception {
        Role viewOnly = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        Organization sakarRoot = createOrganization("Sakar Robotics " + UUID.randomUUID(), OrganizationType.SAKAR_ROOT, null);
        String email = "view-only-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewOnly, sakarRoot.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(post("/api/v1/keenon/robots/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\",\"organizationId\":\"" + sakarRoot.getId() + "\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(keenonApiClient);
    }

    @Test
    void vendorApiFailure_returnsVendorApiError_createsNoRobot() throws Exception {
        Role superAdmin = ensureRole(RoleName.SUPER_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization sakarRoot = createOrganization("Sakar Robotics " + UUID.randomUUID(), OrganizationType.SAKAR_ROOT, null);
        String email = "root-admin-vendorfail-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", superAdmin, sakarRoot.getId());
        String token = login(email, "Password1!");

        when(keenonApiClient.getRobotList(anyString()))
                .thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        mockMvc.perform(post("/api/v1/keenon/robots/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\",\"organizationId\":\"" + sakarRoot.getId() + "\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("VENDOR_API_ERROR"));

        assertThat(robotRepository.findAll()).noneMatch(r -> r.getOrganizationId().equals(sakarRoot.getId()));
    }

    @Test
    void unknownModelWithNoConfiguredSerialPrefix_failsThatRobotOnly_neverInventsAPrefix() throws Exception {
        Role superAdmin = ensureRole(RoleName.SUPER_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization sakarRoot = createOrganization("Sakar Robotics " + UUID.randomUUID(), OrganizationType.SAKAR_ROOT, null);
        String email = "root-admin-unsupportedmodel-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", superAdmin, sakarRoot.getId());
        String token = login(email, "Password1!");

        // Deliberately NOT pre-seeded with a prefix — a genuinely brand-new Keenon model, exactly
        // as the sync's own resolveModel() would create one on first discovery.
        String vendorRobotId = "TEST-" + UUID.randomUUID();
        String unknownModel = "UnknownModel-" + UUID.randomUUID();
        when(keenonApiClient.getRobotList("C00715655")).thenReturn(objectMapper.readTree(
                vendorRobotListJson(vendorRobotId, "MFT-X", "Mystery Robot", unknownModel)));

        String response = mockMvc.perform(post("/api/v1/keenon/robots/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\",\"organizationId\":\"" + sakarRoot.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.failures[0].vendorRobotId").value(vendorRobotId))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).contains("no configured Sakar serial prefix");

        assertThat(robotRepository.findByExternalRobotId(vendorRobotId)).isEmpty();
        // The model row itself IS created (discovery still records it) but with no prefix —
        // never an invented one.
        assertThat(robotModelRepository.findAll()).anyMatch(m -> unknownModel.equals(m.getName()) && m.getSerialPrefix() == null);
    }
}

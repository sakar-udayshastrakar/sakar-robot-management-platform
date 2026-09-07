package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.sakarrobotics.cloud.robot.registry.RobotCapability;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityRepository;
import com.sakarrobotics.cloud.robot.registry.RobotCapabilityType;
import com.sakarrobotics.cloud.robot.registry.RobotLifecycleStatus;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturer;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturerRepository;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Keenon area-sync slice — full Spring context, real {@link
 * KeenonAreaSyncService}/{@link KeenonAreaMappingRepository}/tenant guard,
 * with only {@link KeenonApiClient} (the one real network boundary)
 * replaced by a mock — no real Keenon HTTP call is possible from this test.
 */
class KeenonAreaSyncControllerTest extends IntegrationTestSupport {

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotCapabilityRepository capabilityRepository;
    @Autowired
    private RobotRepository robotRepository;
    @Autowired
    private KeenonAreaMappingRepository areaMappingRepository;
    @MockitoBean
    private KeenonApiClient keenonApiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authorizedUser_syncsAreasForTheirOwnKeenonRobot_andGetAreasThenReturnsThem() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-sync-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModelWithGetAreas();
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F3");

        when(keenonApiClient.getAreaList("C00715655", "94:BA:06:CA:99:F3")).thenReturn(objectMapper.readTree(
                "{\"code\":610000,\"data\":{\"count\":1,\"currentPage\":1,\"pageSize\":100,\"entities\":"
                        + "[{\"mapId\":\"map-1\",\"floor\":1,\"areaIdList\":[\"area-1\",\"area-2\"],"
                        + "\"areaNameList\":[\"Lobby\",\"Conference Room\"]}]}}"));

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/areas/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].vendorAreaId").value("area-1"))
                .andExpect(jsonPath("$.data[0].displayName").value("Lobby"))
                .andExpect(jsonPath("$.data[0].sakarAreaId").isNotEmpty())
                .andExpect(jsonPath("$.data[1].vendorAreaId").value("area-2"));

        assertThat(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).hasSize(2);

        // Requirement: GET /robots/{id}/areas (existing, unmodified endpoint) now returns the
        // synced areas with a real sakarAreaId — same live vendor call, now backed by a real mapping.
        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/areas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].sakarAreaId").isNotEmpty());
    }

    @Test
    void repeatedSync_isIdempotent_doesNotCreateDuplicateActiveMappings() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-sync2-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModelWithGetAreas();
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F4");

        when(keenonApiClient.getAreaList("C00715655", "94:BA:06:CA:99:F4"))
                .thenReturn(objectMapper.readTree("{\"code\":610000,\"data\":{\"entities\":"
                        + "[{\"mapId\":\"map-1\",\"floor\":1,\"areaIdList\":[\"area-1\"],\"areaNameList\":[\"Lobby\"]}]}}"));

        String body = "{\"storeId\":\"C00715655\"}";
        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/areas/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/areas/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        assertThat(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).hasSize(1);
    }

    @Test
    void unknownRobotId_returnsRobotNotFound() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-sync-unknown-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(post("/api/v1/robots/" + UUID.randomUUID() + "/keenon/areas/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void nonKeenonRobot_returnsUnsupportedCapability() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-sync-native-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("Native-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Native Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);
        capabilityRepository.save(new RobotCapability(model.getId(), RobotCapabilityType.GET_AREAS, true));
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "SAKAR-SN-1");

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/areas/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_CAPABILITY"));
        verifyNoInteractions(keenonApiClient);
    }

    @Test
    void crossOrganizationRobot_returnsRobotNotFound_notForbidden() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "orgadmin-sync-a-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());
        String token = login(emailA, "Password1!");

        RobotModel model = keenonModelWithGetAreas();
        Robot robotInOrgB = registerKeenonRobot(orgB.getId(), model.getId(), "94:BA:06:CA:99:F5");

        mockMvc.perform(post("/api/v1/robots/" + robotInOrgB.getId() + "/keenon/areas/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
        verifyNoInteractions(keenonApiClient);
    }

    @Test
    void vendorApiFailure_returnsVendorApiError_andDoesNotMutateAnyMapping() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-sync-fail-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModelWithGetAreas();
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F6");
        when(keenonApiClient.getAreaList(anyString(), anyString()))
                .thenThrow(new ApiException(SakarErrorCode.VENDOR_API_ERROR, "Keenon Open Platform request failed"));

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/areas/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("VENDOR_API_ERROR"));

        assertThat(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).isEmpty();
    }

    @Test
    void emptyVendorAreaResponse_deactivatesPreviouslySyncedMappings() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-sync-empty-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModelWithGetAreas();
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F7");
        when(keenonApiClient.getAreaList("C00715655", "94:BA:06:CA:99:F7"))
                .thenReturn(objectMapper.readTree("{\"code\":610000,\"data\":{\"entities\":"
                        + "[{\"mapId\":\"map-1\",\"floor\":1,\"areaIdList\":[\"area-1\"],\"areaNameList\":[\"Lobby\"]}]}}"));
        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/areas/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"storeId\":\"C00715655\"}"))
                .andExpect(status().isOk());
        assertThat(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).hasSize(1);

        when(keenonApiClient.getAreaList("C00715655", "94:BA:06:CA:99:F7"))
                .thenReturn(objectMapper.readTree("{\"code\":610000,\"data\":{\"entities\":[]}}"));
        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/areas/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"storeId\":\"C00715655\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        assertThat(areaMappingRepository.findByRobotIdAndActiveTrue(robot.getId())).isEmpty();
    }

    private RobotModel keenonModelWithGetAreas() {
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("Keenon-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("C40 S");
        model.setAdapterType(AdapterType.KEENON_CLOUD);
        model.setIntegrationPath(IntegrationPath.KEENON_CLOUD_DEPENDENT);
        model = modelRepository.save(model);
        capabilityRepository.save(new RobotCapability(model.getId(), RobotCapabilityType.GET_AREAS, true));
        return model;
    }

    private Robot registerKeenonRobot(UUID organizationId, UUID modelId, String externalRobotId) {
        Robot robot = new Robot();
        robot.setOrganizationId(organizationId);
        robot.setRobotModelId(modelId);
        robot.setName("Robot " + UUID.randomUUID());
        robot.setSerialNumber("SN-" + UUID.randomUUID());
        robot.setExternalRobotId(externalRobotId);
        robot.setStatus(RobotLifecycleStatus.REGISTERED);
        return robotRepository.save(robot);
    }
}

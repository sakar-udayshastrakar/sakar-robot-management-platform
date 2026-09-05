package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.Role;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.map.RobotMapRepository;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;
import com.sakarrobotics.cloud.robot.registry.AdapterType;
import com.sakarrobotics.cloud.robot.registry.IntegrationPath;
import com.sakarrobotics.cloud.robot.registry.Robot;
import com.sakarrobotics.cloud.robot.registry.RobotLifecycleStatus;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturer;
import com.sakarrobotics.cloud.robot.registry.RobotManufacturerRepository;
import com.sakarrobotics.cloud.robot.registry.RobotModel;
import com.sakarrobotics.cloud.robot.registry.RobotModelRepository;
import com.sakarrobotics.cloud.robot.registry.RobotRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Keenon manual map-sync trigger (Phase 1I) — full Spring context, real
 * {@link KeenonMapImageSyncService}/{@link KeenonMapMetadataSyncService}/
 * {@link KeenonRobotSceneConfigRepository}/{@link RobotMapRepository}/tenant
 * guard, with only {@link KeenonApiClient} (the one real network boundary)
 * mocked.
 */
class KeenonMapSyncControllerTest extends IntegrationTestSupport {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotRepository robotRepository;
    @Autowired
    private KeenonRobotSceneConfigRepository sceneConfigRepository;
    @Autowired
    private RobotMapRepository robotMapRepository;
    @MockitoBean
    private KeenonApiClient keenonApiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void configuredScene_syncsMapMetadataAndImage() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-mapsync-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModel();
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F3");
        KeenonRobotSceneConfig config = new KeenonRobotSceneConfig();
        config.setRobotId(robot.getId());
        config.setSceneCode("7ClJPR");
        config.setSceneName("F");
        sceneConfigRepository.save(config);

        when(keenonApiClient.getMapPosition("7ClJPR", "1")).thenReturn(objectMapper.readTree(
                "{\"data\":{\"targetList\":[{\"name\":\"1_Charging pile2\",\"mapMd5\":\"a3cb0d75faa17c9ab12b9a6434173b42\"}]}}"));
        byte[] png = validPngBytes();
        when(keenonApiClient.getMapData("7ClJPR", "1"))
                .thenReturn(new KeenonMapDataResponse(Base64.getEncoder().encodeToString(png), 570, 763));

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/map/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorMapId").value("7ClJPR"))
                .andExpect(jsonPath("$.data.name").value("F"))
                .andExpect(jsonPath("$.data.width").value(570))
                .andExpect(jsonPath("$.data.height").value(763))
                .andExpect(jsonPath("$.data.mapMd5").value("a3cb0d75faa17c9ab12b9a6434173b42"));

        assertThat(robotMapRepository.findByRobotId(robot.getId())).isPresent();
        assertThat(robotMapRepository.findByRobotId(robot.getId()).orElseThrow().getVendorMapId()).isEqualTo("7ClJPR");
    }

    @Test
    void noConfiguredScene_returnsResourceNotFound_neverCallsVendor() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-mapsync-none-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModel();
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F4");

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/map/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        verifyNoInteractions(keenonApiClient);
    }

    @Test
    void unknownRobotId_returnsRobotNotFound() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-mapsync-unknown-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(post("/api/v1/robots/" + UUID.randomUUID() + "/keenon/map/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
        verifyNoInteractions(keenonApiClient);
    }

    @Test
    void nonKeenonRobot_returnsUnsupportedCapability() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-mapsync-native-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("Native-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Native Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "SAKAR-SN-1");

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/map/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_CAPABILITY"));
        verifyNoInteractions(keenonApiClient);
    }

    private static byte[] validPngBytes() {
        byte[] bytes = new byte[PNG_SIGNATURE.length + 1];
        System.arraycopy(PNG_SIGNATURE, 0, bytes, 0, PNG_SIGNATURE.length);
        bytes[PNG_SIGNATURE.length] = 1;
        return bytes;
    }

    private RobotModel keenonModel() {
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("Keenon-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("C40 S");
        model.setAdapterType(AdapterType.KEENON_CLOUD);
        model.setIntegrationPath(IntegrationPath.KEENON_CLOUD_DEPENDENT);
        return modelRepository.save(model);
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

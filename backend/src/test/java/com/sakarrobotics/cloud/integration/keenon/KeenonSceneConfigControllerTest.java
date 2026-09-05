package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.Role;
import com.sakarrobotics.cloud.iam.RoleName;
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

/**
 * Keenon scene-config slice (Phase 1I) — full Spring context, real {@link
 * KeenonRobotSceneConfigService}/{@link KeenonRobotSceneConfigRepository}/
 * tenant guard. Deliberately no {@link KeenonApiClient} mock at all — this
 * controller never calls Keenon, only reads/writes Sakar-owned configuration.
 */
class KeenonSceneConfigControllerTest extends IntegrationTestSupport {

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotRepository robotRepository;
    @Autowired
    private KeenonRobotSceneConfigRepository sceneConfigRepository;

    @Test
    void authorizedUser_setsAndGetsSceneConfig() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-scene-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModel();
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F3");

        mockMvc.perform(put("/api/v1/robots/" + robot.getId() + "/keenon/scene-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneCode\":\"7ClJPR\",\"sceneName\":\"F\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sceneCode").value("7ClJPR"))
                .andExpect(jsonPath("$.data.sceneName").value("F"));

        assertThat(sceneConfigRepository.findByRobotId(robot.getId())).isPresent();

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/keenon/scene-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sceneCode").value("7ClJPR"))
                .andExpect(jsonPath("$.data.sceneName").value("F"));
    }

    @Test
    void repeatedSet_updatesTheSameRowRatherThanCreatingADuplicate() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-scene2-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModel();
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F4");

        mockMvc.perform(put("/api/v1/robots/" + robot.getId() + "/keenon/scene-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneCode\":\"dTW2N7\",\"sceneName\":\"SR Cleaning\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/robots/" + robot.getId() + "/keenon/scene-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneCode\":\"7ClJPR\",\"sceneName\":\"F\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sceneCode").value("7ClJPR"));

        assertThat(sceneConfigRepository.findAll()).hasSize(1);
        assertThat(sceneConfigRepository.findByRobotId(robot.getId()).orElseThrow().getSceneCode()).isEqualTo("7ClJPR");
    }

    @Test
    void twoRobots_haveIndependentSceneConfigs() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-scene3-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModel();
        Robot robotA = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F5");
        Robot robotB = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F6");

        mockMvc.perform(put("/api/v1/robots/" + robotA.getId() + "/keenon/scene-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneCode\":\"7ClJPR\",\"sceneName\":\"F\"}"))
                .andExpect(status().isOk());

        // robotB was never configured — must never resolve to robotA's scene.
        mockMvc.perform(get("/api/v1/robots/" + robotB.getId() + "/keenon/scene-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unconfiguredRobot_getReturnsResourceNotFound() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-scene-unconfigured-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModel();
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F7");

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/keenon/scene-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void blankSceneCode_isRejectedByValidation() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-scene-blank-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModel();
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F8");

        mockMvc.perform(put("/api/v1/robots/" + robot.getId() + "/keenon/scene-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneCode\":\"\"}"))
                .andExpect(status().isBadRequest());

        assertThat(sceneConfigRepository.findByRobotId(robot.getId())).isEmpty();
    }

    @Test
    void unknownRobotId_returnsRobotNotFound() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-scene-unknown-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(put("/api/v1/robots/" + UUID.randomUUID() + "/keenon/scene-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneCode\":\"7ClJPR\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void crossOrganizationRobot_returnsRobotNotFound_notForbidden() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "orgadmin-scene-a-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());
        String token = login(emailA, "Password1!");

        RobotModel model = keenonModel();
        Robot robotInOrgB = registerKeenonRobot(orgB.getId(), model.getId(), "94:BA:06:CA:99:F9");

        mockMvc.perform(put("/api/v1/robots/" + robotInOrgB.getId() + "/keenon/scene-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneCode\":\"7ClJPR\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
        assertThat(sceneConfigRepository.findByRobotId(robotInOrgB.getId())).isEmpty();
    }

    @Test
    void nonKeenonRobot_returnsUnsupportedCapability() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-scene-native-" + UUID.randomUUID() + "@example.com";
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

        mockMvc.perform(put("/api/v1/robots/" + robot.getId() + "/keenon/scene-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sceneCode\":\"7ClJPR\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_CAPABILITY"));
        assertThat(sceneConfigRepository.findByRobotId(robot.getId())).isEmpty();
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

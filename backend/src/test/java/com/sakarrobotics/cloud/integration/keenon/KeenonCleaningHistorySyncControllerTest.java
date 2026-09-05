package com.sakarrobotics.cloud.integration.keenon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sakarrobotics.cloud.IntegrationTestSupport;
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
import com.sakarrobotics.cloud.cleaning.CleaningSessionRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Keenon cleaning-history manual-sync slice — full Spring context, real
 * {@link KeenonCleaningHistorySyncService}/{@link CleaningSessionRepository}/
 * tenant guard, with only {@link KeenonApiClient} (the one real network
 * boundary) replaced by a mock — no real Keenon HTTP call is possible from
 * this test, and nothing here re-tests {@code KeenonCleaningHistorySyncService}'s
 * own mapping/pagination logic (already covered by
 * {@code KeenonCleaningHistorySyncServiceTest}) — only that this controller
 * correctly authorizes, validates, and delegates to it.
 */
class KeenonCleaningHistorySyncControllerTest extends IntegrationTestSupport {

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotCapabilityRepository capabilityRepository;
    @Autowired
    private RobotRepository robotRepository;
    @Autowired
    private CleaningSessionRepository cleaningSessionRepository;
    @MockitoBean
    private KeenonApiClient keenonApiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authorizedUser_syncsCleaningHistoryForTheirOwnKeenonRobot_appendsNewSessions() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-ch-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModelWithCleaning();
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F3");

        when(keenonApiClient.getCleaningLogs("C00715655", "94:BA:06:CA:99:F3", 1, 50)).thenReturn(objectMapper.readTree(
                "{\"entities\":[{\"cleanArea\":13.24,\"cleanEfficiency\":429.57,\"cleanTiming\":229,\"mState\":\"1\"}]}"));

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/cleaning-history/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newRecords").value(1));

        assertThat(cleaningSessionRepository.findByRobotIdOrderByIdDesc(robot.getId(), PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void repeatedSync_isIdempotent_doesNotDuplicateSessions() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-ch2-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModelWithCleaning();
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F4");

        when(keenonApiClient.getCleaningLogs("C00715655", "94:BA:06:CA:99:F4", 1, 50))
                .thenReturn(objectMapper.readTree("{\"entities\":[{\"cleanArea\":5.0,\"mState\":\"1\"}]}"));

        String body = "{\"storeId\":\"C00715655\"}";
        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/cleaning-history/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newRecords").value(1));
        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/cleaning-history/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newRecords").value(0));

        assertThat(cleaningSessionRepository.findByRobotIdOrderByIdDesc(robot.getId(), PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void viewerRole_cannotTriggerSync_missingRobotConfigurePermission() throws Exception {
        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "viewer-ch-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModelWithCleaning();
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F5");

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/cleaning-history/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        verifyNoInteractions(keenonApiClient);
    }

    @Test
    void crossOrganizationRobot_returnsRobotNotFound_notForbidden() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "orgadmin-ch-a-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());
        String token = login(emailA, "Password1!");

        RobotModel model = keenonModelWithCleaning();
        Robot robotInOrgB = registerKeenonRobot(orgB.getId(), model.getId(), "94:BA:06:CA:99:F6");

        mockMvc.perform(post("/api/v1/robots/" + robotInOrgB.getId() + "/keenon/cleaning-history/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
        verifyNoInteractions(keenonApiClient);
    }

    @Test
    void unknownRobotId_returnsRobotNotFound() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-ch-unknown-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(post("/api/v1/robots/" + UUID.randomUUID() + "/keenon/cleaning-history/sync")
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
        String email = "orgadmin-ch-native-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("Native-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Native Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);
        capabilityRepository.save(new RobotCapability(model.getId(), RobotCapabilityType.CLEANING, true));
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "SAKAR-SN-CLEANHIST-1");

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/cleaning-history/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_CAPABILITY"));
        verifyNoInteractions(keenonApiClient);
    }

    @Test
    void robotModelWithoutCleaningCapability_returnsUnsupportedCapability_beforeCheckingAdapterType() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-ch-nocap-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        // Same adapter type as a real C40 S, but the CLEANING capability itself was never
        // granted to this model — must fail on capability, not silently proceed.
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("Keenon-nocap-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("W3 (no cleaning)");
        model.setAdapterType(AdapterType.KEENON_CLOUD);
        model.setIntegrationPath(IntegrationPath.KEENON_CLOUD_DEPENDENT);
        model = modelRepository.save(model);
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "A8:B5:8E:B5:E3:E7");

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/cleaning-history/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"C00715655\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_CAPABILITY"));
        verifyNoInteractions(keenonApiClient);
    }

    @Test
    void missingStoreId_returnsValidationFailed_neverInventsOrDerivesOne() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-ch-nostoreid-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModelWithCleaning();
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F7");

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/cleaning-history/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(keenonApiClient);
    }

    @Test
    void blankStoreId_returnsValidationFailed() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-ch-blankid-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModelWithCleaning();
        Robot robot = registerKeenonRobot(org.getId(), model.getId(), "94:BA:06:CA:99:F8");

        mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/keenon/cleaning-history/sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(keenonApiClient);
    }

    private RobotModel keenonModelWithCleaning() {
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("Keenon-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("C40 S");
        model.setAdapterType(AdapterType.KEENON_CLOUD);
        model.setIntegrationPath(IntegrationPath.KEENON_CLOUD_DEPENDENT);
        model = modelRepository.save(model);
        capabilityRepository.save(new RobotCapability(model.getId(), RobotCapabilityType.CLEANING, true));
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

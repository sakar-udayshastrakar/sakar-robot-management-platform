package com.sakarrobotics.cloud.robot.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * IDOR / BOLA / tenant-isolation / RBAC coverage for the robot registry API
 * (Master Requirements Part 19.B).
 */
class RobotControllerSecurityTest extends IntegrationTestSupport {

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotCapabilityRepository capabilityRepository;
    @Autowired
    private RobotRepository robotRepository;

    @Test
    void user_cannotSeeRobotBelongingToAnUnrelatedOrganization_getsNotFoundNotForbidden() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "orgadmin-a-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());

        RobotModel model = aFullyCapableModel();
        Robot robotInOrgB = registerRobot(orgB.getId(), model.getId(), "SN-" + UUID.randomUUID());

        String token = login(emailA, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robotInOrgB.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void orgAdmin_canSeeRobotBelongingToADescendantOrganization() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization parent = createOrganization("Parent " + UUID.randomUUID(), OrganizationType.DISTRIBUTOR, null);
        Organization child = createOrganization("Child " + UUID.randomUUID(), OrganizationType.CLIENT, parent.getId());
        String email = "orgadmin-parent-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, parent.getId());

        RobotModel model = aFullyCapableModel();
        Robot robotInChildOrg = registerRobot(child.getId(), model.getId(), "SN-" + UUID.randomUUID());

        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robotInChildOrg.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(robotInChildOrg.getId().toString()));
    }

    @Test
    void viewerRole_cannotRegisterARobot_missingRobotConfigurePermission() throws Exception {
        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "viewer-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        String token = login(email, "Password1!");

        String body = "{\"organizationId\":\"" + org.getId() + "\",\"robotModelId\":\"" + UUID.randomUUID()
                + "\",\"name\":\"Test\",\"serialNumber\":\"SN-X\"}";

        mockMvc.perform(post("/api/v1/robots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ---------------------------------------------------------------
    // Registration authorization (registration audit finding): the
    // organizationId in RegisterRobotRequest must be an organization the
    // authenticated principal can actually manage — reusing exactly the
    // same TenantAccessGuard.assertOrganizationAccess(...) check
    // SiteController.create() already uses for its own organization-scoped
    // write, not a new/parallel mechanism.
    // ---------------------------------------------------------------

    @Test
    void authorizedUser_canRegisterARobotInTheirOwnOrganization() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-reg-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = aFullyCapableModel();
        String serialNumber = "SN-" + UUID.randomUUID();
        String body = "{\"organizationId\":\"" + org.getId() + "\",\"robotModelId\":\"" + model.getId()
                + "\",\"name\":\"Demo Piece\",\"serialNumber\":\"" + serialNumber + "\"}";

        mockMvc.perform(post("/api/v1/robots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.organizationId").value(org.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Demo Piece"))
                .andExpect(jsonPath("$.data.serialNumber").value(serialNumber))
                .andExpect(jsonPath("$.data.status").value("REGISTERED"));

        assertThat(robotRepository.existsBySerialNumber(serialNumber)).isTrue();
    }

    // ---------------------------------------------------------------
    // Vendor (Keenon) identifier scoping: the Sakar-serial <-> vendor-serial
    // mapping is Sakar-Robotics-owned data — only a robot registered under
    // the Sakar Robotics organization (org_type SAKAR_ROOT) may carry a
    // non-null externalRobotId. This is a business rule, not a tenant-access
    // check — the registering org otherwise fully owns the robot.
    // ---------------------------------------------------------------

    @Test
    void sakarRoboticsOrgAdmin_canRegisterARobotWithAnExternalRobotId() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization sakarRobotics = createOrganization("Sakar Robotics Test " + UUID.randomUUID(),
                OrganizationType.SAKAR_ROOT, null);
        String email = "orgadmin-sakarroot-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, sakarRobotics.getId());
        String token = login(email, "Password1!");

        RobotModel model = aFullyCapableModel();
        String serialNumber = "SN-" + UUID.randomUUID();
        // A unique, test-local external id — never a shared/hardcoded "real-looking" SN,
        // since IntegrationTestSupport's Spring context (and its H2 database) is shared
        // across every test class in the same run, not just within this file.
        String externalRobotId = "EXT-" + UUID.randomUUID();
        String body = "{\"organizationId\":\"" + sakarRobotics.getId() + "\",\"robotModelId\":\"" + model.getId()
                + "\",\"name\":\"Demo Piece\",\"serialNumber\":\"" + serialNumber
                + "\",\"externalRobotId\":\"" + externalRobotId + "\"}";

        mockMvc.perform(post("/api/v1/robots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.serialNumber").value(serialNumber));

        Robot saved = robotRepository.findByExternalRobotId(externalRobotId).orElseThrow();
        assertThat(saved.getSerialNumber()).isEqualTo(serialNumber);
    }

    @Test
    void nonSakarRoboticsOrg_cannotRegisterARobotWithAnExternalRobotId_andNoRobotIsCreated() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-noexternal-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = aFullyCapableModel();
        String serialNumber = "SN-" + UUID.randomUUID();
        String externalRobotId = "EXT-" + UUID.randomUUID();
        String body = "{\"organizationId\":\"" + org.getId() + "\",\"robotModelId\":\"" + model.getId()
                + "\",\"name\":\"Demo Piece\",\"serialNumber\":\"" + serialNumber
                + "\",\"externalRobotId\":\"" + externalRobotId + "\"}";

        mockMvc.perform(post("/api/v1/robots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("EXTERNAL_ROBOT_ID_NOT_ALLOWED"));

        assertThat(robotRepository.existsBySerialNumber(serialNumber)).isFalse();
        assertThat(robotRepository.findByExternalRobotId(externalRobotId)).isEmpty();
    }

    @Test
    void secondSakarRoboticsRobot_withTheSameExternalRobotId_isRejected_originalRobotUnchanged_noSecondRowCreated()
            throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization sakarRobotics = createOrganization("Sakar Robotics Test " + UUID.randomUUID(),
                OrganizationType.SAKAR_ROOT, null);
        String email = "orgadmin-dupext-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, sakarRobotics.getId());
        String token = login(email, "Password1!");

        RobotModel model = aFullyCapableModel();
        String firstSerialNumber = "SN-" + UUID.randomUUID();
        String sharedExternalRobotId = "EXT-" + UUID.randomUUID();
        String firstBody = "{\"organizationId\":\"" + sakarRobotics.getId() + "\",\"robotModelId\":\"" + model.getId()
                + "\",\"name\":\"First Robot\",\"serialNumber\":\"" + firstSerialNumber
                + "\",\"externalRobotId\":\"" + sharedExternalRobotId + "\"}";
        mockMvc.perform(post("/api/v1/robots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBody))
                .andExpect(status().isCreated());

        Robot firstRobot = robotRepository.findByExternalRobotId(sharedExternalRobotId).orElseThrow();
        UUID firstRobotId = firstRobot.getId();

        // A second robot, same organization, same externalRobotId, different serial number.
        String secondSerialNumber = "SN-" + UUID.randomUUID();
        String secondBody = "{\"organizationId\":\"" + sakarRobotics.getId() + "\",\"robotModelId\":\"" + model.getId()
                + "\",\"name\":\"Second Robot\",\"serialNumber\":\"" + secondSerialNumber
                + "\",\"externalRobotId\":\"" + sharedExternalRobotId + "\"}";
        mockMvc.perform(post("/api/v1/robots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_EXTERNAL_ROBOT_ID"));

        // The second robot was never created...
        assertThat(robotRepository.existsBySerialNumber(secondSerialNumber)).isFalse();
        // ...and the original robot is completely untouched (not overwritten in place).
        Robot firstRobotAfter = robotRepository.findById(firstRobotId).orElseThrow();
        assertThat(firstRobotAfter.getSerialNumber()).isEqualTo(firstSerialNumber);
        assertThat(firstRobotAfter.getExternalRobotId()).isEqualTo(sharedExternalRobotId);
        assertThat(firstRobotAfter.getName()).isEqualTo("First Robot");
        // Exactly one robot in this organization carries this externalRobotId.
        assertThat(robotRepository.existsByOrganizationIdAndExternalRobotId(sakarRobotics.getId(), sharedExternalRobotId))
                .isTrue();
    }

    @Test
    void orgAdmin_cannotRegisterARobotIntoAnUnrelatedOrganization_getsForbidden_andNoRobotIsCreated() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "orgadmin-reg-a-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());
        String token = login(emailA, "Password1!");

        RobotModel model = aFullyCapableModel();
        String serialNumber = "SN-" + UUID.randomUUID();
        // Org A's admin attempts to register a robot into Org B simply by changing the
        // organizationId in the request body — this must be rejected before any row is saved.
        String body = "{\"organizationId\":\"" + orgB.getId() + "\",\"robotModelId\":\"" + model.getId()
                + "\",\"name\":\"Demo Piece\",\"serialNumber\":\"" + serialNumber + "\"}";

        mockMvc.perform(post("/api/v1/robots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TENANT_ACCESS_DENIED"));

        assertThat(robotRepository.existsBySerialNumber(serialNumber)).isFalse();
    }

    @Test
    void registeringIntoAnUnknownOrganization_returnsOrganizationNotFound_existingConvention() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-reg-unknown-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel model = aFullyCapableModel();
        String serialNumber = "SN-" + UUID.randomUUID();
        String body = "{\"organizationId\":\"" + UUID.randomUUID() + "\",\"robotModelId\":\"" + model.getId()
                + "\",\"name\":\"Demo Piece\",\"serialNumber\":\"" + serialNumber + "\"}";

        // Not a tenant-isolation-shaped rejection here: TenantAccessGuard/OrganizationService's
        // own existing convention is to raise ORGANIZATION_NOT_FOUND for a target id that
        // doesn't exist at all, distinct from TENANT_ACCESS_DENIED for a real-but-inaccessible
        // organization — this test documents that existing behavior, not new behavior.
        mockMvc.perform(post("/api/v1/robots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORGANIZATION_NOT_FOUND"));

        assertThat(robotRepository.existsBySerialNumber(serialNumber)).isFalse();
    }

    @Test
    void robotModelWithoutGetStatusCapability_statusEndpointReturnsUnsupportedCapability() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-cap-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());

        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("NoStatus-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Limited Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);
        // Deliberately no RobotCapability row for GET_STATUS at all -> unsupported.

        Robot robot = registerRobot(org.getId(), model.getId(), "SN-" + UUID.randomUUID());
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_CAPABILITY"));
    }

    @Test
    void robotModelWithoutGetBatteryCapability_batteryEndpointReturnsUnsupportedCapability() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-batcap-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());

        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("NoBattery-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Limited Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);
        // Deliberately no RobotCapability row for GET_BATTERY at all -> unsupported.

        Robot robot = registerRobot(org.getId(), model.getId(), "SN-" + UUID.randomUUID());
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/battery")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_CAPABILITY"));
    }

    @Test
    void user_cannotSeeRobotAreasBelongingToAnUnrelatedOrganization_getsNotFoundNotForbidden() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization orgA = createOrganization("Org A " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        Organization orgB = createOrganization("Org B " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String emailA = "orgadmin-areas-a-" + UUID.randomUUID() + "@example.com";
        createUser(emailA, "Password1!", orgAdmin, orgA.getId());

        RobotModel model = aFullyCapableModel();
        Robot robotInOrgB = registerRobot(orgB.getId(), model.getId(), "SN-" + UUID.randomUUID());

        String token = login(emailA, "Password1!");

        // A user must not be able to reach another organization's robot's areas
        // simply by guessing/changing the id — same tenant guard as every other
        // per-robot read endpoint (getAccessibleOrThrow runs before the
        // capability check, so this fails closed regardless of what the model
        // actually supports).
        mockMvc.perform(get("/api/v1/robots/" + robotInOrgB.getId() + "/areas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void robotModelWithoutGetAreasCapability_areasEndpointReturnsUnsupportedCapability() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-areascap-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());

        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("NoAreas-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Limited Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);
        // Deliberately no RobotCapability row for GET_AREAS at all -> unsupported.

        Robot robot = registerRobot(org.getId(), model.getId(), "SN-" + UUID.randomUUID());
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/areas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_CAPABILITY"));
    }

    @Test
    void keenonRobotWithGetAreasCapabilityButNoSyncedAreaMapping_areasEndpointReturnsResourceNotFound() throws Exception {
        // Exercises the real KeenonRobotAdapter.getAreas() -> storeIdOf() path through the
        // full Spring context (JPA + the real GlobalExceptionHandler) — the other /areas
        // tests above all short-circuit before ever reaching the adapter (tenant guard or
        // capability check), so this is the only coverage of "capability supported, adapter
        // genuinely called, no synced Keenon store mapping yet" end to end.
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-keenonareas-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());

        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("Keenon-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Keenon Model");
        model.setAdapterType(AdapterType.KEENON_CLOUD);
        model.setIntegrationPath(IntegrationPath.KEENON_CLOUD_DEPENDENT);
        model = modelRepository.save(model);
        capabilityRepository.save(new RobotCapability(model.getId(), RobotCapabilityType.GET_AREAS, true));

        Robot robot = registerRobot(org.getId(), model.getId(), "SN-" + UUID.randomUUID());
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + robot.getId() + "/areas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unknownRobotId_areasEndpointReturnsNotFound() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-areasmissing-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + UUID.randomUUID() + "/areas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    @Test
    void unknownRobotId_batteryEndpointReturnsNotFound() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "orgadmin-batmissing-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/robots/" + UUID.randomUUID() + "/battery")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROBOT_NOT_FOUND"));
    }

    private RobotModel aFullyCapableModel() {
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("TestVendor-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("Test Model");
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);
        capabilityRepository.save(new RobotCapability(model.getId(), RobotCapabilityType.GET_STATUS, true));
        return model;
    }

    private Robot registerRobot(UUID organizationId, UUID modelId, String serialNumber) {
        Robot robot = new Robot();
        robot.setOrganizationId(organizationId);
        robot.setRobotModelId(modelId);
        robot.setName("Robot " + serialNumber);
        robot.setSerialNumber(serialNumber);
        robot.setStatus(RobotLifecycleStatus.REGISTERED);
        return robotRepository.save(robot);
    }
}

package com.sakarrobotics.cloud.robot.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
 * Full-Spring-context tests — real {@link RobotSerialReconciliationService}, real {@link
 * SakarSerialNumberService} (a real sequence-backed generator, not a mock), real tenant guard.
 * Fixture robots are inserted directly through {@link RobotRepository}, in exactly the shape the
 * (now-fixed) Sixteenth-pass sync used to leave them in — {@code serial_number} = a raw vendor
 * code, {@code vendor_serial_number} = null — without ever calling Keenon or the sync endpoint.
 */
class RobotSerialReconciliationControllerTest extends IntegrationTestSupport {

    @Autowired
    private RobotRepository robotRepository;
    @Autowired
    private RobotModelRepository robotModelRepository;
    @Autowired
    private RobotManufacturerRepository robotManufacturerRepository;

    private RobotModel keenonModel() {
        RobotManufacturer manufacturer = robotManufacturerRepository.save(new RobotManufacturer("Keenon-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName("C40 S");
        model.setSerialPrefix("CB");
        model.setAdapterType(AdapterType.KEENON_CLOUD);
        model.setIntegrationPath(IntegrationPath.KEENON_CLOUD_DEPENDENT);
        return robotModelRepository.save(model);
    }

    private Robot legacyRobot(UUID organizationId, UUID modelId, String externalRobotId, String legacyMftCode) {
        Robot robot = new Robot();
        robot.setOrganizationId(organizationId);
        robot.setRobotModelId(modelId);
        robot.setName(externalRobotId);
        robot.setSerialNumber(legacyMftCode);
        robot.setExternalRobotId(externalRobotId);
        robot.setStatus(RobotLifecycleStatus.REGISTERED);
        return robotRepository.save(robot);
    }

    @Test
    void authorizedUser_reconcilesFiveLegacyRobots_deterministicallyAndIdempotently() throws Exception {
        Role superAdmin = ensureRole(RoleName.SUPER_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization sakarRoot = createOrganization("Sakar Robotics " + UUID.randomUUID(), OrganizationType.SAKAR_ROOT, null);
        String email = "root-admin-reconcile-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", superAdmin, sakarRoot.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModel();
        // Mirrors the real live-verification data exactly (same 5 externalRobotIds/mftCodes),
        // inserted directly — no Keenon call, no sync endpoint call.
        Robot demoPiece = legacyRobot(sakarRoot.getId(), model.getId(), "94:BA:06:CA:99:F3", "QC402602X00002");
        Robot tajCidade = legacyRobot(sakarRoot.getId(), model.getId(), "94:BA:06:CA:9A:05", "QC402602X00004");
        Robot gujratRobot = legacyRobot(sakarRoot.getId(), model.getId(), "94:BA:06:CA:9A:23", "QC402602X00005");
        Robot s100 = legacyRobot(sakarRoot.getId(), model.getId(), "88:49:2D:5F:29:55", "QS12603BX0002");
        Robot w3 = legacyRobot(sakarRoot.getId(), model.getId(), "A8:B5:8E:B5:E3:E7", "KRW325111W0031");

        mockMvc.perform(post("/api/v1/robots/reconcile-legacy-serials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + sakarRoot.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRobotsInScope").value(5))
                .andExpect(jsonPath("$.data.reconciled").value(5))
                .andExpect(jsonPath("$.data.alreadyReconciled").value(0))
                .andExpect(jsonPath("$.data.notApplicable").value(0));

        // Exact deterministic-ordering assignment (external_robot_id ascending) is proven
        // precisely in RobotSerialReconciliationServiceTest; this test proves the full-stack
        // transformation is correct for every robot regardless of order.
        List<Robot> originals = List.of(demoPiece, tajCidade, gujratRobot, s100, w3);
        for (Robot original : originals) {
            Robot updated = robotRepository.findById(original.getId()).orElseThrow();
            assertThat(updated.getSerialNumber()).matches("SR-CB-\\d{4}-\\d{6}");
            assertThat(updated.getVendorSerialNumber()).isEqualTo(original.getSerialNumber());
            assertThat(updated.getExternalRobotId()).isEqualTo(original.getExternalRobotId());
            assertThat(updated.getOrganizationId()).isEqualTo(sakarRoot.getId());
            assertThat(updated.getRobotModelId()).isEqualTo(model.getId());
        }
        // All five of THIS test's robots have distinct serials (scoped to their own ids —
        // the test DB is shared across this class's other test methods).
        List<String> serials = robotRepository.findAllById(originals.stream().map(Robot::getId).toList()).stream()
                .map(Robot::getSerialNumber).toList();
        assertThat(serials).doesNotHaveDuplicates();
        assertThat(serials).hasSize(5);

        // Second run: idempotent — nothing left to reconcile, and no serial is regenerated.
        String demoPieceSerialAfterFirstRun = robotRepository.findById(demoPiece.getId()).orElseThrow().getSerialNumber();
        mockMvc.perform(post("/api/v1/robots/reconcile-legacy-serials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + sakarRoot.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reconciled").value(0))
                .andExpect(jsonPath("$.data.alreadyReconciled").value(5));
        assertThat(robotRepository.findById(demoPiece.getId()).orElseThrow().getSerialNumber())
                .isEqualTo(demoPieceSerialAfterFirstRun);
    }

    @Test
    void nonLegacyManuallyRegisteredRobot_isNeverModified() throws Exception {
        Role superAdmin = ensureRole(RoleName.SUPER_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization sakarRoot = createOrganization("Sakar Robotics " + UUID.randomUUID(), OrganizationType.SAKAR_ROOT, null);
        String email = "root-admin-nonlegacy-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", superAdmin, sakarRoot.getId());
        String token = login(email, "Password1!");

        RobotModel model = keenonModel();
        Robot manual = new Robot();
        manual.setOrganizationId(sakarRoot.getId());
        manual.setRobotModelId(model.getId());
        manual.setName("Manually Registered");
        manual.setSerialNumber("HAND-ENTERED-0001");
        manual.setExternalRobotId(null);
        manual.setStatus(RobotLifecycleStatus.REGISTERED);
        manual = robotRepository.save(manual);

        mockMvc.perform(post("/api/v1/robots/reconcile-legacy-serials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + sakarRoot.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reconciled").value(0))
                .andExpect(jsonPath("$.data.notApplicable").value(1));

        assertThat(robotRepository.findById(manual.getId()).orElseThrow().getSerialNumber()).isEqualTo("HAND-ENTERED-0001");
    }

    @Test
    void crossOrganizationCaller_returnsTenantAccessDenied() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONFIGURE);
        Organization sakarRoot = createOrganization("Sakar Robotics " + UUID.randomUUID(), OrganizationType.SAKAR_ROOT, null);
        Organization otherOrg = createOrganization("Other Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "other-org-admin-reconcile-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, otherOrg.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(post("/api/v1/robots/reconcile-legacy-serials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + sakarRoot.getId() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TENANT_ACCESS_DENIED"));
    }

    @Test
    void missingRobotConfigurePermission_returnsForbidden() throws Exception {
        Role viewOnly = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        Organization sakarRoot = createOrganization("Sakar Robotics " + UUID.randomUUID(), OrganizationType.SAKAR_ROOT, null);
        String email = "view-only-reconcile-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewOnly, sakarRoot.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(post("/api/v1/robots/reconcile-legacy-serials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + sakarRoot.getId() + "\"}"))
                .andExpect(status().isForbidden());
    }
}

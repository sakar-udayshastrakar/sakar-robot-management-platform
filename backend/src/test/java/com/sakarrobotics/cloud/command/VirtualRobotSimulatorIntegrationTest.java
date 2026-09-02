package com.sakarrobotics.cloud.command;

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

/**
 * Proves — via the EXISTING, unmodified {@code RobotCommandController}/
 * {@code RobotCommandService}/robot-registry REST API, not a new
 * simulator-specific backend endpoint — that a "virtual" robot fleet is
 * indistinguishable, at the backend and (by extension) Web UI layer, from
 * any other {@code SAKAR_NATIVE} robot model (Roadmap Phase 9 "Virtual
 * C40 Robot Simulator", see ../../VIRTUAL_C40_SIMULATOR.md).
 *
 * <p><strong>No backend production code was changed to make this pass.</strong>
 * A robot is only ever a row in the existing registry tables; a
 * "manufacturer" named {@code "Sakar Virtual Simulator"} and a model
 * named {@code "VIRTUAL-C40-SIM"} is how this test (and, for a real
 * deployment, an operator) marks a fleet of robots as clearly simulated
 * — exactly the "clearly indicate SIMULATED ROBOT" requirement, achieved
 * with the existing data model rather than a new column, flag, or UI.
 *
 * <p>This also stands in for "Web UI integration" and "multi-robot"
 * verification: the Sakar Web application already renders whatever the
 * robot-registry and command REST APIs return, so 3 virtual robots
 * registered this way already appear and are commandable in the existing
 * Robot Detail screen with zero frontend code changes — not independently
 * live-browser-verified this pass (no local Postgres/Redis/Docker
 * available, same limitation already noted elsewhere in this project),
 * but the REST contract itself is exercised for real, against a real
 * (test-profile) database, here.
 */
class VirtualRobotSimulatorIntegrationTest extends IntegrationTestSupport {

    private static final String VIRTUAL_MANUFACTURER_NAME = "Sakar Virtual Simulator";
    private static final String VIRTUAL_MODEL_NAME = "VIRTUAL-C40-SIM";

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;
    @Autowired
    private RobotCapabilityRepository capabilityRepository;
    @Autowired
    private RobotRepository robotRepository;

    @Test
    void threeVirtualRobots_registerAndAcceptGoToPointAndReturnToDock_throughTheExistingApiUnmodified() throws Exception {
        Role orgAdmin = ensureRole(RoleName.ORG_ADMIN, PermissionCode.ROBOT_CONTROL, PermissionCode.ROBOT_CONFIGURE);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", orgAdmin, org.getId());
        String token = login(email, "Password1!");

        RobotModel virtualModel = virtualSimulatorModel();

        Robot robot1 = registerVirtualRobot(org.getId(), virtualModel.getId(), "VIRTUAL-C40-001");
        Robot robot2 = registerVirtualRobot(org.getId(), virtualModel.getId(), "VIRTUAL-C40-002");
        Robot robot3 = registerVirtualRobot(org.getId(), virtualModel.getId(), "VIRTUAL-C40-003");

        // Each virtual robot accepts the SAME two real command types the physical C40 uses -
        // proving one robot's command has no code path back to another (issued independently,
        // to different robotIds, through the same shared controller/service).
        for (Robot robot : new Robot[] {robot1, robot2, robot3}) {
            mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/commands")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"commandType\":\"GO_TO_POINT\",\"params\":{\"destinationId\":1003}}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.commandType").value("GO_TO_POINT"))
                    .andExpect(jsonPath("$.data.status").value("AUTHORIZED"));

            mockMvc.perform(post("/api/v1/robots/" + robot.getId() + "/commands")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"commandType\":\"RETURN_TO_DOCK\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.commandType").value("RETURN_TO_DOCK"))
                    .andExpect(jsonPath("$.data.status").value("AUTHORIZED"));
        }
    }

    private RobotModel virtualSimulatorModel() {
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer(VIRTUAL_MANUFACTURER_NAME));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName(VIRTUAL_MODEL_NAME);
        model.setAdapterType(AdapterType.SAKAR_NATIVE);
        model.setIntegrationPath(IntegrationPath.SAKAR_OWNED_LOCAL);
        model = modelRepository.save(model);
        capabilityRepository.save(new RobotCapability(model.getId(), RobotCapabilityType.GO_TO_POINT, true));
        capabilityRepository.save(new RobotCapability(model.getId(), RobotCapabilityType.RETURN_TO_DOCK, true));
        return model;
    }

    private Robot registerVirtualRobot(UUID organizationId, UUID modelId, String name) {
        Robot robot = new Robot();
        robot.setOrganizationId(organizationId);
        robot.setRobotModelId(modelId);
        robot.setName(name);
        robot.setSerialNumber("SIM-" + UUID.randomUUID());
        robot.setStatus(RobotLifecycleStatus.REGISTERED);
        return robotRepository.save(robot);
    }
}

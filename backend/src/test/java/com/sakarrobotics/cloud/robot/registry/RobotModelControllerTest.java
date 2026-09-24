package com.sakarrobotics.cloud.robot.registry;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.Role;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationType;

/**
 * Previously nothing exposed {@code RobotModel}/{@code RobotManufacturer} to
 * a client — this is a read-only lookup list, no schema change.
 */
class RobotModelControllerTest extends IntegrationTestSupport {

    @Autowired
    private RobotManufacturerRepository manufacturerRepository;
    @Autowired
    private RobotModelRepository modelRepository;

    @Test
    void robotView_canListModelsWithResolvedManufacturerName() throws Exception {
        // Uniquely-suffixed name: this controller lists every RobotModel with no tenant/other
        // filtering, and the shared test-suite DB accumulates fixture rows from every other
        // robot-registry test across the whole `mvn test` run (not dropped between test classes)
        // — a literal "C40 S" would collide with rows other tests create under that exact name.
        String modelName = "C40 S-" + UUID.randomUUID();
        RobotManufacturer manufacturer = manufacturerRepository.save(new RobotManufacturer("Keenon-" + UUID.randomUUID()));
        RobotModel model = new RobotModel();
        model.setManufacturerId(manufacturer.getId());
        model.setName(modelName);
        model.setSakarProductName("Sakar CleanBot 5000 Plus");
        model.setAdapterType(AdapterType.KEENON_CLOUD);
        model.setIntegrationPath(IntegrationPath.KEENON_CLOUD_DEPENDENT);
        modelRepository.save(model);

        Role viewer = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        Organization org = createOrganization("Org " + UUID.randomUUID(), OrganizationType.DIRECT_CLIENT, null);
        String email = "viewer-" + UUID.randomUUID() + "@example.com";
        createUser(email, "Password1!", viewer, org.getId());
        String token = login(email, "Password1!");

        mockMvc.perform(get("/api/v1/robot-models").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='" + modelName + "')].manufacturerName").value(manufacturer.getName()))
                .andExpect(jsonPath("$.data[?(@.name=='" + modelName + "')].sakarProductName").value("Sakar CleanBot 5000 Plus"));
    }

    @Test
    void unauthenticatedCaller_isRejected() throws Exception {
        // Every real RoleName in the seeded RBAC matrix (V9__seed_rbac.sql) is granted ROBOT_VIEW,
        // and this suite's shared-DB `ensureRole` fixture accumulates permissions across the whole
        // `mvn test` run — there is no role that reliably lacks ROBOT_VIEW to exercise a 403 case
        // against, so the deterministic negative case here is simply no token at all (401).
        mockMvc.perform(get("/api/v1/robot-models"))
                .andExpect(status().isUnauthorized());
    }
}

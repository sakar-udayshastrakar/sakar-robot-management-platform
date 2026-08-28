package com.sakarrobotics.cloud;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sakarrobotics.cloud.iam.Permission;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.PermissionRepository;
import com.sakarrobotics.cloud.iam.Role;
import com.sakarrobotics.cloud.iam.RoleName;
import com.sakarrobotics.cloud.iam.RoleRepository;
import com.sakarrobotics.cloud.iam.User;
import com.sakarrobotics.cloud.iam.UserRepository;
import com.sakarrobotics.cloud.org.Organization;
import com.sakarrobotics.cloud.org.OrganizationService;
import com.sakarrobotics.cloud.org.OrganizationType;

/**
 * Shared fixtures for tests. Test DB uses H2 with {@code ddl-auto=create-drop}
 * (Flyway disabled — see {@code application-test.yml}), so the RBAC seed
 * data from {@code V9__seed_rbac.sql} does not exist automatically; these
 * helpers create the same roles/permissions programmatically through the
 * real entities/repositories instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    private static com.github.fppt.jedismock.RedisServer fakeRedisServer;

    /**
     * Starts one in-JVM fake Redis server (RESP protocol, no real Redis
     * install/Docker required) for the whole test run, on the port
     * {@code application-test.yml} points Spring Data Redis at. Needed
     * because {@code LoginRateLimiterService} (and, for enabled-Keenon
     * tests, {@code KeenonOAuthTokenService}) are real Redis clients, not
     * mocked in these tests — this exercises the actual Redis interaction
     * rather than stubbing it away.
     */
    @BeforeAll
    static synchronized void startFakeRedis() throws Exception {
        if (fakeRedisServer == null) {
            fakeRedisServer = com.github.fppt.jedismock.RedisServer.newRedisServer(6399);
            fakeRedisServer.start();
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected OrganizationService organizationService;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected PermissionRepository permissionRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected Role ensureRole(RoleName name, PermissionCode... codes) {
        Role role = roleRepository.findByName(name).orElseGet(() -> roleRepository.save(new Role(name, name.name())));
        Arrays.stream(codes).forEach(code -> {
            Permission permission = permissionRepository.findByCode(code)
                    .orElseGet(() -> permissionRepository.save(new Permission(code, code.name())));
            role.getPermissions().add(permission);
        });
        return roleRepository.save(role);
    }

    protected Organization createOrganization(String name, OrganizationType type, UUID parentId) {
        return organizationService.create(name, type, parentId);
    }

    protected User createUser(String email, String rawPassword, Role role, UUID organizationId) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFullName(email);
        user.setRole(role);
        user.setOrganizationId(organizationId);
        return userRepository.save(user);
    }

    protected String login(String email, String rawPassword) throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {
            {
                put("email", email);
                put("password", rawPassword);
            }
        });
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.get("data").get("accessToken").asText();
    }
}

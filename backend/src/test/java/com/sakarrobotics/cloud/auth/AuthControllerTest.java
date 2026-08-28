package com.sakarrobotics.cloud.auth;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.iam.PermissionCode;
import com.sakarrobotics.cloud.iam.Role;
import com.sakarrobotics.cloud.iam.RoleName;

class AuthControllerTest extends IntegrationTestSupport {

    @Test
    void login_withValidCredentials_returnsAccessAndRefreshTokens() throws Exception {
        Role role = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        String email = uniqueEmail();
        createUser(email, "CorrectPassword1!", role, null);

        mockMvc.perform(loginRequest(email, "CorrectPassword1!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void login_withWrongPassword_returnsInvalidCredentialsNotAccountEnumeration() throws Exception {
        Role role = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        String email = uniqueEmail();
        createUser(email, "CorrectPassword1!", role, null);

        mockMvc.perform(loginRequest(email, "WrongPassword!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")));
    }

    @Test
    void login_unknownEmail_returnsSameInvalidCredentialsError() throws Exception {
        mockMvc.perform(loginRequest(uniqueEmail(), "whatever"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")));
    }

    @Test
    void login_afterFiveFailedAttempts_locksAccount() throws Exception {
        Role role = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        String email = uniqueEmail();
        createUser(email, "CorrectPassword1!", role, null);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(loginRequest(email, "WrongPassword!")).andExpect(status().isUnauthorized());
        }

        // The 6th attempt, even with the CORRECT password, must be rejected — the account is locked.
        mockMvc.perform(loginRequest(email, "CorrectPassword1!"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("ACCOUNT_LOCKED")));
    }

    @Test
    void refresh_rotatesTokenAndRejectsReuseOfThePriorToken() throws Exception {
        Role role = ensureRole(RoleName.VIEWER, PermissionCode.ROBOT_VIEW);
        String email = uniqueEmail();
        createUser(email, "CorrectPassword1!", role, null);

        String loginResponse = mockMvc.perform(loginRequest(email, "CorrectPassword1!"))
                .andReturn().getResponse().getContentAsString();
        String firstRefreshToken = objectMapper.readTree(loginResponse).get("data").get("refreshToken").asText();

        // First use rotates successfully.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // Reusing the now-rotated (revoked) token is treated as compromise, not honored.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("TOKEN_REVOKED")));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            String email, String password) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}

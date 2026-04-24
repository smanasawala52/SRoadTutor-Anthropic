package com.sroadtutor.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sroadtutor.auth.dto.LoginRequest;
import com.sroadtutor.auth.dto.RefreshRequest;
import com.sroadtutor.auth.dto.SignupRequest;
import com.sroadtutor.auth.model.Role;
import com.sroadtutor.auth.repository.UserRepository;
import com.sroadtutor.config.PostgresTestcontainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests hitting real HTTP → real Spring beans → real
 * Postgres (Testcontainers) → Flyway-migrated schema.
 *
 * <p>Requires Docker to be running locally.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(PostgresTestcontainerConfig.class)
class AuthControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void cleanDb() {
        // Preserve Flyway-seeded rows; only wipe what the tests create.
        userRepository.deleteAll();
    }

    @Test
    void signup_returnsTokensAndPersistsUser() throws Exception {
        SignupRequest req = new SignupRequest(
                "integration@example.com", "Password1", "Integration Test", "+15550001", Role.INSTRUCTOR);

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.email").value("integration@example.com"))
                .andExpect(jsonPath("$.data.user.role").value("INSTRUCTOR"));

        assert userRepository.findByEmailIgnoreCase("integration@example.com").isPresent();
    }

    @Test
    void signup_rejectsDuplicateEmail() throws Exception {
        SignupRequest req = new SignupRequest(
                "dup@example.com", "Password1", "Dup Test", null, Role.STUDENT);

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void signup_validatesBody() throws Exception {
        String bad = """
                { "email": "not-an-email", "password": "short", "fullName": "", "role": null }
                """;
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void login_thenRefresh_rotatesTokens() throws Exception {
        // 1. signup
        SignupRequest signup = new SignupRequest("flow@example.com", "Password1", "Flow", null, Role.OWNER);
        String signupJson = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(signup)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = mapper.readTree(signupJson).path("data").path("refreshToken").asText();

        // 2. login with same creds — works, and returns a fresh token pair
        LoginRequest login = new LoginRequest("flow@example.com", "Password1");
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()));

        // 3. refresh the signup-issued refresh token → new pair
        RefreshRequest refresh = new RefreshRequest(refreshToken);
        String refreshJson = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(refresh)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newRefresh = mapper.readTree(refreshJson).path("data").path("refreshToken").asText();

        // 4. using the OLD refresh should now be rejected (rotation)
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized());

        // sanity: the new refresh is different
        assert !newRefresh.equals(refreshToken);
    }

    @Test
    void login_wrongPasswordReturns401() throws Exception {
        // create the user first
        SignupRequest signup = new SignupRequest("wrong@example.com", "Password1", "Wrong", null, Role.PARENT);
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest("wrong@example.com", "wrong-password!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpoint_requires401WithoutToken() throws Exception {
        // We haven't built /schools yet, but Spring will deny anything non-public.
        mockMvc.perform(post("/schools/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}

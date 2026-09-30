package com.taskflow.userservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Levanta el contexto completo de Spring (controller + service + repository + H2)
 * y ejercita el flujo real de registro -> login -> consulta protegida por JWT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void flujoCompletoDeRegistroLoginYConsultaProtegida() throws Exception {
        Map<String, String> registerBody = Map.of(
                "name", "Grace Hopper",
                "email", "grace@taskflow.dev",
                "password", "secret123"
        );

        String registerResponse = mockMvc.perform(post("/api/users/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        Long userId = objectMapper.readTree(registerResponse).get("id").asLong();

        // Sin token -> 401
        mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isUnauthorized());

        Map<String, String> loginBody = Map.of(
                "email", "grace@taskflow.dev",
                "password", "secret123"
        );

        String loginResponse = mockMvc.perform(post("/api/users/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginResponse).get("token").asText();

        mockMvc.perform(get("/api/users/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("grace@taskflow.dev"));
    }

    @Test
    void registerRechazaEmailDuplicado() throws Exception {
        Map<String, String> body = Map.of(
                "name", "Linus",
                "email", "linus@taskflow.dev",
                "password", "secret123"
        );

        mockMvc.perform(post("/api/users/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }
}

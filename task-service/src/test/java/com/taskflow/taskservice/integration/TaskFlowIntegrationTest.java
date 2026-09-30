package com.taskflow.taskservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.taskservice.client.UserClient;
import com.taskflow.taskservice.client.UserDto;
import com.taskflow.taskservice.exception.AssigneeNotFoundException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Levanta el contexto completo (controller + service + repository + H2) y
 * reemplaza UserClient por un mock, para poder ejercitar el flujo real de
 * creacion y asignacion de tareas sin depender de que user-service este
 * corriendo. El unit test de TaskService ya cubre la logica de UserClient
 * en aislamiento; aca se prueba el cableado HTTP + persistencia real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserClient userClient;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String tokenFor(Long userId) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3_600_000))
                .signWith(key)
                .compact();
    }

    @Test
    void flujoCompletoDeCreacionYAsignacionConUsuarioValido() throws Exception {
        String token = tokenFor(1L);
        when(userClient.getUserById(eq(5L), any())).thenReturn(new UserDto(5L, "Ada", "ada@taskflow.dev"));

        Map<String, Object> createBody = Map.of("title", "Diseniar el gateway", "description", "desc", "projectId", 10);

        String createResponse = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.assigneeId").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Long taskId = objectMapper.readTree(createResponse).get("id").asLong();

        Map<String, Object> assignBody = Map.of("assigneeId", 5);
        mockMvc.perform(patch("/api/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(assignBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(5));

        Map<String, Object> statusBody = Map.of("status", "IN_PROGRESS");
        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(statusBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void asignarConUsuarioInexistenteDevuelve400YNoAsigna() throws Exception {
        String token = tokenFor(1L);
        when(userClient.getUserById(eq(999L), any())).thenThrow(new AssigneeNotFoundException(999L));

        Map<String, Object> createBody = Map.of("title", "Tarea huerfana", "projectId", 10);
        String createResponse = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long taskId = objectMapper.readTree(createResponse).get("id").asLong();

        Map<String, Object> assignBody = Map.of("assigneeId", 999);
        mockMvc.perform(patch("/api/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(assignBody)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").doesNotExist());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }
}

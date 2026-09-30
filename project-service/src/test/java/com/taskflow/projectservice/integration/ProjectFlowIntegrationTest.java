package com.taskflow.projectservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Levanta el contexto completo (controller + service + repository + H2) y
 * fabrica un JWT propio con el mismo secreto que usaria user-service, para
 * ejercitar el flujo real de creacion/consulta/actualizacion de proyectos
 * protegido por el filtro de autenticacion.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectFlowIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectMapper objectMapper;

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
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void flujoCompletoDeCreacionConsultaYActualizacion() throws Exception {
        String ownerToken = tokenFor(1L);

        Map<String, String> body = Map.of("name", "TaskFlow", "description", "Proyecto de portafolio");

        String createResponse = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(1))
                .andReturn().getResponse().getContentAsString();

        Long projectId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("TaskFlow"));

        // Otro usuario no puede modificarlo
        String otherToken = tokenFor(2L);
        Map<String, String> updateBody = Map.of("name", "Hackeado", "description", "x");
        mockMvc.perform(put("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isForbidden());

        // El owner si puede
        Map<String, String> validUpdate = Map.of("name", "TaskFlow v2", "description", "actualizado");
        mockMvc.perform(put("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("TaskFlow v2"));
    }
}

package com.taskflow.userservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A diferencia de {@code UserFlowIntegrationTest} (que corre contra H2 en
 * "MODE=PostgreSQL", una emulacion), este test levanta un Postgres 16 real
 * en un contenedor Docker via Testcontainers y ejercita el mismo flujo
 * register -> login -> consulta protegida contra el, con el driver y el
 * dialecto de Postgres de verdad (los mismos que usa el servicio en
 * produccion dentro de docker-compose).
 *
 * <p>Este es el tipo de test que no se pudo correr antes en esta maquina:
 * el disco C: se quedo sin espacio durante el desarrollo y Docker Desktop
 * no llegaba a arrancar, asi que cualquier test que dependiera de un
 * contenedor real quedaba descartado. Con espacio libre y Docker Desktop
 * funcionando de nuevo, {@code @ServiceConnection} conecta automaticamente
 * el `DataSource` de Spring Boot al contenedor sin tener que declarar
 * {@code spring.datasource.url} a mano.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class UserPostgresTestcontainersTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registraLoguaYConsultaUsuarioContraPostgresReal() throws Exception {
        Map<String, String> registerBody = Map.of(
                "name", "Katherine Johnson",
                "email", "katherine@taskflow.dev",
                "password", "secret123"
        );

        String registerResponse = mockMvc.perform(post("/api/users/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(registerBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        Long userId = objectMapper.readTree(registerResponse).get("id").asLong();

        Map<String, String> loginBody = Map.of(
                "email", "katherine@taskflow.dev",
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
                .andExpect(jsonPath("$.email").value("katherine@taskflow.dev"));
    }
}

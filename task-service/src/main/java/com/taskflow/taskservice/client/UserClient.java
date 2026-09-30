package com.taskflow.taskservice.client;

import com.taskflow.taskservice.exception.AssigneeNotFoundException;
import com.taskflow.taskservice.exception.UserServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente REST hacia user-service. Es la pieza que demuestra comunicacion
 * sincronica entre microservicios: antes de asignar una tarea a un usuario,
 * task-service consulta a user-service si ese usuario realmente existe.
 * El JWT de quien hizo el pedido original se reenvia tal cual, para que
 * user-service pueda validarlo con el mismo criterio que usa para sus
 * propios endpoints.
 */
@Component
public class UserClient {

    private final RestClient restClient;

    public UserClient(RestClient.Builder restClientBuilder,
                       @Value("${user-service.base-url}") String userServiceBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(userServiceBaseUrl).build();
    }

    public UserDto getUserById(Long userId, String authorizationHeader) {
        try {
            return restClient.get()
                    .uri("/api/users/{id}", userId)
                    .header("Authorization", authorizationHeader)
                    .retrieve()
                    .body(UserDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new AssigneeNotFoundException(userId);
        } catch (RestClientException ex) {
            throw new UserServiceUnavailableException(ex);
        }
    }
}

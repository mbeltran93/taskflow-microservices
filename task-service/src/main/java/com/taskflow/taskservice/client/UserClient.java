package com.taskflow.taskservice.client;

import com.taskflow.taskservice.exception.AssigneeNotFoundException;
import com.taskflow.taskservice.exception.UserServiceUnavailableException;
import com.taskflow.taskservice.tracing.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
 *
 * Tambien propaga el X-Trace-Id: TraceIdFilter ya dejo el traceId de esta
 * request en el MDC (mismo thread, llamada sincronica), asi que lo leemos de
 * ahi y lo mandamos como header en la llamada saliente. Esto es lo que
 * permite correlacionar, con el mismo traceId, el log de task-service que
 * pide la validacion con el log de user-service que la atiende.
 */
@Component
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final RestClient restClient;

    public UserClient(RestClient.Builder restClientBuilder,
                       @Value("${user-service.base-url}") String userServiceBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(userServiceBaseUrl).build();
    }

    public UserDto getUserById(Long userId, String authorizationHeader) {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        log.info("Llamando a user-service para validar assigneeId={} (propagando X-Trace-Id={})",
                userId, traceId);
        try {
            return restClient.get()
                    .uri("/api/users/{id}", userId)
                    .header("Authorization", authorizationHeader)
                    .header(TraceIdFilter.TRACE_ID_HEADER, traceId)
                    .retrieve()
                    .body(UserDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new AssigneeNotFoundException(userId);
        } catch (RestClientException ex) {
            throw new UserServiceUnavailableException(ex);
        }
    }
}

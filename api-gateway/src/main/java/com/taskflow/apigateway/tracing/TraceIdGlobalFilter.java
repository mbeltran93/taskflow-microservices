package com.taskflow.apigateway.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Trazabilidad distribuida en el punto de entrada: si quien llama al gateway
 * ya manda X-Trace-Id lo reusa, si no genera uno nuevo. Ese mismo valor:
 *   1) se agrega como header a la request mutada que el gateway reenvia al
 *      servicio downstream (user/project/task-service), para que el filtro
 *      equivalente de ese servicio lo detecte y lo reuse en vez de generar
 *      uno propio;
 *   2) se devuelve en la respuesta al cliente, para poder pedirle los logs
 *      de un pedido puntual por su traceId;
 *   3) queda en el log del gateway, explicito en el mensaje.
 *
 * Nota tecnica sobre el punto 3: WebFlux ejecuta el pipeline reactivo sobre
 * threads del event loop de Netty que pueden cambiar entre operadores, asi
 * que el MDC (que es ThreadLocal) no sobrevive de forma confiable solo por
 * hacer MDC.put() aca. Por eso este filtro pasa el traceId como argumento
 * explicito del log en vez de depender unicamente de %X{traceId} en el
 * patron de logging (a diferencia de los otros 3 servicios, que corren
 * sobre Servlet con un thread fijo por request y si pueden confiar en MDC).
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Logger log = LoggerFactory.getLogger(TraceIdGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest originalRequest = exchange.getRequest();

        String traceId = originalRequest.getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        final String finalTraceId = traceId;
        final String method = originalRequest.getMethod().name();
        final String path = originalRequest.getURI().getPath();

        ServerHttpRequest mutatedRequest = originalRequest.mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();

        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().set(TRACE_ID_HEADER, finalTraceId);

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        log.info("traceId={} gateway recibio {} {} y lo reenvia downstream", finalTraceId, method, path);

        return chain.filter(mutatedExchange)
                .doOnSuccess(v -> log.info("traceId={} gateway completo {} {} status={}",
                        finalTraceId, method, path, response.getStatusCode()))
                .doOnError(ex -> log.warn("traceId={} gateway fallo {} {} error={}",
                        finalTraceId, method, path, ex.toString()));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}

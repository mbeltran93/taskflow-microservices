package com.taskflow.projectservice.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Trazabilidad distribuida: si la request entrante ya trae X-Trace-Id (por
 * ejemplo porque vino del api-gateway) lo reusa; si no, genera uno nuevo. El
 * valor queda en el MDC de SLF4J para que TODOS los logs emitidos mientras se
 * procesa esta request lo incluyan automaticamente (ver logging.pattern.console
 * en application.yml), y tambien se devuelve en la respuesta.
 *
 * Va con order=0, antes que JwtAuthFilter (order=1), para que incluso las
 * respuestas 401 queden trazadas.
 */
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            log.info("Request recibido: {} {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            log.info("Request completado: {} {} -> status={}", request.getMethod(),
                    request.getRequestURI(), response.getStatus());
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}

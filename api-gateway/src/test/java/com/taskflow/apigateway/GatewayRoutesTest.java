package com.taskflow.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que el gateway arranca y que las 3 rutas declaradas en
 * application.yml (user/project/task-service) quedan registradas.
 */
@SpringBootTest
class GatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void registraLasTresRutasDeLosMicroservicios() {
        var routeIds = routeLocator.getRoutes()
                .map(route -> route.getId())
                .collectList()
                .block();

        assertThat(routeIds).containsExactlyInAnyOrder("user-service", "project-service", "task-service");
    }

    @Test
    void contextoArrancaCorrectamente() {
        StepVerifier.create(routeLocator.getRoutes())
                .expectNextCount(3)
                .verifyComplete();
    }
}

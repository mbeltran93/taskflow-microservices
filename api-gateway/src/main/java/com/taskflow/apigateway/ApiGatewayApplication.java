package com.taskflow.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada unico de TaskFlow (puerto 8080). Enruta cada request segun
 * el path hacia el microservicio correspondiente (ver application.yml) y no
 * hace validacion de JWT propia: cada servicio downstream valida el token con
 * el mismo secreto compartido, asi que el gateway solo actua como proxy/router.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

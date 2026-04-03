package com.subastas.api_subastas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // Habilita el scheduler para finalizar subastas expiradas automaticamente
public class ApiSubastasApplication {

    /*
     * Metodo main - Punto de entrada de la aplicacion
     * Inicia el contexto de Spring Boot y levanta el servidor embebido
     */
    public static void main(String[] args) {
        SpringApplication.run(ApiSubastasApplication.class, args);
    }
}
package com.ejemplo.tickets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada de la aplicacion (aca arranca todo)
 * @ConfigurationPropertiesScan hace que Spring encuentre AppProperties
 * y lo llene con los valores "app.*" de application.properties
 */
// @SpringBootApplication = le dice a Spring "esta es la app principal, busca
// todas mis clases (controllers, services, ...) y configurate solo"
@SpringBootApplication
// Sin esta anotacion Spring no se daria cuenta de que AppProperties existe
@ConfigurationPropertiesScan
public class TicketsApplication {

    public static void main(String[] args) {
        // Levanta el servidor web embebido (Tomcat) en el puerto que dice application.properties
        SpringApplication.run(TicketsApplication.class, args);
    }
}

package com.ejemplo.tickets.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Permite que el frontend llame a la API aunque se abra desde otro origen
 * (por ejemplo, Live Server de VS Code en el puerto 5500)
 * Si el frontend se abre desde el mismo puerto de Spring Boot (8081) no hace falta
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // CORS = el navegador bloquea llamadas entre "origenes" distintos (otro puerto cuenta como otro origen)
        // Aca le digo que deje pasar al Live Server (5500) para que pueda hablar con mi API
        registry.addMapping("/**") // aplica a todas las rutas
                .allowedOrigins("http://127.0.0.1:5500", "http://localhost:5500")
                .allowedMethods("GET", "POST") // solo uso estos dos metodos
                .allowedHeaders("*");
    }
}

package com.ejemplo.tickets.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP para llamar a n8n.
 * El flujo de n8n hace embedding + busqueda en Qdrant + LLM, asi que puede tardar
 * varios segundos: por eso la espera de lectura es larga (120 s)
 */
// @Configuration = clase donde defino "beans" (objetos que Spring crea y reparte por el proyecto)
@Configuration
public class ClientesConfig {

    // @Bean = Spring crea UN RestClient y lo inyecta en N8nService (por el constructor)
    @Bean
    public RestClient n8nClient() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        // Si en 5 segundos no logra conectarse a n8n, se rinde
        fabrica.setConnectTimeout(5_000);
        // Una vez conectado, espera hasta 2 minutos la respuesta (el LLM puede tardar)
        fabrica.setReadTimeout(120_000);
        return RestClient.builder().requestFactory(fabrica).build();
    }
}

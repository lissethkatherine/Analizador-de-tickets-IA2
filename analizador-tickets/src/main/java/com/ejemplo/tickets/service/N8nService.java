package com.ejemplo.tickets.service;

import com.ejemplo.tickets.config.AppProperties;
import com.ejemplo.tickets.dto.RespuestaAnalisis;
import com.ejemplo.tickets.exception.ServicioExternoException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Conecta Spring Boot con la automatizacion de n8n
 *
 * n8n hace todo el RAG: embedding del ticket, busqueda en Qdrant, armado del
 * contexto y consulta al LLM. Spring Boot solo le envia el texto y recibe el analisis
 */
// @Service = clase de logica de negocio que Spring maneja por mi
@Service
public class N8nService {

    private final RestClient cliente;   // el cliente HTTP configurado en ClientesConfig
    private final URI webhook;          // la URL del webhook de n8n
    private final ObjectMapper mapper;  // convierte JSON <-> objetos Java (Jackson)

    // Spring me pasa estas 3 cosas solo (inyeccion de dependencias por constructor)
    public N8nService(RestClient n8nClient, AppProperties props, ObjectMapper mapper) {
        this.cliente = n8nClient;
        // Leo la URL del webhook desde application.properties
        this.webhook = URI.create(props.n8n().webhookUrl());
        this.mapper = mapper;
    }

    /** Envia el ticket al webhook de n8n y devuelve su respuesta ya convertida a objeto */
    public RespuestaAnalisis analizar(String texto) {
        JsonNode respuesta;
        try {
            // Hago un POST a n8n con el cuerpo {"texto": "..."} y leo la respuesta como JSON generico
            respuesta = cliente.post()
                    .uri(webhook)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("texto", texto))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // n8n SI respondio pero con un codigo de error (404, 500...)
            int codigo = e.getStatusCode().value();
            if (codigo == 404) {
                // El error mas comun: workflow apagado o URL de prueba (/webhook-test/)
                throw new ServicioExternoException(
                        "n8n respondió 404: el workflow no está activo o la URL del webhook es incorrecta "
                                + "(usa /webhook/, no /webhook-test/).", e);
            }
            throw new ServicioExternoException(
                    "n8n respondió con error " + codigo + ". Revisa la ejecución en n8n "
                            + "(credencial de OpenRouter, Qdrant y carga de tickets).", e);
        } catch (ResourceAccessException e) {
            // n8n ni siquiera contesto (apagado, sin red, timeout)
            throw new ServicioExternoException("No se pudo conectar con n8n. ¿Está encendido?", e);
        }

        // Respond to Webhook a veces entrega una lista con un solo elemento
        // (si es lista, me quedo con el primero para trabajar siempre con un objeto)
        if (respuesta != null && respuesta.isArray() && !respuesta.isEmpty()) {
            respuesta = respuesta.get(0);
        }
        // Si no llego nada o no es un objeto JSON, algo anda mal
        if (respuesta == null || !respuesta.isObject()) {
            throw new ServicioExternoException("n8n devolvió una respuesta vacía o inválida.");
        }

        try {
            // Convierto el JSON a mi record RespuestaAnalisis
            return mapper.treeToValue(respuesta, RespuestaAnalisis.class);
        } catch (JsonProcessingException e) {
            throw new ServicioExternoException("n8n devolvió un formato inesperado.", e);
        }
    }

    /** Para /salud: n8n expone /healthz en la raiz de su servidor */
    public boolean disponible() {
        try {
            // resolve("/healthz") cambia solo la ruta y conserva el dominio del webhook
            cliente.get().uri(webhook.resolve("/healthz")).retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            // Cualquier fallo = n8n no esta disponible
            return false;
        }
    }
}

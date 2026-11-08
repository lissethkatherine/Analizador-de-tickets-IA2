package com.ejemplo.tickets.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Prediccion que devuelve el LLM (y que la API entrega al frontend)
 * Los nombres JSON van en snake_case para coincidir con lo que pide el prompt
 */
// Si n8n manda campos de mas que no conozco, los ignora en vez de explotar
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResultadoLlm(
        String categoria,
        String prioridad,
        // @JsonProperty = en el JSON el campo se llama "area_sugerida" (con guion bajo)
        // pero en Java uso camelCase (areaSugerida). Esta anotacion los une
        @JsonProperty("area_sugerida") String areaSugerida,
        @JsonProperty("tiempo_estimado_horas") Integer tiempoEstimadoHoras,
        Double confianza, // entre 0 y 1
        String justificacion,
        // Ids de los tickets historicos en los que se apoyo el modelo
        @JsonProperty("casos_usados") List<Long> casosUsados) { }

package com.ejemplo.tickets.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Un ticket historico recuperado de Qdrant, con su porcentaje de similitud. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CasoSimilar(
        Long id,
        String texto,
        String categoria,
        String prioridad,
        String area,
        // Cuantas horas se demoro en resolverse ese caso (nombre en snake_case en el JSON)
        @JsonProperty("horas_resolucion") Integer horasResolucion,
        // Que tan parecido es al ticket nuevo (0 a 100). Es primitivo "double", no puede ser null
        @JsonProperty("similitud_porcentaje") double similitudPorcentaje) { }

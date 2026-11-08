package com.ejemplo.tickets.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Respuesta completa de POST /analizar
 * Si no hay casos suficientemente parecidos, "analisis" viene null
 * y "mensaje" explica por que
 */
// Este record sirve para dos cosas, leer lo que responde n8n Y lo que le devolvemos al frontend
@JsonIgnoreProperties(ignoreUnknown = true)
public record RespuestaAnalisis(
        String texto,                 // el ticket original
        ResultadoLlm analisis,        // la prediccion (null si no hubo casos parecidos)
        List<CasoSimilar> similares,  // los tickets historicos que respaldan la prediccion
        String modelo,                // modelo de IA usado
        String mensaje) { }           // aviso cuando analisis es null

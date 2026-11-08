package com.ejemplo.tickets.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST /analizar: { "texto": "..." } */
// Un "record" es una clase corta e inmutable que solo guarda datos (Java 17).
// Esto es lo que RECIBE la API del frontend.
public record SolicitudAnalisis(
        // @NotBlank: no puede venir vacio ni solo con espacios
        // @Size: entre 10 y 2000 caracteres. Si no cumple, Spring responde 400 con este mensaje
        @NotBlank(message = "Escribe el ticket que quieres analizar.")
        @Size(min = 10, max = 2000, message = "El ticket debe tener entre 10 y 2000 caracteres.")
        String texto) { }

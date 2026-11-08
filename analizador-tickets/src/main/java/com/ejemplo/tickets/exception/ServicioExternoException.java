package com.ejemplo.tickets.exception;

/** Falla de un servicio externo (OpenRouter, Qdrant) o respuesta invalida del LLM. Se responde con 502. */
// Excepcion propia para cuando falla algo que NO es culpa de mi backend (n8n, el LLM, etc.)
public class ServicioExternoException extends RuntimeException {

    // Version simple: solo con mensaje
    public ServicioExternoException(String mensaje) {
        super(mensaje);
    }

    // Version con la causa original, asi en los logs se ve el error real que la provoco
    public ServicioExternoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

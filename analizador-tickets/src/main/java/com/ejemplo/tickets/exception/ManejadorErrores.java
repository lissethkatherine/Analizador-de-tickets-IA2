package com.ejemplo.tickets.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Convierte las excepciones en respuestas JSON { "error": "..." } con el
 * codigo HTTP correcto, para que el frontend siempre reciba el mismo formato
 */
// @RestControllerAdvice = "atajador global" de errores: si cualquier controller lanza una
// excepcion, cae aca y yo decido que respuesta darle al frontend
@RestControllerAdvice
public class ManejadorErrores {

    // Logger para escribir los errores en la consola
    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    // Formato unico de error: { "error": "mensaje" }. El frontend lee datos.error
    public record ErrorApi(String error) { }

    // Cuando falla @Valid (texto vacio, muy corto o muy largo) -> 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorApi> validacion(MethodArgumentNotValidException e) {
        // Tomo solo el primer mensaje de error para no marear al usuario
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(f -> f.getDefaultMessage()).orElse("Datos inválidos.");
        return ResponseEntity.badRequest().body(new ErrorApi(mensaje));
    }

    // Cuando mandan algo que ni siquiera es un JSON valido -> 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorApi> cuerpoInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorApi("El cuerpo de la petición no es un JSON válido."));
    }

    // Cuando falla n8n / el LLM -> 502 (Bad Gateway: "el servidor de atras fallo")
    @ExceptionHandler(ServicioExternoException.class)
    public ResponseEntity<ErrorApi> servicioExterno(ServicioExternoException e) {
        log.error("Fallo de servicio externo: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorApi(e.getMessage()));
    }

    // Para cuando alguien lanza un ResponseStatusException con su propio codigo HTTP
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorApi> estado(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ErrorApi(e.getReason()));
    }

    // Red de seguridad: cualquier otro error que no previ -> 500
    // Al usuario le muestro un mensaje generico (no le filtro detalles internos) y el detalle va al log
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorApi> inesperado(Exception e) {
        log.error("Error inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorApi("Error interno del servidor."));
    }
}

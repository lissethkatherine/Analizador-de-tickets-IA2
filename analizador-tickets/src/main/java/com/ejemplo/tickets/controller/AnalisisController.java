package com.ejemplo.tickets.controller;

import com.ejemplo.tickets.dto.RespuestaAnalisis;
import com.ejemplo.tickets.dto.SolicitudAnalisis;
import com.ejemplo.tickets.model.AnalisisTicket;
import com.ejemplo.tickets.repository.AnalisisTicketRepository;
import com.ejemplo.tickets.service.AnalisisService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// @RestController = esta clase expone endpoints de la API y todo lo que devuelve se manda como JSON
@RestController
public class AnalisisController {

    private final AnalisisService servicio;
    private final AnalisisTicketRepository repositorio;

    public AnalisisController(AnalisisService servicio, AnalisisTicketRepository repositorio) {
        this.servicio = servicio;
        this.repositorio = repositorio;
    }

    /** Analiza un ticket nuevo. @Valid activa las reglas de SolicitudAnalisis (400 si fallan) */
    // POST /analizar -> recibe {"texto": "..."} y devuelve el analisis completo
    @PostMapping("/analizar")
    public RespuestaAnalisis analizar(@Valid @RequestBody SolicitudAnalisis solicitud) {
        // El controller casi no hace nada: solo limpia espacios y delega al service
        return servicio.analizar(solicitud.texto().trim());
    }

    /** Ultimos 20 analisis guardados*/
    // GET /historial -> lista de los ultimos 20 analisis (el frontend la usa para el historial)
    @GetMapping("/historial")
    public List<AnalisisTicket> historial() {
        return repositorio.findTop20ByOrderByCreadoEnDesc();
    }
}

package com.ejemplo.tickets.controller;

import com.ejemplo.tickets.repository.AnalisisTicketRepository;
import com.ejemplo.tickets.service.N8nService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Endpoint tipo "health check" para ver rapido si todo esta funcionando
@RestController
public class SaludController {

    private final AnalisisTicketRepository repositorio;
    private final N8nService n8n;

    public SaludController(AnalisisTicketRepository repositorio, N8nService n8n) {
        this.repositorio = repositorio;
        this.n8n = n8n;
    }

    /** Comprueba que la API, la base de datos y n8n respondan */
    // GET /salud -> {"api":"ok","baseDatos":"ok","n8n":"ok"}
    @GetMapping("/salud")
    public Map<String, String> salud() {
        // LinkedHashMap mantiene el orden en que meto las claves
        Map<String, String> estado = new LinkedHashMap<>();
        // Si llego hasta aca, la API esta viva
        estado.put("api", "ok");
        try {
            // count() hace una consulta sencilla; si la BD esta caida, lanza excepcion
            repositorio.count();
            estado.put("baseDatos", "ok");
        } catch (Exception e) {
            estado.put("baseDatos", "error");
        }
        // Le pregunto a n8n si esta disponible
        estado.put("n8n", n8n.disponible() ? "ok" : "error");
        return estado;
    }
}

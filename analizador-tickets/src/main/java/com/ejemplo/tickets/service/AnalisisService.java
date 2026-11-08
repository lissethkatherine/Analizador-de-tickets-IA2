package com.ejemplo.tickets.service;

import com.ejemplo.tickets.dto.CasoSimilar;
import com.ejemplo.tickets.dto.RespuestaAnalisis;
import com.ejemplo.tickets.dto.ResultadoLlm;
import com.ejemplo.tickets.exception.ServicioExternoException;
import com.ejemplo.tickets.model.AnalisisTicket;
import com.ejemplo.tickets.repository.AnalisisTicketRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Flujo de un analisis en Spring Boot:
 *   1) envia el ticket a n8n (que hace embedding + Qdrant + LLM),
 *   2) valida lo que n8n devuelve,
 *   3) guarda el resultado en el historial (PostgreSQL),
 *   4) responde al frontend
 */
@Service
public class AnalisisService {

    // Mensaje por defecto cuando no se encontraron casos parecidos
    private static final String SIN_INFORMACION =
            "No hay información suficiente en el catálogo para predecir este ticket.";

    private final N8nService n8nService;
    private final AnalisisTicketRepository repositorio;

    public AnalisisService(N8nService n8nService, AnalisisTicketRepository repositorio) {
        this.n8nService = n8nService;
        this.repositorio = repositorio;
    }

    // Metodo principal: aca se ve todo el recorrido de un ticket
    public RespuestaAnalisis analizar(String texto) {
        // 1) Le pregunto a n8n (es lo que tarda unos segundos)
        RespuestaAnalisis respuesta = n8nService.analizar(texto);
        // Si "similares" viene null lo cambio por lista vacia para evitar NullPointerException
        List<CasoSimilar> similares = respuesta.similares() == null ? List.of() : respuesta.similares();

        // n8n no encontro casos parecidos: no hay prediccion que validar ni guardar
        if (respuesta.analisis() == null) {
            String mensaje = (respuesta.mensaje() == null || respuesta.mensaje().isBlank())
                    ? SIN_INFORMACION : respuesta.mensaje();
            return new RespuestaAnalisis(texto, null, similares, respuesta.modelo(), mensaje);
        }

        // 2) Valido la prediccion, 3) la guardo en la BD y 4) la devuelvo al frontend
        ResultadoLlm validado = validar(respuesta.analisis(), similares);
        guardar(texto, validado, respuesta.modelo());
        return new RespuestaAnalisis(texto, validado, similares, respuesta.modelo(), null);
    }

    /** Segunda barrera de seguridad: aunque n8n ya valida, el backend vuelve a comprobar todo */
    private ResultadoLlm validar(ResultadoLlm r, List<CasoSimilar> similares) {
        // Compruebo que categoria, prioridad y area esten dentro de las listas oficiales
        // (si el LLM dijo "facturacion", lo corrijo a "Facturación")
        String categoria = Catalogo.canonico(r.categoria(), Catalogo.CATEGORIAS);
        String prioridad = Catalogo.canonico(r.prioridad(), Catalogo.PRIORIDADES);
        String area = Catalogo.canonico(r.areaSugerida(), Catalogo.AREAS);

        // Si alguno es null, el LLM invento un valor que no existe -> error
        if (categoria == null || prioridad == null || area == null) {
            throw new ServicioExternoException(
                    "n8n devolvió valores fuera del catálogo permitido. Intenta de nuevo.");
        }

        // Horas: si el LLM dio un numero valido (>= 0) lo uso
        // Si no, calculo el promedio de horas de los casos similares (o 24 si no hay ninguno)
        int horas = (r.tiempoEstimadoHoras() != null && r.tiempoEstimadoHoras() >= 0)
                ? r.tiempoEstimadoHoras()
                : (int) Math.round(similares.stream()
                        .mapToInt(c -> c.horasResolucion() == null ? 0 : c.horasResolucion())
                        .average().orElse(24));

        // Confianza: la fuerzo a quedar entre 0 y 1 (si es null, 0)
        double confianza = r.confianza() == null ? 0.0 : Math.max(0.0, Math.min(1.0, r.confianza()));

        // Solo dejo los ids de casos que realmente estaban en la lista de similares
        // (el LLM podria inventarse ids) y quito repetidos con distinct()
        Set<Long> idsValidos = similares.stream().map(CasoSimilar::id).collect(Collectors.toSet());
        List<Long> casosUsados = r.casosUsados() == null ? List.of()
                : r.casosUsados().stream().filter(idsValidos::contains).distinct().toList();

        // strip() quita espacios sobrantes al inicio y al final
        String justificacion = r.justificacion() == null ? "" : r.justificacion().strip();

        return new ResultadoLlm(categoria, prioridad, area, horas, confianza, justificacion, casosUsados);
    }

    // Pasa los datos del analisis a la entidad y la guarda en PostgreSQL
    private void guardar(String texto, ResultadoLlm a, String modelo) {
        AnalisisTicket e = new AnalisisTicket();
        e.setTextoTicket(texto);
        e.setCategoria(a.categoria());
        e.setPrioridad(a.prioridad());
        e.setAreaSugerida(a.areaSugerida());
        e.setTiempoEstimadoHoras(a.tiempoEstimadoHoras());
        e.setConfianza(a.confianza());
        e.setJustificacion(a.justificacion());
        // Convierto la lista [3, 7, 12] en el texto "3,7,12" para guardarla en una sola columna
        e.setIdsCasosUsados(a.casosUsados().stream().map(String::valueOf).collect(Collectors.joining(",")));
        e.setModelo(modelo);
        // save() hace el INSERT (y @PrePersist le pone la fecha)
        repositorio.save(e);
    }
}

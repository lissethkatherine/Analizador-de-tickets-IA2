package com.ejemplo.tickets.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Historial: un registro por cada ticket analizado por el sistema */
// @Entity = esta clase representa una tabla en la base de datos (JPA/Hibernate la crea sola)
@Entity
// El nombre real de la tabla en PostgreSQL
@Table(name = "analisis_ticket")
// Lombok me genera los getters y setters para no escribirlos a mano
@Getter
@Setter
// Constructor vacio, JPA lo necesita para poder crear objetos al leer de la BD
@NoArgsConstructor
public class AnalisisTicket {

    // Clave primaria, IDENTITY = la base de datos le va poniendo 1, 2, 3... automatico
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // El texto original que escribio el usuario. No puede ser null y llega hasta 2000 caracteres
    // (el mismo limite que valida SolicitudAnalisis)
    @Column(nullable = false, length = 2000)
    private String textoTicket;

    // Estos 5 campos son la prediccion que devolvio el LLM
    private String categoria;
    private String prioridad;
    private String areaSugerida;
    private Integer tiempoEstimadoHoras;
    private Double confianza; // va de 0 a 1

    // Explicacion del modelo de por que decidio eso (le subo el largo porque por defecto son 255)
    @Column(length = 2000)
    private String justificacion;

    /** Ids de los casos historicos citados, separados por coma Ej: "3,7,12" */
    // Lo guardo como un String simple para no complicarme con otra tabla
    private String idsCasosUsados;

    // Que modelo de IA hizo el analisis (ej: openai/gpt-4.1-mini)
    private String modelo;

    // Fecha y hora en que se guardo el analisis
    private LocalDateTime creadoEn;

    // @PrePersist = JPA ejecuta este metodo justo ANTES de insertar el registro,
    // asi la fecha se pone sola y no tengo que acordarme de hacerlo
    @PrePersist
    void antesDeGuardar() {
        this.creadoEn = LocalDateTime.now();
    }
}

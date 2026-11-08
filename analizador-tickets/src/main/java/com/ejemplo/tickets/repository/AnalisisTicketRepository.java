package com.ejemplo.tickets.repository;

import com.ejemplo.tickets.model.AnalisisTicket;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// Extender JpaRepository me regala gratis save(), findAll(), count(), delete(), etc.
// sin escribir nada de SQL. <Entidad, TipoDelId>
public interface AnalisisTicketRepository extends JpaRepository<AnalisisTicket, Long> {

    /** Spring genera la consulta solo a partir del nombre del metodo */
    // Se lee asi: Top20 (los primeros 20) + OrderBy CreadoEn Desc (del mas nuevo al mas viejo)
    List<AnalisisTicket> findTop20ByOrderByCreadoEnDesc();
}

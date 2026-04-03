package com.subastas.api_subastas.repository;

import com.subastas.api_subastas.model.Subasta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface SubastaRepository extends JpaRepository<Subasta, Long> {
    
    /*
     * Obtiene subastas por estado exacto
     * Parametro: estado - PENDIENTE, ACTIVA, FINALIZADA
     * Retorna: Lista de subastas con el estado especificado
     */
    List<Subasta> findByEstado(String estado);
    
    /*
     * Obtiene subastas por estado sin distincion de mayusculas/minusculas
     * Parametro: estado - PENDIENTE, ACTIVA, FINALIZADA
     * Retorna: Lista de subastas con el estado especificado (case insensitive)
     */
    List<Subasta> findByEstadoIgnoreCase(String estado);
    
    /*
     * Obtiene subastas que cumplen dos condiciones:
     *   1. Tienen un estado especifico
     *   2. Su tiempo de finalizacion es anterior a la fecha dada
     * Parametros: estado - Estado de la subasta, fecha - Limite de tiempo
     * Retorna: Lista de subastas expiradas
     * Uso: Scheduler para finalizar subastas automaticamente
     */
    List<Subasta> findByEstadoAndTiempoFinBefore(String estado, LocalDateTime fecha);
}
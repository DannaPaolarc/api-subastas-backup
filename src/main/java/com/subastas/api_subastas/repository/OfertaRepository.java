package com.subastas.api_subastas.repository;

import com.subastas.api_subastas.model.Oferta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OfertaRepository extends JpaRepository<Oferta, Long> {
    
    /*
     * Obtiene todas las ofertas de una subasta ordenadas por monto descendente
     * Parametro: subastaId - Identificador de la subasta
     * Retorna: Lista de ofertas ordenadas de mayor a menor monto
     */
    List<Oferta> findBySubastaIdOrderByMontoDesc(Long subastaId);
    
    /*
     * Obtiene las 10 mejores ofertas de una subasta
     * Parametro: subastaId - Identificador de la subasta
     * Retorna: Lista con las 10 ofertas de mayor monto
     * Uso: Mostrar ranking en la interfaz de usuario
     */
    List<Oferta> findTop10BySubastaIdOrderByMontoDesc(Long subastaId);
}
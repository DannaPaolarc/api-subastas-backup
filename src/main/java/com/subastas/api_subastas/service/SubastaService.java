package com.subastas.api_subastas.service;

import com.subastas.api_subastas.model.*;
import com.subastas.api_subastas.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SubastaService {

    @Autowired 
    private SubastaRepository subastaRepo;
    
    @Autowired 
    private OfertaRepository ofertaRepo;
    
    @Autowired 
    private UsuarioRepository usuarioRepo;
    
    @Autowired 
    private SimpMessagingTemplate ws;

    // Formateador para hora en formato 24 horas
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    // METODOS PUBLICOS
    
    /*
     * Obtiene una subasta por su ID
     * Parametro: id - Identificador de la subasta
     * Retorna: Subasta encontrada
     * Lanza: RuntimeException si no existe
     */
    public Subasta obtenerPorId(Long id) {
        return subastaRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Subasta no encontrada"));
    }

    /*
     * Obtiene todas las subastas activas
     * Retorna: Lista de subastas con estado ACTIVA
     */
    public List<Subasta> getActivas() { 
        return subastaRepo.findByEstado("ACTIVA"); 
    }
    
    /*
     * Obtiene todas las subastas (activas y finalizadas)
     * Retorna: Lista completa de subastas
     */
    public List<Subasta> getTodas() { 
        return subastaRepo.findAll(); 
    }

    /*
     * Obtiene las 10 mejores ofertas de una subasta
     * Parametro: subastaId - Identificador de la subasta
     * Retorna: Lista de ofertas ordenadas por monto descendente
     */
    public List<Oferta> getOfertas(Long subastaId) {
        return ofertaRepo.findTop10BySubastaIdOrderByMontoDesc(subastaId);
    }

    /*
     * Crea una nueva subasta en estado PENDIENTE
     * Parametro: s - Objeto Subasta con datos basicos
     * Validaciones: Precio inicial > 0, Producto no vacio
     * Retorna: Subasta creada
     */
    @Transactional
    public Subasta crear(Subasta s) {
        if (s.getPrecioInicial() == null || s.getPrecioInicial() <= 0) {
            throw new RuntimeException("Precio inicial invalido");
        }
        if (s.getProducto() == null || s.getProducto().isBlank()) {
            throw new RuntimeException("Producto requerido");
        }
        
        s.setPrecioActual(s.getPrecioInicial());
        s.setEstado("PENDIENTE");
        s.setIncrementoMinimo(s.getIncrementoMinimo() != null ? s.getIncrementoMinimo() : 100.0);
        
        return subastaRepo.save(s);
    }

    /*
     * Inicia una subasta pendiente
     * Parametros: id - ID de subasta, duracionMinutos - Duracion en minutos
     * Validaciones: Subasta no activa, Duracion positiva
     * Retorna: Subasta iniciada con estado ACTIVA y tiempo de fin calculado
     */
    @Transactional
    public Subasta iniciar(Long id, int duracionMinutos) {
        Subasta s = subastaRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Subasta no encontrada"));

        if ("ACTIVA".equals(s.getEstado())) {
            throw new RuntimeException("La subasta ya esta activa");
        }
        if (duracionMinutos <= 0) {
            throw new RuntimeException("Duracion invalida");
        }

        s.setEstado("ACTIVA");
        s.setTiempoInicio(LocalDateTime.now());
        s.setTiempoFin(LocalDateTime.now().plusMinutes(duracionMinutos));

        subastaRepo.save(s);

        broadcast(s.getId(), sistemaMsg("Subasta iniciada. Duracion: " + duracionMinutos + " minutos", s.getId()));
        return s;
    }

    /*
     * Finaliza una subasta activa manualmente
     * Parametro: id - ID de subasta
     * Retorna: Subasta finalizada
     */
    @Transactional
    public Subasta finalizar(Long id) {
        Subasta s = subastaRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Subasta no encontrada"));

        if ("FINALIZADA".equals(s.getEstado())) {
            throw new RuntimeException("Ya esta finalizada");
        }

        return cerrarSubasta(s);
    }

    /*
     * Realiza una oferta en una subasta activa
     * Parametros: subastaId - ID de subasta, usuarioId - ID del usuario, monto - Cantidad ofertada
     * Validaciones:
     *   - Monto > 0
     *   - Subasta esta ACTIVA
     *   - Subasta no expirada
     *   - Monto >= precioActual + incrementoMinimo
     * Logica adicional:
     *   - Si quedan menos de 10 segundos, se extiende el tiempo +30 segundos
     * Retorna: Subasta actualizada
     */
    @Transactional
    public Subasta ofertar(Long subastaId, Long usuarioId, Double monto) {
        Subasta s = subastaRepo.findById(subastaId)
            .orElseThrow(() -> new RuntimeException("Subasta no encontrada"));
        Usuario u = usuarioRepo.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // VALIDACIONES
        if (monto == null || monto <= 0) {
            throw new RuntimeException("Monto invalido");
        }
        if ("FINALIZADA".equals(s.getEstado())) {
            throw new RuntimeException("La subasta ya termino");
        }
        if (!"ACTIVA".equals(s.getEstado())) {
            throw new RuntimeException("La subasta no esta activa");
        }
        if (s.getTiempoFin().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("La subasta ya expiro");
        }
        
        double minimo = s.getPrecioActual() + s.getIncrementoMinimo();
        if (monto < minimo) {
            throw new RuntimeException("El monto minimo es $" + String.format("%,.0f", minimo));
        }

        // CREAR OFERTA
        Oferta oferta = new Oferta();
        oferta.setSubasta(s);
        oferta.setUsuario(u);
        oferta.setMonto(monto);
        ofertaRepo.save(oferta);

        // ACTUALIZAR SUBASTA
        s.setPrecioActual(monto);
        s.setGanador(u.getNombre());

        // LOGICA DE EXTENSION DE TIEMPO (ULTIMOS 10 SEGUNDOS)
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limiteExtension = ahora.plusSeconds(10);
        
        if (s.getTiempoFin().isBefore(limiteExtension)) {
            LocalDateTime nuevoTiempoFin = s.getTiempoFin().plusSeconds(30);
            s.setTiempoFin(nuevoTiempoFin);
            
            // Mensaje especial para todos los usuarios
            MensajeChat extensionMsg = new MensajeChat();
            extensionMsg.setUsuario("SISTEMA");
            extensionMsg.setContenido("Ultimos segundos. Se agregaron 30 segundos mas");
            extensionMsg.setTipo("SISTEMA");
            extensionMsg.setHora(LocalTime.now().format(FMT));
            extensionMsg.setSubastaId(subastaId);
            broadcast(subastaId, extensionMsg);
        }

        subastaRepo.save(s);

        // BROADCAST DE LA PUJA
        MensajeChat pujaMsg = new MensajeChat();
        pujaMsg.setUsuario(u.getNombre());
        pujaMsg.setContenido(" oferto $" + String.format("%,.0f", monto));
        pujaMsg.setTipo("PUJA");
        pujaMsg.setHora(LocalTime.now().format(FMT));
        pujaMsg.setSubastaId(subastaId);
        broadcast(subastaId, pujaMsg);

        // Broadcast del nuevo precio
        MensajeChat precioMsg = new MensajeChat();
        precioMsg.setUsuario("SISTEMA");
        precioMsg.setContenido("PRECIO_ACTUAL:" + monto);
        precioMsg.setTipo("PRECIO");
        precioMsg.setHora(LocalTime.now().format(FMT));
        precioMsg.setSubastaId(subastaId);
        broadcast(subastaId, precioMsg);

        return s;
    }

    // METODOS PRIVADOS

    /*
     * Scheduler que se ejecuta cada 3 segundos
     * Busca subastas ACTIVAS cuyo tiempo de fin ya paso
     * Las finaliza automaticamente
     */
    @Scheduled(fixedDelay = 3000)
    public void revisarExpiradas() {
        List<Subasta> expiradas = subastaRepo
                .findByEstadoAndTiempoFinBefore("ACTIVA", LocalDateTime.now());

        for (Subasta s : expiradas) {
            cerrarSubasta(s);
        }
    }

    /*
     * Cierra una subasta y determina el ganador
     * Parametro: s - Subasta a finalizar
     * Retorna: Subasta con estado FINALIZADA y ganador asignado
     */
    private Subasta cerrarSubasta(Subasta s) {
        if ("FINALIZADA".equals(s.getEstado())) {
            return s;
        }

        s.setEstado("FINALIZADA");

        List<Oferta> ofertas = ofertaRepo.findBySubastaIdOrderByMontoDesc(s.getId());

        if (!ofertas.isEmpty()) {
            Oferta mejorOferta = ofertas.get(0);
            s.setGanador(mejorOferta.getUsuario().getNombre());
        }

        subastaRepo.save(s);

        // Mensaje de finalizacion
        String msg = s.getGanador() != null
                ? "SUBASTA FINALIZADA. Ganador: " + s.getGanador() + " con $" + String.format("%,.0f", s.getPrecioActual())
                : "Subasta finalizada sin ofertas.";

        MensajeChat finalMsg = new MensajeChat();
        finalMsg.setUsuario("SISTEMA");
        finalMsg.setContenido(msg);
        finalMsg.setTipo("FINALIZADA");
        finalMsg.setHora(LocalTime.now().format(FMT));
        finalMsg.setSubastaId(s.getId());
        
        broadcast(s.getId(), finalMsg);
        
        // Mensaje especial con formato para el frontend
        MensajeChat resultadoMsg = new MensajeChat();
        resultadoMsg.setUsuario("SISTEMA");
        resultadoMsg.setContenido("SUBASTA_FINALIZADA:" + s.getId() + ":" + msg);
        resultadoMsg.setTipo("FINALIZADA");
        resultadoMsg.setHora(LocalTime.now().format(FMT));
        resultadoMsg.setSubastaId(s.getId());
        broadcast(s.getId(), resultadoMsg);

        return s;
    }

    /*
     * Envia un mensaje WebSocket a los canales correspondientes
     * Parametros: subastaId - ID de subasta, msg - Mensaje a enviar
     * Destinos: /topic/subasta/{id} (especifico) y /topic/publico (general)
     */
    private void broadcast(Long subastaId, MensajeChat msg) {
        ws.convertAndSend("/topic/subasta/" + subastaId, msg);
        ws.convertAndSend("/topic/publico", msg);
    }

    /*
     * Crea un mensaje de sistema estandarizado
     * Parametros: texto - Contenido del mensaje, subastaId - ID de subasta
     * Retorna: MensajeChat configurado como tipo SISTEMA
     */
    private MensajeChat sistemaMsg(String texto, Long subastaId) {
        MensajeChat m = new MensajeChat();
        m.setUsuario("SISTEMA");
        m.setContenido(texto);
        m.setTipo("SISTEMA");
        m.setHora(LocalTime.now().format(FMT));
        m.setSubastaId(subastaId);
        return m;
    }
}
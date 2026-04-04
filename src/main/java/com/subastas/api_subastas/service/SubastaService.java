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
import java.time.ZoneId;
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

    // Zona horaria de Mexico (Ciudad de Mexico)
    private static final ZoneId ZONA_MEXICO = ZoneId.of("America/Mexico_City");
    
    // Formateador para hora en formato 24 horas
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    // METODOS PUBLICOS
    
    public Subasta obtenerPorId(Long id) {
        return subastaRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Subasta no encontrada"));
    }

    public List<Subasta> getActivas() { 
        return subastaRepo.findByEstado("ACTIVA"); 
    }
    
    public List<Subasta> getTodas() { 
        return subastaRepo.findAll(); 
    }

    public List<Oferta> getOfertas(Long subastaId) {
        return ofertaRepo.findTop10BySubastaIdOrderByMontoDesc(subastaId);
    }

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

    @Transactional
    public Subasta iniciar(Long id, int duracionMinutos) {
        System.out.println("=== DURACION RECIBIDA: " + duracionMinutos + " minutos ===");
        Subasta s = subastaRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Subasta no encontrada"));

        if ("ACTIVA".equals(s.getEstado())) {
            throw new RuntimeException("La subasta ya esta activa");
        }
        if (duracionMinutos <= 0) {
            throw new RuntimeException("Duracion invalida");
        }

        s.setEstado("ACTIVA");
        s.setTiempoInicio(LocalDateTime.now(ZONA_MEXICO));
        s.setTiempoFin(LocalDateTime.now(ZONA_MEXICO).plusMinutes(duracionMinutos));

        subastaRepo.save(s);

        broadcast(s.getId(), sistemaMsg("Subasta iniciada. Duracion: " + duracionMinutos + " minutos", s.getId()));
        return s;
    }

    @Transactional
    public Subasta finalizar(Long id) {
        Subasta s = subastaRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Subasta no encontrada"));

        if ("FINALIZADA".equals(s.getEstado())) {
            throw new RuntimeException("Ya esta finalizada");
        }

        return cerrarSubasta(s);
    }

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
        
        // Usar zona horaria de Mexico
        LocalDateTime ahoraMexico = LocalDateTime.now(ZONA_MEXICO);
        if (s.getTiempoFin().isBefore(ahoraMexico)) {
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
        LocalDateTime limiteExtension = ahoraMexico.plusSeconds(10);
        
        if (s.getTiempoFin().isBefore(limiteExtension)) {
            LocalDateTime nuevoTiempoFin = s.getTiempoFin().plusSeconds(30);
            s.setTiempoFin(nuevoTiempoFin);
            
            // Mensaje especial para todos los usuarios
            MensajeChat extensionMsg = new MensajeChat();
            extensionMsg.setUsuario("SISTEMA");
            extensionMsg.setContenido("Ultimos segundos. Se agregaron 30 segundos mas");
            extensionMsg.setTipo("SISTEMA");
            extensionMsg.setHora(LocalTime.now(ZONA_MEXICO).format(FMT));
            extensionMsg.setSubastaId(subastaId);
            broadcast(subastaId, extensionMsg);
        }

        subastaRepo.save(s);

        // BROADCAST DE LA PUJA
        MensajeChat pujaMsg = new MensajeChat();
        pujaMsg.setUsuario(u.getNombre());
        pujaMsg.setContenido(" oferto $" + String.format("%,.0f", monto));
        pujaMsg.setTipo("PUJA");
        pujaMsg.setHora(LocalTime.now(ZONA_MEXICO).format(FMT));
        pujaMsg.setSubastaId(subastaId);
        broadcast(subastaId, pujaMsg);

        // Broadcast del nuevo precio
        MensajeChat precioMsg = new MensajeChat();
        precioMsg.setUsuario("SISTEMA");
        precioMsg.setContenido("PRECIO_ACTUAL:" + monto);
        precioMsg.setTipo("PRECIO");
        precioMsg.setHora(LocalTime.now(ZONA_MEXICO).format(FMT));
        precioMsg.setSubastaId(subastaId);
        broadcast(subastaId, precioMsg);

        return s;
    }

    // METODOS PRIVADOS

    @Scheduled(fixedDelay = 3000)
    public void revisarExpiradas() {
        LocalDateTime ahoraMexico = LocalDateTime.now(ZONA_MEXICO);
        List<Subasta> expiradas = subastaRepo
                .findByEstadoAndTiempoFinBefore("ACTIVA", ahoraMexico);

        for (Subasta s : expiradas) {
            cerrarSubasta(s);
        }
    }

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
        finalMsg.setHora(LocalTime.now(ZONA_MEXICO).format(FMT));
        finalMsg.setSubastaId(s.getId());
        
        broadcast(s.getId(), finalMsg);
        
        // Mensaje especial con formato para el frontend
        MensajeChat resultadoMsg = new MensajeChat();
        resultadoMsg.setUsuario("SISTEMA");
        resultadoMsg.setContenido("SUBASTA_FINALIZADA:" + s.getId() + ":" + msg);
        resultadoMsg.setTipo("FINALIZADA");
        resultadoMsg.setHora(LocalTime.now(ZONA_MEXICO).format(FMT));
        resultadoMsg.setSubastaId(s.getId());
        broadcast(s.getId(), resultadoMsg);

        return s;
    }

    private void broadcast(Long subastaId, MensajeChat msg) {
        ws.convertAndSend("/topic/subasta/" + subastaId, msg);
        ws.convertAndSend("/topic/publico", msg);
    }

    private MensajeChat sistemaMsg(String texto, Long subastaId) {
        MensajeChat m = new MensajeChat();
        m.setUsuario("SISTEMA");
        m.setContenido(texto);
        m.setTipo("SISTEMA");
        m.setHora(LocalTime.now(ZONA_MEXICO).format(FMT));
        m.setSubastaId(subastaId);
        return m;
    }
}
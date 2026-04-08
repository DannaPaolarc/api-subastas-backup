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

    @Autowired private SubastaRepository subastaRepo;
    @Autowired private OfertaRepository ofertaRepo;
    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private SimpMessagingTemplate ws;

    private static final ZoneId ZONA_UTC = ZoneId.of("UTC");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    public Subasta obtenerPorId(Long id) {
        return subastaRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Subasta no encontrada"));
    }

    public List<Subasta> getActivas() { return subastaRepo.findByEstado("ACTIVA"); }
    
    public List<Subasta> getTodas() { return subastaRepo.findAll(); }

    public List<Oferta> getOfertas(Long subastaId) {
        return ofertaRepo.findTop10BySubastaIdOrderByMontoDesc(subastaId);
    }

    @Transactional
    public Subasta crear(Subasta s) {
        if (s.getPrecioInicial() == null || s.getPrecioInicial() <= 0) throw new RuntimeException("Precio inicial invalido");
        if (s.getProducto() == null || s.getProducto().isBlank()) throw new RuntimeException("Producto requerido");
        
        s.setPrecioActual(s.getPrecioInicial());
        s.setEstado("PENDIENTE");
        s.setIncrementoMinimo(s.getIncrementoMinimo() != null ? s.getIncrementoMinimo() : 100.0);
        return subastaRepo.save(s);
    }

    @Transactional
    public Subasta iniciar(Long id, int duracionMinutos) {
        Subasta s = subastaRepo.findById(id).orElseThrow(() -> new RuntimeException("No encontrada"));

        if ("ACTIVA".equals(s.getEstado())) throw new RuntimeException("La subasta ya esta activa");
        if (duracionMinutos <= 0) throw new RuntimeException("Duracion invalida");

        s.setEstado("ACTIVA");
        // Forzamos UTC exacto para evitar el desfase de 300 min
        LocalDateTime ahora = LocalDateTime.now(ZONA_UTC);
        s.setTiempoInicio(ahora);
        s.setTiempoFin(ahora.plusMinutes(duracionMinutos));

        subastaRepo.save(s);
        broadcast(s.getId(), sistemaMsg("Subasta iniciada. Finaliza: " + s.getTiempoFin().format(FMT) + " UTC", s.getId()));
        return s;
    }

    @Transactional
    public Subasta ofertar(Long subastaId, Long usuarioId, Double monto) {
        Subasta s = subastaRepo.findById(subastaId).orElseThrow(() -> new RuntimeException("No encontrada"));
        Usuario u = usuarioRepo.findById(usuarioId).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (monto == null || monto <= 0) throw new RuntimeException("Monto invalido");
        if (!"ACTIVA".equals(s.getEstado())) throw new RuntimeException("La subasta no esta activa");
        
        LocalDateTime ahoraUTC = LocalDateTime.now(ZONA_UTC);
        if (s.getTiempoFin().isBefore(ahoraUTC)) throw new RuntimeException("La subasta ya expiro");
        
        double minimo = s.getPrecioActual() + s.getIncrementoMinimo();
        if (monto < minimo) throw new RuntimeException("El monto minimo es $" + String.format("%,.0f", minimo));

        Oferta oferta = new Oferta();
        oferta.setSubasta(s);
        oferta.setUsuario(u);
        oferta.setMonto(monto);
        ofertaRepo.save(oferta);

        s.setPrecioActual(monto);
        s.setGanador(u.getNombre());

        // Extensión de tiempo (Últimos 15 segundos)
        if (s.getTiempoFin().isBefore(ahoraUTC.plusSeconds(15))) {
            s.setTiempoFin(s.getTiempoFin().plusSeconds(30));
            broadcast(subastaId, sistemaMsg("¡Puja de último momento! +30 seg.", subastaId));
        }

        subastaRepo.save(s);

        //  Notificamos la PUJA
        MensajeChat pujaMsg = new MensajeChat();
        pujaMsg.setUsuario(u.getNombre());
        pujaMsg.setContenido("PUJA de $" + String.format("%,.0f", monto));
        pujaMsg.setTipo("PUJA");
        pujaMsg.setHora(LocalTime.now(ZONA_UTC).format(FMT));
        pujaMsg.setSubastaId(subastaId);
        broadcast(subastaId, pujaMsg);

        //  Notificamos cambio de PRECIO
        MensajeChat precioMsg = new MensajeChat();
        precioMsg.setUsuario("SISTEMA");
        precioMsg.setContenido("PRECIO_ACTUAL:" + monto);
        precioMsg.setTipo("PRECIO");
        precioMsg.setHora(LocalTime.now(ZONA_UTC).format(FMT));
        precioMsg.setSubastaId(subastaId);
        broadcast(subastaId, precioMsg);

        return s;
    }

    @Scheduled(fixedDelay = 3000)
    public void revisarExpiradas() {
        LocalDateTime ahoraUTC = LocalDateTime.now(ZONA_UTC);
        List<Subasta> expiradas = subastaRepo.findByEstadoAndTiempoFinBefore("ACTIVA", ahoraUTC);
        for (Subasta s : expiradas) { cerrarSubasta(s); }
    }

    @Transactional
    public Subasta finalizar(Long id) {
        Subasta s = subastaRepo.findById(id).orElseThrow(() -> new RuntimeException("No encontrada"));
        return cerrarSubasta(s);
    }

    private Subasta cerrarSubasta(Subasta s) {
        if ("FINALIZADA".equals(s.getEstado())) return s;
        s.setEstado("FINALIZADA");
        List<Oferta> ofertas = ofertaRepo.findBySubastaIdOrderByMontoDesc(s.getId());
        if (!ofertas.isEmpty()) s.setGanador(ofertas.get(0).getUsuario().getNombre());
        subastaRepo.save(s);

        String texto = s.getGanador() != null ? "FINALIZADA. Ganador: " + s.getGanador() : "Finalizada sin ofertas.";
        broadcast(s.getId(), sistemaMsg(texto, s.getId()));
        return s;
    }

    private void broadcast(Long subastaId, MensajeChat msg) {
        ws.convertAndSend("/topic/subasta/" + subastaId, msg);
    }

    private MensajeChat sistemaMsg(String texto, Long subastaId) {
        MensajeChat m = new MensajeChat();
        m.setUsuario("SISTEMA");
        m.setContenido(texto);
        m.setTipo("SISTEMA");
        m.setHora(LocalTime.now(ZONA_UTC).format(FMT));
        m.setSubastaId(subastaId);
        return m;
    }
}
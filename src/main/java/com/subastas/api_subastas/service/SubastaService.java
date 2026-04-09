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

//contiene toda la logica de mi app para gestionar las subastas maneja creacion, inicio el tiempo lo majenaa ahi 
// Tecnologias que utilizo Spring Service, Transaccional, Scheduled, WebSocket
@Service
public class SubastaService {

    @Autowired private SubastaRepository subastaRepo;
    @Autowired private OfertaRepository ofertaRepo;
    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private SimpMessagingTemplate ws;

    // Configuración para México (Tamaulipas)
    private static final ZoneId ZONA_MEXICO = ZoneId.of("America/Mexico_City");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm"); //utilizo esto para mensajes 

    public Subasta obtenerPorId(Long id) {
        return subastaRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Subasta no encontrada"));
    }

    public List<Subasta> getActivas() { return subastaRepo.findByEstado("ACTIVA"); }
    //contiene todas las subastas
    public List<Subasta> getTodas() { return subastaRepo.findAll(); }
     ///conntiene las ofertas ,as altas 
    public List<Oferta> getOfertas(Long subastaId) {
        return ofertaRepo.findTop10BySubastaIdOrderByMontoDesc(subastaId);
    }
    //validaciones de precios producto incremneto es minimo de 100 
    @Transactional
    public Subasta crear(Subasta s) {
        if (s.getPrecioInicial() == null || s.getPrecioInicial() <= 0) throw new RuntimeException("Precio inicial invalido");
        if (s.getProducto() == null || s.getProducto().isBlank()) throw new RuntimeException("Producto requerido");
        
        s.setPrecioActual(s.getPrecioInicial());
        s.setEstado("PENDIENTE");
        s.setIncrementoMinimo(s.getIncrementoMinimo() != null ? s.getIncrementoMinimo() : 100.0);
        return subastaRepo.save(s);
    }
    //valida las subastas que esten activas o no registra la horade inicio calcula la hora finalizada
    @Transactional
    public Subasta iniciar(Long id, int duracionMinutos) {
        Subasta s = subastaRepo.findById(id).orElseThrow(() -> new RuntimeException("No encontrada"));

        if ("ACTIVA".equals(s.getEstado())) throw new RuntimeException("La subasta ya esta activa");
        if (duracionMinutos <= 0) throw new RuntimeException("Duracion invalida");

        s.setEstado("ACTIVA");
        LocalDateTime ahora = LocalDateTime.now(ZONA_MEXICO);
        s.setTiempoInicio(ahora);
        s.setTiempoFin(ahora.plusMinutes(duracionMinutos));
        //noti a los usuarios coenctados 
        subastaRepo.save(s);
        broadcast(s.getId(), sistemaMsg("Subasta iniciada. Finaliza: " + s.getTiempoFin().format(FMT), s.getId()));
        return s;
    }
    // procesa una puja realizada por un usuario en una subasta activa
    @Transactional
    public Subasta ofertar(Long subastaId, Long usuarioId, Double monto) {
        Subasta s = subastaRepo.findById(subastaId).orElseThrow(() -> new RuntimeException("No encontrada"));
        Usuario u = usuarioRepo.findById(usuarioId).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (monto == null || monto <= 0) throw new RuntimeException("Monto invalido");
        if (!"ACTIVA".equals(s.getEstado())) throw new RuntimeException("La subasta no esta activa");
        
        LocalDateTime ahora = LocalDateTime.now(ZONA_MEXICO);
        if (s.getTiempoFin().isBefore(ahora)) throw new RuntimeException("La subasta ya expiro");
        
        double minimo = s.getPrecioActual() + s.getIncrementoMinimo();
        if (monto < minimo) throw new RuntimeException("El monto minimo es $" + String.format("%,.0f", minimo));

        Oferta oferta = new Oferta();
        oferta.setSubasta(s);
        oferta.setUsuario(u);
        oferta.setMonto(monto);
        ofertaRepo.save(oferta);

        s.setPrecioActual(monto);
        s.setGanador(u.getNombre());

        // Si faltan menos de 15 segundos, extendemos 30 segundos más
        if (s.getTiempoFin().isBefore(ahora.plusSeconds(15))) {
            s.setTiempoFin(s.getTiempoFin().plusSeconds(30));
            broadcast(subastaId, sistemaMsg("¡Puja de último momento! +30 seg.", subastaId));
        }

        subastaRepo.save(s);

        // Mensaje de Puja con hora de México antes tomaba la global toma una mas serca de tamaulipas 
        MensajeChat pujaMsg = new MensajeChat();
        pujaMsg.setUsuario(u.getNombre());
        pujaMsg.setContenido("PUJA de $" + String.format("%,.0f", monto));
        pujaMsg.setTipo("PUJA"); 
        pujaMsg.setHora(LocalTime.now(ZONA_MEXICO).format(FMT));
        pujaMsg.setSubastaId(subastaId);
        broadcast(subastaId, pujaMsg);

        // Mensaje de actualización de precio
        MensajeChat precioMsg = new MensajeChat();
        precioMsg.setUsuario("SISTEMA");
        precioMsg.setContenido("PRECIO_ACTUAL:" + monto);
        precioMsg.setTipo("PRECIO");
        precioMsg.setHora(LocalTime.now(ZONA_MEXICO).format(FMT));
        precioMsg.setSubastaId(subastaId);
        broadcast(subastaId, precioMsg);

        return s;
    }

    @Scheduled(fixedDelay = 3000)
    public void revisarExpiradas() {
        LocalDateTime ahora = LocalDateTime.now(ZONA_MEXICO);
        List<Subasta> expiradas = subastaRepo.findByEstadoAndTiempoFinBefore("ACTIVA", ahora);
        for (Subasta s : expiradas) { cerrarSubasta(s); }
    }
    //finaliza la subasta activa 
    @Transactional
    public Subasta finalizar(Long id) {
        Subasta s = subastaRepo.findById(id).orElseThrow(() -> new RuntimeException("No encontrada"));
        return cerrarSubasta(s);
    }
//cuando finaliza obtiene las ofertas y da el ganadro notifica el resulado
    private Subasta cerrarSubasta(Subasta s) {
        if ("FINALIZADA".equals(s.getEstado())) return s;
        s.setEstado("FINALIZADA");
        List<Oferta> ofertas = ofertaRepo.findBySubastaIdOrderByMontoDesc(s.getId());
        if (!ofertas.isEmpty()) s.setGanador(ofertas.get(0).getUsuario().getNombre());
        subastaRepo.save(s);
//resultado de la subast 
        String texto = s.getGanador() != null ? "¡SUBASTA FINALIZADA! Ganador: " + s.getGanador() : "Finalizada sin ofertas.";
        
        MensajeChat finalMsg = sistemaMsg(texto, s.getId());
        finalMsg.setTipo("FIN"); 
        broadcast(s.getId(), finalMsg);
        
        return s;
    }
//es a don de se envua de la base de datos 
    private void broadcast(Long subastaId, MensajeChat msg) {
        ws.convertAndSend("/topic/subasta/" + subastaId, msg);
    }
//mnesaaje configurado tipo sistema y la hora actual al finlaizar la subast 
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
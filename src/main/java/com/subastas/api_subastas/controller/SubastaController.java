package com.subastas.api_subastas.controller;

import com.subastas.api_subastas.model.*;
import com.subastas.api_subastas.repository.UsuarioRepository;
import com.subastas.api_subastas.service.SubastaService;
import com.subastas.api_subastas.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/subastas")
public class SubastaController {

    @Autowired 
    private SubastaService service;
    
    @Autowired 
    private UsuarioRepository usuarioRepo;
    
    @Autowired 
    private JwtService jwtService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @GetMapping("/activas")
    public List<Subasta> activas() { 
        return service.getActivas(); 
    }

    @GetMapping("/todas")
    public List<Subasta> todas() { 
        return service.getTodas(); 
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) { 
        try {
            return ResponseEntity.ok(service.obtenerPorId(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/ofertas")
    public List<Oferta> ofertas(@PathVariable Long id) { 
        return service.getOfertas(id); 
    }
    
    // --- ADMIN ---

    @PostMapping("/crear")
    public ResponseEntity<?> crear(@RequestBody Subasta s) {
        try {
            validarAdmin();
            return ResponseEntity.ok(service.crear(s));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/iniciar")
    public ResponseEntity<?> iniciar(@PathVariable Long id,
                                     @RequestParam(defaultValue = "5") int minutos) {
        try {
            validarAdmin();
            return ResponseEntity.ok(service.iniciar(id, minutos));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/finalizar")
    public ResponseEntity<?> finalizar(@PathVariable Long id) {
        try {
            validarAdmin();
            return ResponseEntity.ok(service.finalizar(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    // --- USUARIO (OFERTAR) ---
    
    @PostMapping("/{id}/ofertar")
    public ResponseEntity<?> ofertar(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "Token requerido"));
        }

        String token = authHeader.substring(7);
        String correo;
        try {
            correo = jwtService.getCorreo(token);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Token inválido"));
        }

        Optional<Usuario> usuarioOpt = usuarioRepo.findByCorreoIgnoreCase(correo);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Usuario no encontrado"));
        }

        Double monto;
        try {
            monto = Double.valueOf(body.get("monto").toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Monto inválido"));
        }

        try {
            Usuario realUser = usuarioOpt.get();
            Subasta resultado = service.ofertar(id, realUser.getId(), monto);
            
            // Notificar por WebSocket
            MensajeChat aviso = new MensajeChat();
            aviso.setUsuario(realUser.getNombre());
            aviso.setContenido("PUJA de $" + String.format("%,.0f", monto));
            aviso.setTipo("PUJA");
            aviso.setSubastaId(id);
            aviso.setHora(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            
            messagingTemplate.convertAndSend("/topic/subasta/" + id, aviso);
            
            return ResponseEntity.ok(resultado);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    private void validarAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.getAuthorities().stream()
            .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"))) {
            throw new RuntimeException("No tienes permisos de ADMIN");
        }
    }
}
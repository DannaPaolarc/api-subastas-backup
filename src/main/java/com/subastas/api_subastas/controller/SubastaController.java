package com.subastas.api_subastas.controller;

import com.subastas.api_subastas.model.*;
import com.subastas.api_subastas.repository.UsuarioRepository;
import com.subastas.api_subastas.service.SubastaService;
import com.subastas.api_subastas.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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

    //ENDPOINTS PUBLICOS
    
    /*
     * Obtiene todas las subastas activas
     * URL: GET /api/subastas/activas
     * Acceso: Publico (no requiere autenticacion)
     * Retorna: Lista de subastas con estado ACTIVA
     */
    @GetMapping("/activas")
    public List<Subasta> activas() { 
        return service.getActivas(); 
    }

    /*
     * Obtiene todas las subastas (activas y finalizadas)
     * URL: GET /api/subastas/todas
     * Acceso: Publico (no requiere autenticacion)
     * Retorna: Lista completa de subastas
     */
    @GetMapping("/todas")
    public List<Subasta> todas() { 
        return service.getTodas(); 
    }
    
    /*
     * Obtiene una subasta por su ID
     * URL: GET /api/subastas/{id}
     * Parametro: id - Identificador de la subasta
     * Acceso: Publico
     * Retorna: Subasta encontrada o HTTP 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) { 
        try {
            return ResponseEntity.ok(service.obtenerPorId(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /*
     * Obtiene el historial de ofertas de una subasta
     * URL: GET /api/subastas/{id}/ofertas
     * Parametro: id - Identificador de la subasta
     * Acceso: Publico
     * Retorna: Lista de ofertas ordenadas por monto descendente
     */
    @GetMapping("/{id}/ofertas")
    public List<Oferta> ofertas(@PathVariable Long id) { 
        return service.getOfertas(id); 
    }
    
    //ENDPOINTS DE ADMINISTRADOR
    
    /*
     * Crea una nueva subasta
     * URL: POST /api/subastas/crear
     * Body: Objeto Subasta (producto, descripcion, precioInicial, incrementoMinimo)
     * Acceso: Solo ADMIN
     * Retorna: Subasta creada o HTTP 400 con error
     */
    @PostMapping("/crear")
    public ResponseEntity<?> crear(@RequestBody Subasta s) {
        try {
            validarAdmin();
            return ResponseEntity.ok(service.crear(s));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /*
     * Inicia una subasta existente
     * URL: POST /api/subastas/{id}/iniciar?minutos={duracion}
     * Parametros: id - ID de subasta, minutos - Duracion en minutos (default 5)
     * Acceso: Solo ADMIN
     * Retorna: Subasta iniciada o HTTP 400 con error
     */
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

    /*
     * Finaliza una subasta activa
     * URL: POST /api/subastas/{id}/finalizar
     * Parametro: id - ID de subasta
     * Acceso: Solo ADMIN
     * Retorna: Subasta finalizada o HTTP 400 con error
     */
    @PostMapping("/{id}/finalizar")
    public ResponseEntity<?> finalizar(@PathVariable Long id) {
        try {
            validarAdmin();
            return ResponseEntity.ok(service.finalizar(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    //ENDPOINTS DE USUARIO 
    
    /*
     * Realiza una oferta en una subasta activa
     * URL: POST /api/subastas/{id}/ofertar
     * Header: Authorization: Bearer {token}
     * Body: {"monto": cantidad}
     * Parametro: id - ID de subasta
     * Acceso: Usuarios autenticados
     * Retorna: Subasta actualizada o HTTP 401/400 con error
     * Validaciones:
     *   - Token valido y no expirado
     *   - Usuario existe
     *   - Monto valido y mayor al minimo requerido
     *   - Subasta esta activa
     *   - Tiempo no expirado
     */
    @PostMapping("/{id}/ofertar")
    public ResponseEntity<?> ofertar(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // Validar presencia del token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "Token requerido"));
        }

        String token = authHeader.substring(7);
        String correo;
        
        // Validar token
        try {
            correo = jwtService.getCorreo(token);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Token invalido"));
        }

        // Buscar usuario
        Optional<Usuario> usuarioOpt = usuarioRepo.findByCorreoIgnoreCase(correo);
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Usuario no encontrado"));
        }

        // Validar monto
        Double monto;
        try {
            monto = Double.valueOf(body.get("monto").toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Monto invalido"));
        }

        // Realizar oferta
        try {
            Subasta resultado = service.ofertar(id, usuarioOpt.get().getId(), monto);
            return ResponseEntity.ok(resultado);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    //METODOS PRIVADOS
    
    /*
     * Valida que el usuario autenticado tenga rol ADMIN
     * Lanza excepcion si no tiene permisos
     */
    private void validarAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.getAuthorities().stream()
            .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"))) {
            throw new RuntimeException("No tienes permisos de ADMIN");
        }
    }
}
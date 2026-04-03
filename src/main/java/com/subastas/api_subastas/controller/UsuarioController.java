package com.subastas.api_subastas.controller;

import com.subastas.api_subastas.model.Usuario;
import com.subastas.api_subastas.repository.UsuarioRepository;
import com.subastas.api_subastas.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepo;
    
    @Autowired
    private PasswordEncoder encoder;
    
    @Autowired
    private JwtService jwtService;
    
    // Patrones de validacion
    private static final Pattern CORREO = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern TELEFONO = Pattern.compile("^\\+?[0-9]{7,15}$");

    /*
     * Lista todos los usuarios registrados
     * URL: GET /api/usuarios
     * Acceso: Requiere autenticacion (idealmente solo ADMIN)
     * Retorna: Lista completa de usuarios
     */
    @GetMapping
    public List<Usuario> listarUsuarios() {
        return usuarioRepo.findAll();
    }

    /*
     * Obtiene un usuario por su ID
     * URL: GET /api/usuarios/{id}
     * Parametro: id - Identificador del usuario
     * Retorna: Usuario encontrado o HTTP 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerUsuario(@PathVariable Long id) {
        return usuarioRepo.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /*
     * Actualiza el perfil de un usuario
     * URL: PUT /api/usuarios/{id}
     * Parametro: id - Identificador del usuario
     * Body: Campos a actualizar (nombre, correo, direccion, telefono, contrasena)
     * Validaciones:
     *   - Correo: formato valido y no duplicado
     *   - Telefono: formato internacional opcional
     *   - Contraseña: minimo 6 caracteres
     * Retorna: Usuario actualizado o HTTP 400 con error
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarPerfil(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Usuario u = usuarioRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        // Validar nombre
        if (body.containsKey("nombre") && body.get("nombre") != null && !body.get("nombre").isEmpty()) {
            u.setNombre(body.get("nombre"));
        }
        
        // Validar correo
        if (body.containsKey("correo") && body.get("correo") != null && !body.get("correo").isEmpty()) {
            String nuevoCorreo = body.get("correo");
            if (!CORREO.matcher(nuevoCorreo).matches()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Formato de correo invalido"));
            }
            if (!u.getCorreo().equals(nuevoCorreo) && usuarioRepo.existsByCorreoIgnoreCase(nuevoCorreo)) {
                return ResponseEntity.badRequest().body(Map.of("error", "El correo ya esta registrado"));
            }
            u.setCorreo(nuevoCorreo);
        }
        
        // Validar direccion
        if (body.containsKey("direccion") && body.get("direccion") != null) {
            u.setDireccion(body.get("direccion"));
        }
        
        // Validar telefono
        if (body.containsKey("telefono") && body.get("telefono") != null && !body.get("telefono").isEmpty()) {
            String telefono = body.get("telefono");
            if (!TELEFONO.matcher(telefono).matches()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Formato de telefono invalido (solo numeros, opcional +)"));
            }
            u.setTelefono(telefono);
        }
        
        // Validar contraseña
        if (body.containsKey("contrasena") && body.get("contrasena") != null && !body.get("contrasena").isEmpty()) {
            String nuevaPass = body.get("contrasena");
            if (nuevaPass.length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("error", "La contraseña debe tener al menos 6 caracteres"));
            }
            u.setContrasena(encoder.encode(nuevaPass));
        }
        
        usuarioRepo.save(u);
        
        return ResponseEntity.ok(Map.of(
            "mensaje", "Perfil actualizado correctamente",
            "id", u.getId(),
            "nombre", u.getNombre(),
            "correo", u.getCorreo(),
            "direccion", u.getDireccion(),
            "telefono", u.getTelefono()
        ));
    }

    /*
     * Elimina un usuario por su ID
     * URL: DELETE /api/usuarios/{id}
     * Parametro: id - Identificador del usuario
     * Acceso: Requiere autenticacion (idealmente solo ADMIN)
     * Retorna: Mensaje de exito o HTTP 404
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarUsuario(@PathVariable Long id) {
        if (!usuarioRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        usuarioRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("mensaje", "Usuario eliminado correctamente"));
    }
}
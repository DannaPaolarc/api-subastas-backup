package com.subastas.api_subastas.controller;

import com.subastas.api_subastas.model.Usuario;
import com.subastas.api_subastas.repository.UsuarioRepository;
import com.subastas.api_subastas.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired 
    private UsuarioRepository usuarioRepo;
    
    @Autowired 
    private PasswordEncoder encoder;
    
    @Autowired 
    private JwtService jwt;

    // Configuracion del administrador desde variables de entorno
    @Value("${admin.correo:admin@admin.com}")
    private String adminCorreo;

    @Value("${admin.password:123456}")
    private String adminPassword;

    // Patrones de validacion para campos de usuario
    private static final Pattern CORREO   = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern TELEFONO = Pattern.compile("^\\+?[0-9]{7,15}$");
    private static final Pattern NOMBRE   = Pattern.compile("^[a-zA-ZáéíóúÁÉÍÓÚñÑ ]{2,50}$");

    /*
     * Endpoint de inicio de sesion
     * URL: POST /api/auth/login
     * Parametros en body: correo, contrasena
     * Retorna: token JWT y datos del usuario si es exitoso
     * Respuestas:
     *   200 OK - Login exitoso, devuelve token y datos
     *   400 Bad Request - Datos incompletos
     *   401 Unauthorized - Credenciales incorrectas
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String correo = body.get("correo");
        String contrasena = body.get("contrasena");

        // Validar que los campos no sean nulos
        if (correo == null || contrasena == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Datos incompletos"));
        }

        // Buscar usuario por correo (case insensitive)
        Optional<Usuario> opt = usuarioRepo.findByCorreoIgnoreCase(correo);

        // Verificar existencia y contraseña
        if (opt.isEmpty() || !encoder.matches(contrasena, opt.get().getContrasena())) {
            return ResponseEntity.status(401).body(Map.of("error", "Correo o contraseña incorrectos"));
        }

        Usuario u = opt.get();
        
        // Devolver token y datos del usuario
        return ResponseEntity.ok(Map.of(
            "token", jwt.generar(u.getCorreo(), u.getRol()),
            "rol", u.getRol(),
            "nombre", u.getNombre(),
            "correo", u.getCorreo(),
            "id", u.getId(),
            "direccion", u.getDireccion(),
            "telefono", u.getTelefono()
        ));
    }

    /*
     * Endpoint de registro de nuevos usuarios
     * URL: POST /api/auth/registro
     * Parametros en body: nombre, correo, contrasena, direccion, telefono (opcional)
     * Retorna: token JWT y datos del usuario creado
     * Validaciones:
     *   - Nombre: solo letras, 2-50 caracteres
     *   - Correo: formato valido
     *   - Contraseña: minimo 6 caracteres
     *   - Telefono: formato internacional opcional
     * Respuestas:
     *   200 OK - Registro exitoso
     *   400 Bad Request - Datos invalidos o correo ya registrado
     */
    @PostMapping("/registro")
    public ResponseEntity<?> registro(@RequestBody Map<String, String> body) {
        String nombre = body.get("nombre");
        String correo = body.get("correo");
        String contrasena = body.get("contrasena");
        String direccion = body.get("direccion");
        String telefono = body.getOrDefault("telefono", "");

        // Validar campos obligatorios
        if (nombre == null || correo == null || contrasena == null || direccion == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Todos los campos son requeridos"));
        }

        // Validar formato del nombre
        if (!NOMBRE.matcher(nombre).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre solo puede contener letras"));
        }
        
        // Validar formato del correo
        if (!CORREO.matcher(correo).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El correo no tiene un formato válido"));
        }

        // Validar longitud de contraseña
        if (contrasena.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "La contraseña debe tener al menos 6 caracteres"));
        }

        // Validar formato de telefono si fue proporcionado
        if (!telefono.isEmpty() && !TELEFONO.matcher(telefono).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El teléfono debe tener formato internacional (+52 1234567890)"));
        }

        // Verificar que el correo no este registrado
        if (usuarioRepo.existsByCorreoIgnoreCase(correo)) {
            return ResponseEntity.badRequest().body(Map.of("error", "El correo ya está registrado"));
        }

        // Crear y guardar nuevo usuario
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setCorreo(correo);
        u.setContrasena(encoder.encode(contrasena));
        u.setDireccion(direccion);
        u.setTelefono(telefono);
        u.setRol("USUARIO");  // Rol por defecto
        usuarioRepo.save(u);

        // Devolver token y datos del nuevo usuario
        return ResponseEntity.ok(Map.of(
            "token", jwt.generar(u.getCorreo(), u.getRol()),
            "rol", u.getRol(),
            "nombre", u.getNombre(),
            "correo", u.getCorreo(),
            "id", u.getId(),
            "direccion", u.getDireccion(),
            "telefono", u.getTelefono()
        ));
    }
}
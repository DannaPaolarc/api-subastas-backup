package com.subastas.api_subastas.config;

import org.springframework.security.crypto.password.PasswordEncoder;
import com.subastas.api_subastas.security.JwtFilter;
import com.subastas.api_subastas.repository.UsuarioRepository;
import com.subastas.api_subastas.model.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.web.cors.*;
import java.util.List;

/*
 * SecurityConfig - Configuracion de seguridad de la aplicacion
 * Proposito: Configurar autenticacion, autorizacion, CORS y filtros JWT
 * Dependencias: JwtFilter, UsuarioRepository, PasswordEncoder
 */
@Configuration
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;
    
    @Autowired
    private UsuarioRepository usuarioRepo;
    
    // Valores del administrador desde archivo .env
    @Value("${admin.correo:admin@admin.com}")
    private String adminCorreo;
    
    @Value("${admin.password:123456}")
    private String adminPassword;

    /*
     * Configura la cadena de filtros de seguridad
     * Define que endpoints son publicos y cuales requieren autenticacion
     * Retorna: SecurityFilterChain configurado
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // Deshabilitar CSRF para API REST
            .cors(cors -> cors.configurationSource(corsSource()))  // Configurar CORS
            .httpBasic(basic -> basic.disable())  // Deshabilitar HTTP Basic
            .formLogin(form -> form.disable())  // Deshabilitar formulario de login
            .authorizeHttpRequests(auth -> auth
                // Endpoints publicos - no requieren token
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/ws-subastas/**").permitAll()
                .requestMatchers("/api/subastas/activas").permitAll()
                .requestMatchers("/api/subastas/todas").permitAll()
                // Endpoints protegidos - requieren autenticacion
                .requestMatchers("/api/subastas/crear").authenticated()
                .requestMatchers("/api/subastas/*/iniciar").authenticated()
                .requestMatchers("/api/subastas/*/finalizar").authenticated()
                .requestMatchers("/api/subastas/*/ofertar").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /*
     * Provee el manejador de autenticacion
     * Retorna: AuthenticationManager para validar credenciales
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /*
     * Codificador de contraseñas usando BCrypt
     * Retorna: PasswordEncoder para encriptar y verificar passwords
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    /*
     * Carga los detalles del usuario para autenticacion
     * Soporta tanto administrador virtual como usuarios de BD
     * Retorna: UserDetailsService con la logica de carga
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            // Verificar si es el administrador (usuario virtual)
            if (username.equals(adminCorreo)) {
                return User.builder()
                    .username(adminCorreo)
                    .password(passwordEncoder().encode(adminPassword))
                    .roles("ADMIN")
                    .build();
            }
            // Buscar usuario en la base de datos
            Usuario usuario = usuarioRepo.findByCorreoIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
            return User.builder()
                .username(usuario.getCorreo())
                .password(usuario.getContrasena())
                .roles(usuario.getRol())
                .build();
        };
    }

    /*
     * Configura CORS para permitir peticiones desde cualquier origen
     * Permite metodos GET, POST, PUT, DELETE, OPTIONS
     * Retorna: CorsConfigurationSource configurado
     */
    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
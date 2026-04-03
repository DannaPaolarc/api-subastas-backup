package com.subastas.api_subastas.security;

import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.List;
import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwt;

    /*
     * Metodo principal del filtro que se ejecuta en cada peticion
     * Parametros:
     *   request - Peticion HTTP entrante
     *   response - Respuesta HTTP saliente
     *   chain - Cadena de filtros para continuar la ejecucion
     * Proceso:
     *   1. Extrae el header Authorization
     *   2. Verifica que sea un token Bearer
     *   3. Valida el token con JwtService
     *   4. Si es valido, crea el contexto de autenticacion
     *   5. Continua con la cadena de filtros
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // Extraer header Authorization
        String auth = request.getHeader("Authorization");

        // Verificar que sea un token Bearer
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);

            // Validar token
            if (jwt.valido(token)) {
                String correo = jwt.getCorreo(token);

                // Crear autoridades basadas en el rol del usuario
                List<SimpleGrantedAuthority> roles = List.of(
                    new SimpleGrantedAuthority("ROLE_" + jwt.getRol(token))
                );

                // Crear token de autenticacion de Spring Security
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                correo, null, roles);

                // Establecer autenticacion en el contexto solo si no existe
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }

        // Continuar con la cadena de filtros
        chain.doFilter(request, response);
    }
}
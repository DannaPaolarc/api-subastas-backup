package com.subastas.api_subastas.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    // Clave secreta para firmar tokens (desde variables de entorno)
    @Value("${jwt.secret}")
    private String secret;

    // Tiempo de expiracion del token: 24 horas en milisegundos
    private static final long EXPIRACION = 86_400_000L;

    /*
     * Genera la clave de firma a partir del secret
     * Retorna: Key objeto Key para HMAC-SHA
     */
    private Key key() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /*
     * Genera un nuevo token JWT
     * Parametros:
     *   correo - Correo del usuario (subject)
     *   rol - Rol del usuario (USUARIO o ADMIN)
     * Retorna: String con el token JWT firmado
     */
    public String generar(String correo, String rol) {
        return Jwts.builder()
                .setSubject(correo)                    // Asunto: correo del usuario
                .claim("rol", rol)                     // Claim personalizado: rol
                .setIssuedAt(new Date())               // Fecha de emision
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRACION)) // Expiracion
                .signWith(key())                       // Firmar con la clave secreta
                .compact();
    }

    /*
     * Parsea un token y extrae sus claims
     * Parametro: token - Token JWT a parsear
     * Retorna: Claims con toda la informacion del token
     * Lanza excepcion si el token es invalido o expirado
     */
    public Claims parsear(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /*
     * Verifica si un token es valido
     * Parametro: token - Token JWT a validar
     * Retorna: true si es valido, false si no
     */
    public boolean valido(String token) {
        try { 
            parsear(token); 
            return true; 
        } catch (Exception e) { 
            return false; 
        }
    }

    /*
     * Extrae el correo del usuario desde el token
     * Parametro: token - Token JWT
     * Retorna: Correo electronico
     */
    public String getCorreo(String token) { 
        return parsear(token).getSubject(); 
    }
    
    /*
     * Extrae el rol del usuario desde el token
     * Parametro: token - Token JWT
     * Retorna: Rol del usuario (USUARIO o ADMIN)
     */
    public String getRol(String token) { 
        return parsear(token).get("rol", String.class); 
    }
}
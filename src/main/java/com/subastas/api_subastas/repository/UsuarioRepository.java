package com.subastas.api_subastas.repository;

import com.subastas.api_subastas.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    
    /*
     * Busca un usuario por su correo exacto
     * Parametro: correo - Correo electronico del usuario
     * Retorna: Optional con el usuario si existe
     */
    Optional<Usuario> findByCorreo(String correo);
    
    /*
     * Verifica si existe un usuario con el correo exacto
     * Parametro: correo - Correo electronico a verificar
     * Retorna: true si existe, false si no
     */
    boolean existsByCorreo(String correo);
    
    /*
     * Busca un usuario por correo sin distincion de mayusculas/minusculas
     * Parametro: correo - Correo electronico del usuario
     * Retorna: Optional con el usuario si existe
     * Uso: Login case insensitive
     */
    Optional<Usuario> findByCorreoIgnoreCase(String correo);
    
    /*
     * Verifica si existe un usuario con el correo (case insensitive)
     * Parametro: correo - Correo electronico a verificar
     * Retorna: true si existe, false si no
     * Uso: Validacion de correo unico en registro
     */
    boolean existsByCorreoIgnoreCase(String correo);
}
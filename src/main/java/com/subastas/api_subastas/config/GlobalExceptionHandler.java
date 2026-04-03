package com.subastas.api_subastas.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/*
 * GlobalExceptionHandler - Manejador global de excepciones para toda la API
 * Intercepta cualquier excepcion no capturada en los controladores
 * Autor: Sistema de subastas
 * Fecha: 2025
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /*
     * Maneja excepciones de tipo RuntimeException
     * Parametro: ex - La excepcion capturada
     * Retorna: HTTP 400 (Bad Request) con el mensaje de error original
     * Uso: Validaciones de negocio, datos invalidos, etc.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> manejarRuntime(RuntimeException ex) {
        return ResponseEntity.badRequest().body(
                Map.of("error", ex.getMessage())
        );
    }

    /*
     * Maneja cualquier otra excepcion no especificada
     * Parametro: ex - La excepcion capturada
     * Retorna: HTTP 500 (Internal Server Error) con mensaje generico
     * Uso: Errores inesperados del servidor, fallos de conexion, etc.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> manejarGeneral(Exception ex) {
        return ResponseEntity.internalServerError().body(
                Map.of("error", "Error interno del servidor")
        );
    }
}
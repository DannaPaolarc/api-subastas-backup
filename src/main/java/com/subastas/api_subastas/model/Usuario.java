package com.subastas.api_subastas.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
public class Usuario {

    // Identificador unico del usuario (autogenerado)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nombre completo del usuario
    @Column(nullable = false)
    private String nombre;

    // Correo electronico (unico en el sistema)
    @Column(nullable = false, unique = true)
    private String correo;

    // Contraseña encriptada con BCrypt
    @Column(nullable = false)
    private String contrasena;

    // Direccion de envio del usuario
    @Column(nullable = false)
    private String direccion;

    // Numero de telefono (opcional)
    private String telefono;

    // Fecha y hora de registro (automatica)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    // Rol del usuario: USUARIO o ADMIN
    @Column(nullable = false)
    private String rol = "USUARIO";

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    
    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }
    
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime f) { this.fechaRegistro = f; }
    
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
}
package com.subastas.api_subastas.model;

public class MensajeChat {

    // Nombre del usuario que envia el mensaje
    private String usuario;
    
    // Contenido del mensaje
    private String contenido;
    
    // Tipo de mensaje: CHAT, PUJA, SISTEMA, ADMIN
    private String tipo;
    
    // Hora de envio en formato HH:mm
    private String hora;
    
    // Identificador de la subasta a la que pertenece el mensaje
    private Long subastaId;

    // Constructor vacio requerido para deserializacion JSON
    public MensajeChat() {}

    // Getters y Setters
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
    
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    
    public String getHora() { return hora; }
    public void setHora(String hora) { this.hora = hora; }
    
    public Long getSubastaId() { return subastaId; }
    public void setSubastaId(Long subastaId) { this.subastaId = subastaId; }
}
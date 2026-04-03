package com.subastas.api_subastas.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "subastas")
public class Subasta {

    // Identificador unico de la subasta (autogenerado)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nombre del producto en subasta
    @Column(nullable = false)
    private String producto;

    // Descripcion detallada del producto
    @Column(length = 1000)
    private String descripcion;

    // URL de la imagen del producto
    @Column(length = 2000)
    private String imageUrl;

    // Precio base de la subasta
    @Column(nullable = false)
    private Double precioInicial;

    // Precio actual (se actualiza con cada oferta)
    @Column(nullable = false)
    private Double precioActual;

    // Monto minimo que debe incrementar cada oferta
    @Column(nullable = false)
    private Double incrementoMinimo = 100.0;

    // Fecha y hora de inicio de la subasta
    private LocalDateTime tiempoInicio;
    
    // Fecha y hora de finalizacion de la subasta
    private LocalDateTime tiempoFin;

    // Estado actual: PENDIENTE, ACTIVA, FINALIZADA
    @Column(nullable = false)
    private String estado = "PENDIENTE";

    // Nombre del usuario ganador (se asigna al finalizar)
    private String ganador;

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getProducto() { return producto; }
    public void setProducto(String producto) { this.producto = producto; }
    
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public Double getPrecioInicial() { return precioInicial; }
    public void setPrecioInicial(Double p) { this.precioInicial = p; }
    
    public Double getPrecioActual() { return precioActual; }
    public void setPrecioActual(Double p) { this.precioActual = p; }
    
    public Double getIncrementoMinimo() { return incrementoMinimo; }
    public void setIncrementoMinimo(Double i) { this.incrementoMinimo = i; }
    
    public LocalDateTime getTiempoInicio() { return tiempoInicio; }
    public void setTiempoInicio(LocalDateTime t) { this.tiempoInicio = t; }
    
    public LocalDateTime getTiempoFin() { return tiempoFin; }
    public void setTiempoFin(LocalDateTime t) { this.tiempoFin = t; }
    
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    
    public String getGanador() { return ganador; }
    public void setGanador(String ganador) { this.ganador = ganador; }
}
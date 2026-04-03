package com.subastas.api_subastas.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ofertas")
public class Oferta {

    // Identificador unico de la oferta (autogenerado)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Usuario que realiza la oferta
    // Relacion Muchos a Uno: un usuario puede tener muchas ofertas
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    // Subasta en la que se realiza la oferta
    // Relacion Muchos a Uno: una subasta puede tener muchas ofertas
    @ManyToOne
    @JoinColumn(name = "subasta_id")
    private Subasta subasta;

    // Monto de la oferta (no puede ser nulo)
    @Column(nullable = false)
    private Double monto;
    
    // Fecha y hora de la oferta (se inicializa automaticamente al crear)
    private LocalDateTime fecha = LocalDateTime.now();

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    
    public Subasta getSubasta() { return subasta; }
    public void setSubasta(Subasta subasta) { this.subasta = subasta; }
    
    public Double getMonto() { return monto; }
    public void setMonto(Double monto) { this.monto = monto; }
    
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
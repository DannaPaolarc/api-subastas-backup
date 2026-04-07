package com.subastas.api_subastas.controller;

import com.subastas.api_subastas.model.MensajeChat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Controller
public class ChatController {

    // Servicio para enviar mensajes WebSocket a clientes suscritos
    @Autowired private SimpMessagingTemplate ws;
    
    // Formateador para la hora de los mensajes (formato 24 horas)
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    @MessageMapping("/chat.enviar")
    public void enviarMensaje(@Payload MensajeChat msg) {
        System.out.println("=== 1. MENSAJE RECIBIDO: " + msg.getContenido());
        
        if (msg.getUsuario() == null || msg.getContenido() == null)
            return;

        msg.setHora(LocalTime.now().format(FMT));
        msg.setTipo("CHAT");

        System.out.println("=== 2. REENVIANDO A /topic/subasta/" + msg.getSubastaId());
        ws.convertAndSend("/topic/subasta/" + msg.getSubastaId(), msg);
        
        System.out.println("=== 3. REENVIANDO A /topic/publico");
        ws.convertAndSend("/topic/publico", msg);
        
        System.out.println("=== 4. MENSAJE PROCESADO CORRECTAMENTE ===");
    }

    @MessageMapping("/chat.conectar")
    public void conectar(@Payload MensajeChat msg) {
        msg.setHora(LocalTime.now().format(FMT));
        msg.setTipo("SISTEMA");
        msg.setContenido(msg.getUsuario() + " se unió a la subasta");
        ws.convertAndSend("/topic/subasta/" + msg.getSubastaId(), msg);
        ws.convertAndSend("/topic/publico", msg);
    }
}
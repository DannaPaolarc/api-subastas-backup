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

    /*
     * Endpoint para enviar mensajes de chat
     * Destino STOMP: /app/chat.enviar
     * Parametro: msg - Objeto MensajeChat con usuario, contenido y subastaId
     * Funcionamiento:
     *   1. Valida que usuario y contenido no sean nulos
     *   2. Asigna hora actual y tipo chat
     *   3. Difunde el mensaje al topic especifico de la subasta
     *   4. Difunde el mensaje al topic publico general
     * El mensaje se recibe en los clientes suscritos a /topic/subasta/{id} y /topic/publico
     */
    @MessageMapping("/chat.enviar")
    public void enviarMensaje(@Payload MensajeChat msg) {

        if (msg.getUsuario() == null || msg.getContenido() == null)
            return;

        msg.setHora(LocalTime.now().format(FMT));
        msg.setTipo("CHAT");

        // Enviar al canal especifico de la subasta
        ws.convertAndSend("/topic/subasta/" + msg.getSubastaId(), msg);
        
        // Enviar al canal publico general
        ws.convertAndSend("/topic/publico", msg);
    }

    /*
     * Endpoint para notificar cuando un usuario se une a una subasta
     * Destino STOMP: /app/chat.conectar
     * Parametro: msg - Objeto MensajeChat con usuario y subastaId
     * Funcionamiento:
     *   1. Asigna hora actual y tipo sistema
     *   2. Genera mensaje de bienvenida 
     *   3. Difunde al topic especifico de la subasta
     *   4. Difunde al topic publico general
     */
    @MessageMapping("/chat.conectar")
    public void conectar(@Payload MensajeChat msg) {
        msg.setHora(LocalTime.now().format(FMT));
        msg.setTipo("SISTEMA");
        msg.setContenido(msg.getUsuario() + " se unió a la subasta");
        ws.convertAndSend("/topic/subasta/" + msg.getSubastaId(), msg);
        ws.convertAndSend("/topic/publico", msg);
    }
}
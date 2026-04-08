package com.subastas.api_subastas.config;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatHub extends TextWebSocketHandler {
    
    private static final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        System.out.println("=== CONECTADO: " + session.getId());
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("=== MENSAJE RECIBIDO: " + payload);
        
        for (WebSocketSession s : sessions.values()) {
            if (s.isOpen() && !s.getId().equals(session.getId())) {
                s.sendMessage(new TextMessage(payload));
            }
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        System.out.println("=== DESCONECTADO: " + session.getId());
    }
}
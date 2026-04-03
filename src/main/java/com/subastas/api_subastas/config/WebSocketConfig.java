package com.subastas.api_subastas.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;


@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /*
     * Configura el broker de mensajes y los prefijos de destino
     * Parametro: config - Registro de configuracion del broker
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Habilita un broker simple en memoria para el prefijo /topic
        // Los mensajes enviados a /topic son difundidos a todos los clientes suscritos
        config.enableSimpleBroker("/topic");
        
        // Prefijo para mensajes enviados desde el cliente al servidor
        // Los metodos anotados con @MessageMapping escuchan en /app/*
        config.setApplicationDestinationPrefixes("/app");
    }

    /*
     * Registra los endpoints STOMP para conexiones WebSocket
     * Parametro: registry - Registro de endpoints
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint para conexiones WebSocket
        registry.addEndpoint("/ws-subastas")
                // Permite conexiones desde cualquier origen (necesario para CORS)
                .setAllowedOriginPatterns("*")
                // Habilita SockJS como alternativa para navegadores sin WebSocket
                .withSockJS();
    }
}
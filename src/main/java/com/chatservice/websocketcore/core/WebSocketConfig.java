package com.chatservice.websocketcore.core;

import com.chatservice.concurrency.SemaphoreRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.chatservice.joinroom.service.IRoomJoinService;
import com.chatservice.websocketcore.model.ChatSessionRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
	
	IRoomJoinService roomJoinService;
	ChatSessionRegistry chatSessionRegistry;
	SemaphoreRegistry semaphoreRegistry;

	public WebSocketConfig(
			IRoomJoinService roomJoinService,
			ChatSessionRegistry chatSessionRegistry,
			SemaphoreRegistry semaphoreRegistry) {
		
		this.roomJoinService = roomJoinService;
		this.chatSessionRegistry = chatSessionRegistry;
		this.semaphoreRegistry = semaphoreRegistry;
	}
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(textWebSocketHandler(roomJoinService, chatSessionRegistry), "/chat")
                .addInterceptors(handshakeInterceptor())
                .setAllowedOrigins("*");
    }
    @Bean
    public WebSocketHandler textWebSocketHandler (
    		IRoomJoinService roomJoinService,
    		ChatSessionRegistry chatSessionRegistry) {
        return new ChatTextWebSocketHandler(roomJoinService, chatSessionRegistry, semaphoreRegistry);
    }
    @Bean
    public HandshakeInterceptor handshakeInterceptor () {
        return new ChatHandShakeIntercepter(chatSessionRegistry);
    }
}
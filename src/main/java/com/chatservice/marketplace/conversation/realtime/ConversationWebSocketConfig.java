package com.chatservice.marketplace.conversation.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.chatservice.marketplace.conversation.ConversationRepository;

/**
 * /ws/conversations 경로에 핸들러와 인터셉터를 등록한다. 기존 WebSocketConfig 와 같은 구조다.
 * 허용 Origin 은 지정하지 않아 Spring 기본값(같은 출처만 허용)을 쓴다.
 */
@Configuration
@EnableWebSocket
public class ConversationWebSocketConfig implements WebSocketConfigurer {

	private final ConversationSessionRegistry registry;
	private final ConversationRepository conversationRepository;

	public ConversationWebSocketConfig(ConversationSessionRegistry registry,
			ConversationRepository conversationRepository) {
		this.registry = registry;
		this.conversationRepository = conversationRepository;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry handlerRegistry) {
		handlerRegistry.addHandler(conversationWebSocketHandler(), "/ws/conversations")
				.addInterceptors(new ConversationHandshakeInterceptor(conversationRepository));
	}

	@Bean
	public ConversationWebSocketHandler conversationWebSocketHandler() {
		return new ConversationWebSocketHandler(registry);
	}
}

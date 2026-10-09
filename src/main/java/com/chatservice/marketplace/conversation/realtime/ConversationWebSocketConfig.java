package com.chatservice.marketplace.conversation.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.chatservice.marketplace.conversation.service.IConversationService;

/**
 * /ws/conversations 에 대화 핸들러와 핸드셰이크 인터셉터를 등록한다. 기존 WebSocketConfig 와 같은 구조다.
 * 허용 Origin 은 지정하지 않아 Spring 기본값(같은 출처만 허용)을 쓴다. 쿠키로 인증하는 연결이라
 * 다른 사이트의 페이지가 사용자 쿠키로 대화 이벤트를 받는 것을 막기 위해서다.
 */
@Configuration
@EnableWebSocket
public class ConversationWebSocketConfig implements WebSocketConfigurer {

    private final ConversationSessionRegistry registry;
    private final IConversationService conversationService;

    public ConversationWebSocketConfig(ConversationSessionRegistry registry, IConversationService conversationService) {
        this.registry = registry;
        this.conversationService = conversationService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry handlerRegistry) {
        handlerRegistry.addHandler(new ConversationWebSocketHandler(registry), "/ws/conversations")
                .addInterceptors(new ConversationHandshakeInterceptor(conversationService));
    }
}

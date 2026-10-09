package com.chatservice.marketplace.conversation.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 대화 WebSocket 연결 관리(설계 7.2). 기존 ChatTextWebSocketHandler 를 바탕으로 만들고 permit 처리,
 * 인원수 계산, INFO·USER_COUNT 브로드캐스트를 뺐다.
 *
 * 메시지 전송은 REST 로 하므로 클라이언트가 보내는 텍스트 프레임은 무시한다.
 * 같은 회원이 같은 대화에 다시 연결하면 기존 연결을 종료 코드 3000 으로 닫고 새 연결로 바꾼다.
 */
public class ConversationWebSocketHandler extends TextWebSocketHandler {

    /** 기존 chat.js 와 같은 코드. 클라이언트는 이 코드를 받으면 다시 연결하지 않는다. */
    public static final CloseStatus REPLACED_BY_NEW_CONNECTION = new CloseStatus(3000, "다른 탭에서 접속했습니다.");

    private static final Logger logger = LoggerFactory.getLogger(ConversationWebSocketHandler.class);

    private final ConversationSessionRegistry registry;

    public ConversationWebSocketHandler(ConversationSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long conversationId = conversationId(session);
        String memberId = memberId(session);
        WebSocketSession previous = registry.register(conversationId, memberId, session);
        logger.info("[대화 WebSocket] 연결 conversationId={}, memberId={}, replaced={}",
                conversationId, memberId, previous != null);
        if (previous != null && previous != session && previous.isOpen()) {
            previous.close(REPLACED_BY_NEW_CONNECTION);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        logger.debug("[대화 WebSocket] 클라이언트 프레임 무시 conversationId={}, memberId={}",
                conversationId(session), memberId(session));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(conversationId(session), memberId(session), session);
        logger.info("[대화 WebSocket] 종료 conversationId={}, memberId={}, code={}",
                conversationId(session), memberId(session), status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        registry.remove(conversationId(session), memberId(session), session);
        logger.warn("[대화 WebSocket] 전송 오류 conversationId={}, memberId={}, reason={}",
                conversationId(session), memberId(session), exception.getMessage());
    }

    private static Long conversationId(WebSocketSession session) {
        return (Long) session.getAttributes().get(ConversationHandshakeInterceptor.ATTR_CONVERSATION_ID);
    }

    private static String memberId(WebSocketSession session) {
        return (String) session.getAttributes().get(ConversationHandshakeInterceptor.ATTR_MEMBER_ID);
    }
}

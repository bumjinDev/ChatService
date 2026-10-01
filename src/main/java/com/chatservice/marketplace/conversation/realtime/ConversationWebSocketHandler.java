package com.chatservice.marketplace.conversation.realtime;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 대화 WebSocket 핸들러. 기존 ChatTextWebSocketHandler 를 바탕으로 permit 처리, 인원수 계산,
 * INFO·USER_COUNT 브로드캐스트를 뺐다. 서버가 보내는 이벤트만 전달하며 클라이언트가 보낸 텍스트 프레임은 무시한다.
 */
public class ConversationWebSocketHandler extends TextWebSocketHandler {

	/** 같은 회원의 새 연결이 기존 연결을 대체할 때 쓰는 종료 코드 */
	public static final CloseStatus REPLACED = new CloseStatus(3000, "다른 연결로 대체되었습니다.");

	private static final Logger log = LoggerFactory.getLogger(ConversationWebSocketHandler.class);

	private final ConversationSessionRegistry registry;

	public ConversationWebSocketHandler(ConversationSessionRegistry registry) {
		this.registry = registry;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		Long conversationId = conversationId(session);
		String memberId = memberId(session);
		WebSocketSession previous = registry.register(conversationId, memberId, session);
		log.info("대화 WebSocket 연결 conversationId={} memberId={} sessionId={}", conversationId, memberId,
				session.getId());
		if (previous != null && previous != session && previous.isOpen()) {
			try {
				previous.close(REPLACED);
			} catch (IOException e) {
				log.warn("이전 세션 종료 실패 sessionId={} 원인={}", previous.getId(), e.getMessage());
			}
		}
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		log.info("클라이언트 텍스트 프레임은 사용하지 않으므로 무시 conversationId={} memberId={} length={}",
				conversationId(session), memberId(session), message.getPayloadLength());
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		boolean removed = registry.remove(conversationId(session), memberId(session), session);
		log.info("대화 WebSocket 종료 conversationId={} memberId={} code={} removed={}", conversationId(session),
				memberId(session), status.getCode(), removed);
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		registry.remove(conversationId(session), memberId(session), session);
		log.warn("대화 WebSocket 전송 오류 conversationId={} memberId={} 원인={}", conversationId(session),
				memberId(session), exception.getMessage());
	}

	private Long conversationId(WebSocketSession session) {
		return (Long) session.getAttributes().get(ConversationHandshakeInterceptor.ATTR_CONVERSATION_ID);
	}

	private String memberId(WebSocketSession session) {
		return (String) session.getAttributes().get(ConversationHandshakeInterceptor.ATTR_USER_ID);
	}
}

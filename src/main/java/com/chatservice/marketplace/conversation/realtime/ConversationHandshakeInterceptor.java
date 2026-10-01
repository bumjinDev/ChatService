package com.chatservice.marketplace.conversation.realtime;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.chatservice.marketplace.conversation.Conversation;
import com.chatservice.marketplace.conversation.ConversationRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * /ws/conversations 핸드셰이크 검사. 기존 ChatHandShakeIntercepter 를 바탕으로 roomNumber, userName, sessionKey 처리를 뺐다.
 * - conversationId 쿼리 값이 없거나 숫자가 아니면 400
 * - 대화가 없으면 404
 * - 요청한 회원(JwtAuthProcessorFilter 가 넣은 userId 요청 속성)이 참여자가 아니면 403
 * 통과하면 세션 속성에 conversationId 와 userId 를 넣는다.
 */
public class ConversationHandshakeInterceptor implements HandshakeInterceptor {

	public static final String ATTR_CONVERSATION_ID = "conversationId";
	public static final String ATTR_USER_ID = "userId";

	private static final Logger log = LoggerFactory.getLogger(ConversationHandshakeInterceptor.class);

	private final ConversationRepository conversationRepository;

	public ConversationHandshakeInterceptor(ConversationRepository conversationRepository) {
		this.conversationRepository = conversationRepository;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
		if (!(request instanceof ServletServerHttpRequest servletRequest)) {
			response.setStatusCode(HttpStatus.BAD_REQUEST);
			return false;
		}
		HttpServletRequest http = servletRequest.getServletRequest();
		Object userId = http.getAttribute(ATTR_USER_ID);
		if (!(userId instanceof String memberId)) {
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}
		Long conversationId = parseId(http.getParameter(ATTR_CONVERSATION_ID));
		if (conversationId == null) {
			response.setStatusCode(HttpStatus.BAD_REQUEST);
			return false;
		}
		Optional<Conversation> conversation = conversationRepository.findById(conversationId);
		if (conversation.isEmpty()) {
			response.setStatusCode(HttpStatus.NOT_FOUND);
			return false;
		}
		if (!conversation.get().isParticipant(memberId)) {
			log.info("대화 참여자가 아닌 회원의 WebSocket 연결 거부 conversationId={} memberId={}", conversationId, memberId);
			response.setStatusCode(HttpStatus.FORBIDDEN);
			return false;
		}
		attributes.put(ATTR_CONVERSATION_ID, conversationId);
		attributes.put(ATTR_USER_ID, memberId);
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Exception exception) {
		// 핸드셰이크 이후 처리는 없다.
	}

	private Long parseId(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Long.valueOf(value.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}

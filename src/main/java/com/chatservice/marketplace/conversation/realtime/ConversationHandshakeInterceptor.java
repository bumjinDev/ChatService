package com.chatservice.marketplace.conversation.realtime;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.chatservice.marketplace.conversation.service.IConversationService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 대화 WebSocket 핸드셰이크 검사(설계 5.3, 7.2). 기존 ChatHandShakeIntercepter 를 바탕으로 만들고
 * roomNumber·userName·sessionKey 처리를 뺐다.
 *
 * 인증은 앞단의 /ws/** 보안 체인이 하고, JwtAuthProcessorFilter 가 넣은 userId 요청 속성을 읽는다.
 * - conversationId 가 없거나 숫자가 아니면 400
 * - 요청한 회원이 대화 참여자가 아니면 403. 존재하지 않는 대화도 참여자가 아니므로 403 으로 거부한다.
 */
public class ConversationHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_CONVERSATION_ID = "conversationId";
    static final String ATTR_MEMBER_ID = "userId";

    private static final Logger logger = LoggerFactory.getLogger(ConversationHandshakeInterceptor.class);

    private final IConversationService conversationService;

    public ConversationHandshakeInterceptor(IConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        Long conversationId = parseId(servletRequest.getParameter("conversationId"));
        if (conversationId == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        String memberId = (String) servletRequest.getAttribute(ATTR_MEMBER_ID);
        if (memberId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (!conversationService.isParticipant(conversationId, memberId)) {
            logger.info("[대화 WebSocket] 참여자가 아님 conversationId={}, memberId={}", conversationId, memberId);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(ATTR_CONVERSATION_ID, conversationId);
        attributes.put(ATTR_MEMBER_ID, memberId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 핸드셰이크 뒤 추가 처리는 없다.
    }

    private static Long parseId(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

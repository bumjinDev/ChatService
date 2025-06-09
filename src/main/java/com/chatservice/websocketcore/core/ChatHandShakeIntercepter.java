package com.chatservice.websocketcore.core;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.chatservice.websocketcore.model.ChatSessionRegistry;

import jakarta.servlet.http.HttpServletRequest;

public class ChatHandShakeIntercepter implements HandshakeInterceptor {

	ChatSessionRegistry chatSessionRegistry;
	
    private static final Logger logger = LogManager.getLogger(ChatHandShakeIntercepter.class);

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        logger.debug("ChatHandShakeIntercepter.beforeHandshake() 진입");

        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();

        String roomNumber = servletRequest.getParameter("roomNumber");
        String userName = (String) servletRequest.getAttribute("userName");
        String userId = (String) servletRequest.getAttribute("userId");

        if (roomNumber == null) {
            logger.warn("roomNumber 파라미터 없음 - 핸드쉐이크 거부");
            return false;
        }
        logger.info("roomNumber={}, userName={}", roomNumber, userName);
        
        attributes.put("roomNumber", roomNumber);
        attributes.put("userName", userName);
        attributes.put("userId", userId);

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        logger.debug("afterHandshake()!");
        /* 현재 방 번호로 접속 요청 해온 사용자의 userName 을 확인하여 Socket 세션을 있는 리스트를 사용자 ID 로 추가 */
        
    }
}

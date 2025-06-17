package com.chatservice.websocketcore.core;

import java.util.HashSet;
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

/**
 * @class ChatHandShakeIntercepter
 * @brief WebSocket 핸드셰이크 시 클라이언트의 요청 파라미터 및 인증정보를 추출하여
 *        세션 attributes에 주입하고, 각 방/사용자별 sessionKey 발급 및 세션 상태 등록까지 수행하는 인터셉터.
 * @author [작성자]
 * @version [버전]
 * @see org.springframework.web.socket.server.HandshakeInterceptor
 */
public class ChatHandShakeIntercepter implements HandshakeInterceptor {

    // @field chatSessionRegistry : 세션 레지스트리 DI
    ChatSessionRegistry chatSessionRegistry;

    // @field logger : 로그 기록자
    private static final Logger logger = LogManager.getLogger(ChatHandShakeIntercepter.class);

    /**
     * @constructor ChatHandShakeIntercepter
     * @param chatSessionRegistry 세션 상태/등록/조회 책임 객체 DI
     */
    public ChatHandShakeIntercepter(ChatSessionRegistry chatSessionRegistry){
        // @ 초기화
        this.chatSessionRegistry = chatSessionRegistry;
        logger.info("[ChatHandShakeIntercepter] 생성자 호출: chatSessionRegistry DI 주입 완료");
    }

    /**
     * @override beforeHandshake
     * @brief WebSocket 핸드셰이크 직전 호출. HTTP 파라미터와 세션키 발급 등 모든 초기 속성 설정.
     * @param request    클라이언트 HTTP 요청
     * @param response   HTTP 응답 객체
     * @param wsHandler  WebSocket 핸들러
     * @param attributes 세션 attributes(Map<String, Object>)
     * @return boolean   핸드셰이크 허용 여부(true=허용, false=거부)
     * @throws Exception 예외 발생 시 스택 추적
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        logger.debug("ChatHandShakeIntercepter.beforeHandshake() 진입");
        logger.info("[beforeHandshake] 진입");

        // @step1: ServletHttpRequest로 캐스팅, 실제 HTTP 파라미터 추출
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        logger.info("[beforeHandshake] ServletHttpRequest 파싱 성공");

        // @step2: 방 번호 파라미터 추출(필수)
        String roomNumber = servletRequest.getParameter("roomNumber");
        // @step3: 사용자 이름, 아이디 추출(세션/필터 등에서 미리 설정되었다고 가정)
        String userName = (String) servletRequest.getAttribute("userName");
        String userId = (String) servletRequest.getAttribute("userId");
        logger.info("[beforeHandshake] roomNumber 파라미터 추출: roomNumber={}", roomNumber);
        logger.info("[beforeHandshake] userName={}, userId={}", userName, userId);

        // @step4: 필수 파라미터 체크 - 방 번호 누락 시 핸드셰이크 거부
        if (roomNumber == null) {
            logger.warn("roomNumber 파라미터 없음 - 핸드쉐이크 거부");
            logger.info("[beforeHandshake] roomNumber 파라미터 없음 - 핸드쉐이크 거부");
            return false;
        }
        logger.info("[beforeHandshake] 필수 파라미터 존재 - 핸드셰이크 진행");

        // @step5: 세션 단위 랜덤 sessionKey 생성(UUID)
        String sessionKey = java.util.UUID.randomUUID().toString();
        logger.info("[beforeHandshake] sessionKey 생성: sessionKey={}", sessionKey);

        // @step6: 모든 필수 속성/파라미터를 attributes에 등록(WebSocketSession.getAttributes()로 복사됨)
        attributes.put("roomNumber", roomNumber);      // @ 세션용 방번호
        attributes.put("userName", userName);          // @ 세션용 사용자이름
        attributes.put("userId", userId);              // @ 세션용 사용자ID
        attributes.put("sessionKey", sessionKey);      // @ 세션 식별 고유키
        logger.info("[beforeHandshake] attributes 등록 완료: roomNumber={}, userName={}, userId={}, sessionKey={}",
                roomNumber, userName, userId, sessionKey);

        // @step7: ChatSessionRegistry의 (roomId, userId) 별 sessionKey 등록(상태 일치화)
        chatSessionRegistry.saveSessionKey(roomNumber, userId, sessionKey);
        logger.info("[beforeHandshake] sessionKey ChatSessionRegistry 저장 완료: roomNumber={}, userId={}, sessionKey={}",
                roomNumber, userId, sessionKey);

        logger.debug("[beforeHandshake] sessionKey 생성/저장: roomNumber={}, userId={}, sessionKey={}",
                roomNumber, userId, sessionKey
        );

        // @return true: 정상 진행(핸드셰이크 허용)
        logger.info("[beforeHandshake] 핸드셰이크 정상 종료 - true 반환");
        return true;
    }

    /**
     * @override afterHandshake
     * @brief 핸드셰이크 완료 후 후처리(사용하지 않을 시 생략 가능, 주석만 기록)
     * @param request    클라이언트 HTTP 요청
     * @param response   HTTP 응답
     * @param wsHandler  WebSocket 핸들러
     * @param exception  예외 발생 시 Exception 객체
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        logger.debug("afterHandshake()!");
        logger.info("[afterHandshake] 호출");
        // @실제 운영시 필요 로직 삽입 지점
        /* 현재 방 번호로 접속 요청 해온 사용자의 userName 을 확인하여 Socket 세션을 있는 리스트를 사용자 ID 로 추가 */
    }
}
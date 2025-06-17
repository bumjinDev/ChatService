package com.chatservice.websocketcore.core;

import com.chatservice.joinroom.service.IRoomJoinService;
import com.chatservice.websocketcore.model.ChatSessionRegistry;
import com.chatservice.concurrency.SemaphoreRegistry;
import com.chatservice.concurrency.SemaphoreRegistry.UserRoomKey;

import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

/**
 * @class ChatTextWebSocketHandler
 * @brief WebSocket 연결/해제 및 실시간 메시지 송수신의 **실제 동작 지점**이며,
 *        동시성 환경에서 race condition, 중복 세션, 인원수/permit/상태 불일치 방지를 구조적으로 강제하는 핵심 핸들러.
 *
 * @responsibility
 * - 입장 시: DB/메모리 인원수 증가, 세션 중복 강제 종료, 실제 세션 등록, permit 추적 정보 해제, 실시간 메시지/인원수 전파
 * - 퇴장 시: 세션/permit 정리, 실시간 인원수/퇴장 메시지, 메모리 및 세션 구조 일관성 보장
 * - 메시지 처리: race 없이 방 전체 브로드캐스트 수행(오류/중복/누수 방지)
 *
 * @called_by WebSocketConfig.registerWebSocketHandlers()
 */
public class ChatTextWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LogManager.getLogger(ChatTextWebSocketHandler.class);

    private final IRoomJoinService roomJoinService;

    private final ChatSessionRegistry chatSessionRegistry;
    private final SemaphoreRegistry semaphoreRegistry;

    public ChatTextWebSocketHandler(

            IRoomJoinService roomJoinService,
            ChatSessionRegistry chatSessionRegistry,
            SemaphoreRegistry semaphoreRegistry) {

        this.roomJoinService = roomJoinService;
        this.chatSessionRegistry = chatSessionRegistry;
        this.semaphoreRegistry = semaphoreRegistry;
    }

    /**
     * @method handleTextMessage
     * @brief 채팅 메시지 수신 시 동방 내 실질적 전체 사용자에게 원자적 브로드캐스트.
     *        HashSet 기반 roomList 구조에서 isOpen 세션만 전송하여 일관성 보장.
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomNumber = (String) session.getAttributes().get("roomNumber");
        String userName = (String) session.getAttributes().get("userName");

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "CHAT");
        jsonObject.put("roomNumber", roomNumber);
        jsonObject.put("user", userName);
        jsonObject.put("content", message.getPayload());

        broadcast(roomNumber, new TextMessage(jsonObject.toString()));
    }

    /**
     * @method afterConnectionEstablished
     * @brief WebSocket 연결 성립 시점: 인원/permit 증가, 중복 세션 감지/종료, 세션 등록, permit 정리, 인원/입장 메시지 전파.
     *
     * [구조적 통제 흐름]
     * 1. DB/메모리 인원수 증가 – 실제로 세마포어 기반 인원수 갱신 및 DB/메모리 반영
     * 2. 중복 세션 강제 종료 – 동일 userId의 2중 세션 탐지 → 이전 세션 안내 및 강제 disconnect
     * 3. 현재 세션 등록 – roomList/roomUserSessions 모두에 등록, 이후 메시지/인원 전파 근거 마련
     * 4. permit 추적 정보 해제 – 연결 성공한 세션은 permit 점유만 남지 않게 추적 정보 제거(TTL 회수 방지)
     * 5. 실시간 인원/입장 메시지 전파 – 구조적 race 없는 실시간 브로드캐스트
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        logger.info("[afterConnectionEstablished] {}", LocalDateTime.now());

        String roomNumber = Optional.ofNullable((String) session.getAttributes().get("roomNumber"))
                .orElseThrow(() -> new IllegalArgumentException("roomNumber 없음"));
        String userName = Optional.ofNullable((String) session.getAttributes().get("userName"))
                .orElseThrow(() -> new IllegalArgumentException("userName 없음"));
        String userId = Optional.ofNullable((String) session.getAttributes().get("userId"))
                .orElseThrow(() -> new IllegalArgumentException("userId 없음"));
        int roomId = Integer.parseInt(roomNumber);

        logger.info("[입장] userName={}, userId={}, roomId={}", userName, userId, roomId);
        // [1] permit 추적 정보 해제(실제 연결 시점에서만)
        semaphoreRegistry.removePermitTracking(new UserRoomKey(userId, roomId));
        // [2] DB/메모리 내 인원수/방 생성 갱신 (실제 인원 동기화 책임은 ChatSessionRegistry가 갖는다)
        roomJoinService.joinRoom(roomId);
        // [3] 방 만든 이후 순서로, 세션 등록/상태 통합(중복 세션 강제 종료, 임시 퇴장 복귀 등) - 단일 원자적 호출
        chatSessionRegistry.handleUserSessionOnConnect(roomNumber, userId, session);

        logger.info("[입장 완료 - 세마포어] permit : userId={}, roomId={}, 남은 permit={}",
                userId, roomId, semaphoreRegistry.getAvailablePermits(roomId));

        /* ===== 방 내 전체 인원들에게 방 접속했다는 알림. */

        int currnetPeople = chatSessionRegistry.getRoom(roomId).getMaxPeople() - semaphoreRegistry.getAvailablePermits(roomId);
        logger.info("[디버깅] currentPeople 계산: roomId={}, maxPeople={}, availablePermits={}, currentPeople(전체 인원수 - 세마포어 '남은'' 수)={}, currentPeople(ChatRoom.getCurrentPeople={}",
                roomId,
                chatSessionRegistry.getRoom(roomId).getMaxPeople(),
                semaphoreRegistry.getAvailablePermits(roomId),
                chatSessionRegistry.getRoom(roomId).getMaxPeople() - semaphoreRegistry.getAvailablePermits(roomId),
                chatSessionRegistry.getRoom(roomId).getCurrentPeople()
        );


        // [4] 실시간 입장 메시지/인원수 브로드캐스트 (roomList 등 집합 정보 역시 ChatSessionRegistry가 내부적으로 관리)
        JSONObject infoMsg = new JSONObject();
        infoMsg.put("type", "INFO");
        infoMsg.put("content", userName + "님이 입장하셨습니다.");
        broadcast(roomNumber, new TextMessage(infoMsg.toString()));

        JSONObject countMsg = new JSONObject();
        countMsg.put("type", "USER_COUNT");
        // 세션 개수 기준 인원 집계도 ChatSessionRegistry를 통해 조회
        countMsg.put("count", currnetPeople);  // 인원 수 확인.
        broadcast(roomNumber, new TextMessage(countMsg.toString()));
        logger.info("[broadcast] roomNumber={} - 현재 세션 수={}", roomNumber, currnetPeople);
    }


    /**
     * @method afterConnectionClosed
     * @brief WebSocket 세션 종료 시점(명시적/비정상/새로고침 등): permit/세션/메모리 동기화, TTL 기반 임시 처리, 인원수/퇴장 메시지 브로드캐스트, 불필요 데이터 삭제.
     *
     * [구조적 흐름]
     * 1. permit 회수 – 세마포어 인원 감소
     * 2. 명시적 종료/비정상 종료 분기 – TTL/roomUserVOMap 기반 임시 처리
     * 3. 실시간 퇴장 메시지/인원수 브로드캐스트
     * 4. roomList/roomUserSessions의 세션/엔트리 삭제 – race/누수/오류 방지
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("[afterConnectionClosed] {}", LocalDateTime.now());

        String roomNumber = (String) session.getAttributes().get("roomNumber");
        String userName = Optional.ofNullable((String) session.getAttributes().get("userName")).orElse("Unknown");
        String userId = Optional.ofNullable((String) session.getAttributes().get("userId")).orElse("Unknown");

        int roomId = Integer.parseInt(roomNumber);
        logger.info("[퇴장] - 현재 세션 정보 : roomId={}, userId={}, userName={}", roomId, userId, userName);

        // [2] 상태 동기화(정상/비정상 분기) – ChatSessionRegistry에만 위임
        chatSessionRegistry.handleUserSessionOnClose(roomNumber, userId, session, status);

        logger.info("[세마포어 확인] - roomId={}, 여유 인원 수 : permit={}", roomId, semaphoreRegistry.getAvailablePermits(roomId));

        int currnetPeople = chatSessionRegistry.getRoom(Integer.parseInt(roomNumber)).getMaxPeople() - semaphoreRegistry.getAvailablePermits(roomId);

        // [3] 실시간 퇴장 메시지/인원수 브로드캐스트 – ChatSessionRegistry에서 인원 정보/세션 수 등만 조회
        JSONObject infoMsg = new JSONObject();
        infoMsg.put("type", "INFO");
        infoMsg.put("content", userName + "님이 퇴장하셨습니다.");
        broadcast(roomNumber, new TextMessage(infoMsg.toString()));

        JSONObject countMsg = new JSONObject();
        countMsg.put("type", "USER_COUNT");
        countMsg.put("count", currnetPeople);   // 방 인원수 브로드 캐스팅.
        broadcast(roomNumber, new TextMessage(countMsg.toString()));
        logger.info("[broadcast] roomNumber={} - 현재 세션 수={}", roomNumber, currnetPeople);
    }


    /**
     * @method handleTransportError
     * @brief 예외/네트워크 오류 발생 시 permit 반환 및 추적 정보 제거 – 실제 서버/네트워크 장애 환경 대응
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Integer roomId = (Integer) session.getAttributes().get("roomId");
        String userId = (String) session.getAttributes().get("userId");
        if (roomId != null && userId != null) {
            semaphoreRegistry.releasePermitOnly(roomId);
            semaphoreRegistry.removePermitTracking(new UserRoomKey(userId, roomId));
            logger.error("[오류 종료] roomId={}, userId={}, 이유={}", roomId, userId, exception.getMessage());
        }
    }

    /**
     * @method broadcast
     * @brief 방 번호(roomNumber) 기준, 실시간 세션 집합(roomList) 내 전체 isOpen 세션 대상으로 메시지 원자적 전파
     */
    private void broadcast(String roomNumber, TextMessage message) throws Exception {

        HashSet<WebSocketSession> sessions = chatSessionRegistry.getRoomSessions(roomNumber);
        if (sessions == null || sessions.isEmpty()) {
            logger.warn("[broadcast] roomNumber={} - 브로드캐스트 대상 세션 없음 (null or empty)", roomNumber);
            return;
        }
        logger.info("[broadcast] roomNumber={} - 전체 세션 수={}", roomNumber, sessions.size());
        int sendCount = 0, openCount = 0;
        for (WebSocketSession session : sessions) {
            boolean isOpen = session.isOpen();
            logger.debug("[broadcast] 대상 세션: sessionId={}, isOpen={}", session.getId(), isOpen);
            if (isOpen) {
                try {
                    session.sendMessage(message);
                    sendCount++;
                    logger.debug("[broadcast] 메시지 전송 성공: sessionId={}", session.getId());
                } catch (Exception e) {
                    logger.error("[broadcast] 메시지 전송 실패: sessionId={}, error={}", session.getId(), e.getMessage());
                }
                openCount++;
            }
        }
        logger.info("[broadcast] roomNumber={} - 전송 대상 open 세션 수={}, 실제 전송 성공={}", roomNumber, openCount, sendCount);
    }

}
package com.chatservice.websocketcore.core;

import com.chatservice.exportroom.service.IExportRoomService;
import com.chatservice.joinroom.service.IRoomJoinService;
import com.chatservice.websocketcore.model.ChatSessionRegistry;
import com.chatservice.concurrency.SemaphoreRegistry;
import com.chatservice.concurrency.SemaphoreRegistry.UserRoomKey;

import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

/**
 * @class ChatTextWebSocketHandler
 * @brief WebSocket 연결 수립, 해제 및 실시간 메시지 송수신을 담당하는 핵심 핸들러
 *
 * @responsibility
 * - 입장 시: 세션 등록, 중복 제거, permit 정리
 * - 퇴장 시: 세션 제거, permit 회수, DB 인원 차감 및 방 삭제 여부 확인
 * - 메시지 처리: 채팅 메시지 broadcast
 *
 * @called_by WebSocketConfig.registerWebSocketHandlers()
 */
public class ChatTextWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LogManager.getLogger(ChatTextWebSocketHandler.class);

    private final IRoomJoinService roomJoinService;
    private final IExportRoomService exportRoomService;
    private final ChatSessionRegistry chatSessionRegistry;
    private final SemaphoreRegistry semaphoreRegistry;

    public ChatTextWebSocketHandler(IRoomJoinService roomJoinService,
                                    IExportRoomService exportRoomService,
                                    ChatSessionRegistry chatSessionRegistry,
                                    SemaphoreRegistry semaphoreRegistry) {
        this.roomJoinService = roomJoinService;
        this.exportRoomService = exportRoomService;
        this.chatSessionRegistry = chatSessionRegistry;
        this.semaphoreRegistry = semaphoreRegistry;
    }

    /**
     * @method handleTextMessage
     * @brief 채팅 메시지를 수신하면 동일 방에 참여한 모든 사용자에게 전송
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
     * @brief WebSocket 연결 수립 시 초기 상태 등록 및 세션 추적 처리
     *
     * @steps
     * 1. DB 인원 증가 (joinRoom)
     * 2. 중복 세션 강제 종료
     * 3. 현재 세션 등록
     * 4. 세마포어 permit 추적 정보 제거 (입장 확정)
     * 5. 인원수/입장 메시지 broadcast
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomNumber = Optional.ofNullable((String) session.getAttributes().get("roomNumber"))
                .orElseThrow(() -> new IllegalArgumentException("roomNumber 없음"));
        String userName = Optional.ofNullable((String) session.getAttributes().get("userName"))
                .orElseThrow(() -> new IllegalArgumentException("userName 없음"));
        String userId = Optional.ofNullable((String) session.getAttributes().get("userId"))
                .orElseThrow(() -> new IllegalArgumentException("userId 없음"));

        int roomId = Integer.parseInt(roomNumber);

        logger.info("[입장] userName={}, userId={}, roomId={}", userName, userId, roomId);

        roomJoinService.joinRoom(roomId); // DB 인원 증가

        chatSessionRegistry.roomUserSessions.computeIfAbsent(roomNumber, k -> new HashMap<>());
        WebSocketSession prevSession = chatSessionRegistry.roomUserSessions.get(roomNumber).get(userName);

        if (prevSession != null && prevSession.isOpen()) {
            logger.warn("[중복 세션] 기존 세션 종료: userName={}, roomId={}", userName, roomId);
            JSONObject reasonMsg = new JSONObject();
            reasonMsg.put("type", "FORCE_DISCONNECT");
            reasonMsg.put("content", "다른 탭에서 접속하여 연결이 종료되었습니다.");
            prevSession.sendMessage(new TextMessage(reasonMsg.toString()));
            prevSession.close(CloseStatus.NORMAL);
        }

        chatSessionRegistry.roomUserSessions.get(roomNumber).put(userName, session);
        chatSessionRegistry.roomList.computeIfAbsent(roomNumber, k -> new HashSet<>()).add(session);

        semaphoreRegistry.removePermitTracking(new UserRoomKey(userId, roomId));
        logger.info("[세마포어 추가] permit : userId={}, roomId={}, 남은 permit={}",
                userId, roomId, semaphoreRegistry.getAvailablePermits(roomId));

        JSONObject infoMsg = new JSONObject();
        infoMsg.put("type", "INFO");
        infoMsg.put("content", userName + "님이 입장하셨습니다.");
        broadcast(roomNumber, new TextMessage(infoMsg.toString()));

        JSONObject countMsg = new JSONObject();
        countMsg.put("type", "USER_COUNT");
        countMsg.put("count", chatSessionRegistry.roomList.get(roomNumber).size());
        broadcast(roomNumber, new TextMessage(countMsg.toString()));
    }

    /**
     * @method afterConnectionClosed
     * @brief 사용자 연결 종료 시 세션 정리, 세마포어 반환, DB 인원 차감
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomNumber = (String) session.getAttributes().get("roomNumber");
        String userName = Optional.ofNullable((String) session.getAttributes().get("userName")).orElse("Unknown");
        String userId = Optional.ofNullable((String) session.getAttributes().get("userId")).orElse("Unknown");

        int roomId = Integer.parseInt(roomNumber);
        logger.info("[퇴장] userName={}, userId={}, roomId={}", userName, userId, roomId);

        HashSet<WebSocketSession> sessions = chatSessionRegistry.roomList.get(roomNumber);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                chatSessionRegistry.roomList.remove(roomNumber);
            }
        }

        HashMap<String, WebSocketSession> userSessions = (HashMap<String, WebSocketSession>) chatSessionRegistry.roomUserSessions.get(roomNumber);
        if (userSessions != null && userSessions.get(userName) == session) {
            userSessions.remove(userName);
        }

        semaphoreRegistry.releasePermitOnly(roomId);
        semaphoreRegistry.removePermitTracking(new UserRoomKey(userId, roomId));
        logger.info("[세마포어 반환] roomId={}, 현재 permit={}", roomId, semaphoreRegistry.getAvailablePermits(roomId));

        try {
            exportRoomService.exportRoom(roomId);
        } catch (IllegalStateException e) {
            logger.warn("[DB 인원 감소 실패] roomId={}, 이유={}", roomId, e.getMessage());
        }

        JSONObject infoMsg = new JSONObject();
        infoMsg.put("type", "INFO");
        infoMsg.put("content", userName + "님이 퇴장하셨습니다.");
        broadcast(roomNumber, new TextMessage(infoMsg.toString()));

        HashSet<WebSocketSession> updatedSessions = chatSessionRegistry.roomList.get(roomNumber);
        if (updatedSessions != null) {
            JSONObject countMsg = new JSONObject();
            countMsg.put("type", "USER_COUNT");
            countMsg.put("count", updatedSessions.size());
            broadcast(roomNumber, new TextMessage(countMsg.toString()));
        }
    }

    /**
     * @method handleTransportError
     * @brief 예외 발생 시 자원 정리 및 permit 반환
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
     * @brief 특정 방 사용자 전체에게 메시지 전파
     */
    private void broadcast(String roomNumber, TextMessage message) throws Exception {
        HashSet<WebSocketSession> sessions = chatSessionRegistry.roomList.get(roomNumber);
        if (sessions != null) {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            }
        }
    }
}

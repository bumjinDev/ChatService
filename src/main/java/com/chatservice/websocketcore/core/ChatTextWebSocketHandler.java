package com.chatservice.websocketcore.core;

import com.chatservice.joinroom.dao.ChatRoomMemoryRegistry;
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
    private final ChatRoomMemoryRegistry chatRoomMemoryRegistry;
    private final ChatSessionRegistry chatSessionRegistry;
    private final SemaphoreRegistry semaphoreRegistry;

    public ChatTextWebSocketHandler(IRoomJoinService roomJoinService,
                                    ChatRoomMemoryRegistry chatRoomMemoryRegistry,
                                    ChatSessionRegistry chatSessionRegistry,
                                    SemaphoreRegistry semaphoreRegistry) {
        this.roomJoinService = roomJoinService;
        this.chatRoomMemoryRegistry = chatRoomMemoryRegistry;
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
    @SuppressWarnings("resource")
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

        /* roomJoinService.joinRoom(roomId) :
        *   -  새로운 방 생성 그리고 새로운 방 입장이든 기존 방 입장이든 해당 방 번호 인원수를 세마포어로 갱신
        * */
        roomJoinService.joinRoom(roomId);

        /*
          - chatSessionRegistry에서 roomUserSessions에 해당 방 번호의 세션 맵(HashMap)이 없을 경우 미리 초기화 하여 저장 한다.
          - 동일 Id의 한 사용자가 하나의 방에 2개 이상(탭 2개) 동시 접속하는 것을 금지하는 목적이 있다.
          - 다음 줄에서 roomUserSessions.get(roomNumber) 호출 시 null 발생 및 NullPointerException을 방지하기 위한 처리다.
          - 바로 다음 과정에서 중복 검사를 하므로 현재 위치에서는 현재 생성된 WebSocket 을 관리 리스트에 포함 시키지 않고, 검사 이후에 리스트 내 반영.
         */
        chatSessionRegistry.roomUserSessions.computeIfAbsent(roomNumber, k -> new HashMap<>());

        /* 웹 소켓 중복 성립 여부를 검사 후 close() 실행 */
        if (chatSessionRegistry.containsUser(roomNumber, userId)) {

            logger.warn("[중복 세션 확인] - 기존 세션 : userName={}, roomId={}", userName, roomId);

            WebSocketSession prevSession = chatSessionRegistry.roomUserSessions.get(roomNumber).get(userId);
            /* 방 내 재 접속이 되었음으로 기존 접속 중이던 페이지 내 안내 메시지 전달 */
            JSONObject reasonMsg = new JSONObject();
            reasonMsg.put("type", "FORCE_DISCONNECT");
            reasonMsg.put("content", "다른 탭에서 접속하여 연결이 종료 합니다.");
            prevSession.sendMessage(new TextMessage(reasonMsg.toString()));

            prevSession.close(CloseStatus.NORMAL);
            logger.info("기존 중복 세션 종료 - userName={}, roomId={}", userName, roomId);
        }

        /* 현재 방 번호의 브로드 캐스트 도메인 내 현재 WebSocket Session 추가. */
        chatSessionRegistry.roomList.computeIfAbsent(roomNumber, k -> new HashSet<>()).add(session);
        /* WebSocketSession 리스트 관리 자료 구조 내 각 채팅 방 번호 별 새롭게 접속한 접속한 사용자(Id) 목록 추가.  */
        chatSessionRegistry.roomUserSessions.get(roomNumber).put(userName, session);

        /* WebSocketSession 이 정상적으로 수립 까지 되었으니 스케줄러에서 미 연결 세션으로써 제외하지 않도록 해당 목록에서 제거 */
        semaphoreRegistry.removePermitTracking(new UserRoomKey(userId, roomId));

        logger.info("[입장 완료 - 세마포어] permit : userId={}, roomId={}, 남은 permit={}",
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

        logger.info("[afterConnectionClosed] {}", LocalDateTime.now());

        String roomNumber = (String) session.getAttributes().get("roomNumber");
        String userName = Optional.ofNullable((String) session.getAttributes().get("userName")).orElse("Unknown");
        String userId = Optional.ofNullable((String) session.getAttributes().get("userId")).orElse("Unknown");

        int roomId = Integer.parseInt(roomNumber);
        logger.info("[퇴장] - 현재 세션 정보 : roomId={}, userId={}, userName={},  ",  roomId, userId, userName);
        // 임시 디버깅 시작 ==
        // 임시 디버깅 끝   ==

        semaphoreRegistry.releasePermitOnly(roomId);
        logger.info("[세마포어 반환] - roomId={}, 현재 방 총 접속 인원수 : permit={}", roomId, semaphoreRegistry.getAvailablePermits(roomId));

        /*
         * [명시적 종료/비정상 종료 구분 및 TTL 관리]
         *
         * - 명시적 종료가 아닌 경우(예: 새로고침, 비정상 종료 등)는 브라우저 동작 상 서버에서는 실제 종료와 구분이 불가능하다.
         *   따라서, 해당 방(roomId) 내 사용자(userId)에 대해 TTL(Time-To-Live) 정책을 적용하여,
         *   스케줄러가 주기적으로 TTL 기준으로 만료 사용자를 자동 관리하도록 한다, 즉 TTL 내 동일한 방 번호로 동일한 사용자 Id 가 재 진입하는 시간을 설정 하여,
         *   해당 TTL 내 재 진입 하면 새로고침이라 간주하고, 재 진입을 하지 않는 다면 완전히 나갔다고 판단 하여 방 인원수를 감소 한다.
         *   참고: 새로고침 등으로 close()가 발생해도, 세마포어 인원 감소 로직은 동일하게 작동해야 하므로 반드시 상태 반영이 필요하다.
         */
        if (status.getCode() == 1000) {
            chatRoomMemoryRegistry.removeUserFromRoom(roomId, userId);
        }

        else {
            chatRoomMemoryRegistry.addOrUpdateUserInRoom(roomId, userId);
        }


        /* 퇴장 메시지를 브로드 캐스팅으로 전달. */
        JSONObject infoMsg = new JSONObject();
        infoMsg.put("type", "INFO");
        infoMsg.put("content", userName + "님이 퇴장하셨습니다.");
        broadcast(roomNumber, new TextMessage(infoMsg.toString()));

        /* 방 인원수를 브로드 캐스팅으로 전달. */
        HashSet<WebSocketSession> updatedSessions = chatSessionRegistry.roomList.get(roomNumber);
        if (updatedSessions != null) {
            JSONObject countMsg = new JSONObject();
            countMsg.put("type", "USER_COUNT");
            countMsg.put("count", updatedSessions.size());
            broadcast(roomNumber, new TextMessage(countMsg.toString()));
        }

        /*
         * [방 번호별 브로드캐스트 세션 관리]
         * - 각 방 번호(roomNumber)별 WebSocket 세션 목록(HashSet<WebSocketSession>)에서 현재 세션을 제거한다.
         *
         * - if (sessions != null):
         *     해당 방 번호에 대응하는 세션 목록이 존재하는 경우에만 제거 작업을 수행한다.
         *     이는, 방이 이미 삭제되었거나 세션 목록이 초기화되지 않은 경우 NullPointerException을 예방하기 위함이다.
         *
         *   - sessions.remove(session):
         *       현재 종료 또는 퇴장한 세션을 해당 방의 세션 목록에서 제거하여, 이후 브로드캐스트 대상에서 제외한다.
         *
         *   - if (sessions.isEmpty()):
         *       세션 제거 후 해당 방에 남아 있는 세션이 하나도 없을 경우,
         *       방 번호에 해당하는 세션 목록(roomList의 entry) 자체를 삭제하여,
         *       불필요한 메모리 점유(메모리 누수)를 방지하고, 더 이상 사용되지 않는 방에 대한 불필요한 상태 유지를 차단한다.
         */
        HashSet<WebSocketSession> sessions = chatSessionRegistry.roomList.get(roomNumber);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                chatSessionRegistry.roomList.remove(roomNumber);
            }
        }

        /*
         * [방 번호별 사용자 세션 관리]
         * - 방 번호별 사용자 세션 맵(HashMap<String, WebSocketSession>)에서 현재 사용자의 세션을 제거한다.
         *
         * - if (userSessions != null && userSessions.get(userName) == session):
         *     해당 방에 대한 사용자 세션 맵이 존재하고,
         *     해당 사용자(userName)에 등록된 세션이 현재 종료되는 세션(session)과 일치하는 경우에만 제거 작업을 수행한다.
         *     이는, 다른 탭에서 동일 userId로 별도 세션이 존재할 수 있으므로, 정확히 일치하는 세션에 한해 제거하여
         *     의도치 않은 세션 삭제를 방지한다.
         *
         *   - userSessions.remove(userName):
         *       해당 사용자의 세션을 사용자 세션 맵에서 제거하여,
         *       이후 중복 로그인 방지 및 세션 추적에서 제외한다.
         *
         *   - if (userSessions.isEmpty()):
         *       세션 제거 후 해당 방의 사용자 세션 맵이 비게 될 경우,
         *       roomUserSessions에서 방 번호에 대한 엔트리 자체를 삭제하여,
         *       필요 없는 빈 데이터 구조의 메모리 상주 및 관리 비용을 제거한다.
         */
        HashMap<String, WebSocketSession> userSessions = (HashMap<String, WebSocketSession>) chatSessionRegistry.roomUserSessions.get(roomNumber);
        if (userSessions != null && userSessions.get(userName) == session) {
            userSessions.remove(userName);
            if (userSessions.isEmpty()) {
                chatSessionRegistry.roomUserSessions.remove(roomNumber);
            }
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
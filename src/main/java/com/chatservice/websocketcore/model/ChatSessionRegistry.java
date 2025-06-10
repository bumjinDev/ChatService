package com.chatservice.websocketcore.model;

import com.chatservice.concurrency.SemaphoreRegistry;
import com.chatservice.joinroom.dao.ChatRoom;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [WebSocket 세션 관리 레지스트리: 통합 진입/퇴장 상태 분기 완전 일치화 버전]
 * ───────────────────────────────────────────────
 * 방-사용자 단위 세션 제어, 임시 퇴장 복귀, 동시성 race 완전 제어,
 * 실제 permit/roomMap/VO/세션 상태 일관성 통합.
 */
@Component
public class ChatSessionRegistry {

    private final SemaphoreRegistry semaphoreRegistry;
    private static final Logger logger = LoggerFactory.getLogger(ChatSessionRegistry.class);

    /**
     * [roomList] : 브로드 캐스티 목적 - 방 번호 → 해당 방의 WebSocket 세션 집합
     *
     * 목적:
     * 1. 방별 전체 사용자에게 메시지 브로드캐스트를 하기 위한 기준 세션 목록
     * 2. WebSocket 연결 시점(afterConnectionEstablished)에서 인원 수 증가
     * 3. WebSocket 종료 시점(afterConnectionClosed)에서 인원 수 감소
     */
    public Map<String, HashSet<WebSocketSession>> roomList = new HashMap<>();
    /**
     * [roomUserSessions] : 방 번호 → (userId → WebSocket 세션) 맵
     *
     * 목적:
     * 1. 동일 사용자가 브라우저 탭을 여러 개 열어 중복 접속할 경우 선제적 감지 및 차단
     * 2. 동일 사용자가 새로 고침 시 중복 세션 접근 방지
     * 2. 비정상 종료된 세션(브라우저 강제 종료, 재부팅 등) 이후 빠른 중복 감지
     * 3. beforeHandshake 시 userName 기반 중복 여부 확인 → 이미 존재 시 이전 세션 강제 종료
     *
     * Map<RoomNumber, Map<userId, WebSocketSession>>
     */
    public Map<String, Map<String, WebSocketSession>> roomUserSessions = new HashMap<>();

    /* == 인원수 갱신 == */
    // [방 번호 → ChatRoom]
    private final Map<Integer, ChatRoom> roomMap = new ConcurrentHashMap<>();
    // [방 번호 → (userId → RoomUserStateVO)]
    private final Map<Integer, Map<String, RoomUserStateVO>> roomUserVOMap = new ConcurrentHashMap<>();
    // 비 명시적 종료에 의한 새로고침 유지 시간 : 사용자 TTL (3분)
    private static final long USER_TTL_MILLIS = 10 * 1000L;

    public ChatSessionRegistry(SemaphoreRegistry semaphoreRegistry){
        this.semaphoreRegistry = semaphoreRegistry;
    }

    /**
     * [입장 시 상태 통합 처리]
     * - 기존 동일 userId 세션 강제 종료
     * - 임시 퇴장(VO) 복귀 처리
     * - 신규/복귀 모두 세션 갱신
     * - 반드시 afterConnectionEstablished에서만 호출
     * @return true: 복귀/기존 세션, false: 완전 신규
     */
    public void handleUserSessionOnConnect(String roomId, String userId, WebSocketSession session) {

        // [1] 해당 방(roomId) 내 userId로 기존 WebSocketSession 존재 여부 확인 및 맵 획득
        Map<String, WebSocketSession> userSessions = roomUserSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
        WebSocketSession prevSession = userSessions.get(userId);

        // [2] 기존 동일 userId 세션이 존재하고, 세션이 여전히 열려 있을 경우(=중복 접속 감지)
        if (prevSession != null && prevSession.isOpen()) {
            try {
                // 2-1. userSessions 맵 내 userId의 세션을 신규 session으로 갱신
                userSessions.put(userId, session);

                // 2-2. 브로드캐스트 대상 세션 집합(roomList)에서 이전 세션을 제거하고, 신규 세션을 추가하여
                //      메시지 전파 대상의 중복, 유령 세션 문제를 예방
                roomList.get(roomId).remove(prevSession);
                roomList.get(roomId).add(session);
                // 2-3. 중복 탭 판단 시 인원 수 검증 및 증가(JoinService.confirm()) 을 수행 않으나, 다음 close() 과정 에서 세마포어 감소 따라 여기서 재 증가.
                semaphoreRegistry.tryAcquire(Integer.parseInt(roomId), userId);

                // 2-3. 기존 prevSession에 대해 close()를 호출하여
                //      클라이언트 단 세션 강제 종료 및 이후 afterConnectionClosed 호출 유도
                prevSession.close();

            } catch (Exception ignored) {}
        }

        // [3] 새로고침 등으로 재접속된 케이스에 대해 임시 퇴장(TTL 기반) 정보가 roomUserVOMap에 존재 한다면,이를 제거하여(복귀 처리) 방 내 인원수, 상태 일관성 유지
        else if (getRoomUserVOMap(Integer.parseInt(roomId)) != null) { roomUserVOMap.get(Integer.parseInt(roomId)).remove(userId); }

        /* 위 문제 들이 확인되지 않았다면 이미 JoinService.confirm() 내에서 장성 동작 이라 간주. */
    }

    /*
     * [종료 시 상태 통합 처리]
     * - 정상 종료(1000): 완전 삭제, permit 즉시 반환
     * - 비정상(새로고침/탭 종료 등): 임시 퇴장(VO)로 전환
     * - 반드시 afterConnectionClosed에서만 호출
     */
    public void handleUserSessionOnClose(String roomId, String userId, CloseStatus closeStatus) {

        /* 방 번호 확인 */
        int roomNum = Integer.parseInt(roomId);

        /* 1. 명시적 종료 : 현재 웹 소켓이 포함된 방 번호 내 브로드 캐스트와 중복 세션 리스트 삭제. 리스트 삭제.*/
        if (closeStatus.getCode() == 1000) {
            // 정상 종료(즉시 permit 반환 및 상태 동기화)
            removeUserFromRoom(roomNum, userId);
            logger.info("[정상 종료: 즉시 삭제] roomId={}, userId={}", roomId, userId);
        } 
        
        /* 2. 비 명시적 종료 : 일관된 방 정보 유지(방 인원수 0명 일 시 잘못된 방 삭제 방지 목적)을 위한 현재 방 목록에서 즉시 제거 아닌,
        *       별도의 리스터 업을 하여 TTL 기준으로 스케줄링 처리 하여 추후 실제 제거 여부 판별 후 진행 */
        else {
            addOrUpdateUserInRoom(roomNum, userId);
            logger.info("[비정상 종료: 임시 퇴장(TTL)] roomId={}, userId={}", roomId, userId);
        }
    }

    /**
     * [roomUserVOMap 임시 퇴장 등록/갱신]
     */
    public void addOrUpdateUserInRoom(int roomNumber, String userId) {
        Map<String, RoomUserStateVO> userMap =
                roomUserVOMap.computeIfAbsent(roomNumber, k -> new ConcurrentHashMap<>());
        userMap.put(userId, new RoomUserStateVO(userId, System.currentTimeMillis() + USER_TTL_MILLIS));
    }


    /**
     * [실제 사용자 완전 퇴장/삭제, 상태 일치화]
     * - roomUserVOMap에서 제거, userMap 비면 roomMap/roomUserVOMap 모두 삭제
     * - permit/인원수 갱신 일치
     */
    public void removeUserFromRoom(int roomNumber, String userId) {

        Map<String, RoomUserStateVO> userMap = roomUserVOMap.get(roomNumber);
        if (userMap != null) {
            userMap.remove(userId);
            if (userMap.isEmpty()) {
                roomUserVOMap.remove(roomNumber);
                roomMap.remove(roomNumber);
                return;
            }
        }
        // 인원수 갱신(permit 기준)
        ChatRoom chatRoom = roomMap.get(roomNumber);
        if (chatRoom != null) {
            updateRoomCurrentPeople(roomNumber, semaphoreRegistry.getAvailablePermits(roomNumber));
            if (chatRoom.getCurrentPeople() == 0 && userMap != null && userMap.isEmpty()) {
                roomMap.remove(roomNumber);
                roomUserVOMap.remove(roomNumber);
            }
        }
    }

    /**
     * [방 생성]
     */
    public void createRoom(int roomNumber, ChatRoom chatRoom) {
        roomMap.put(roomNumber, chatRoom);
        roomUserVOMap.put(roomNumber, new ConcurrentHashMap<>());
    }

    /**
     * [방 정보 조회]
     */
    public ChatRoom getRoom(int roomNumber) {
        return roomMap.get(roomNumber);
    }

    /**
     * [방 내 인원수 갱신: permit 기반]
     */
    public void updateRoomCurrentPeople(int roomNumber, int newCurrentPeople) {
        roomMap.computeIfPresent(roomNumber, (num, room) -> {
            room.setCurrentPeople(newCurrentPeople);
            return room;
        });
    }

    /**
     * [전체 방 정보 반환]
     */
    public Map<Integer, ChatRoom>  getRooms() {
        return roomMap;
    }

    /**
     * [roomUserVOMap 조회 전용 getter]
     * @param roomNumber 대상 방 번호 (int)
     * @return 해당 방 번호의 사용자 상태 맵, 없으면 null
     */
    public Map<String, RoomUserStateVO> getRoomUserVOMap(int roomNumber) {
        return roomUserVOMap.get(roomNumber);
    }


    /**
     * [TTL 만료 사용자 및 방 자동 정리]
     * - roomUserVOMap 기준으로만 수행, permit/roomMap 일치 보장
     */
    public void cleanupExpiredUsers() {
        long now = System.currentTimeMillis();

        // 방 번호 복사본 확보(ConcurrentHashMap 동시성 대응)
        Set<Integer> roomsToCheck = new HashSet<>(roomUserVOMap.keySet());
        logger.info("[cleanupExpiredUsers] 시작: now={}, 대상 방 개수={}", now, roomsToCheck.size());

        for (Integer roomNumber : roomsToCheck) {
            Map<String, RoomUserStateVO> userMap = roomUserVOMap.get(roomNumber);

            // 방 내 사용자 없음(이미 정리됨) → skip
            if (userMap == null || userMap.isEmpty()) {
                logger.debug("[cleanupExpiredUsers] skip: 방 {} userMap null or empty", roomNumber);
                continue;
            }

            List<String> expiredUserIds = new ArrayList<>();
            for (Map.Entry<String, RoomUserStateVO> entry : userMap.entrySet()) {
                RoomUserStateVO vo = entry.getValue();
                if (vo == null) {
                    logger.warn("[cleanupExpiredUsers] 방 {} userId {} VO null", roomNumber, entry.getKey());
                    continue;
                }
                if (vo.getExpireAt() < now) {
                    expiredUserIds.add(entry.getKey());
                    logger.info("[cleanupExpiredUsers] TTL 만료 감지: 방 {} userId={} expireAt={}", roomNumber, entry.getKey(), vo.getExpireAt());
                }
            }

            for (String userId : expiredUserIds) {
                logger.info("[cleanupExpiredUsers] 만료 사용자 제거: 방 {} userId={}", roomNumber, userId);
                removeUserFromRoom(roomNumber, userId);
            }

            if (expiredUserIds.size() > 0) {
                logger.info("[cleanupExpiredUsers] 방 {} 내 만료 사용자 {}명 처리 완료", roomNumber, expiredUserIds.size());
            }
        }
        logger.info("[cleanupExpiredUsers] 전체 정리 완료");
    }


    /**
     * [특정 방의 모든 WebSocketSession을 HashSet으로 반환]
     * - 내부적으로 userId → WebSocketSession map에서 session만 추출, 중복 없는 집합 반환
     * - 실시간 브로드캐스트 등에서 사용
     */
    public HashSet<WebSocketSession> getRoomSessions(String roomNumber) {
        HashSet<WebSocketSession> userSessions = roomList.get(roomNumber);
        if (userSessions == null) return new HashSet<>();
        else return userSessions;
    }

    /**
     * [조회용: 현재 해당 방에 userId가 실시간 세션으로 등록되어 있는지]
     */
    public boolean containsUser(String roomId, String userId) {
        Map<String, WebSocketSession> userSessions = roomUserSessions.get(roomId);
        return userSessions != null && userSessions.containsKey(userId);
    }


    /*
     * [사용자 상태 VO 구조]
     */
    public static class RoomUserStateVO {
        private final String userId;
        @Setter
        private long expireAt; // 만료 시각(ms)
        public RoomUserStateVO(String userId, long expireAt) {
            this.userId = userId;
            this.expireAt = expireAt;
        }
        public String getUserId() { return userId; }
        public long getExpireAt() { return expireAt; }
    }



}

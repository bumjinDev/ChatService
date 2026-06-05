package com.chatservice.websocketcore.model;

import com.chatservice.concurrency.SemaphoreRegistry;
import com.chatservice.joinroom.dao.ChatRoom;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @class ChatSessionRegistry
 * @brief WebSocket 세션/상태/인원/중복탭 일관성 통합 관리 레지스트리. 동시성 제어, race condition, 복귀, 임시퇴장 등 전체 책임.
 */
@Component
public class ChatSessionRegistry {

    // =========================================================================
    // 1. [상수·내부 상태 구조체 선언]
    // =========================================================================

    /**
     * @brief 새로고침 등 비명시적 종료 사용자의 임시 VO TTL(실서비스 10*1000L=10초, 본 테스트용 0.5초=500ms)
     */
    private static final long USER_TTL_MILLIS = 10 * 1000L; // 10초

    /**
     * @staticclass RoomUserStateVO
     * @brief 임시 퇴장/복귀/TTL 만료 사용자 상태 VO
     */
    public static class RoomUserStateVO {
        private final String userId;
        @Setter
        private long expireAt; // 만료 시각(ms)

        public RoomUserStateVO(String userId, long expireAt) {
            this.userId = userId;
            this.expireAt = expireAt;
        }

        public String getUserId() {
            return userId;
        }

        public long getExpireAt() {
            return expireAt;
        }
    }

    // =========================================================================
    // 2. [상태/자료구조·의존성 선언부]
    // =========================================================================

    /**
     * @brief 세마포어(permit, 인원수, 동시성 race 제어) 레지스트리 DI
     */
    private final SemaphoreRegistry semaphoreRegistry;
    /**
     * @brief 로그 기록자
     */
    private static final Logger logger = LoggerFactory.getLogger(ChatSessionRegistry.class);

    /**
     * @brief 브로드캐스트용 세션 집합(방번호 → 세션 Set)
     */
    public Map<String, HashSet<WebSocketSession>> roomList = new HashMap<>();
    /**
     * @brief 중복탭/새로고침 구분용(방번호 → (userId → 세션))
     */
    public Map<String, Map<String, WebSocketSession>> roomUserSessions = new HashMap<>();
    /**
     * @brief 각 방의 상태/인원수/메타정보(방번호 → ChatRoom)
     */
    private final Map<Integer, ChatRoom> roomMap = new ConcurrentHashMap<>();
    /*
     * @return userId→RoomUserStateVO Map
     * @method getRoomUserVOMap
     * @brief 비명시적 종료/복귀 상태 VO Map 조회
     */
    /**
     * @brief 임시퇴장(비명시적 종료) VO(방번호 → (userId → VO))
     */
    @Getter
    private final Map<Integer, Map<String, RoomUserStateVO>> roomUserVOMap = new ConcurrentHashMap<>();
    /**
     * @brief sessionKey 추적(방번호 → (userId → sessionKey))
     */
    private final Map<String, Map<String, String>> roomUserSessionKeyMap = new ConcurrentHashMap<>();

    // =========================================================================
    // 3. [생성자/초기화]
    // =========================================================================

    /**
     * @constructor
     * @brief DI를 통한 세마포어 레지스트리 주입, 상태/자료구조 초기화 책임.
     */
    public ChatSessionRegistry(SemaphoreRegistry semaphoreRegistry) {
        this.semaphoreRegistry = semaphoreRegistry;
    }

    // =========================================================================
    // 4. [외부 진입점: 연결/종료 핸들러]
    // =========================================================================

    /**
     * @method handleUserSessionOnConnect
     * @brief WebSocket 입장 분기 디스패처. 서버 sessionKey와 클라이언트 sessionKey 두 값만으로
     *        아래 결정표대로 분기한다. (반드시 afterConnectionEstablished 내부에서만 호출)
     *
     *  serverKey | clientKey            | 처리
     *  --------- | -------------------- | ---------------------------------------------------
     *  null      | "null"               | onFirstEntry      (신규 키 발급·등록)
     *  null      | 값 존재              | 강제 리셋 후 onFirstEntry 흐름으로 낙하
     *  존재      | == serverKey         | onSameTabRefresh  (TTL 잔존 VO 정리 후 세션 덮어쓰기)
     *  존재      | "null"               | onDuplicateTab    (기존 세션 close(3000) 후 교체)
     *  존재      | ≠serverKey & ≠"null" | onDesync          (close(3001))
     *
     *  ※ 분기 평가 순서 자체가 로직이다(유효성 → 최초 → 새로고침 → 중복탭 → desync). 순서 변경 금지.
     */
    public void handleUserSessionOnConnect(String roomId, String userId, WebSocketSession session) throws Exception {
        // 서버 저장 sessionKey 조회(최초 입장 or 기존 세션 판별 기준)
        String serverSessionKey = getSessionKey(roomId, userId);
        logger.info("[서버 측 세션키 조회] 서버 세션키 로드 완료: roomId={}, userId={}, serverSessionKey={}", roomId, userId, serverSessionKey);

        String clientSessionKey = resolveClientSessionKey(session);

        /* [유효성] 서버 기록은 없는데 클라이언트가 stale 키를 보유 → 명시적 종료 후 JS 오류/조작으로 간주.
         *          clientSessionKey를 "null"로 강제 리셋하고 클라이언트에도 초기화를 명령한 뒤, 아래 최초 입장 흐름으로 낙하한다. */
        if(!clientSessionKey.equals("null") && serverSessionKey == null) {
            logger.info("[유효성 검사] 서버 세션키 없음, 클라이언트 세션키 존재 → 강제 초기화: roomId={}, userId={}, clientSessionKey={}", roomId, userId, clientSessionKey);

            clientSessionKey = "null"; // 서버 변수 기준에서 세션키 불일치 복원
            logger.info("[세션키 리셋] clientSessionKey 변수 null로 초기화 완료");

            sendSessionKeyNull(session); // 클라이언트 세션키 초기화 명령 전송({"sessionKey":null})
            logger.info("[클라이언트 동기화] sessionKey=null 메시지 전송 완료");
        }

        if (clientSessionKey.equals("null") && serverSessionKey == null) { onFirstEntry(roomId, userId, session); return; }
        if (clientSessionKey.equals(serverSessionKey))                  { onSameTabRefresh(roomId, userId, session); return; }
        if (clientSessionKey.equals("null"))                            { onDuplicateTab(roomId, userId, session); return; }
        onDesync(session, clientSessionKey, serverSessionKey);
    }

    /**
     * @method resolveClientSessionKey
     * @brief 핸드셰이크에서 주입된 클라이언트 sessionKey(attr)를 문자열로 정규화한다(미존재 시 문자열 "null").
     */
    private String resolveClientSessionKey(WebSocketSession session) {
        Object rawKey = session.getAttributes().get("sessionKey"); // 핸드셰이크 중 설정된 클라이언트 세션키
        logger.info("[클라이언트 세션키 조회] 클라이언트 rawKey 추출: rawKey={}", rawKey);

        String clientSessionKey = (rawKey == null) ? "null" : rawKey.toString(); // null 방어 및 명시적 문자열화
        logger.info("[클라이언트 세션키 확정] 클라이언트 세션키 처리 완료: clientSessionKey={}", clientSessionKey);
        return clientSessionKey;
    }

    /**
     * @method sendSessionKey
     * @brief 클라이언트에 sessionKey 동기화 명령 전송 → {"type":"SESSION_KEY","sessionKey":"<sessionKey>"}
     *        (값을 따옴표로 감싸는 형태. desync 분기는 의도적으로 "null" 문자열을 그대로 보낸다.)
     */
    private void sendSessionKey(WebSocketSession session, String sessionKey) throws Exception {
        session.sendMessage(new TextMessage(
                String.format("{\"type\":\"SESSION_KEY\",\"sessionKey\":\"%s\"}", sessionKey)));
    }

    /**
     * @method sendSessionKeyNull
     * @brief 클라이언트 sessionKey 초기화 명령 전송 → {"type":"SESSION_KEY","sessionKey":null} (JSON literal null).
     *        sendSessionKey(…,"null")의 따옴표 형태와 페이로드가 다르므로 별도 메서드로 분리 유지.
     */
    private void sendSessionKeyNull(WebSocketSession session) throws Exception {
        session.sendMessage(new TextMessage("{\"type\":\"SESSION_KEY\",\"sessionKey\":null}"));
    }

    /**
     * @method onFirstEntry
     * @brief [최초 입장] clientKey=="null" && serverKey==null. 신규 UUID 발급·저장·전송 후 세션 등록.
     *        (세마포어 증가는 JoinService.confirm()에서 이미 수행됨)
     */
    private void onFirstEntry(String roomId, String userId, WebSocketSession session) throws Exception {
        logger.info("[최초 입장] 신규 접속 판정 → 세션키 발급 시작: roomId={}, userId={}", roomId, userId);

        String newSessionKey = UUID.randomUUID().toString(); // UUID 기반 새 sessionKey 생성
        logger.info("[세션키 생성] UUID 생성 완료: sessionKey={}", newSessionKey);

        saveSessionKey(roomId, userId, newSessionKey); // 서버 세션키 저장
        logger.info("[세션키 저장] 서버 registry에 저장 완료");

        sendSessionKey(session, newSessionKey); // 클라이언트에 세션키 전송
        logger.info("[세션키 전송] 클라이언트 전달 완료: sessionKey={}", newSessionKey);

        roomUserSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(userId, session); // 사용자 세션 등록
        logger.info("[세션 등록] roomUserSessions 등록 완료");

        roomList.computeIfAbsent(roomId, k -> new HashSet<>()).add(session); // 브로드캐스트용 등록
        logger.info("[브로드캐스트 등록] roomList 등록 완료");
    }

    /**
     * @method onSameTabRefresh
     * @brief [동일탭 새로고침/정상 복귀] clientKey==serverKey. race로 close() 미완료일 수 있어 TTL VO를
     *        최대 100ms polling 후 정리하고, 동일 세션으로 덮어쓰기 한다.
     *        ※ roomList/roomUserSessions의 remove(session) 줄은 원본 동작(키 불일치 포함)을 그대로 유지한다.
     */
    private void onSameTabRefresh(String roomId, String userId, WebSocketSession session) throws Exception {
        logger.info("[새로 고침 복귀] 동일 sessionKey 감지: roomId={}, userId={}", roomId, userId);

        int roomNum = Integer.parseInt(roomId);
        int maxWaitMs = 100, waited = 0;

        // race로 인해 close() 처리 미완료시 최대 100ms까지 대기(상태 동기화 보장)
        while (
                roomUserVOMap.containsKey(roomNum) &&
                        roomUserVOMap.get(roomNum).containsKey(userId) &&
                        waited < maxWaitMs
        ) {
            Thread.sleep(5);
            waited += 5;
        }
        logger.info("[race polling 종료] 대기 시간: {}ms", waited);

        // TTL 값 내 재 접속 했으니 해당 목록을 삭제, 스케줄러에서 오인 없도록 처리 함.
        if (roomUserVOMap.containsKey(roomNum)) {
            roomUserVOMap.get(roomNum).remove(userId);
            logger.info("[TTL 제거] roomUserVOMap 내 userId 제거 완료");

            if (roomUserVOMap.get(roomNum).isEmpty()) {
                roomUserVOMap.remove(roomNum);
                logger.info("[roomUserVOMap 삭제] 해당 방 비어 있음 → roomNum={} 제거", roomNum);
            }
        }

        // 기존 브로드 캐스트 도메인 제거
        if(roomList.get(roomId) != null) { roomList.get(roomId).remove(session); }
        if(roomUserSessions.get(roomId) != null) { roomUserSessions.get(roomId).remove(session); }
        logger.info("[세션 제거] 기존 broadcast 세션 제거 완료");

        if(roomUserSessions.get(roomId) != null && roomUserSessions.get(roomId).isEmpty()) {
            roomUserSessions.remove(roomId);
            logger.info("[roomUserSessions 삭제] roomId={} 제거됨", roomId);
        }
        if(roomList.get(roomId) != null && roomList.get(roomId).isEmpty()) {
            roomList.remove(roomId);
            logger.info("[roomList 삭제] roomId={} 제거됨", roomId);
        }

        // 새로운 브로드 캐스트 도메인 갱신
        roomUserSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(userId, session);
        roomList.computeIfAbsent(roomId, k -> new HashSet<>()).add(session);
        logger.info("[세션 갱신] 동일 탭 정상 복귀 완료: roomId={}, userId={}", roomId, userId);
    }

    /**
     * @method onDuplicateTab
     * @brief [중복 탭] clientKey=="null" && serverKey!=null. 기존 세션을 close(3000)로 강제 종료하고
     *        새 sessionKey를 발급·동기화한 뒤 세션을 교체한다.
     *        ※ roomList.get(roomId).remove(session) 줄은 원본 동작을 그대로 유지한다.
     */
    private void onDuplicateTab(String roomId, String userId, WebSocketSession session) throws Exception {
        logger.info("[중복탭 진입] clientSessionKey=null, serverSessionKey 존재 → 기존 세션 제거 시작: roomId={}, userId={}", roomId, userId);

        /* 이전 세션 강제 종료 수행 */
        WebSocketSession prevSession = roomUserSessions.get(roomId).get(userId);
        if (prevSession != null) {
            prevSession.close(new CloseStatus(3000, "중복 세션 강제 종료"));
            logger.info("[세션 종료] 기존 prevSession 강제 close 완료");
        }

        /* 새로운 탭 접속 했으므로, 세션 스토리지 내 값 또한 새로운 값으로 갱신 */
        String newSessionKey = UUID.randomUUID().toString();        // 새로운 세션 스토리지 값
        saveSessionKey(roomId, userId, newSessionKey);              // 새로운 세션 스토리지 값을 서버 내 저장.
        logger.info("[세션키 갱신] sessionKey 재발급 완료: sessionKey={}", newSessionKey);
        /* 새로운 세션 스토리지 값을 클라이언트 내 저장 : 이후 새로 고침 혹은 추가적인 중복 탭 방지 목적. */
        sendSessionKey(session, newSessionKey);
        logger.info("[클라이언트 전달] sessionKey 전송 완료");

        // 기존 과거의 브로드 캐스트 도메인 제거
        try { roomUserSessions.get(roomId).remove(userId); } catch (Exception e) { logger.error(e.getMessage()); }
        roomList.get(roomId).remove(session);
        logger.info("[세션 제거] 중복탭 이전 세션 제거 완료");

        // 새롭운 브로드 캐스트 도메인 갱신
        roomUserSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(userId, session);
        roomList.computeIfAbsent(roomId, k -> new HashSet<>()).add(session);
        logger.info("[세션 등록 완료] 중복탭 세션 교체 완료: roomId={}, userId={}", roomId, userId);
    }

    /**
     * @method onDesync
     * @brief [예외/동기화 불일치] 클라이언트가 키를 가졌으나 서버에 없거나 불일치 → 인위적 접근으로 간주.
     *        서버는 별도 저장 없이 클라이언트 SessionStorage 초기화를 명령하고 현재 세션을 close(3001)한다.
     *        ※ 여기서는 의도적으로 sendSessionKey(session,"null") → {"sessionKey":"null"}(따옴표 형태)를 보낸다.
     */
    private void onDesync(WebSocketSession session, String clientSessionKey, String serverSessionKey) throws Exception {
        logger.info("[예외/동기화 불일치] clientSessionKey={}, serverSessionKey={} ", clientSessionKey, serverSessionKey);
        sendSessionKey(session, "null");
        session.close(new CloseStatus(3001, "중복 세션 강제 종료"));
    }


    /**
     * @method handleUserSessionOnClose
     * @brief WebSocket 종료 분기 디스패처. close code로 분기한다. (반드시 afterConnectionClosed에서만 호출)
     *
     *  code  | 의미                      | 처리
     *  ----- | ------------------------- | ---------------------------------------------
     *  1000  | 명시적 나가기             | onExplicitClose (permit 반환·키 삭제·방 정리)
     *  3000  | 중복탭 강제종료(서버발)   | 로그만 (데이터는 새 탭이 이미 인수)
     *  3001  | 잘못된 접근 차단          | 로그만
     *  그 외 | 새로고침·탭·브라우저 종료 | markImplicitExitUser (TTL VO 등록)
     */
    public void handleUserSessionOnClose(String roomId, String userId, WebSocketSession session, CloseStatus closeStatus) {
        int roomNum = Integer.parseInt(roomId);
        logger.debug("[handleUserSessionOnClose] 진입: roomId={}, userId={}, closeStatus={}", roomId, userId, closeStatus);

        if (closeStatus.getCode() == 1000) {
            onExplicitClose(roomId, userId, roomNum);
        } else if (closeStatus.getCode() == 3000) {
            /* 중복 탭 생성으로 인한 종료 신호: 브라우저가 아니라 서버가 close(3000)을 호출한 결과다.
               자료구조는 새 탭이 onDuplicateTab에서 이미 인수했으므로 여기서는 로그만 남긴다. */
            logger.info("[중복 탭 따른 접속 종료] roomId={}, 현재 방 인원수={}", roomId, getRoom(roomNum).getCurrentPeople());
        } else if (closeStatus.getCode() == 3001) {
            logger.info("잘못된 세션 접근 방지 차원 종료");
        } else {
            /* 새로고침/탭/브라우저 종료: 진짜 퇴장인지 새로고침 재접속인지 JS로는 구분 불가 → 서버가 TTL로 판정한다. */
            logger.info("[비 명시적 종료] 탭 또는 브라우저 종료 - roomId={}, userId={}, closeStatus={}", roomId, userId, closeStatus);
            markImplicitExitUser(roomNum, userId);
        }
    }

    /**
     * @method onExplicitClose
     * @brief [명시적 종료(1000)] 세마포어 permit 반환 → 서버 sessionKey 삭제 → 방/세션 정리.
     *        서버가 sessionKey를 지워야(saveSessionKey 부재) 재진입 시 "최초 접속"으로 판정되어 중복탭 오인을 막는다.
     *        JS가 SessionStorage를 지우는 것은 사용자가 조작 가능하므로 서버 측 삭제가 정합성의 기준이 된다.
     */
    private void onExplicitClose(String roomId, String userId, int roomNum) {
        logger.info("[명시적 종료] roomId={}, userId={}", roomId, userId);
        // 무조건 세마포어 감소 우선..그걸 기반으로 "removeUserFromRoom" 호출 시 ChatRoom 내역 정리
        semaphoreRegistry.releasePermitOnly(roomNum);
        /* 서버 내 세션 스토리지 제거 */
        removeSessionKey(roomId, userId);
        /* 브로드 캐스트 및 중복 확인 리스트 제거.  */
        removeUserFromRoom(roomNum, userId);
        logger.info("[정상 종료] roomId={}, 현재 방 인원수={}", roomId, getRoom(roomNum).getCurrentPeople());
    }

    // =========================================================================
    // 5. [상태/세션/라이프사이클 관리 메소드]
    // =========================================================================

    /**
     * @param roomNumber 방 번호
     * @param userId     사용자 ID
     * @method addOrUpdateUserInRoom
     * @brief 비명시적 종료(TTL)인 탭 또는 브라우저 통한 종료(중복 탭 종료 아님) 시에 "roomUserVOMap" 내 사용자 등록/갱신, 즉 가장 중요한 점은 여기서 새로 고침을 목적으로 둔 단순 종료든지,
     *  아니면 진짜 단순 나가기 든지..이를 포괄적으로 처리할 수 있도록, 즉 이후에 다시 새로고침이라면 재 접속 할 수 있도록 포괄해서 자료구조를 조작해야 된다.
     */
    public void markImplicitExitUser(int roomNumber, String userId) {
        // [1] 로그: 메서드 진입 및 파라미터 기록
        logger.info("[markImplicitExitUser] - roomNumber={}, userId={}", roomNumber, userId);

        // [2] 해당 방 번호의 "roomUserVOMap" 이 없다면 지금 위치에서 새롭게 생성, 그리고 "userMap" 으로 반환.
        Map<String, RoomUserStateVO> userMap = roomUserVOMap.computeIfAbsent(roomNumber, k -> {
            logger.info("[markImplicitExitUser] 신규 방 userMap 생성: roomNumber={}", roomNumber);
            return new ConcurrentHashMap<String, RoomUserStateVO>();
        });

        // [3] 만료 시각 계산 : 만약 이 안에 안 들어오면 스케줄러에서 "userMap.get(userId)" 내용 까지 전부 지우고 이에 따라 진짜 방 나간 것으로 간주해서 방 진짜로 삭제 해 버림.
        long now = System.currentTimeMillis();
        long expireAt = now + USER_TTL_MILLIS;
        // [4] 실제 등록/갱신 처리
        userMap.put(userId, new RoomUserStateVO(userId, expireAt));
        logger.info("[markImplicitExitUser] roomUserVOMap 등록 완료: roomNumber={}, userId={}, expireAt={}", roomNumber, userId, userMap.get(userId).getExpireAt());

        /* 일단 현재 브로드 캐스트 도메인을 제외 한다, 새로 고침으로 인해 발생된 close() 이든, 단순 종료에 의한 close() 발생이든 간에, 불 필요한 브로드 캐스트 전달 방지 목적 */
        // [A] 방 번호 기준으로 sessionSet 추출
        HashSet<WebSocketSession> sessionSet = roomList.get(String.valueOf(roomNumber));
        if (sessionSet != null) {
            // [B] 해당 userId에 매핑된 WebSocketSession 객체 탐색
            WebSocketSession targetSession = null;
            for (WebSocketSession session : sessionSet) {
                Object attrUserId = session.getAttributes().get("userId");
                if (userId.equals(attrUserId)) {
                    targetSession = session;
                    break;
                }
            }
            // [C] 찾았다면 정확히 제거
            if (targetSession != null) {
                sessionSet.remove(targetSession);
                logger.info("[markImplicitExitUser] 브로드캐스트 도메인에서 해당 세션 제거: roomNumber={}, userId={}", roomNumber, userId);
            }
        }
        /* 방 인원 수 감소 : 현 위치에서 삭제를 하지 않으면 기술상, 새로 고침과 진짜 탭 단위 종료 시 구분이 안되는 상황에서 나중에 재 인원수 갱신을 할 지 언정, 사용자 경험에 결함이 생기기 때문.
         *   ** 단, 실제 접속 판별은 TTL 기반으로 일정 시간 내 새로고침으로써 재 접속하지 않으면 "userMap" 에 의해서 실제로 삭제 된다. */
        getRoom(roomNumber).setCurrentPeople(semaphoreRegistry.getAvailablePermits(roomNumber) + 1);
        /* 중복 세션 리스트는 삭제하지 않음, 근거는 JoinService.confirm() 접근 시 새로 고침은 결국 현재 존재하는 세션 리스트 목록을 기반하여 판별하기 때문. */
    }


    /*
     * @method removeUserFromRoom
     * @brief 실제 사용자 완전 퇴장/상태 정합성 정리(permit 반환, 방/세션/VO 모두 동기화), 세마포어는 정리는 하지 않는다,
     * @param roomNumber 방 번호
     * @param userId     사용자 ID
     */
    public void removeUserFromRoom(int roomNumber, String userId) {

        String roomIdStr = String.valueOf(roomNumber); // [동작] 방 번호를 String으로 변환하여 자료구조 키에 일관적으로 사용

        // 1. roomUserSessions에서 해당 userId의 세션 삭제
        Map<String, WebSocketSession> userSessions = roomUserSessions.get(roomIdStr); // [조회] 방 별로 관리되는 userId→WebSocketSession 맵 획득(실시간 세션 추적의 기준)
        logger.info("[removeUserFromRoom] userSessions 조회: roomNumber={}, userId={}, userSessionsNull={}", roomNumber, userId, userSessions == null);

        if (userSessions != null) {
            // 해당 userId에 해당하는 세션 제거(실질적으로 세션 추적에서 사용자 단위로 강제 퇴장)
            WebSocketSession removedSession = userSessions.remove(userId); // [행위] 해당 userId에 매핑된 WebSocketSession 제거(중복 세션까지 확정 제거)
            logger.info("[removeUserFromRoom] userSessions.remove: roomNumber={}, userId={}, sessionNull={}", roomNumber, userId, removedSession == null);

            // userSessions가 완전히 비면 roomUserSessions에서 roomId 자체를 제거(방이 비면, 전체 세션맵에서도 방 단위 삭제)
            if (userSessions.isEmpty()) {
                roomUserSessions.remove(roomIdStr); // [정합성] 더 이상 이 방에 어떤 사용자 세션도 없음을 명시
                logger.info("[removeUserFromRoom] userSessions 비어 삭제: roomNumber={}", roomNumber);
            }
        }

        // 2. roomList에서 해당 userId에 대한 세션 삭제 (세션 객체를 직접 참조할 필요 없이 userId만 기준)
        HashSet<WebSocketSession> sessionSet = roomList.get(roomIdStr); // [조회] 브로드캐스트 목적의 세션 집합(방 단위 세션 전체 집합)
        logger.info("[removeUserFromRoom] roomList.get: roomNumber={}, sessionSetNull={}", roomNumber, sessionSet == null);

        if (sessionSet != null) {
            // userSessions에서 이미 삭제했으므로, sessionSet에서는 해당 userId에 매핑된 모든 세션을 제거
            // (userSessions가 삭제되었으므로 sessionSet에 남은 참조도 모두 정리)
            sessionSet.removeIf(ws -> {
                Object attrUserId = ws.getAttributes().get("userId"); // [추출] 세션 속성에서 userId 획득(JS handshake에서 주입)
                return userId.equals(attrUserId); // [동작] 동일 userId에 해당하는 세션만 제거
            });

            logger.info("[removeUserFromRoom] sessionSet.removeIf(userId=={}): roomNumber={}", userId, roomNumber);

            // sessionSet이 비면 roomList에서 roomId 자체를 제거(더 이상 이 방에 전송 대상 세션이 없음)
            if (sessionSet.isEmpty()) {
                roomList.remove(roomIdStr); // [정합성] 브로드캐스트 대상 세션 집합 완전 정리
                logger.info("[removeUserFromRoom] roomList 비어 삭제: roomNumber={}", roomNumber);
            }
        }

        /* 방 내 인원수를 세마포어 기반으로 정리, 그리고 방 인원수가 비어 있다면 해당 방 자체를 지움, */
        ChatRoom chatRoom = roomMap.get(roomNumber); // [조회] 실제 채팅방 정보 객체(ChatRoom: maxPeople, currentPeople 등)
        logger.debug("[removeUserFromRoom] roomMap.get: roomNumber={}, chatRoomNull={}", roomNumber, chatRoom == null);
        if (chatRoom != null) {
            int currentPeople = chatRoom.getMaxPeople() - semaphoreRegistry.getAvailablePermits(roomNumber); // [계산] 세마포어 permit과 maxPeople로 현재 인원 산출
            updateRoomCurrentPeople(roomNumber, currentPeople); // [동기화] ChatRoom 객체 내 인원수 필드 갱신
            logger.debug("[removeUserFromRoom] 세마포어 permit 조회: roomNumber={}, availablePermits={}", roomNumber, currentPeople);

            boolean chatRoomEmpty = currentPeople == 0; // [판별] 현재 방 인원이 0명인지 확인(퇴장, 스케줄러 등으로 완전히 비었는가)
            boolean voEmpty = !roomUserVOMap.containsKey(roomNumber) || roomUserVOMap.get(roomNumber).isEmpty(); // [판별] 임시 퇴장/복귀 VO(비명시적 종료 상태)까지 완전 비었는가
            boolean sessionEmpty = !roomUserSessions.containsKey(String.valueOf(roomNumber)) || roomUserSessions.get(String.valueOf(roomNumber)).isEmpty(); // [판별] 세션까지 모두 정리되었는가

            logger.debug("[removeUserFromRoom] roomNumber={}, chatRoomEmpty={}, voEmpty={}, sessionEmpty={}",
                    roomNumber, chatRoomEmpty, voEmpty, sessionEmpty);

            /* 실제 방 삭제 시 필요한 3 가지 조건.
             *   1. 방 인원수 0 명
             *   2. 방 임시 퇴장 인원 수 0명 : 만약 새로 고침 로직 구현 중 이라면, 방 인원수는 0 명이나 새로고침은 필히 다시 재 접속을 하는데, 이 상황을 상정하면,  방을 삭제하면 안되기 때문.
             *   3. 중복 세션 리스트 삭제 : 해당 리스트가 삭제되지 않으면 이후 새롭게 들어오는 세션을 구분 못하므로, 이 부분을 방지하기 위함.
             * */
            if (voEmpty && sessionEmpty && chatRoomEmpty) {
                roomMap.remove(roomNumber); // [행위] 실제 방 정보(roomMap) 자체를 메모리에서 완전 삭제
                logger.info("[removeUserFromRoom] 빈 방 삭제: roomNumber={}", roomNumber);
            }
        }
    }

    /**
     * @param roomNumber       방 번호
     *      * @param newCurrentPeople 갱신 인원수
     *      * @method updateRoomCurrentPeople
     *      * @brief permit/세마포어 기반 인원수 동기화. ChatRoom 내 인원수 직접 갱신.
     */
    public void updateRoomCurrentPeople(int roomNumber, int newCurrentPeople) {
        roomMap.computeIfPresent(roomNumber, (num, room) -> {
            room.setCurrentPeople(newCurrentPeople);
            logger.info("[updateRoomCurrentPeople] roomNumber={}, newCurrentPeople={}, chatRoom.CurrentPeople()={}", roomNumber, newCurrentPeople, room.getCurrentPeople());
            return room;
        });
    }

    /**
     * @param roomNumber 방 번호
     * @param chatRoom   ChatRoom 객체
     * @method createRoom
     * @brief 신규 방/상태 자료구조 생성 및 등록
     */
    public void createRoom(int roomNumber, ChatRoom chatRoom) {
        roomMap.put(roomNumber, chatRoom);
        roomUserVOMap.put(roomNumber, new ConcurrentHashMap<>());
        logger.info("[createRoom] 신규 방 생성: roomNumber={}, chatRoom={}", roomNumber, chatRoom);
    }

// =========================================================================
// 6. [조회/Getter/Export/Utility 메소드]
// =========================================================================

    /**
     * @param roomNumber 방 번호
     * @return ChatRoom 객체
     * @method getRoom
     * @brief 단일 방 정보 반환
     */
    public ChatRoom getRoom(int roomNumber) {
        ChatRoom chatRoom = roomMap.get(roomNumber);
        logger.info("[getRoom] roomNumber={}", roomNumber);
        return chatRoom;
    }

    /**
     * @return 방번호→ChatRoom Map
     * @method getRooms
     * @brief 전체 방 정보 반환(Map)
     */
    public Map<Integer, ChatRoom> getRooms() {
        logger.info("[getRooms] 전체 방 개수={}", roomMap.size());
        return roomMap;
    }

    /**
     * @param roomNumber 방 번호
     * @return HashSet<WebSocketSession>
     * @method getRoomSessions
     * @brief 특정 방의 모든 WebSocketSession을 HashSet으로 반환
     */
    public HashSet<WebSocketSession> getRoomSessions(String roomNumber) {

        logger.info("[getRoomSessions] roomNumber={}", roomNumber);

        HashSet<WebSocketSession> userSessions = roomList.get(roomNumber);

        logger.info("[getRoomSessions] roomNumber={}, sessionCount={}",
                roomNumber, userSessions == null ? 0 : userSessions.size());

        if (userSessions == null) return new HashSet<>();

        else return userSessions;
    }

    /*
     * @param roomId 방 번호
     * @param userId 사용자ID
     * @return true/false
     * @method containsUser
     * @brief 해당 방에 userId가 실시간 세션으로 등록되어 있는지 조회(1차 중복세션 방지)
     */
    public boolean containsUser(String roomId, String userId) {
        Map<String, WebSocketSession> userSessions = roomUserSessions.get(roomId);
        boolean exists = userSessions != null && userSessions.containsKey(userId);
        logger.info("[containsUser] roomId={}, userId={}, exists={}", roomId, userId, exists);
        return exists;
    }


    // =========================================================================
    // 7. [sessionKey 관리(저장/조회/동등성비교)]
    // =========================================================================

    /**
     * @param roomId     방 번호
     * @param userId     사용자ID
     * @param sessionKey 세션키(UUID 등)
     * @method saveSessionKey
     * @brief roomUserSessionKeyMap에 sessionKey 저장/갱신
     */
    public void saveSessionKey(String roomId, String userId, String sessionKey) {
        roomUserSessionKeyMap
                .computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .put(userId, sessionKey);
        logger.debug("[saveSessionKey] 저장: roomId={}, userId={}, sessionKey={}", roomId, userId, sessionKey);
    }

    /**
     * @param roomId     방 번호
     * @param userId     사용자ID
     * @method removeSessionKey
     * @brief roomUserSessionKeyMap에서 sessionKey를 삭제(특정 userId 단위)
     */
    public void removeSessionKey(String roomId, String userId) {
        Map<String, String> userKeyMap = roomUserSessionKeyMap.get(roomId);
        if (userKeyMap != null) {
            userKeyMap.remove(userId);
            logger.debug("[removeSessionKey] 삭제: roomId={}, userId={}", roomId, userId);
            // userId 모두 삭제된 경우, roomId 엔트리 자체도 삭제
            if (userKeyMap.isEmpty()) {
                roomUserSessionKeyMap.remove(roomId);
                logger.debug("[removeSessionKey] roomId 엔트리까지 삭제: roomId={}", roomId);
            }
        }
    }


    /**
     * @method getSessionKey
     * @brief (roomId, userId) 기준 현재 서버 측에 등록된 sessionKey(UUID) 반환
     *        - 클라이언트(브라우저)에서 세션 키와 서버의 저장 상태가 동등한지 검증하는 데 사용.
     *        - 반환값이 null이면: (1) 아예 입장 기록이 없거나, (2) 퇴장/만료로 삭제된 상태임을 의미.
     *        - "roomUserSessionKeyMap" 구조는 방 번호(roomId)별로 <userId, sessionKey> 쌍을 관리.
     *        - 반환 값이 null일 때 반드시 클라이언트와 서버의 세션 동기화 불일치 상황을 감지·처리해야 하며,
     *          race condition 상황(즉시 퇴장/즉시 입장 동시 발생 등)에서도 참조된다.
     * @param roomId 방 번호 (String, 자료구조 키)
     * @param userId 사용자 ID
     * @return sessionKey (UUID 등, String), 저장된 값 없으면 null
     */
    public String getSessionKey(String roomId, String userId) {
        logger.info("[getSessionKey] 진입: roomId={}, userId={}", roomId, userId);

        // [1] 방 번호(roomId) 기준, 서버에 등록된 <userId, sessionKey> 맵 조회
        Map<String, String> userKeyMap = roomUserSessionKeyMap.get(roomId);
        if (userKeyMap == null) {
            // [2] 해당 방 번호에 대한 세션키 맵이 존재하지 않음(=등록된 적 없음, 또는 전체 방 삭제 등)
            logger.info("[getSessionKey] userKeyMap==null: roomId={}, userId={}", roomId, userId);
            return null;
        }

        // [3] 실제 userId 기준 sessionKey 조회 (존재하지 않으면 null)
        String sessionKey = userKeyMap.get(userId);

        // [4] 반환값 로그: sessionKey 존재 여부를 구분하여 기록
        if (sessionKey == null) {
            logger.debug("[getSessionKey] sessionKey==null: roomId={}, userId={}", roomId, userId);
        } else {
            logger.debug("[getSessionKey] 반환: roomId={}, userId={}, sessionKey={}", roomId, userId, sessionKey);
        }

        return sessionKey;
    }



    // =========================================================================
    // 8. [내부/보조/유틸리티]
    // =========================================================================

    /**
     * @method cleanupExpiredUsers
     * @brief roomUserVOMap 기준 TTL 만료 사용자 및 빈 방 자동 정리(상태 일치 보장)
     *        - 각 TTL 만료 시점의 userId에 대해 roomUserVOMap, 세마포어 permit, roomUserSessions, roomList, roomMap까지 일관성 있게 제거
     *        - 삭제/변경/누수 방지 처리에 대해 상세 로그 기록
     */
    /**
     * @method cleanupExpiredUsers
     * @brief TTL 만료 사용자 및 빈 방 리소스/상태/세션/VO 완전 일관성 통합 제거. 동시성 race/누수 방지, 구조적 일치성 강제.
     */
    public void cleanupExpiredUsers() {
        long now = System.currentTimeMillis();
        Set<Integer> roomsToCheck = new HashSet<>(roomUserVOMap.keySet());

        /* "roomUserVOMap" 내 모든 방 번호 별로 순차 조회 하면서 각 방 내 VO 들을 각각 시간 기준으로 검사하면서 스케줄링 대상 이면 진행.    */
        for (Integer roomNumber : roomsToCheck) {           // 각 방 번호를 기준으로 순차적으로 조회.
            /* 각 방 번호에 대한 <사용자 ID. VO 객체> 집합 추출 */
            Map<String, RoomUserStateVO> userMap = roomUserVOMap.get(roomNumber);

            /* VO TTL 검사 후 삭제 대상 리스트업 : 현재 위치에서 삭제 진행하지 않고 이후에 삭제 진행할 리스트 업 */
            List<String> expiredUserIds = new ArrayList<>();    // 리스터 업, 즉 "roomUserVOMap" 순회 하면서 각 방 번호 저장 된 자료구조 객체 내부를 확인해서 TTL 초과된 VO 객체들 검사 수행.
            /* 현재 방 번호의 <사용자 ID. VO 객체> 집합 내부를 순차적으로 확인하여 실제 TTL 이 초과된 VO 객체 있는 지 확인. */
            for (Map.Entry<String, RoomUserStateVO> entry : userMap.entrySet()) {
                /* 검사 진행. */
                RoomUserStateVO roomUserStateVO = entry.getValue();
                /* TTL 초과 검사 결과 해당 <사용자 Id, vo 객체> 를 확인 후 해당 사용자 ID 를 순차적으로 저장.  */
                if (roomUserStateVO.getExpireAt() < now) {
                    expiredUserIds.add(entry.getKey());
                    logger.info("[cleanupExpiredUsers] TTL 만료 객체, 리스트 내 포함 - roomNumber={}, userId={}, expireAt={}, now={}", roomNumber, entry.getKey(), roomUserStateVO.getExpireAt(), now);
                }
            }

            // 현재 방 번호 내 TTL 만료 사용자 일괄 정리
            for (String userId : expiredUserIds) {
                logger.info("[cleanupExpiredUsers] 시작: roomUserVOMap(방 번호 기준) 개수={}, now={}", roomsToCheck.size(), now);
                try {
                    /* 1. removeUserFromRoom에서 상태 정리 : 해당 메소드는 방 번호와 User Id 를 받아서 다음 자료구조 들을 갱신하며
                        1) 브로드 캐스트 도메인(roomList)
                        2) 중복 세션 리스트(roomUserSessions)
                        3) 실제 방 ID 별 방 정보 (roomMap)

                        즉, 새로 고침 혹은 미 명료적으로 채팅방을 나갔으면서 TTL 복귀 값을 초과한 사용자들은 전부 실제 채팅방에서 제외한다.
                     */
                    /* 실제 TTL 초과 인원수 삭제 */
                    roomUserVOMap.get(roomNumber).remove(userId);
                    /* 메모리 누수 방지 : 현재 방 번호 기준, "roomUserVOMap" 이 비었다면, 이를 삭제  */
                    if(roomUserVOMap.get(roomNumber).isEmpty()) { roomUserVOMap.remove(roomNumber); }
                    /* 1. permit 반환 : "removeUserFromRoom" 내에서는 별도로 세마포어 감소를 하지 않기 때문에 현재 위치에서 수행. */
                    semaphoreRegistry.releasePermitOnly(roomNumber);
                    /* 2. 비 명료적 종료(새로 고침 혹은 단순 탭 단위 종료)한 사용자에 대한 자료구조 갱신. */
                    removeUserFromRoom(roomNumber, userId);

                    /* 미 삭제 방 로그 */
                    if(getRoom(roomNumber) != null) {
                        logger.info("[cleanupExpiredUsers] 방 별 유후 접속자 정리 : roomNumber={}, 방 내 현재 인원수={}, userId={}",
                                roomNumber, getRoom(roomNumber).getCurrentPeople() - semaphoreRegistry.getAvailablePermits(roomNumber) ,userId);
                    }
                    /* 방 삭제 로그 */
                    else { logger.info("[cleanupExpiredUsers] 방 종료 - roomNumber={}, userId={}", roomNumber, userId); }

                } catch (Exception e) { logger.error("[cleanupExpiredUsers] 예외: roomNumber={}, userId={}, err={}", roomNumber, userId, e.toString(), e); }
                logger.info("[cleanupExpiredUsers] 종료: 남은 방 개수={}", roomUserVOMap.size());
            }
        }
    }
}
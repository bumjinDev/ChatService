package com.chatservice.joinroom.dao;

import com.chatservice.concurrency.SemaphoreRegistry;
import com.chatservice.joinroom.service.RoomJoinService;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 사용자별 TTL 기반 인원 관리 구조 반영
 *
 * 각 방의 참여자를 단순 Set이 아니라 Map<String, RoomUserStateVO>로 관리하며,
 * VO 내부에 TTL 등 만료 시간, 사용자 ID, 필요시 기타 상태를 포함.
 *
 * 방 번호 -> (사용자 ID -> 사용자 상태 VO) 구조로 관리.
 *
 * 만료 로직: 스케줄러에서 전체 방-사용자 VO 순회하며, 만료된 VO를 제거.
 *          방에 남은 사용자가 없으면 방 자체를 메모리에서 제거.
 */

@Component
public class ChatRoomMemoryRegistry {

    SemaphoreRegistry semaphoreRegistry;
    private static final Logger logger = LoggerFactory.getLogger(ChatRoomMemoryRegistry.class);

    public ChatRoomMemoryRegistry(SemaphoreRegistry semaphoreRegistry){
        this.semaphoreRegistry = semaphoreRegistry;
    }

    // 방 번호별 ChatRoom 객체 관리 : 채팅 대기방 리스트 페이지 내 표현될 정보, 즉 방 인원 수 변동에 따라서 세마포어 별개로 현재 방 인원 수 조정
    private final Map<Integer, ChatRoom> roomMap = new ConcurrentHashMap<>();

    /**
     * 방 번호별 사용자별 상태 VO 관리 (ID → VO)
     *
     * 구조적/설계적 배경:
     * - WebSocket 연결 종료(close) 시, 실제 “나가기” 버튼 클릭이 아닌 탭 종료, 브라우저 X 버튼, 새로고침 등 클라이언트 이벤트의 구분이 불가능하다.
     * - 서버 입장에서는 별도의 플래그나 식별 정보를 활용해도 이 이벤트들을 논리적으로 분리할 수 없어, 명시적 퇴장 외 모든 케이스에 대해 사용자 세션을 즉시 제거하지 않고,
     *   사용자별 내부 VO 객체(RoomUserStateVO)에 TTL(만료 시각)을 부여하여 짧은 시간 동안 임시 유지한다.
     *
     * 동작 및 스케줄링 정책:
     * - 스케줄러는 주기적으로 roomUserVOMap(방 번호 → 사용자 ID → VO)을 순회하며,
     *   각 사용자 VO의 TTL을 검사해 만료된 사용자를 삭제하고 해당 삭제 된 Id 의 사용자가 소속된 방 번호를 "roomMap" 에서 찾아서 이 역시 삭제한다.
     * - 만약 방 내 사용자가 모두 만료 되면, 해당 방 번호를 roomMap(방 번호 → ChatRoom)에서 직접 제거하여 방 자체를 소멸시킨다.
     * - 스케줄러 검사 주기는 실제 구현상 매우 짧게 설정할 수 있으나, 이로 인한 리소스 소모(성능 저하, 불필요 반복)는 추후 개선 과제로 별도 관리한다.
     *
     * 구현상 주요 변경 및 보완 사항:
     * - 기존 WebSocket Registry에 존재하던 “중복 입장 방지” 및 “사용자 관리” 책임은 본 구조로 완전히 이관하며, Registry 내 별도 자료구조는 삭제한다.
     * - 사용자가 일정 시간 내 재입장하면, 해당 userId에 대한 VO의 TTL이 갱신되어 “새로고침”으로 간주, roomUserVOMap에서 즉시 복원된다.
     * - 반면, TTL 내 재입장이 없으면 스케줄러가 자동으로 인원/방 모두에서 완전히 제거한다.
     *
     * 설계상 효과:
     * - 명확한 인원 관리, 중복/재입장/새로고침 케이스 자동 분기, 실시간 상태 일관성 보장.
     * - 모든 상태 변화가 단일 구조 내에서 동기화되므로, 별도의 동시성/이중화/중복 제어 로직이 불필요해진다.
     */
    private final Map<Integer, Map<String, RoomUserStateVO>> roomUserVOMap = new ConcurrentHashMap<>();


    // 사용자별 TTL 유지 시간(예시: 3분) - 실무적으론 설정값 주입
    private static final long USER_TTL_MILLIS = 3 * 60 * 1000L;

    // 사용자 상태 VO 정의 (내장형, 필요시 별도 파일 분리 가능)
    public static class RoomUserStateVO {
        private final String userId;
        @Setter
        private long expireAt; // ms 기준

        public RoomUserStateVO(String userId, long expireAt) {
            this.userId = userId;
            this.expireAt = expireAt;
        }
        public String getUserId() { return userId; }
        public long getExpireAt() { return expireAt; }
    }

    // 방 생성 : 실제 방 생성 하는 것, TTL 유지 목적 아님
    public void createRoom(int roomNumber, ChatRoom chatRoom) {
        roomMap.put(roomNumber, chatRoom);
        roomUserVOMap.put(roomNumber, new ConcurrentHashMap<>());
    }

    // 방 정보 조회 : 실제 방 번호 별 방이 있는 지 조회.
    public ChatRoom getRoom(int roomNumber) {
        return roomMap.get(roomNumber);
    }


    /**
     * [사용자 퇴장 및 방 상태 일관성 처리]
     *
     * 목적:
     * - 사용자가 명시적으로 방을 나가거나, 스케줄러에 의해 자동으로 퇴장 처리될 때,(roomUserVOMap 내 TTL 만료)
     *   roomUserVOMap과 roomMap 등 상태 정보를 일관성 있게 갱신·정리한다.
     * - 이때 인원수 동기화 뿐만 아닌 필요시 방 삭제까지 모든 책임을 일괄 처리한다.
     *
     * 사용처:
     * 1) WebSocketHandler.close() : 실시간 명시적 퇴장 시에 발생되는 permit 반환 따라 "roomMap" 내 상태 정리.
     * 2) cleanupExpiredUsers() : "roomUserVOMap" 내 TTL 만료 사용자 스케줄러에 의한 자동 정리 시 상태 정리.
     * 3) 위 두 경로 모두에서 방 내 인원이 0명일 경우, roomMap/roomUserVOMap에서 방 정보를 완전히 삭제한다.
     *
     * 중요한 설계 기준:
     * - permit(세마포어) 감소는 반드시 WebSocketHandler.afterConnectionClosed() 내에서 일괄 수행되어야 한다, 근거는
     *   중복 permit 감소 또는 코드 분산으로 인한 유지보수 비용 증가를 방지한다.
     *
     * - cleanupExpiredUsers 역시 내부적으로 현재 메소드를 호출하여 코드 중복 없이, 각 세션/사용자별 퇴장 처리를 일관성 있게 관리하도록 설계되어 있다.
     */
    public void removeUserFromRoom(int roomNumber, String userId) {

        // 1. roomUserVOMap에서 해당 사용자 제거(새로고침/비정상 종료 포함)
        Map<String, RoomUserStateVO> userMap = roomUserVOMap.get(roomNumber);
        if (userMap != null) {
            userMap.remove(userId);
            // 사용자 모두 제거된 경우 방 자체 roomMap에서도 제거
            if (userMap.isEmpty()) {
                roomUserVOMap.remove(roomNumber);
                roomMap.remove(roomNumber);
            }
        }

        // 2. 현재 방 인원수(세마포어 permit 기반) 정보 갱신
        ChatRoom chatRoom = roomMap.get(roomNumber);
        if (chatRoom != null) {
            updateRoomCurrentPeople(roomNumber, semaphoreRegistry.getAvailablePermits(roomNumber));
        }

        // 3. 인원수 0명인 경우 방 정보 일괄 삭제
        if (chatRoom.getCurrentPeople() == 0 && !userMap.isEmpty()) {
            roomMap.remove(roomNumber);
            roomUserVOMap.remove(roomNumber);
        }
    }




    // 사용자 퇴장 - 명시적 아닌 탭 또는 브라우저 x 버튼 통한 나간 것.
    public void addOrUpdateUserInRoom(int roomNumber, String userId) {
        Map<String, RoomUserStateVO> userMap =
                roomUserVOMap.computeIfAbsent(roomNumber, k -> new ConcurrentHashMap<>());
        userMap.put(userId, new RoomUserStateVO(userId, System.currentTimeMillis() + USER_TTL_MILLIS));
    }

    /* 방 내 인원수를 갱신 하며, WebSocketHandler.close() 및 JoinRoomService.joinRoom() 에서 수행(이유는 세션이 실제로 확정 되는 건, 웹 소켓 핸들러 이후 이므로) */
    public void updateRoomCurrentPeople(int roomNumber, int newCurrentPeople) {
        roomMap.computeIfPresent(roomNumber, (num, room) -> {
            room.setCurrentPeople(newCurrentPeople);
            return room;
        });
    }

    /*
     * [새로고침 여부 확인 및 중복 처리 방지 메소드]
     *
     * - 목적: roomUserVOMap 내 해당 방 번호(roomNumber)의 요청자(userId)가 이미 존재하면
     *         이는 "새로고침" 또는 동일 사용자의 재입장으로 판단한다.
     *         이 경우, 중복된 세마포어 인원 증가, 불필요한 신규 방 생성, 기존 방 여부 재판별 등의
     *         비효율을 차단하기 위해 기존 VO를 반환하고, 없으면 null을 반환하여 정상 입장 로직을 수행하게 한다.
     *   (JoinRoomService.confirmRoom()에서 호출됨)
     */
    public RoomUserStateVO findRoomUserStateVO(int roomNumber, String userId) {

        // 해당 방 번호에 대한 사용자 상태 맵 존재 여부를 Optional로 감싼다.
        Optional<Map<String, RoomUserStateVO>> userMap = Optional.ofNullable(roomUserVOMap.get(roomNumber));
        Optional<RoomUserStateVO> roomUserStateVO;

        if (userMap.isPresent()) {
            // 해당 userId에 대한 상태 VO가 존재하면, 새로고침 또는 중복 입장 시도로 간주
            roomUserStateVO = Optional.ofNullable(userMap.get().get(userId));
            // 값이 존재하면 기존 VO 반환(중복 처리 차단), 없으면 null 반환(정상 입장 로직 수행)
            if (!roomUserStateVO.isEmpty()) {
                logger.info("[새로 고침] - 사용자 = {}, 방 번호 = {}", userId, roomNumber);
                return roomUserStateVO.get();
            } else
                return null;

        } else {
            // 방 번호 자체가 존재하지 않는 경우 → 해당 사용자의 새로운 입장으로 간주
            return null;
        }
    }


    /* 재 접속이 확인 되면 해당 방 id 별 접속한 사용자 id를 기준으로, 스케줄러에 의해 제거 되지 않도록 "roomUserVOMap" 내에서 해당 사용자 목록 제거.
    * 사용처 : JoinRoomService.confirmJoinRoom() */
    public void removeUserRoomMap(int roomNumber, String userId){
        roomUserVOMap.get(roomNumber).remove(userId);
    }

    /* "roomMap" 전체 반환, "RoomListApiService.getRoomList()" 에서 사용함.  */
    public Map<Integer, ChatRoom>  getRooms() {
        return roomMap;
    }


    /*
     * [스케줄러 기반 만료 사용자 정리]
     *
     * 목적:
     * - roomUserVOMap 내 각 방(roomNumber) 별 사용자(UserId) 상태를 주기적으로 검사 하여,
     *   TTL(expireAt) 기준으로 만료된 사용자를 자동으로 퇴장 처리한다.
     * - 이 방식은 새로고침·브라우저 종료 등 명시적 퇴장 신호가 불가능한 비정상 종료 케이스를 TTL 기반으로 판별·정리하는 역할이다.
     *
     * 동작 흐름:
     * 1. 모든 방 번호를 순회하며, 각 방의 사용자 상태 Map을 조회한다.
     * 2. 각 사용자별로 expireAt < 현재시각(now)인 경우, 만료된 사용자로 간주한다.
     * 3. 만료된 사용자에 대해 removeUserFromRoom(roomNumber, userId)를 호출하여
     *    permit 반환·상태 동기화·방 삭제 여부 판정 등 모든 후속 처리를 일괄 위임한다.
     *
     * 주의:
     * - 반드시 removeUserFromRoom를 호출 하여 실제 "roomUserVOMap"의 반환 및 현재 방 상태에 따른 동기화가 일관되게 이뤄져야 한다.
     */
    public void cleanupExpiredUsers() {
        long now = System.currentTimeMillis();
        Set<Integer> roomsToCheck = new HashSet<>(roomUserVOMap.keySet());

        for (Integer roomNumber : roomsToCheck) {
            Map<String, RoomUserStateVO> userMap = roomUserVOMap.get(roomNumber);
            if (userMap == null) continue;

            List<String> expiredUserIds = new ArrayList<>();
            for (Map.Entry<String, RoomUserStateVO> entry : userMap.entrySet()) {
                if (entry.getValue().getExpireAt() < now) {
                    expiredUserIds.add(entry.getKey());
                }
            }

            // permit 반환·상태 동기화 등 모든 후속 처리는 removeUserFromRoom로 일괄 위임
            for (String userId : expiredUserIds) {
                removeUserFromRoom(roomNumber, userId);
            }
        }
    }
}

package com.chatservice.concurrency;

import com.chatservice.joinroom.service.RoomJoinService;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * @class SemaphoreRegistry
 * @brief 채팅방별 동시 입장 제어를 위한 세마포어 레지스트리
 *
 * @details
 * - 방 번호 기준 세마포어(Semaphore)를 관리하며, 입장 요청에 대해 permit 점유를 시도하고,
 *   연결 성공 여부에 따라 자원을 정리한다.
 * - TTL 기반 사용자 추적 및 세마포어 생성 시점 관리를 통해 누락, 누수 방지
 */
@Component
public class SemaphoreRegistry {

    private static final Logger logger = LoggerFactory.getLogger(SemaphoreRegistry.class);

    /**
     * [1] 세마포어 저장소
     * - 각 roomId에 대해 입장 가능 인원을 제한하는 Semaphore 객체를 보관
     * - 초기 등록은 RoomJoinService에서, 회수는 ChatServiceScheduler 또는 연결 종료 시점
     */
    private final Map<Integer, Semaphore> semaphoreMap = new ConcurrentHashMap<>();

    /**
     * [2] 생성 시각 저장소
     * - createdAtMap: 임시 방 생성 시점 기록
     * - TTL 초과된 방은 연결 실패로 간주 → 스케줄러가 자원 제거
     * - WebSocket 연결 완료되면 제거 또는 timestamp 갱신 필요
     */
    private final Map<Integer, Long> createdAtMap = new ConcurrentHashMap<>();

    /**
     * [3] 사용자-방 permit 점유 추적
     * - WebSocket 연결이 실패할 경우 TTL 스케줄러가 점유 permit 회수 대상 확인용
     * - 연결 성공 시 WebSocketHandler가 직접 제거
     * -- GETTER --
     *
     * @return 전체 사용자-방 permit 점유 목록
     *
     */

    @Getter
    private final Map<UserRoomKey, Long> userPermitMap = new ConcurrentHashMap<>();

    /**
     * @class UserRoomKey
     * @brief userId + roomId 조합 기반 키 (userPermitMap의 Key 용도)
     *
     * @note
     * - String userId로 타입 변경됨
     * - hashCode / equals 오버라이드 필수
     */
    @Getter
    public static class UserRoomKey {
        private final String userId;
        private final int roomId;

        public UserRoomKey(String userId, int roomId) {
            this.userId = userId;
            this.roomId = roomId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            UserRoomKey that = (UserRoomKey) obj;
            return roomId == that.roomId &&
                    (userId != null ? userId.equals(that.userId) : that.userId == null);
        }

        @Override
        public int hashCode() {
            int result = userId != null ? userId.hashCode() : 0;
            result = 31 * result + roomId;
            return result;
        }
    }

    // ============================================================================================
    // 🔹 등록 및 자원 초기화
    // ============================================================================================

    /**
     * @method registerSemaphoreOnly
     * @param roomId 방 번호
     * @param maxPeople 최대 입장 가능 인원
     * @brief DB에 이미 존재하는 방에 대해 세마포어만 등록 (TTL 없음)
     * @called_by RoomJoinService
     */
    public void registerSemaphoreOnly(int roomId, int maxPeople) {
        semaphoreMap.putIfAbsent(roomId, new Semaphore(maxPeople, true));
    }

    /**
     * @method registerWithTimestamp
     * @brief 신규 방 입장 요청 → 세마포어 등록 + 생성 시각 기록
     * @note TTL 초과 시 ChatServiceScheduler에서 자동 제거 대상
     */
    public void registerWithTimestamp(int roomId, int maxPeople) {
        registerSemaphoreOnly(roomId, maxPeople);
        createdAtMap.putIfAbsent(roomId, System.currentTimeMillis());
    }

    // ============================================================================================
    // 🔹 Permit 점유 및 추적
    // ============================================================================================

    /**
     * @method tryAcquire
     * @param roomId 방 번호
     * @param userId 사용자 ID
     * @return 점유 성공 여부
     * @brief 사용자별 permit 점유 시도 및 추적 등록
     * @called_by RoomJoinService
     */
    public boolean tryAcquire(int roomId, String userId) {

        logger.info("tryAcquire() - roomId={}, userId={}", roomId, userId);

        Semaphore sem = semaphoreMap.get(roomId);
        if (sem != null && sem.tryAcquire()) {
            userPermitMap.put(new UserRoomKey(userId, roomId), System.currentTimeMillis());
            return true;
        }
        return false;
    }

    /**
     * @method releasePermitOnly
     * @param roomId 방 번호
     * @brief permit 자원만 반환, 사용자 추적 정보는 유지
     * @called_by WebSocketHandler.afterConnectionClosed(), handleTransportError()
     */
    public void releasePermitOnly(int roomId) {
        Semaphore sem = semaphoreMap.get(roomId);
        if (sem != null) {
            sem.release();
        }
    }

    /**
     * @method removePermitTracking
     * @param key UserRoomKey
     * @brief 사용자 연결 성공 시 추적 정보 제거 (TTL 회수 제외 처리)
     * @called_by WebSocketHandler.afterConnectionEstablished()
     */
    public void removePermitTracking(UserRoomKey key) {
        userPermitMap.remove(key);
    }

    // ============================================================================================
    // 🔹 조회 및 상태 검사
    // ============================================================================================

    /**
     * @method exists
     * @brief roomId 기반 세마포어 등록 여부 확인
     * @called_by ChatServiceScheduler
     */
    public boolean exists(int roomId) {
        return semaphoreMap.containsKey(roomId);
    }

    /**
     * @method getAvailablePermits
     * @brief 현재 roomId의 permit 잔여 수 반환 (모니터링용)
     */
    public int getAvailablePermits(int roomId) {
        Semaphore sem = semaphoreMap.get(roomId);
        return (sem != null) ? sem.availablePermits() : -1;
    }

    /**
     * @method getCreatedAt
     * @brief TTL 기준 시간 조회용 (스케줄러 사용)
     */
    public Long getCreatedAt(int roomId) {
        return createdAtMap.get(roomId);
    }

    /**
     * @method resetTimestamp
     * @brief WebSocket 연결 진행 중일 경우 시각 재설정 → TTL 오회수 방지
     */
    public void resetTimestamp(int roomId) {
        if (createdAtMap.containsKey(roomId)) {
            createdAtMap.put(roomId, System.currentTimeMillis());
        }
    }

    /**
     * @method getRegisteredRoomIds
     * @return 현재 등록된 roomId 전체 목록
     * @called_by ChatServiceScheduler
     */
    public Set<Integer> getRegisteredRoomIds() {
        return semaphoreMap.keySet();
    }
}

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
 * SemaphoreRegistry
 * ──────────────────────────────────────────────────────────────
 * [핵심 역할]
 * - 채팅방별 동시 입장 인원 제어(permit) 및 자원 추적/회수 담당
 * - race condition 방지, permit 누락/누수 예방, 비정상 세션/입장 실패 자동 복구 구조의 중심 컴포넌트
 *
 * [핵심 불변식]
 * - 각 방의 permit 점유 상태 == 실질 연결 인원수와 반드시 일치
 * - 자원 미반납(예: 비정상 종료, race, 예외) 케이스 자동 탐지/회수 가능
 */
@Component
public class SemaphoreRegistry {

    private static final Logger logger = LoggerFactory.getLogger(SemaphoreRegistry.class);

    /**
     * [세마포어 맵]
     * roomId → Semaphore (동시 입장 가능 인원 제한)
     * - 동시성 안전: ConcurrentHashMap 사용
     * - 각 방별로 독립적 동시성 제어 단위
     * - 등록: RoomJoinService, 회수: Scheduler 또는 정상/비정상 종료 시점
     */
    private final Map<Integer, Semaphore> semaphoreMap = new ConcurrentHashMap<>();

    /**
     * [방 생성 시각 맵]
     * roomId → 생성 timestamp(ms)
     * - 임시 방(TTL) 상태 추적용, 입장 대기 중 race/failure 자동 회수 정책 구현에 활용
     * - 스케줄러가 주기적으로 TTL 초과 방/세마포어/permit 자동 회수
     */
    private final Map<Integer, Long> createdAtMap = new ConcurrentHashMap<>();

    /**
     * [사용자별 permit 점유 맵]
     * UserRoomKey(userId, roomId) → 점유 시각(ms)
     * - 정상 연결 실패/비정상 종료시 스케줄러 기반 회수/복구 대상 추적
     * - 실질 연결 확정 시 WebSocketHandler가 직접 제거(중복/누락/permit mis-match 방지)
     */
    @Getter
    private final Map<UserRoomKey, Long> userPermitMap = new ConcurrentHashMap<>();

    /**
     * UserRoomKey
     * ──────────────────────────────────────────────────────────────
     * [userId, roomId] 조합 기반 불변식 추적 키
     * - hashCode, equals 반드시 오버라이드
     * - 동시 permit 점유 및 해제, 중복 체크, TTL 회수의 atomic 단위로 사용
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

    // =======================================================================
    // 🔹 등록 및 초기화
    // =======================================================================

    /**
     * registerSemaphoreOnly
     * - DB 기반 기존 방 입장 시점: permit 자원만 선등록(TTL 불필요)
     * - 이미 등록된 방이면 skip(중복방지)
     */
    public void registerSemaphoreOnly(int roomId, int maxPeople) {
        semaphoreMap.putIfAbsent(roomId, new Semaphore(maxPeople, true));
    }

    /**
     * registerWithTimestamp
     * - 신규 방 입장 시점: permit(세마포어) 등록 + 생성 시각 기록(TTL 관리)
     * - 스케줄러가 race/연결 실패/permit 미회수 케이스 자동 탐지 가능하게 만듦
     */
    public void registerWithTimestamp(int roomId, int maxPeople) {
        registerSemaphoreOnly(roomId, maxPeople);
        createdAtMap.putIfAbsent(roomId, System.currentTimeMillis());
    }

    // =======================================================================
    // 🔹 Permit 점유/회수/추적
    // =======================================================================

    /**
     * tryAcquire
     * - 방 입장 permit 점유(동시성 race 안전)
     * - 성공 시 userPermitMap에 점유 시각 등록(스케줄러/정상 연결 여부 추적)
     * - 실패(정원 초과 등) 시 false
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
     * releasePermitOnly
     * - permit만 반환(세션 등 실제 연결 종료와 분리)
     * - 비정상 종료, 연결 오류 등 상황에서도 사용
     */
    public void releasePermitOnly(int roomId) {
        Semaphore sem = semaphoreMap.get(roomId);
        if (sem != null) {
            sem.release();
        }
    }

    /**
     * removePermitTracking
     * - 정상 연결(WebSocket established) 이후 userPermitMap에서 점유 정보 제거
     * - TTL 스케줄러가 잘못 회수하는 것 방지
     */
    public void removePermitTracking(UserRoomKey key) {
        userPermitMap.remove(key);
    }

    // =======================================================================
    // 🔹 상태/자원 모니터링 및 조회
    // =======================================================================

    /**
     * exists
     * - 해당 roomId 세마포어 등록 여부 확인
     */
    public boolean exists(int roomId) {
        return semaphoreMap.containsKey(roomId);
    }

    /**
     * getAvailablePermits
     * - 현 시점 roomId permit 남은 수(정상/비정상 상태 실시간 모니터링)
     */
    public int getAvailablePermits(int roomId) {
        Semaphore sem = semaphoreMap.get(roomId);
        return (sem != null) ? sem.availablePermits() : -1;
    }

    /**
     * getCreatedAt
     * - 방 생성 시각 조회(TTL 초과 검증/자동 회수 용도)
     */
    public Long getCreatedAt(int roomId) {
        return createdAtMap.get(roomId);
    }

    /**
     * resetTimestamp
     * - 연결 진행 중 race, 중복, 네트워크 지연 등으로 TTL 회수 방지 필요시 사용
     */
    public void resetTimestamp(int roomId) {
        if (createdAtMap.containsKey(roomId)) {
            createdAtMap.put(roomId, System.currentTimeMillis());
        }
    }

    /**
     * getRegisteredRoomIds
     * - 현재 세마포어 등록 방 전체 조회
     * - 스케줄러 등 외부 모니터링, 누수 회수 대상 선정용
     */
    public Set<Integer> getRegisteredRoomIds() {
        return semaphoreMap.keySet();
    }
}

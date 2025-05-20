package com.chatservice.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chatservice.createroom.memory.InMemoryRoomQueueTracker;
import com.chatservice.concurrency.SemaphoreRegistry;
import com.chatservice.concurrency.SemaphoreRegistry.UserRoomKey;
import com.chatservice.scheduler.RoomQueueEntityJpa;

@Component
public class ChatServiceScheduler {

	private final InMemoryRoomQueueTracker inMemoryRoomQueueTracker;
	private final RoomQueueEntityJpa roomQueueEntityJpa;
	private final SemaphoreRegistry semaphoreRegistry;

	// TTL 기준 (정책성 상수) - 추후 Config로 이동 고려
	private static final int EXPIRATION_MINUTES = 2;
	private static final long TTL_MILLIS = EXPIRATION_MINUTES * 60 * 1000L;
	private static final long TTL_LIMIT_MS = 120_000L;

	private static final Logger logger = LoggerFactory.getLogger(ChatServiceScheduler.class);

	public ChatServiceScheduler(
			InMemoryRoomQueueTracker tracker,
			RoomQueueEntityJpa roomQueueEntityJpa,
			SemaphoreRegistry semaphoreRegistry) {
		this.inMemoryRoomQueueTracker = tracker;
		this.roomQueueEntityJpa = roomQueueEntityJpa;
		this.semaphoreRegistry = semaphoreRegistry;
	}

	/**
	 * [방 생성 대기열 TTL 정리]
	 *
	 * - InMemoryRoomQueueTracker 기반으로, 일정 시간 이상 미생성된 방은 삭제 처리
	 * - DB와 메모리 정합성 유지 목적
	 */
	@Scheduled(fixedRate = 20 * 1000) // 20초마다 실행
	public void cleanUpPendingRoomQueue() {
		var expiredRooms = inMemoryRoomQueueTracker.removeExpiredRooms(EXPIRATION_MINUTES);

		for (var room : expiredRooms) {
			try {
				roomQueueEntityJpa.deleteByRoomNumber(room.getRoomNumber());
				logger.info("[방 생성 대기열 - 삭제됨] roomNumber=" + room.getRoomNumber());
			} catch (Exception e) {
				logger.error("[방 생성 대기열 - 삭제 실패] roomNumber=" + room.getRoomNumber() + ", 이유: " + e.getMessage());
			}
		}
	}

	/**
	 * [사용자 permit TTL 회수]
	 *
	 * - userPermitMap 기준으로 TTL을 초과한 사용자-방 조합을 순회
	 * - 세션 연결 실패로 판단하고 자원 반환 처리
	 * - release 후 추적 정보 제거까지 함께 진행됨
	 */
	@Scheduled(fixedRate = 30000) // 30초마다 실행
	public void clearExpiredUserPermits() {
		long now = System.currentTimeMillis();

		for (var entry : semaphoreRegistry.getUserPermitMap().entrySet()) {
			UserRoomKey key = entry.getKey();
			long createdAt = entry.getValue();

			if (now - createdAt > TTL_LIMIT_MS) {
				semaphoreRegistry.releasePermitOnly(key.getRoomId());	// 자원 반환
				semaphoreRegistry.removePermitTracking(key);			// 추적 정보 제거
				System.out.println("[TTL 만료] 사용자 permit 회수됨: " + key);
			}
		}
	}

	/**
	 * [createdAtMap 기반 dead room 감지]
	 *
	 * - 생성된 세마포어 중, 즉 방 생성 혹은 입장 요청에 따라 생성된(JoinService.confirmJoinRoom()) 세션이 일정 시간 이상 WebSocket 연결이 이루어지지 않은 방 감지
	 * - 자원 자체는 회수하지 않으며, 이 상태로 남은 방은 수동 정리가 필요할 수 있음
	 * - 현재는 로그 출력만 수행, 추후 정책 수립 가능
	 */
	@Scheduled(fixedRate = 60000) // 60초마다 실행
	public void logExpiredCreatedRooms() {
		long now = System.currentTimeMillis();

		for (Integer roomId : semaphoreRegistry.getRegisteredRoomIds()) {
			Long createdAt = semaphoreRegistry.getCreatedAt(roomId);

			if (createdAt != null && (now - createdAt > TTL_MILLIS)) {
				System.out.println("[경고] 생성 후 TTL 초과 방 감지됨: roomId=" + roomId);
				// 회수 정책 수립 전: 로그만 출력
			}
		}
	}
}

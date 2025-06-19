package com.chatservice.scheduler;

import com.chatservice.websocketcore.model.ChatSessionRegistry;
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
	ChatSessionRegistry chatSessionRegistry;

	private static final Logger logger = LoggerFactory.getLogger(ChatServiceScheduler.class);

	public ChatServiceScheduler(
			InMemoryRoomQueueTracker tracker,
			RoomQueueEntityJpa roomQueueEntityJpa,
			SemaphoreRegistry semaphoreRegistry,
			ChatSessionRegistry chatSessionRegistry) {
		this.inMemoryRoomQueueTracker = tracker;
		this.roomQueueEntityJpa = roomQueueEntityJpa;
		this.semaphoreRegistry = semaphoreRegistry;
		this.chatSessionRegistry = chatSessionRegistry;
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

	 /* [사용자 세션 TTL 만료 정리]
			* - 0.5초(500ms)마다 ChatSessionRegistry의 cleanupExpiredUsers() 호출
     * - 비명시적 종료, TTL 만료, race condition 등 세션/인원수/VO 상태 정합성 유지
     */
	@Scheduled(fixedRate = 500) // 0.5초마다 실행
	public void cleanupExpiredUsersJob() {
		if(!chatSessionRegistry.getRoomUserVOMap().isEmpty()) {
			try {
				chatSessionRegistry.cleanupExpiredUsers();
			} catch (Exception e) {
				logger.error("[cleanupExpiredUsersJob] 실행 중 예외 발생", e);
			}
		}
	}
}
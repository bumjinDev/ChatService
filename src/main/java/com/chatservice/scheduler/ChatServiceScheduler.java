package com.chatservice.scheduler;

import com.chatservice.websocketcore.model.ChatSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chatservice.createroom.memory.InMemoryRoomQueueTracker;
import com.chatservice.concurrency.SemaphoreRegistry;
import com.chatservice.concurrency.SemaphoreRegistry.UserRoomKey;

/**
 * ChatServiceScheduler
 * ───────────────────────────────────────────────
 * [전체 구조적 책임]
 * - 채팅 서비스 내 모든 비동기(비실시간) 자원, 상태, 세션, permit, 방, 사용자 추적 데이터에 대해
 *   "시간 기반 자동 정합성 복구"를 보장하는 핵심 스케줄러
 * - race/누락/예외/사용자 탈락/네트워크 불일치 등 실시간 서비스 특유의 상태 손실에 대한 **최종적 복구 안전망** 역할
 */
@Component
public class ChatServiceScheduler {

	private final InMemoryRoomQueueTracker inMemoryRoomQueueTracker;
	private final RoomQueueEntityJpa roomQueueEntityJpa;
	private final SemaphoreRegistry semaphoreRegistry;
	private final ChatSessionRegistry chatSessionRegistry;

	// TTL(만료시간) 정책 - 코드 내 상수지만, 실무에선 config/환경변수로 이동하는 것이 정석
	private static final int EXPIRATION_MINUTES = 2;
	private static final long TTL_MILLIS = EXPIRATION_MINUTES * 60 * 1000L;
	private static final long TTL_LIMIT_MS = 120_000L; // 2분

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
	 * [방 생성 대기열(TTL) 정리 루틴]
	 * ─────────────────────────────
	 * - 비정상·미완성(사용자 미입장/생성 미확정) 방이 메모리/DB 대기열에 계속 남는 것을 자동으로 회수
	 * - **DB-메모리 상태 동기화** 및 불필요 리소스 해제 보장
	 * - race, 네트워크 지연, 사용자의 비정상 인터럽트 등 모든 케이스를 실시간 아닌 주기성 복구로 커버
	 */
	@Scheduled(fixedRate = 20_000) // 20초마다 실행
	public void cleanUpPendingRoomQueue() {
		// 1. 만료 방 리스트 추출 및 메모리상 삭제
		var expiredRooms = inMemoryRoomQueueTracker.removeExpiredRooms(EXPIRATION_MINUTES);

		for (var room : expiredRooms) {
			try {
				roomQueueEntityJpa.deleteByRoomNumber(room.getRoomNumber()); // DB에서도 제거
				logger.info("[방 생성 대기열 - 삭제됨] roomNumber=" + room.getRoomNumber());
			} catch (Exception e) {
				logger.error("[방 생성 대기열 - 삭제 실패] roomNumber=" + room.getRoomNumber() + ", 이유: " + e.getMessage());
			}
		}
	}

	/**
	 * [사용자 permit(TTL) 회수 루틴]
	 * ─────────────────────────────
	 * - WebSocket 연결 불발, 네트워크 단절, race/실패 등으로 permit만 점유되고 세션 연결이 확정되지 않은 사용자-방 조합 자동 회수
	 * - 상태 불일치(permit 점유/실제 인원수/세션) 자동 동기화의 핵심 루틴
	 * - 세마포어 실제 자원과 추적 정보(userPermitMap) 동시 정리
	 */
	@Scheduled(fixedRate = 30_000) // 30초마다 실행
	public void clearExpiredUserPermits() {
		long now = System.currentTimeMillis();

		for (var entry : semaphoreRegistry.getUserPermitMap().entrySet()) {
			UserRoomKey key = entry.getKey();
			long createdAt = entry.getValue();

			// TTL 초과 시점: 실질 연결 없이 permit만 점유한 것으로 판정
			if (now - createdAt > TTL_LIMIT_MS) {
				semaphoreRegistry.releasePermitOnly(key.getRoomId());   // 세마포어 permit 반환
				semaphoreRegistry.removePermitTracking(key);            // 추적 정보(userPermitMap)도 제거
				System.out.println("[TTL 만료] 사용자 permit 회수됨: " + key);
			}
		}
	}

	/**
	 * [createdAtMap 기반 dead room 감지 루틴]
	 * ─────────────────────────────
	 * - 방 생성/입장 요청은 있었으나, 실질적으로 WebSocket 세션으로 연결되지 않은 dead room(유령 방) 감지
	 * - 실무적으론 미회수/방치 자원 자동화 회수 정책 설계에 핵심적 근거 제공
	 * - 현재는 단순 경고 로그만 남기나, 실질 자원 회수·삭제까지 연결하는 것이 실무에선 필요
	 */
	@Scheduled(fixedRate = 60_000) // 60초마다 실행
	public void logExpiredCreatedRooms() {
		long now = System.currentTimeMillis();

		for (Integer roomId : semaphoreRegistry.getRegisteredRoomIds()) {
			Long createdAt = semaphoreRegistry.getCreatedAt(roomId);

			// Dead room 탐지 조건: 생성 이후 연결/입장 미확정 상태가 TTL 이상 지속
			if (createdAt != null && (now - createdAt > TTL_MILLIS)) {
				System.out.println("[경고] 생성 후 TTL 초과 방 감지됨: roomId=" + roomId);
				// 실무 확장: 이후 자원 회수/삭제 정책 구현 필요
			}
		}
	}

	/**
	 * [roomUserVOMap 기반 사용자 TTL 만료/방 자동 정리 루틴]
	 * ─────────────────────────────
	 * - 실질적으로 채팅방 인원수, 세마포어 permit, 메모리 roomMap, 사용자 추적정보(roomUserVOMap)의 일관성 보장
	 * - TTL 만료 사용자를 방에서 강제 퇴장 처리. 모든 사용자가 만료된 방은 roomMap/roomUserVOMap에서 완전 제거(메모리/상태 누수 방지)
	 * - 단일 루틴에서 인원수-permit-room 객체 동기화까지 일괄 처리하도록 구현
	 */
	@Scheduled(fixedRate = 20_00) // 20초마다 실행
	public void cleanupExpiredRoomUsersAndRooms() {
		chatSessionRegistry.cleanupExpiredUsers();
	}
}

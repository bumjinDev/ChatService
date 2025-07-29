package com.chatservice.joinroom.service;

import com.chatservice.concurrency.SemaphoreRegistry;
import com.chatservice.createroom.model.RoomQueueVO;
import com.chatservice.createroom.memory.InMemoryRoomQueueTracker;
import com.chatservice.joinroom.converter.RoomConverter;
import com.chatservice.joinroom.dao.*;
import com.chatservice.joinroom.exception.RoomBadJoinFullException;
import com.chatservice.websocketcore.model.ChatSessionRegistry;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RoomJoinService
 * ──────────────────────────────────────────────────────────────
 * 채팅방 입장 요청에 대한 사전 검증(permit 점유) 및 입장 확정(DB/메모리 인원수 반영) 도메인 서비스
 * - 입장 전(REST/Handshake): permit(동시 인원) 사전 확보
 * - 연결 확정(WebSocket established): DB/메모리/세션 동기화 및 인원수 증가
 */
@Service
public class RoomJoinService implements IRoomJoinService {

    private final InMemoryRoomQueueTracker inMemoryRoomQueueTracker;
    private final SemaphoreRegistry semaphoreRegistry;
    private final RoomConverter roomConverter;
    private final ChatSessionRegistry chatSessionRegistry;

    private static final Logger logger = LoggerFactory.getLogger(RoomJoinService.class);

    public RoomJoinService(
            RoomConverter roomConverter,
            InMemoryRoomQueueTracker inMemoryRoomQueueTracker,
            SemaphoreRegistry semaphoreRegistry,
            ChatSessionRegistry chatSessionRegistry) {

        this.roomConverter = roomConverter;
        this.inMemoryRoomQueueTracker = inMemoryRoomQueueTracker;
        this.semaphoreRegistry = semaphoreRegistry;
        this.chatSessionRegistry = chatSessionRegistry;
    }

    /**
     * confirmJoinRoom
     * ──────────────────────────────────────────────────────────────
     * [입장 사전 검증 및 permit 점유]
     * - 실제 WebSocket 연결/세션 확정 전, 인원 초과 방지와 race condition 차단을 위해 동시 입장 permit(세마포어) 확보 시도
     * - 새로고침, 2중 탭, 임시 세션, race, 비정상 방번호 등 실시간 이벤트 분기 처리
     * - 성공 시 permit 점유, 실패(정원 초과/비정상 방) 시 예외
     */
    @Override
    @Transactional
    public void confirmJoinRoom(int roomNumber, String userId) {

        logger.info("[입장 요청 수신] 시각={} roomNumber={}, userId={}", LocalDateTime.now(), roomNumber, userId);
        /*
            1) 중복 이라 판단 시 별도의 입장 과정 미 수행
            ** 단순 새로 고침이라 판단은 단순 방 인원수 갱신 목적 이니 그냥 통과.
         */
        if(chatSessionRegistry.containsUser(String.valueOf(roomNumber), userId)){
            return;
        }
        // 신규 방(아직 DB에 실체 없음) 대기열 확인
        Optional<RoomQueueVO> roomQueueVO =
                Optional.ofNullable(inMemoryRoomQueueTracker.getRoom(roomNumber));
        // 기존 방(DB/메모리 실체) 확인
        Optional<ChatRoom> chatRoom =
                Optional.ofNullable(chatSessionRegistry.getRoom(roomNumber));

        /* 현재 위에서는 permit 점유만 시도하고  */
        if (roomQueueVO.isPresent()) {

            logger.warn("[신규 방 입장 생성 요청] : roomNumber={}, userId={}", roomNumber, userId);

            int maxPeople = roomQueueVO.get().getMaxPeople();
            
           /*
               1. 새로운 방이니 그에 따라 새로운 방 ID에 해당하는 객체 세마포어 생성.
               2. 새로운 방을 생성 했는데 안 들어올 것을 대비해서 자료구조 내 방 정보 갱신("createdAtMap") -> 실제 방 입장 해야지만, 해당 객체를 이후 createroom 도메인 내에서 삭제함으로써 생성 요청된 방이 삭제되지 않음.
           * */
            semaphoreRegistry.registerWithTimestamp(roomNumber, maxPeople);

            boolean acquired = semaphoreRegistry.tryAcquire(roomNumber, userId);

            if (!acquired) {
                logger.warn("[방 생성 실패] 신규 방 permit 점유 실패: roomNumber={}, userId={}", roomNumber, userId);
                throw new RoomBadJoinFullException("입장 실패 : 생성을 실패 하였습니다. ");
            }

            return;
        }

        else if (chatRoom.isPresent()) {

            logger.warn("[기존 방 입장 요청] : roomNumber={}, userId={}", roomNumber, userId);

            boolean acquired = semaphoreRegistry.tryAcquire(roomNumber, userId); // permit 점유

            if (!acquired) {
                logger.warn("[입장 거부] 기존 방 permit 점유 실패: roomNumber={}, userId={}", roomNumber, userId);
                throw new RoomBadJoinFullException("입장 실패: 방 정원이 가득 찼습니다.");
            }
            logger.warn("[방 여유 인원수] : roomNumber={}, 방 여유 인원수={}", roomNumber, semaphoreRegistry.getAvailablePermits(roomNumber));
            return;
        }

        else {
            logger.error("존재하지 않는 방에 대한 입장 시도 감지: roomNumber={}", roomNumber);
            throw new RoomBadJoinFullException("유효하지 않은 방 번호입니다. roomNumber=" + roomNumber);
        }
    }

    /**
     * joinRoom
     * ──────────────────────────────────────────────────────────────
     * [실제 입장 확정, DB/메모리 인원 반영]
     * - WebSocket 연결 완료(세션 수립) 후 호출
     * - 방 실체(DB/메모리) 생성 및 인원수 동기화
     * - permit 기반 인원수 보정(중복/누락 방지)
     */
    @Override
    @Transactional
    public void joinRoom(int roomNumber) {

        // 기존 방(DB/메모리 실체) 존재 여부 확인
        Optional<ChatRoom> chatRoom = Optional.ofNullable(chatSessionRegistry.getRoom(roomNumber));

        // [1] 기존 방 없음 → 방 생성(대기열 메타 정보 기반)
        if (chatRoom.isEmpty()) {

            logger.info("신규 방 생성 과정 진행 - roomNumber={}", roomNumber);
            // 방 생성 대기열에서 메타 정보 조회 (없으면 치명적 오류)
            RoomQueueVO roomVO = Optional.ofNullable(inMemoryRoomQueueTracker.getRoom(roomNumber))
                    .orElseThrow(() -> new IllegalStateException("방 생성 대기열에서 해당 방을 찾을 수 없습니다. roomNumber=" + roomNumber));
            // inMemoryRoomQueueTracker(방 생성 대기열) 내 방 정보를 실제 방으로 생성
            chatSessionRegistry.createRoom(roomNumber, roomConverter.toChatRoom(roomVO));
            // 대기열 방 상태를 "생성 완료"로 전환(플래그)
            roomVO.setJoined(true);
        }
        // [2] 신규/기존 방 상관 없이 세마포어 기반 permit을 가지고 방 목록 내 보일 인원수 동기화 : 방 생성 혹은 입장 완료 시점에 이미 동기화.
        chatSessionRegistry.updateRoomCurrentPeople(roomNumber, chatSessionRegistry.getRoom(roomNumber).getMaxPeople() -  semaphoreRegistry.getAvailablePermits(roomNumber));
        logger.info("[updateRoomCurrentPeople] 동기화: roomNumber={}, availablePermits={}", roomNumber, semaphoreRegistry.getAvailablePermits(roomNumber));
    }
}
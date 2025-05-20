package com.chatservice.joinroom.service;

import com.chatservice.concurrency.SemaphoreRegistry;
import com.chatservice.createroom.model.RoomQueueVO;
import com.chatservice.createroom.memory.InMemoryRoomQueueTracker;
import com.chatservice.joinroom.dao.IRoomJoinRepository;
import com.chatservice.joinroom.dao.RoomPeopleProjection;
import com.chatservice.joinroom.exception.RoomBadJoinFullException;
import com.chatservice.joinroom.model.JoinRoomConverter;

import com.chatservice.websocketcore.model.ChatSessionRegistry;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @class RoomJoinService
 * @brief 채팅방 입장 요청에 대한 사전 검증 및 세마포어 자원 점유를 담당하는 도메인 서비스
 *
 * @responsibility
 * - 입장 가능성 검증 및 SemaphoreRegistry 등록 및 permit 점유
 * - 실질적인 DB방 생성/참여 인원 수 증가 처리는 joinRoom()에서 수행
 *
 * @called_by
 * - WebSocket Handshake 직전 컨트롤러 or 미리 입장 검사 수행하는 계층
 */
@Service
public class RoomJoinService implements IRoomJoinService {

    private final IRoomJoinRepository roomJoinRepository;
    private final JoinRoomConverter roomConverter;
    private final InMemoryRoomQueueTracker inMemoryRoomQueueTracker;
    private final SemaphoreRegistry semaphoreRegistry;

    private final ChatSessionRegistry chatSessionRegistry;

    private static final Logger logger = LoggerFactory.getLogger(RoomJoinService.class);

    public RoomJoinService(
            IRoomJoinRepository roomJoinRepository,
            JoinRoomConverter roomConverter,
            InMemoryRoomQueueTracker inMemoryRoomQueueTracker,
            SemaphoreRegistry semaphoreRegistry,
            ChatSessionRegistry chatSessionRegistry) {

        this.roomJoinRepository = roomJoinRepository;
        this.roomConverter = roomConverter;
        this.inMemoryRoomQueueTracker = inMemoryRoomQueueTracker;
        this.semaphoreRegistry = semaphoreRegistry;
        this.chatSessionRegistry = chatSessionRegistry;
    }

    /**
     * @method confirmJoinRoom
     * @brief 사용자가 입장 요청 시, 세마포어 permit을 점유하여 사전 입장 가능성을 확인함
     *
     * @param roomNumber 입장 요청 대상 방 번호
     * @param userId 사용자 식별자
     *
     * @steps
     * 1. 메모리 대기열 조회 → 신규 방 여부 판단
     * 2. 신규 방: 세마포어 등록 + createdAtMap 등록
     * 3. 기존 방: 등록된 세마포어에서 permit 점유 시도
     * 4. 점유 실패 시 예외
     *
     * @throws RoomBadJoinFullException 방이 유효하지 않거나, permit 점유 실패한 경우
     *
     * @called_by WebSocket 연결 전 필수 선행 로직
     */
    @Override
    @Transactional
    public synchronized void confirmJoinRoom(int roomNumber, String userId) {

        logger.info("입장 요청 수신: roomNumber={}, userId={}", roomNumber, userId);

        Optional<RoomQueueVO> roomQueueVO =
                Optional.ofNullable(inMemoryRoomQueueTracker.getRoom(roomNumber));
        Optional<RoomPeopleProjection> roomPeopleProjection =
                roomJoinRepository.loadPeopleInfo(roomNumber);

        // 이미 세션이 존재하는 경우 중복 입장 허용 (세션은 WebSocketHandler에서 먼저 생성)
        if (chatSessionRegistry.containsUser(String.valueOf(roomNumber), userId)) {
            logger.info("중복 입장 시도 허용 - 이미 접속 중: roomNumber={}, userId={}", roomNumber, userId);
            return;
        }

        // [1] 신규 방
        if (roomQueueVO.isPresent()) {
            int maxPeople = roomQueueVO.get().getMaxPeople();

            // TTL 추적 등록
            semaphoreRegistry.registerWithTimestamp(roomNumber, maxPeople);

            // 점유 시도
            boolean acquired = semaphoreRegistry.tryAcquire(roomNumber, userId);
            if (!acquired) {
                logger.warn("[입장 거부] 신규 방 permit 점유 실패: roomNumber={}, userId={}", roomNumber, userId);
                throw new RoomBadJoinFullException("입장 실패: 방 정원이 가득 찼습니다.");
            }
            return;
        }

        // [2] 기존 방
        else if (roomPeopleProjection.isPresent()) {
            boolean acquired = semaphoreRegistry.tryAcquire(roomNumber, userId);
            if (!acquired) {
                logger.warn("[입장 거부] 기존 방 permit 점유 실패: roomNumber={}, userId={}", roomNumber, userId);
                throw new RoomBadJoinFullException("입장 실패: 방 정원이 가득 찼습니다.");
            }
            return;
        }

        // [3] 비정상 방 번호
        else {
            logger.error("존재하지 않는 방에 대한 입장 시도 감지: roomNumber={}", roomNumber);
            throw new RoomBadJoinFullException("유효하지 않은 방 번호입니다. roomNumber=" + roomNumber);
        }
    }

    /**
     * @method joinRoom
     * @brief 실제 WebSocket 연결 수립 후 확정 입장 → DB 내 방 생성 또는 인원 수 증가 처리
     *
     * @param roomNumber 입장 대상 방 번호
     *
     * @steps
     * 1. DB에 존재하지 않으면 새 방 생성 (대기열 기준)
     * 2. 인원 수 증가 처리
     *
     * @called_by WebSocketHandler.afterConnectionEstablished()
     */
    @Override
    @Transactional
    public synchronized void joinRoom(int roomNumber) {

        // [1] DB 테이블 존재 여부 확인 (물리적 방 유무)
        boolean existsInDB = roomJoinRepository.findRoomTable(roomNumber).isPresent();

        if (!existsInDB) {
            // [1-1] 생성 대기열에서 메타정보 조회 (실패 시 치명적)
            logger.info("신규 방 생성 처리 진행: roomNumber={}", roomNumber);
            RoomQueueVO roomVO = Optional.ofNullable(inMemoryRoomQueueTracker.getRoom(roomNumber))
                    .orElseThrow(() -> new IllegalStateException("방 생성 대기열에서 해당 방을 찾을 수 없습니다. roomNumber=" + roomNumber));

            // [1-2] DB에 방 테이블 생성
            roomJoinRepository.createRoomTable(roomConverter.toEntity(roomConverter.toDTO(roomVO)));

            // [1-3] 대기열에서 해당 방은 "생성 완료" 상태로 전환
            roomVO.setJoined(true);
        }

        // [2] DB 내 인원 수 증가
        roomJoinRepository.incrementParticipantCount(roomNumber);
        logger.info("DB 인원수 증가 완료: roomNumber={}", roomNumber);
    }
}

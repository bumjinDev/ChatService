package com.chatservice.joinroom.service;

import com.chatservice.concurrency.SemaphoreRegistry;
import com.chatservice.createroom.model.RoomQueueVO;
import com.chatservice.createroom.memory.InMemoryRoomQueueTracker;
import com.chatservice.joinroom.dao.*;
import com.chatservice.joinroom.exception.RoomBadJoinFullException;

import com.chatservice.websocketcore.model.ChatSessionRegistry;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.text.html.Option;


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

    private final ChatRoomMemoryRegistry chatRoomMemoryRegistry;
    private final InMemoryRoomQueueTracker inMemoryRoomQueueTracker;
    private final SemaphoreRegistry semaphoreRegistry;
    private final RoomConverter roomConverter;
    private final ChatSessionRegistry chatSessionRegistry;

    private static final Logger logger = LoggerFactory.getLogger(RoomJoinService.class);

    public RoomJoinService(
            ChatRoomMemoryRegistry chatRoomMemoryRegistry,
            RoomConverter roomConverter,
            InMemoryRoomQueueTracker inMemoryRoomQueueTracker,
            SemaphoreRegistry semaphoreRegistry,
            ChatSessionRegistry chatSessionRegistry) {

        this.chatRoomMemoryRegistry = chatRoomMemoryRegistry;
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

        logger.info("[입장 요청 수신] 시각={} roomNumber={}, userId={}", LocalDateTime.now(), roomNumber, userId);

        Optional<RoomQueueVO> roomQueueVO =
                Optional.ofNullable(inMemoryRoomQueueTracker.getRoom(roomNumber));
        Optional<ChatRoom> chatRoom =
                Optional.ofNullable(chatRoomMemoryRegistry.getRoom(roomNumber));    // 실제 기존 방 조차 없다면 아에 잘못된 방 번호로 인위적으로 api 요청 한 것이므로 이 또한 고려.

        /*
         * [새로고침(TTL 기반 재입장) 및 2중탭(동일 ID 중복 세션) 분기 처리]
         *
         * 1. chatSessionRegistry: 2중탭(동일 ID 동시 접속) 탐지
         *    - chatRoomMemoryRegistry 분기를 통과하면서도, 동일 방/ID로 이미 세션이 존재하면,
         *      이는 2개 이상의 브라우저/탭에서 중복 접속을 시도하는 전형적 케이스이다.
         *    - 이때는 기존 세션을 강제 종료하고 새로운 세션만 등록해야 메시지 브로드캐스트, 인원 집계, 상태 동기화 등 실질적 race를 원천적으로 방지할 수 있다.
         *    - 실질 세션의 맺기/끊기는 핸들러에서 동기화되고, 본 분기에서는 추가적인 permit/방 생성 등 입장 프로세스를 절대 진행하지 않는다.
         *    - 구조적 목적: 동일 방/ID 조합의 세션 단일성, 메시지 및 인원수의 정합성, 중복 세션으로 인한 race, 불일치, 메시지 손실을 원천적으로 차단한다.
         *
         * 2. chatRoomMemoryRegistry: 새로고침(TTL 기반 임시 세션) 탐지
         *    - roomUserStateVO가 존재한다는 것은, 해당 사용자가 이전 연결 종료 시 명시적 퇴장(close, code 2000)이 아닌
         *      브라우저 X, 새로고침 등 불명확 이벤트로 인해 afterConnectionClosed만 호출되고 정상 입장 절차가 분기된 상태임을 의미한다.
         *    - 이 경우 실질적인 방 생성 등 상태 변경은 afterConnectionClosed/afterConnectionEstablished의 동기화 루틴에서 이미 처리되었으므로,
         *      추가적인 생성 처리를 이중 수행해서는 안 된다.
         *      하지만 세마포어 증/감 여부는 현재 "roomUserVOMap" 자료구조 관리와 별도로 WebSocket Handler.close() 발생 시 새로고침 상황에서도 일괄적으로
         *      감소되므로 현재 새로고침으로써 재 진입한 사용자에 대해서는 세마포어를 재 할당 해야 된다.
         *
         *    - 구조적 목적: 새로고침 시점의 roomUserVOMap 임시 정보만 제거하고, 후속 입장 처리를 분기 종료시켜 race 및 상태 불일치를 차단한다.
         *
         * 두 registry는 목적과 책임이 완전히 다르다:
         * - chatRoomMemoryRegistry는 임시 세션(TTL) 관리와 새로고침 복원을 담당,
         * - chatSessionRegistry는 실질 연결(동일ID 2중탭) 중복 방지와 브로드캐스트 도메인 유지의 책임을 진다.
         * 본 분기는 각 registry의 역할을 분리해 구조적으로 상태 일관성, 이중 처리, 실시간 동기화 오류를 방지한다.
         */

        Optional<ChatRoomMemoryRegistry.RoomUserStateVO> roomUserStateVO = Optional.ofNullable(chatRoomMemoryRegistry.findRoomUserStateVO(roomNumber, userId));

        logger.info("[명시적 세션 종료 확인] - 여부 : {}, 시각 : {}", roomUserStateVO.isPresent(), LocalDateTime.now());

        if (roomUserStateVO.isPresent()) {
            /* 임시 세션 정보 제가 */
            chatRoomMemoryRegistry.removeUserRoomMap(roomNumber, userId);
            /* 세마포어 재 할당 - 접근 불가 시 새로고침 사이 재 접속한 인원이라고 판단 후 접속 불가능 판정. */
            if (!semaphoreRegistry.tryAcquire(roomNumber, userId)) {
                logger.warn("[새로 고침 입장 거부] permit 점유 실패: roomNumber={}, userId={}", roomNumber, userId);
                throw new RoomBadJoinFullException("입장 실패: 방 정원이 가득 찼습니다.");
            }
            return;
        }
        boolean chatDuplicateConfirm = chatSessionRegistry.containsUser(String.valueOf(roomNumber), userId);
        logger.info("[이중 접속 확인] - 여부={}, 시각={} roomNumber={}, userId={}", chatDuplicateConfirm, LocalDateTime.now(), roomNumber, userId);
        if (chatDuplicateConfirm) {

            /* chatSessionRegistry.roomUserSessions 삭제 시점은 실제로 연결이 성립된 Establised 위치에서 수행 */
            /* 이후 기존 세션 강제 종료 및 새로운 세션 등록 로직으로 진입, */
            return;
        }

        // [1] 신규 방
        if (roomQueueVO.isPresent()) {

            logger.warn("[신규 방 입장 생성 요청] : roomNumber={}, userId={}", roomNumber, userId);
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
        else if (chatRoom.isPresent()) {
            
            logger.warn("[기존 방 입장 생성 요청] : roomNumber={}, userId={}", roomNumber, userId);
            
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

        /* [1] 새로운 방이 아닌 기존 방 데이터 존재 여부 확인
            - 기존 미 확인 : 새로운 방 생성 로직 진행 및 이에 따른 스케줄러 관련 작업 수행
            - 기존 방 확인 : 한다면, 이미 이전에 세마포어도 증가 또한 했으므로 별 다른 진행 하지 않음.
        */
        // 기존 방 정보가 없다면 Null 반환할 테니, 이를 대비.
        Optional<ChatRoom> chatRoom = Optional.ofNullable(chatRoomMemoryRegistry.getRoom(roomNumber));
        /* 기존 방 번호가 없다면, 새로운 방 생성 관련 로직 수행 */
        if (chatRoom.isEmpty()) {

            logger.info("신규 방 생성 과정 진행 - roomNumber={}", roomNumber);

            // [1-1] 생성 대기열에서 메타정보 조회 (실패 시 치명적)
            RoomQueueVO roomVO = Optional.ofNullable(inMemoryRoomQueueTracker.getRoom(roomNumber))
                    .orElseThrow(() -> new IllegalStateException("방 생성 대기열에서 해당 방을 찾을 수 없습니다. roomNumber=" + roomNumber));

            // [1-2] 메모리 내 실제 방 정보 생성(Create 패키지는 새로운 방 생성 대기열만 생성하므로 실제 방 정보는 여기서 생성함.)
            chatRoomMemoryRegistry.createRoom(roomNumber, roomConverter.toChatRoom(roomVO));

            // [1-3] 대기열에서 해당 방은 "생성 완료" 상태로 전환
            roomVO.setJoined(true);
        }
        /* 인원수 갱신 : 새로운 방 생성이든, 기존 방 입장이든 동일한 인원수 갱신 작업 수행(단순 +1 이 아닌, 세마포어 기반 초기화)
        *   ** 중요한 점은 현재 위치는 입장 시의 인원수 확인일 뿐이므로, 나간 사용자에 대한 방 현재 인원 수 정보는 "ChatTextWebSocketHandler.afterConnectionClosed()" 에서 수행.  */
        chatRoomMemoryRegistry.updateRoomCurrentPeople(roomNumber, semaphoreRegistry.getAvailablePermits(roomNumber));
    }
}
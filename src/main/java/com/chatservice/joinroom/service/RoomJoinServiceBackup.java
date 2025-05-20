package com.chatservice.joinroom.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.chatservice.createroom.memory.InMemoryRoomQueueTracker;
import com.chatservice.createroom.model.RoomQueueVO;
import com.chatservice.joinroom.dao.IRoomJoinRepository;
import com.chatservice.joinroom.dao.RoomPeopleProjection;
import com.chatservice.joinroom.exception.RoomBadJoinFullException;

import com.chatservice.joinroom.model.RoomDTO;
import com.chatservice.joinroom.model.JoinRoomConverter;

import jakarta.transaction.Transactional;

/* 현재 서비스 빈은 별도의 컨트롤러에서 호출 되는 것이 아니라 웹 소켓 세션 맺어진 직후,
 * 웹 소켓 핸들러에서 반환된다. */
@Service
public class RoomJoinServiceBackup {

	IRoomJoinRepository roomJoinRepository;
	JoinRoomConverter roomConverter;
    InMemoryRoomQueueTracker inMemoryRoomQueueTracker;

    public RoomJoinServiceBackup(
    		IRoomJoinRepository roomJoinRepository,
            JoinRoomConverter roomConverter,
            InMemoryRoomQueueTracker inMemoryRoomQueueTracker) {
        
        this.roomJoinRepository = roomJoinRepository;
        this.roomConverter = roomConverter;
        this.inMemoryRoomQueueTracker = inMemoryRoomQueueTracker;
    }
    /**
     * 방 입장 로직의 외부 진입점
     * @param roomNumber 클라이언트가 입장 시도한 방 번호로써 컨트롤로부터 호출되어 
     */
    /* **현재는 synchronized 를 하지만 반드시 큐잉으로 대체 필요 */
    @Transactional
    public synchronized void joinRoom(int roomNumber) {
    	
        /* 새로운 방 생성 요청 여부 확인 : 방 생성 대기열 목록 DBMS 내에 존재하는 방 테이블(ROOMS) 확인 (현재 실제 DBMS을 기준으로 검색하나, 추후 redis 캐시로 이관 예정 ) */
        if (!roomJoinRepository.findRoomTable(roomNumber).isPresent()) {   // 방 존재하지 않음 → 방 생성 대기엘에서 방 생성
       
            // 방 존재하지 않음 → 대기열에서 정보 꺼내 생성 후 입장, 'inMmemoryRoomQueue' 는 DI 로서 'create' 도메인에서 가져온 것임.
        	RoomQueueVO roomVO = Optional.ofNullable(inMemoryRoomQueueTracker.getRoom(roomNumber))
        		    .orElseThrow(() -> new IllegalStateException("새로운 방 생성 대기열 내 요청된 방 번호 정보를 찾을 수 없습니다."));		/* 이건 정말 현재 생성 대기 중인 방도 아니고, 생성 된 방 목록에도 포함되지 않으니 의도된 잘못된 접근이라 상정하고 추후 커스텀 예외 처리 */
            /* 방 생성 대기열이 있다면, 생성 후 추가(실제 대기방 테이블 : 'Rooms') */
            // !!createNewRoomTable(roomConverter.toDTO(roomVO));				// 실제 방 생성
            /* 스케줄러에서 주기적으로 DBMS 접근 하여 삭제 가능 하게 함.*/
            roomVO.setJoined(true); 
            /* 방 생성 완료 후 인원 수 1 증가 */
            incrementRoomParticipant(roomNumber);
            return;
        }
        /* 기존 방 내 입장 요청 : 
         * 	1. 인원수 검사 후 가득 찬 상태라면 ExceptionHandler 호출(레포지토리 빈에서 수행) 하여 아에 의도된 잘못된 접근이라 판단하고 단순 alert 을 띄우는 "새로운 에러 페이지" 반환
         * 	2. 아니라면 그냥 방 인원수 증가
        */
        RoomPeopleProjection roomPeopleProjection = roomJoinRepository.loadPeopleInfo(roomNumber)
                .orElseThrow();

        if(roomPeopleProjection.getCurrentPeople() >= roomPeopleProjection.getMaxPeople()) { throw new RoomBadJoinFullException(String.valueOf(roomNumber));}
        incrementRoomParticipant(roomNumber);
    }
    
    // 방 입장 시 인원 수 증가 (방 테이블 기준)
    private void incrementRoomParticipant(int roomNumber) {
    	roomJoinRepository.incrementParticipantCount(roomNumber);
    }
    // 실제 방 테이블 생성 로직
    private void createNewRoomTable(RoomDTO roomMeta) {
    	roomJoinRepository.createRoomTable(roomConverter.toEntity(roomMeta));
    }
}

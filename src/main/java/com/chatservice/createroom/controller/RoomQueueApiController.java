package com.chatservice.createroom.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.chatservice.createroom.model.RoomQueueDTO;
import com.chatservice.createroom.service.IRoomQueueService;

@Controller
public class RoomQueueApiController {

	/*
	 * 채팅방 생성 대기열 등록 요청 처리.
	 * WebSocket 세션 간 동기화 문제를 해결하기 위해,
	 * 요청받은 채팅방 생성 처리는 별도의 '채팅방 생성 대기열' 테이블에 등록하는 방식으로 구현됩니다.
	 *
	 * 설계 시 기능 위주가 아닌 자원 중심으로 명세를 정의하고 구분하는 원칙을 적용했습니다.
	 * newRoom.js에서 요청받는 이 지점에서는 방 생성 대기열에만 등록하며,
	 * 실제 방 입장은 다른 로직에서 수행되고, 이때 이미 생성된 방이든 현재 대기열에 있는 방이든
	 * 동일한 방 입장 엔드포인트로 접근하여 처리됩니다.
	 */
	IRoomQueueService roomQueueService;
	
	public RoomQueueApiController(IRoomQueueService roomQueueService) {
		this.roomQueueService = roomQueueService;
	}
	
	@PostMapping("rooms/new")
	public ResponseEntity<Integer> createRoom(@RequestBody RoomQueueDTO roomCreateDto) {
		
		int dummyRoomNumber = roomQueueService.creationQueueService(roomCreateDto); // 예시 방 번호
		return ResponseEntity.status(HttpStatus.CREATED).body(dummyRoomNumber); // 201 Created와 방 번호 반환 예시
	}
}

package com.chatservice.roomlist.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatservice.roomlist.model.RoomDTO;
import com.chatservice.roomlist.service.IRoomListApiService;

/* roomPage.js 에서 실제 방 목록 정보를 요청 받아 반환하는 컨트롤러 */
@RestController
public class RoomListApiController {

	IRoomListApiService roomListApiService;
	
	public RoomListApiController(IRoomListApiService roomListApiService) {
		this.roomListApiService = roomListApiService;
	}
	
	@GetMapping("/api/rooms")
	public List<RoomDTO> apiRoomes() {
		
		return roomListApiService.getRoomList();
	}
}

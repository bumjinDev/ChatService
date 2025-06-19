package com.chatservice.createroom.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RoomQueueViewController {

	/* 채팅 방 생성 페이지 요청 처리 */
	@GetMapping("rooms/new")
	public String createRoom() {
		return "chatservice/new/newRoom";
	}
}

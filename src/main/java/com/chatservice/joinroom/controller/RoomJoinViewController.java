package com.chatservice.joinroom.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.chatservice.joinroom.service.IRoomJoinService;

/* 방 접속 가능 한지 여부 확인 까지 끝난 이후에 방 페이지를 실제로 받는 목적의 컨트롤러.
 * 	newRoom.js 에서 바로 요청 받는 지점이기도 하며, 해당 jsp 페이지 받고 난 뒤에 내부에서 웹 소켓 세션 생성함. */
/* 현재 방 인원수를 확인하여 초과하면 익셉션 핸들러에서 방 인원 처리 */
@Controller
public class RoomJoinViewController {

	IRoomJoinService roomJoinService;
	
	public RoomJoinViewController(IRoomJoinService roomJoinService) {
		this.roomJoinService = roomJoinService;
	}
	
	@GetMapping("/rooms/{roomNumber}")
	public String showChatRoomPage(
			
			@PathVariable("roomNumber") int roomNumber,
			Authentication authenticiaon,
			@AuthenticationPrincipal String userId,
			Model model) {

		/* 현재 요청된 방에 대한 접속 가능 여부 및 접속 대기큐 생성 */
		roomJoinService.confirmJoinRoom(roomNumber, userId);
		
		model.addAttribute("roomNumber", roomNumber);					// 방 번호
		/* 현재 방 참여 인원 : 웹 소켓 측에서 페이지 랜더링 후 해결 */
		model.addAttribute("nickName", authenticiaon.getDetails());		// 사용자 닉네임
		
		/* 실제 Webosocket 채팅 프로그램 세션 연결 하는 페이지 반환. */
		return "chatservice/chat/chat";
	}
}
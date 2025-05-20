package com.chatservice.roomlist.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/* 단순 페이지만 반환, 다만 현재 요청 지점은 SpringSeucirty 에서 JWT 검사한 결과에 따라
 * 받은 Authnetication.Details() 내 사용자 닉네임 포함 */
@Controller
public class RoomListViewController {

	@GetMapping("/rooms")
	public String roomlist(
			Authentication authentication,
			Model model) {
		
		/* 채팅 방 목록 페이지 내 현재 인증된 사용자 닉네임 표시 */
		model.addAttribute("nickName", authentication.getDetails());
		
		return "chatservice/rooms/roomPage";
	}
}

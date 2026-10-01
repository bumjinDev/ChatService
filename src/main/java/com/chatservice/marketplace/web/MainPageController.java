package com.chatservice.marketplace.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 메인 화면(index.jsp)을 그린다. 사용을 중단한 WebController 를 대신하며, 방 개수와 인원 집계는 하지 않는다.
 * index.jsp 는 userName 이 "none" 이면 비회원 화면을 보여 준다.
 */
@Controller
public class MainPageController {

	@GetMapping("/")
	public String mainPage(Authentication authentication, Model model) {
		String userName = (authentication != null && authentication.getDetails() instanceof String name)
				? name
				: "none";
		model.addAttribute("userName", userName);
		return "index";
	}
}

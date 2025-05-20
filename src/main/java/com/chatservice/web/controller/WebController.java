package com.chatservice.web.controller;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.chatservice.web.service.IWebService;

@Controller
public class WebController {

	IWebService webService;
	
	public WebController(IWebService webService) {
		this.webService = webService;
	}
	
	 /**
     * index.jsp(메인 페이지) 요청 처리
     * 
     * - 전체 채팅 방 개수 및 전체 채팅 인원 수 반환
     * - 쿠키를 통해 로그인 여부 확인 후, 세션에 'user' 속성 설정
     * 로그인 세션 저장은 Spring MVC 가 아닌 WebSocket 내 별도로 저장할 것!
     */
    @GetMapping(value = "/")
    public String loadMainPage(
    		Authentication authentication, // Spring Security가 인증된 사용자 정보를 Principal 객체로 주입, 없으면 null
    		Model model
    		) {
        // 전체 채팅 방 개수 및 사용자 전체 인원수 조회
        Map<String, Object> mainPageVO = webService.loadMainInfo(authentication);
        
        model.addAttribute("userName",	mainPageVO.get("userName"));		// JWT 존재 여부에 따라서 JSP 랜더링 되는 DOM 요소 및 JS와 CSS 요소 로드 하는 것이 달라짐
        model.addAttribute("totalRoom", mainPageVO.get("roomCount"));
        model.addAttribute("totalUser", mainPageVO.get("userCount"));
        
        return "index";
    }
}

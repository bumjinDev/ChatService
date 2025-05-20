package com.chatservice.user.controller;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.chatservice.user.service.IMemberService;

import org.slf4j.Logger;

/**
 * MemberMembersController는 회원 관련 요청을 처리하는 컨트롤러입니다.
 * - 로그인 성공, 회원 정보 수정, 회원 가입 등 다양한 회원 관련 작업을 처리합니다.
 * - 각 요청은 적절한 서비스 레이어로 위임되어 로직을 수행합니다.
 */

@Controller
@RequestMapping("/members")
public class MembersViewController {

	private static final Logger logger = LoggerFactory.getLogger(MembersViewController.class);
	
	IMemberService memberService;
	
	public MembersViewController(IMemberService memberService) {
		this.memberService = memberService;
	}
	
	/* login.jsp : 로그인 요청 페이지 (실제 로그인 요청은 POST 요청으로써 필터 클래스에 요청됨.)*/
	@GetMapping("/login")
	public String pageLogin(
			@CookieValue(name = "Authorization", required = false) String token,	/* JWT 정보가 없으면 그냥 login.jsp 반환, 만약에 있으면 이미 로그인 상태에서 요청한 것이므로 자동으로 JSP 에서 리다이렉트 반환 처리. */
			Model model) {

		model.addAttribute("token", token);
		
		return "members/login";
	}
	
	/* join.jsp : 회원가입 요청 페이지 */
	@GetMapping("/join")
	public String pageJoin() {
		
		logger.info("MembersController.pageJoin!");  // 로그 추가
		return "members/join";
	}
	
	 /**
     * 회원 정보 수정 페이지 요청 처리.
     * 
     * - 현재 로그인한 회원의 정보를 가져와 수정 페이지에 전달합니다.
     * - MembersEditPageService를 호출하여 필요한 데이터를 조회합니다.
     *
     * @param httpRequest 사용자 요청 객체 (로그인된 사용자 정보 포함)
     * @param model       뷰로 전달할 데이터 추가
     * @return 회원 정보 수정 페이지 경로
    
     */
    @GetMapping("/edit")
    public String modifiMember(
    		@RequestParam("editid") String editId,
    		Model model ) {

        logger.info("MembersController.modifiMmeber()!");
        model.addAttribute("MembersVO", memberService.searchEditMember(editId));
        
        return "members/modify";
    }
}

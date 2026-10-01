package com.chatservice.marketplace.conversation;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 대화 화면(conversation.jsp)을 돌려준다. 화면에는 대화 ID 만 넘기고, 대화 내용은 conversation.js 가
 * 인증이 필요한 /api/** 와 /ws/** 로 조회한다. 따라서 이 경로 자체는 데이터를 담지 않는다.
 */
@Controller
public class ConversationViewController {

	@GetMapping("/conversations/{conversationId}")
	public String conversationPage(@PathVariable("conversationId") Long conversationId, Model model) {
		model.addAttribute("conversationId", conversationId);
		return "marketplace/conversation";
	}
}

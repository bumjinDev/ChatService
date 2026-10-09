package com.chatservice.marketplace.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 대화 화면(conversation.jsp)을 돌려준다(설계 8.2). 화면 자체에는 데이터가 없고, 화면의 스크립트가
 * 인증이 필요한 /api/** 와 /ws/** 를 호출해 내용을 채운다. ?conversationId= 가 없으면 내 대화 목록만 보여 준다.
 */
@Controller
public class ConversationPageController {

    @GetMapping("/conversations")
    public String conversationPage() {
        return "marketplace/conversation";
    }
}

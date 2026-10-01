package com.chatservice.marketplace.conversation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.chatservice.marketplace.support.IntegrationTestSupport;

/** 대화 화면 경로 검증. 화면은 대화 ID 만 받고 데이터는 인증된 API 로 조회한다. */
class ConversationPageTest extends IntegrationTestSupport {

	@Test
	void 대화_화면은_conversation_jsp_를_그리고_대화_ID_만_넘긴다() throws Exception {
		mockMvc.perform(get("/conversations/123"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("/WEB-INF/views/marketplace/conversation.jsp"))
				.andExpect(model().attribute("conversationId", 123L))
				.andExpect(model().size(1));
	}
}

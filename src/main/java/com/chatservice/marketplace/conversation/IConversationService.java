package com.chatservice.marketplace.conversation;

public interface IConversationService {

	/** 상품별 1:1 대화를 시작하거나 기존 대화를 돌려준다(F-005). */
	ConversationStartResult start(String memberId, Long productId);
}

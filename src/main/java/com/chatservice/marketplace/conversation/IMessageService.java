package com.chatservice.marketplace.conversation;

public interface IMessageService {

	/** 대화 참여자의 메시지를 저장하고 상대방의 열린 세션에 전달한다(F-006). */
	MessageResponse send(String memberId, Long conversationId, MessageSendRequest request);
}

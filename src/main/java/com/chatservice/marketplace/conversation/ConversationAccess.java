package com.chatservice.marketplace.conversation;

import org.springframework.stereotype.Component;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;

/** 대화를 조회하고 요청한 회원이 참여자인지 확인한다. 없으면 404, 참여자가 아니면 403 이다. */
@Component
public class ConversationAccess {

	private final ConversationRepository conversationRepository;

	public ConversationAccess(ConversationRepository conversationRepository) {
		this.conversationRepository = conversationRepository;
	}

	public Conversation requireParticipant(String memberId, Long conversationId) {
		Conversation conversation = conversationRepository.findById(conversationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
		if (!conversation.isParticipant(memberId)) {
			throw new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER);
		}
		return conversation;
	}
}

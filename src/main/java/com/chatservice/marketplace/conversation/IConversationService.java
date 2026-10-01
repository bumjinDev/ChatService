package com.chatservice.marketplace.conversation;

import java.time.Instant;
import java.util.List;

public interface IConversationService {

	/** 상품별 1:1 대화를 시작하거나 기존 대화를 돌려준다(F-005). */
	ConversationStartResult start(String memberId, Long productId);

	/** 요청한 회원이 구매 희망자 또는 판매자인 대화 목록. 생성 시각 내림차순이다(F-007). */
	List<ConversationSummaryResponse> listMine(String memberId);

	/** 대화 상세·거래 표시. 참여자만 조회할 수 있다(F-007). */
	ConversationDetailResponse getDetail(String memberId, Long conversationId);

	/** 저장된 메시지를 messageId 오름차순으로 돌려준다. afterId 가 있으면 그보다 큰 메시지만 돌려준다(F-007). */
	List<MessageResponse> getMessages(String memberId, Long conversationId, Long afterId);

	/** 결제 성공 시 실제 구매자와 판매자의 대화가 없으면 시스템 생성 대화로 만든다(F-010). */
	Conversation ensurePartyConversation(Long productId, String buyerId, String sellerId, Instant now);
}

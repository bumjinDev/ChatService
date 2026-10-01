package com.chatservice.marketplace.conversation;

import org.springframework.stereotype.Component;

import com.chatservice.marketplace.conversation.realtime.RealtimeEvents.ConversationStateEvent;
import com.chatservice.marketplace.conversation.realtime.RealtimePublisher;

/**
 * 상품 판매 종료나 거래 종료로 대화의 쓰기 가능 여부가 바뀔 때 CONVERSATION_STATE 이벤트를 보낸다(설계 명세서 5.3절).
 * 상태는 호출 시점(같은 트랜잭션 안의 변경 반영 후)에 계산하고, 전달은 커밋 뒤에 한다.
 */
@Component
public class ConversationNotifier {

	private final ConversationRepository conversationRepository;
	private final ConversationStateEvaluator stateEvaluator;
	private final RealtimePublisher realtimePublisher;

	public ConversationNotifier(ConversationRepository conversationRepository,
			ConversationStateEvaluator stateEvaluator, RealtimePublisher realtimePublisher) {
		this.conversationRepository = conversationRepository;
		this.stateEvaluator = stateEvaluator;
		this.realtimePublisher = realtimePublisher;
	}

	/** 상품이 판매 종료되었을 때 그 상품의 모든 대화에 보낸다. excludeMemberId 는 사건을 일으킨 회원이다. */
	public void notifyProductConversations(Long productId, String excludeMemberId) {
		for (Conversation conversation : conversationRepository.findByProductIdOrderByConversationIdAsc(productId)) {
			publish(conversation, excludeMemberId);
		}
	}

	/** 거래가 최종 상태가 되었을 때 실제 당사자 대화에 보낸다. 시스템 처리이면 excludeMemberId 는 null 이다. */
	public void notifyPartyConversation(Long productId, String buyerId, String excludeMemberId) {
		conversationRepository.findFirstByProductIdAndBuyerIdOrderByConversationIdAsc(productId, buyerId)
				.ifPresent(conversation -> publish(conversation, excludeMemberId));
	}

	private void publish(Conversation conversation, String excludeMemberId) {
		ConversationState state = stateEvaluator.evaluate(conversation);
		realtimePublisher.publish(conversation.getConversationId(),
				new ConversationStateEvent(conversation.getConversationId(), state.product().getStatus(),
						state.writable(), state.readOnlyReason()),
				excludeMemberId);
	}
}

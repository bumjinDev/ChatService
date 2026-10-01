package com.chatservice.marketplace.conversation;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chatservice.marketplace.common.MemberDirectory;
import com.chatservice.marketplace.conversation.ConversationDetailResponse.OrderSummary;
import com.chatservice.marketplace.conversation.ConversationDetailResponse.Participant;
import com.chatservice.marketplace.conversation.ConversationDetailResponse.ProductView;
import com.chatservice.marketplace.offer.OfferResponse;
import com.chatservice.marketplace.offer.PriceOfferRepository;
import com.chatservice.marketplace.product.Product;

/** 대화 상세 응답을 조립한다. 상품, 참여자 닉네임, 쓰기 가능 여부, 제안 목록(생성 순), 당사자 주문 요약을 붙인다. */
@Component
public class ConversationAssembler {

	private final ConversationStateEvaluator stateEvaluator;
	private final PriceOfferRepository offerRepository;
	private final MemberDirectory memberDirectory;

	public ConversationAssembler(ConversationStateEvaluator stateEvaluator, PriceOfferRepository offerRepository,
			MemberDirectory memberDirectory) {
		this.stateEvaluator = stateEvaluator;
		this.offerRepository = offerRepository;
		this.memberDirectory = memberDirectory;
	}

	public ConversationDetailResponse detail(Conversation conversation, String memberId) {
		ConversationState state = stateEvaluator.evaluate(conversation);
		Product product = state.product();
		Map<String, String> nicknames = memberDirectory
				.nicknames(List.of(conversation.getBuyerId(), conversation.getSellerId()));
		List<OfferResponse> offers = offerRepository
				.findByConversationIdOrderByCreatedAtAscOfferIdAsc(conversation.getConversationId()).stream()
				.map(OfferResponse::of)
				.toList();
		OrderSummary order = state.partyOrder()
				.map(o -> new OrderSummary(o.getOrderId(), o.getTradeStatus(), o.getShippingStatus()))
				.orElse(null);
		return new ConversationDetailResponse(
				conversation.getConversationId(),
				new ProductView(product.getProductId(), product.getName(), product.getDescription(),
						product.getCategory(), product.getPrice(), product.getStatus()),
				new Participant(nicknames.get(conversation.getBuyerId())),
				new Participant(nicknames.get(conversation.getSellerId())),
				conversation.roleOf(memberId),
				state.writable(),
				state.readOnlyReason(),
				offers,
				order);
	}
}

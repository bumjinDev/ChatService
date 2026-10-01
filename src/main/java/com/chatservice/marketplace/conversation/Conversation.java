package com.chatservice.marketplace.conversation;

import java.time.Instant;

import com.chatservice.marketplace.common.MemberRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * CONVERSATION 테이블. 상품 하나와 구매 희망자 하나의 1:1 대화다.
 * 쓰기 가능 여부는 저장하지 않고 상품과 주문 상태로 계산한다(설계 명세서 6.6절).
 */
@Entity
@Table(name = "CONVERSATION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "CONVERSATION_ID")
	private Long conversationId;

	@Column(name = "PRODUCT_ID", nullable = false)
	private Long productId;

	@Column(name = "BUYER_ID", nullable = false)
	private String buyerId;

	@Column(name = "SELLER_ID", nullable = false)
	private String sellerId;

	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	/** true 이면 결제 성공 시 시스템이 만든 대화다. */
	@Column(name = "CREATED_BY_SYSTEM", nullable = false)
	private boolean createdBySystem;

	public static Conversation open(Long productId, String buyerId, String sellerId, Instant now,
			boolean createdBySystem) {
		Conversation conversation = new Conversation();
		conversation.productId = productId;
		conversation.buyerId = buyerId;
		conversation.sellerId = sellerId;
		conversation.createdAt = now;
		conversation.createdBySystem = createdBySystem;
		return conversation;
	}

	public boolean isParticipant(String memberId) {
		return buyerId.equals(memberId) || sellerId.equals(memberId);
	}

	public MemberRole roleOf(String memberId) {
		return buyerId.equals(memberId) ? MemberRole.BUYER : MemberRole.SELLER;
	}

	public String counterpartOf(String memberId) {
		return buyerId.equals(memberId) ? sellerId : buyerId;
	}
}

package com.chatservice.marketplace.conversation.realtime;

import java.time.Instant;

import com.chatservice.marketplace.conversation.ReadOnlyReason;
import com.chatservice.marketplace.offer.OfferStatus;
import com.chatservice.marketplace.product.ProductStatus;

/** 서버가 클라이언트로 보내는 이벤트(설계 명세서 5.3절). JSON 의 type 필드로 종류를 구분한다. */
public final class RealtimeEvents {

	private RealtimeEvents() {
	}

	public record MessageEvent(String type, Long messageId, Long conversationId, String senderId,
			String senderNickname, String content, Instant createdAt) {

		public MessageEvent(Long messageId, Long conversationId, String senderId, String senderNickname,
				String content, Instant createdAt) {
			this("MESSAGE", messageId, conversationId, senderId, senderNickname, content, createdAt);
		}
	}

	public record OfferEvent(String type, Long offerId, Long conversationId, long amount, OfferStatus status,
			Instant createdAt, Instant respondedAt) {

		public OfferEvent(Long offerId, Long conversationId, long amount, OfferStatus status, Instant createdAt,
				Instant respondedAt) {
			this("OFFER", offerId, conversationId, amount, status, createdAt, respondedAt);
		}
	}

	public record ConversationStateEvent(String type, Long conversationId, ProductStatus productStatus,
			boolean writable, ReadOnlyReason readOnlyReason) {

		public ConversationStateEvent(Long conversationId, ProductStatus productStatus, boolean writable,
				ReadOnlyReason readOnlyReason) {
			this("CONVERSATION_STATE", conversationId, productStatus, writable, readOnlyReason);
		}
	}
}

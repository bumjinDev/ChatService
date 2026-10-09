package com.chatservice.marketplace.conversation.domain;

import java.time.Instant;

import org.hibernate.type.NumericBooleanConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 상품에 대한 구매 희망자와 판매자의 1:1 대화(CONVERSATION). 상품과 구매 희망자 조합에 하나다(F-005).
 * 쓰기 가능 여부는 저장하지 않고 상품·주문 상태로 계산한다(설계 6.6).
 * sellerId 는 참여자 검사 때 상품을 다시 읽지 않도록 상품에서 복사해 둔다.
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

    /** true 이면 문의 없이 결제한 구매자를 위해 결제 성공 시 시스템이 만든 대화다. DB 에는 NUMBER(1) 0/1 로 저장한다. */
    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "CREATED_BY_SYSTEM", nullable = false)
    private boolean createdBySystem;

    public static Conversation open(Long productId, String buyerId, String sellerId, boolean createdBySystem, Instant now) {
        Conversation conversation = new Conversation();
        conversation.productId = productId;
        conversation.buyerId = buyerId;
        conversation.sellerId = sellerId;
        conversation.createdBySystem = createdBySystem;
        conversation.createdAt = now;
        return conversation;
    }

    public boolean isParticipant(String memberId) {
        return buyerId.equals(memberId) || sellerId.equals(memberId);
    }

    public boolean isBuyer(String memberId) {
        return buyerId.equals(memberId);
    }
}

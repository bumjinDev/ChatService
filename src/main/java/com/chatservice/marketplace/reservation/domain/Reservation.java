package com.chatservice.marketplace.reservation.domain;

import java.time.Instant;

import com.chatservice.marketplace.common.PartyRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자가 구매 희망자의 가격 제안을 수락할 때 제안 수량만큼 걸리는 예약(RESERVATION). OFFER_ID 가 그 제안을 가리킨다.
 *
 * 예약은 재고를 차감하지 않는다. 활성이고 만료 시각 전인 예약의 수량만큼 다른 회원의 구매 가능 수량이 줄어들고,
 * 예약 중에는 예약된 구매 희망자가 합의 가격으로 제안 수량을 결제한다. 결제하면 예약이 끝난다.
 * 합의 가격 주문이 발송 전에 취소되면 끝난 행은 그대로 두고 같은 내용의 새 행을 만든다.
 * 끝난 예약은 지우지 않고 {@link ReservationStatus}로 구분해 남긴다. 다른 엔티티와는 식별자 값으로만 연결한다.
 *
 * 상태 변경 메서드({@link #release}, {@link #expire}, {@link #fulfill})는 엔티티 필드만 바꾼다. DB 반영은 flush 때
 * dirty checking 으로 실행되는 UPDATE 가 한다. 상태 검사는 현재 트랜잭션이 조회한 필드 값으로 하며, 조회 뒤에 다른
 * 트랜잭션이 커밋한 변경은 검사에 반영되지 않는다.
 */
@Entity
@Table(name = "RESERVATION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RESERVATION_ID")
    private Long reservationId;

    @Column(name = "PRODUCT_ID", nullable = false)
    private Long productId;

    /** 예약을 건 대화 ID. 대화 상세가 이 상품·구매 희망자의 예약을 보여 주는 데 쓴다. */
    @Column(name = "CONVERSATION_ID", nullable = false)
    private Long conversationId;

    /** 예약된 구매 희망자 ID. */
    @Column(name = "BUYER_ID", nullable = false)
    private String buyerId;

    @Column(name = "SELLER_ID", nullable = false)
    private String sellerId;

    /** 이 예약을 만든 가격 제안 ID. 합의 상태를 계산할 때 예약 행을 제안과 연결한다. 판매자의 예약 걸기로 만들어진 기존 행은 null 이다. */
    @Column(name = "OFFER_ID")
    private Long offerId;

    @Column(name = "QUANTITY", nullable = false)
    private long quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private ReservationStatus status;

    @Column(name = "RESERVED_AT", nullable = false)
    private Instant reservedAt;

    /** 만료 시각. 예약 시각에 {@code marketplace.reservation.duration}을 더한 값이다. */
    @Column(name = "EXPIRES_AT", nullable = false)
    private Instant expiresAt;

    /** 끝난 시각. 해제, 만료, 결제 종료 때 저장하며 활성이면 null 이다. */
    @Column(name = "ENDED_AT")
    private Instant endedAt;

    /** 해제 주체. 해제된 예약만 값이 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "RELEASED_BY")
    private PartyRole releasedBy;

    /** 결제로 끝났을 때 그 주문 ID. 결제 종료가 아니면 null 이다. */
    @Column(name = "FULFILLED_ORDER_ID")
    private Long fulfilledOrderId;

    public static Reservation reserve(Long productId, Long conversationId, String buyerId, String sellerId,
                                      Long offerId, long quantity, Instant reservedAt, Instant expiresAt) {
        Reservation reservation = new Reservation();
        reservation.productId = productId;
        reservation.conversationId = conversationId;
        reservation.buyerId = buyerId;
        reservation.sellerId = sellerId;
        reservation.offerId = offerId;
        reservation.quantity = quantity;
        reservation.status = ReservationStatus.ACTIVE;
        reservation.reservedAt = reservedAt;
        reservation.expiresAt = expiresAt;
        return reservation;
    }

    public boolean isActive() {
        return status == ReservationStatus.ACTIVE;
    }

    /**
     * 활성이고 만료 시각 전인지 확인한다.
     *
     * 만료 시각이 지났지만 아직 자동 해제가 실행되지 않은 예약은 다른 회원의 구매 가능 수량을 줄이지 않고,
     * 예약된 구매 희망자의 결제도 예약으로 인정하지 않는다.
     *
     * @param now 처리 기준 시각
     * @return 상태가 {@code ACTIVE}이고 {@code now}가 만료 시각보다 이르면 {@code true}
     */
    public boolean isActiveAt(Instant now) {
        return isActive() && now.isBefore(expiresAt);
    }

    public boolean isBuyer(String memberId) {
        return buyerId.equals(memberId);
    }

    public boolean isSeller(String memberId) {
        return sellerId.equals(memberId);
    }

    public boolean isParty(String memberId) {
        return isBuyer(memberId) || isSeller(memberId);
    }

    /**
     * 예약을 해제(RELEASED)로 바꾸고 해제 주체와 끝난 시각을 기록한다.
     *
     * @param by  해제 주체. 판매자는 {@code SELLER}, 예약된 구매 희망자는 {@code BUYER}
     * @param now 끝난 시각(ENDED_AT)
     * @throws IllegalStateException 활성 예약이 아니면 던진다.
     */
    public void release(PartyRole by, Instant now) {
        requireActive();
        this.status = ReservationStatus.RELEASED;
        this.releasedBy = by;
        this.endedAt = now;
    }

    /**
     * 예약을 기간 만료(EXPIRED)로 바꾸고 끝난 시각을 기록한다. 만료 시각이 지났는지는 호출하는 쪽이 먼저 검사한다.
     *
     * @param now 끝난 시각(ENDED_AT)
     * @throws IllegalStateException 활성 예약이 아니면 던진다.
     */
    public void expire(Instant now) {
        requireActive();
        this.status = ReservationStatus.EXPIRED;
        this.endedAt = now;
    }

    /**
     * 예약을 결제 종료(FULFILLED)로 바꾸고 주문 ID 와 끝난 시각을 기록한다.
     *
     * @param orderId 예약된 구매 희망자가 결제한 주문 ID
     * @param now     끝난 시각(ENDED_AT)
     * @throws IllegalStateException 활성 예약이 아니면 던진다.
     */
    public void fulfill(Long orderId, Instant now) {
        requireActive();
        this.status = ReservationStatus.FULFILLED;
        this.fulfilledOrderId = orderId;
        this.endedAt = now;
    }

    /** 상태 전환이 허용되는지 확인한다. 서비스가 현재 상태를 먼저 검사한다는 전제로 둔 마지막 확인이다. */
    private void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException("활성 예약이 아닙니다. reservationId=" + reservationId + ", status=" + status);
        }
    }
}

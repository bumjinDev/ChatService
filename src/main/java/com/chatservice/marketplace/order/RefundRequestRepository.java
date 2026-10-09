package com.chatservice.marketplace.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatservice.marketplace.order.domain.OrderEnums.RefundDecision;
import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;
import com.chatservice.marketplace.order.domain.RefundRequest;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    Optional<RefundRequest> findByOrderId(Long orderId);

    /**
     * 주문의 환불 요청 행이 있는지 확인한다.
     *
     * Spring Data JPA가 메서드 이름으로 쿼리를 생성한다. 조건은 {@code orderId}가 일치하는 행이다.
     * 사용하는 곳:
     * - 환불 요청 접수: 환불 요청이 이미 있으면 중복 요청을 거절한다.
     * - 확인 기간 만료 자동 완료: 환불 요청이 있으면 완료하지 않는다.
     * ORDER_ID 컬럼에 UNIQUE 제약이 없으므로, 주문당 환불 요청 한 행은 이 확인으로만 유지된다.
     *
     * @param orderId 확인할 주문 ID
     * @return 환불 요청이 있으면 {@code true}
     */
    boolean existsByOrderId(Long orderId);

    /**
     * 판매자 응답 기한이 지난 환불 요청의 ID 목록을 조회한다.
     *
     * 조건: 처리 결과가 {@code decision}이고, 주문의 거래 상태가 {@code tradeStatus}이며, 응답 기한이 {@code now} 이하인 환불 요청.
     * 무응답 자동 환불은 {@code PENDING}, {@code ON_HOLD}를 전달한다.
     * REFUND_REQUEST와 PURCHASE_ORDER를 주문 ID로 내부 조인한다.
     * 주문 ID가 아니라 환불 요청 ID를 반환한다. 엔티티는 조회하지 않으며, 잠금을 걸지 않는다. 환불 요청 ID 오름차순으로 반환한다.
     *
     * @param decision    조회할 처리 결과
     * @param tradeStatus 조회할 주문의 거래 상태
     * @param now         기준 시각. 응답 기한이 이 값 이하인 요청을 조회한다.
     * @return 조건에 맞는 환불 요청 ID 목록. 없으면 빈 목록
     */
    @Query("select r.refundRequestId from RefundRequest r, PurchaseOrder o"
            + " where o.orderId = r.orderId and r.decision = :decision and o.tradeStatus = :tradeStatus"
            + " and r.responseDeadlineAt <= :now"
            + " order by r.refundRequestId")
    List<Long> findIdsByResponseExpired(@Param("decision") RefundDecision decision,
                                        @Param("tradeStatus") TradeStatus tradeStatus,
                                        @Param("now") Instant now);
}

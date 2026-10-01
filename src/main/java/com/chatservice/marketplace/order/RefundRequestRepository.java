package com.chatservice.marketplace.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

	Optional<RefundRequest> findFirstByOrderIdOrderByRefundRequestIdAsc(Long orderId);

	boolean existsByOrderId(Long orderId);

	/** 무응답 자동 환불 대상: 응답 대기, 응답 기한이 now 이하, 주문이 보류 중. */
	@Query("SELECT r.refundRequestId FROM RefundRequest r, PurchaseOrder o WHERE o.orderId = r.orderId "
			+ "AND r.decision = com.chatservice.marketplace.order.RefundDecision.PENDING "
			+ "AND r.responseDeadlineAt <= :now "
			+ "AND o.tradeStatus = com.chatservice.marketplace.order.TradeStatus.ON_HOLD "
			+ "ORDER BY r.refundRequestId")
	List<Long> findExpiredPendingIds(@Param("now") Instant now);
}

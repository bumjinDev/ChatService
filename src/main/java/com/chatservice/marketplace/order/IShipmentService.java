package com.chatservice.marketplace.order;

import java.time.Instant;

public interface IShipmentService {

	/** 판매자가 발송 정보를 등록해 배송 중으로 바꾼다(F-011). */
	OrderDetailResponse registerShipment(String memberId, Long orderId, ShipmentRegisterRequest request);

	/**
	 * 모의 배송 기간이 지난 배송 중 주문을 배송 완료로 바꾼다(F-014). 주문 한 건씩 별도 트랜잭션으로 처리하고 처리 건수를 돌려준다.
	 */
	int completeDueDeliveries(Instant now);
}

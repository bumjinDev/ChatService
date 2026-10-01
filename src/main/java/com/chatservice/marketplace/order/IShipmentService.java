package com.chatservice.marketplace.order;

public interface IShipmentService {

	/** 판매자가 발송 정보를 등록해 배송 중으로 바꾼다(F-011). */
	OrderDetailResponse registerShipment(String memberId, Long orderId, ShipmentRegisterRequest request);
}

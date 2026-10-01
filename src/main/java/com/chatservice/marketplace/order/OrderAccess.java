package com.chatservice.marketplace.order;

import org.springframework.stereotype.Component;

import com.chatservice.marketplace.common.BusinessException;
import com.chatservice.marketplace.common.ErrorCode;

/** 주문을 조회하고 요청한 회원의 역할을 확인한다. 없으면 404, 역할이 맞지 않으면 403 이다. */
@Component
public class OrderAccess {

	private final PurchaseOrderRepository orderRepository;

	public OrderAccess(PurchaseOrderRepository orderRepository) {
		this.orderRepository = orderRepository;
	}

	public PurchaseOrder require(Long orderId) {
		return orderRepository.findById(orderId).orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
	}

	public PurchaseOrder requireSeller(String memberId, Long orderId) {
		PurchaseOrder order = require(orderId);
		if (!order.getSellerId().equals(memberId)) {
			throw new BusinessException(ErrorCode.NOT_SELLER);
		}
		return order;
	}

	public PurchaseOrder requireBuyer(String memberId, Long orderId) {
		PurchaseOrder order = require(orderId);
		if (!order.getBuyerId().equals(memberId)) {
			throw new BusinessException(ErrorCode.NOT_BUYER);
		}
		return order;
	}

	public PurchaseOrder requireParty(String memberId, Long orderId) {
		PurchaseOrder order = require(orderId);
		if (!order.isParty(memberId)) {
			throw new BusinessException(ErrorCode.NOT_TRADE_PARTY);
		}
		return order;
	}
}

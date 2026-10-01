package com.chatservice.marketplace.order;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.order.OrderSummaryResponse.ProductSummary;
import com.chatservice.marketplace.product.Product;
import com.chatservice.marketplace.product.ProductRepository;

/** 주문·배송·결제·최종 금전 결과 조회(F-019). 조회는 상태를 바꾸지 않는다. */
@Service
public class OrderQueryService implements IOrderQueryService {

	private final PurchaseOrderRepository orderRepository;
	private final ProductRepository productRepository;
	private final OrderAccess orderAccess;
	private final OrderDetailAssembler assembler;

	public OrderQueryService(PurchaseOrderRepository orderRepository, ProductRepository productRepository,
			OrderAccess orderAccess, OrderDetailAssembler assembler) {
		this.orderRepository = orderRepository;
		this.productRepository = productRepository;
		this.orderAccess = orderAccess;
		this.assembler = assembler;
	}

	@Override
	@Transactional(readOnly = true)
	public List<OrderSummaryResponse> listPurchases(String memberId) {
		return summarize(orderRepository.findByBuyerIdOrderByConfirmedAtDescOrderIdDesc(memberId));
	}

	@Override
	@Transactional(readOnly = true)
	public List<OrderSummaryResponse> listSales(String memberId) {
		return summarize(orderRepository.findBySellerIdOrderByConfirmedAtDescOrderIdDesc(memberId));
	}

	/** 주문이 없으면 404, 당사자가 아니면 403 NOT_TRADE_PARTY. 수령인과 주소는 당사자에게만 나간다. */
	@Override
	@Transactional(readOnly = true)
	public OrderDetailResponse getDetail(String memberId, Long orderId) {
		return assembler.detail(orderAccess.requireParty(memberId, orderId), memberId);
	}

	private List<OrderSummaryResponse> summarize(List<PurchaseOrder> orders) {
		Map<Long, Product> products = productRepository
				.findAllById(orders.stream().map(PurchaseOrder::getProductId).distinct().toList()).stream()
				.collect(Collectors.toMap(Product::getProductId, Function.identity()));
		return orders.stream().map(order -> {
			Product product = products.get(order.getProductId());
			return new OrderSummaryResponse(order.getOrderId(),
					new ProductSummary(order.getProductId(), product == null ? null : product.getName()),
					order.getPaidAmount(), order.getPriceSource(), order.getShippingStatus(), order.getTradeStatus(),
					order.getConfirmedAt(), order.getFinalizedAt());
		}).toList();
	}
}

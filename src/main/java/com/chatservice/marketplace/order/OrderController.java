package com.chatservice.marketplace.order;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chatservice.marketplace.order.dto.CancelRequest;
import com.chatservice.marketplace.order.dto.OrderDetailResponse;
import com.chatservice.marketplace.order.dto.OrderSummaryResponse;
import com.chatservice.marketplace.order.dto.PlaceOrderRequest;
import com.chatservice.marketplace.order.dto.RefundRequestBody;
import com.chatservice.marketplace.order.dto.ShipmentRequest;
import com.chatservice.marketplace.order.service.IOrderCancellationService;
import com.chatservice.marketplace.order.service.IOrderQueryService;
import com.chatservice.marketplace.order.service.IOrderService;
import com.chatservice.marketplace.order.service.IRefundService;
import com.chatservice.marketplace.order.service.IShipmentService;
import com.chatservice.marketplace.order.service.ITradeCompletionService;

import jakarta.validation.Valid;

/**
 * 주문의 생성부터 거래 종료까지를 처리하는 REST API 컨트롤러.
 *
 * 모든 경로는 {@code /api/orders} 아래에 있고 인증이 필요하다. 인증은 보안 필터가 처리하며,
 * 인증되지 않은 요청은 이 컨트롤러에 도달하기 전에 401로 거절된다. 각 메서드는 인증된 회원 ID를
 * {@link AuthenticationPrincipal}로 받는다.
 *
 * 이 컨트롤러는 업무 규칙을 검사하지 않는다. 입력 형식 검사는 {@link Valid}에 맡기고,
 * 권한·상태·기한 검사와 데이터 변경은 서비스에 위임한다.
 *
 * 상태를 변경하는 요청은 두 단계로 처리한다.
 *   1. 변경 서비스 메서드를 호출한다. 변경은 이 호출의 트랜잭션 안에서 반영되고, 메서드가 반환되면 커밋된다.
 *   2. {@link IOrderQueryService#getDetail}로 커밋된 주문을 다시 조회해 응답 본문을 만든다.
 *       이 조회는 별도의 읽기 전용 트랜잭션에서 실행된다.
 * 따라서 2단계 조회가 실패해도 1단계에서 커밋한 변경은 그대로 남는다.
 *
 * 서비스가 던진 {@link com.chatservice.marketplace.common.error.BusinessException}과
 * 입력 검증 예외는 {@link com.chatservice.marketplace.common.error.ApiExceptionHandler}가
 * {@code {code, status, message, fieldErrors}} 형식의 응답으로 변환한다.
 *
 * @see IOrderService
 * @see IOrderQueryService
 * @see IShipmentService
 * @see IOrderCancellationService
 * @see ITradeCompletionService
 * @see IRefundService
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final IOrderService orderService;
    private final IOrderQueryService orderQueryService;
    private final IShipmentService shipmentService;
    private final IOrderCancellationService cancellationService;
    private final ITradeCompletionService tradeCompletionService;
    private final IRefundService refundService;

    public OrderController(IOrderService orderService, IOrderQueryService orderQueryService,
                           IShipmentService shipmentService, IOrderCancellationService cancellationService,
                           ITradeCompletionService tradeCompletionService, IRefundService refundService) {
        this.orderService = orderService;
        this.orderQueryService = orderQueryService;
        this.shipmentService = shipmentService;
        this.cancellationService = cancellationService;
        this.tradeCompletionService = tradeCompletionService;
        this.refundService = refundService;
    }

    /**
     * 요청한 회원이 구매자로서 참여한 주문 목록을 조회한다(F-019).
     *
     * 정렬 순서는 구매 확정 시각 내림차순이고, 시각이 같으면 주문 ID 내림차순이다.
     * 페이지 단위로 나누지 않고 전체 목록을 반환한다.
     *
     * @param memberId 인증된 회원 ID
     * @return 구매 주문 요약 목록. 주문이 없으면 빈 목록이다
     */
    @GetMapping("/purchases")
    public List<OrderSummaryResponse> purchases(@AuthenticationPrincipal String memberId) {
        return orderQueryService.purchases(memberId);
    }

    /**
     * 요청한 회원이 판매자로서 참여한 주문 목록을 조회한다(F-019).
     *
     * 정렬 기준과 반환 범위는 {@link #purchases}와 같다.
     *
     * @param memberId 인증된 회원 ID
     * @return 판매 주문 요약 목록. 주문이 없으면 빈 목록이다
     */
    @GetMapping("/sales")
    public List<OrderSummaryResponse> sales(@AuthenticationPrincipal String memberId) {
        return orderQueryService.sales(memberId);
    }

    /**
     * 주문 상세를 조회한다(F-019).
     *
     * 응답에는 수령인과 배송 주소, 발송·취소·환불 정보, 요청한 회원 본인의 잔액 변동 내역이 들어간다.
     * 수령인과 주소가 포함되므로 주문의 구매자와 판매자만 조회할 수 있다.
     * 결제 응답을 받지 못한 구매자는 이 API로 결제 결과를 확인한다.
     *
     * @param memberId 인증된 회원 ID
     * @param orderId  조회할 주문 ID
     * @return 주문 상세
     * @throws com.chatservice.marketplace.common.error.BusinessException
     *           - {@code ORDER_NOT_FOUND}(404): 주문이 없다
     *           - {@code NOT_TRADE_PARTY}(403): 요청한 회원이 주문의 구매자도 판매자도 아니다
     */
    @GetMapping("/{orderId}")
    public OrderDetailResponse detail(@AuthenticationPrincipal String memberId,
                                      @PathVariable("orderId") Long orderId) {
        return orderQueryService.getDetail(memberId, orderId);
    }

    /**
     * 잔액으로 상품을 결제하고 주문을 생성한다(F-010).
     *
     * 결제 금액은 요청에서 받지 않는다. 서비스가 적용 단가와 구매 수량을 곱해 계산한다.
     * 결제가 성공하면 주문 생성, 잔액 차감, 재고 차감이 하나의 트랜잭션으로 반영된다.
     *
     * @param memberId 인증된 회원 ID. 이 회원이 구매자가 된다
     * @param request  상품 ID, 구매 수량, 수령인, 배송 주소, 선택 값인 합의 제안 ID와 요청 식별자
     * @return 201 Created와 생성된 주문의 상세
     * @throws org.springframework.web.bind.MethodArgumentNotValidException
     *         필수 값이 없거나 길이·범위가 맞지 않으면 400 {@code VALIDATION_ERROR}로 응답한다
     * @throws com.chatservice.marketplace.common.error.BusinessException
     *           - {@code PRODUCT_NOT_FOUND}(404): 상품이 없다
     *           - {@code SELF_TRADE_NOT_ALLOWED}(403): 본인 상품이다
     *           - {@code PRODUCT_NOT_ON_SALE}(409): 판매 종료된 상품이다
     *           - {@code INSUFFICIENT_STOCK}(409): 구매 수량이 남은 수량보다 많다
     *           - {@code INVALID_OFFER_SELECTION}(409): 이 상품과 구매자에게 수락된 제안이 아니다
     *           - {@code VALIDATION_ERROR}(400): 총 결제 금액이 저장 가능한 범위를 넘는다
     *           - {@code INSUFFICIENT_BALANCE}(409): 잔액이 총 결제 금액보다 적다
     * @see IOrderService#placeOrder
     */
    @PostMapping
    public ResponseEntity<OrderDetailResponse> placeOrder(@AuthenticationPrincipal String memberId,
                                                          @Valid @RequestBody PlaceOrderRequest request) {
        Long orderId = orderService.placeOrder(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderQueryService.getDetail(memberId, orderId));
    }

    /**
     * 판매자가 발송 정보를 등록하고 주문을 배송 중으로 변경한다(F-011).
     *
     * 발송 정보는 주문당 한 번만 등록할 수 있으며 수정할 수 없다.
     * 등록하면 모의 배송이 시작되고, 설정된 기간이 지나면 자동 처리가 배송 완료로 변경한다.
     *
     * @param memberId 인증된 회원 ID
     * @param orderId  발송할 주문 ID
     * @param request  택배사명, 운송장 번호, 선택 값인 요청 식별자
     * @return 201 Created와 변경된 주문의 상세
     * @throws org.springframework.web.bind.MethodArgumentNotValidException
     *         택배사명이나 운송장 번호가 비어 있거나 너무 길면 400 {@code VALIDATION_ERROR}로 응답한다
     * @throws com.chatservice.marketplace.common.error.BusinessException
     *           - {@code ORDER_NOT_FOUND}(404): 주문이 없다
     *           - {@code NOT_SELLER}(403): 요청한 회원이 판매자가 아니다
     *           - {@code ORDER_NOT_IN_PROGRESS}(409): 이미 종료된 주문이다
     *           - {@code ALREADY_SHIPPED}(409): 발송 정보가 이미 있다
     *           - {@code SHIPMENT_DEADLINE_PASSED}(409): 발송 기한이 지났다
     * @see IShipmentService#register
     */
    @PostMapping("/{orderId}/shipment")
    public ResponseEntity<OrderDetailResponse> registerShipment(@AuthenticationPrincipal String memberId,
                                                                @PathVariable("orderId") Long orderId,
                                                                @Valid @RequestBody ShipmentRequest request) {
        shipmentService.register(memberId, orderId, request.carrierName(), request.trackingNumber(), request.requestId());
        return ResponseEntity.status(HttpStatus.CREATED).body(orderQueryService.getDetail(memberId, orderId));
    }

    /**
     * 구매자나 판매자가 발송 전 주문을 취소한다(F-012).
     *
     * 취소하면 주문이 취소 완료로 종료되고, 결제 금액 전액이 구매자 잔액으로 반환된다.
     * 차감된 재고와 상품의 판매 상태는 바뀌지 않는다.
     *
     * @param memberId 인증된 회원 ID
     * @param orderId  취소할 주문 ID
     * @param request  취소 사유
     * @return 취소된 주문의 상세
     * @throws org.springframework.web.bind.MethodArgumentNotValidException
     *         취소 사유가 비어 있거나 500자를 넘으면 400 {@code VALIDATION_ERROR}로 응답한다
     * @throws com.chatservice.marketplace.common.error.BusinessException
     *           - {@code ORDER_NOT_FOUND}(404): 주문이 없다
     *           - {@code NOT_TRADE_PARTY}(403): 요청한 회원이 주문의 구매자도 판매자도 아니다
     *           - {@code TRADE_ALREADY_FINALIZED}(409): 이미 종료된 주문이다
     *           - {@code ORDER_ALREADY_SHIPPED}(409): 발송 정보가 이미 등록되었다
     *           - {@code VALIDATION_ERROR}(400): 반환 후 잔액이 저장 가능한 범위를 넘는다
     * @see IOrderCancellationService#cancelByParty
     */
    @PostMapping("/{orderId}/cancel")
    public OrderDetailResponse cancel(@AuthenticationPrincipal String memberId,
                                      @PathVariable("orderId") Long orderId,
                                      @Valid @RequestBody CancelRequest request) {
        cancellationService.cancelByParty(memberId, orderId, request.reason());
        return orderQueryService.getDetail(memberId, orderId);
    }

    /**
     * 구매자가 상품을 정상적으로 받았다고 확인한다(F-015).
     *
     * 확인하면 거래가 정상 완료로 종료되고, 결제 금액이 판매자 잔액에 판매대금으로 지급된다.
     * 확인은 배송이 완료된 뒤 상품 확인 기한 전까지만 할 수 있다.
     *
     * @param memberId 인증된 회원 ID
     * @param orderId  확인할 주문 ID
     * @return 정상 완료된 주문의 상세
     * @throws com.chatservice.marketplace.common.error.BusinessException
     *           - {@code ORDER_NOT_FOUND}(404): 주문이 없다
     *           - {@code NOT_BUYER}(403): 요청한 회원이 구매자가 아니다
     *           - {@code ORDER_ON_HOLD}(409): 환불 요청으로 보류 중이다
     *           - {@code TRADE_ALREADY_FINALIZED}(409): 이미 종료된 주문이다
     *           - {@code ORDER_NOT_DELIVERED}(409): 아직 배송 완료 전이다
     *           - {@code INSPECTION_PERIOD_ENDED}(409): 상품 확인 기한이 지났다
     *           - {@code VALIDATION_ERROR}(400): 지급 후 판매자 잔액이 저장 가능한 범위를 넘는다
     * @see ITradeCompletionService#confirmReceipt
     */
    @PostMapping("/{orderId}/confirm-receipt")
    public OrderDetailResponse confirmReceipt(@AuthenticationPrincipal String memberId,
                                              @PathVariable("orderId") Long orderId) {
        tradeCompletionService.confirmReceipt(memberId, orderId);
        return orderQueryService.getDetail(memberId, orderId);
    }

    /**
     * 구매자가 환불을 요청하고 거래를 보류 상태로 변경한다(F-017).
     *
     * 요청은 주문당 한 번만 할 수 있으며 수정하거나 철회할 수 없다.
     * 요청이 접수된 시각부터 판매자 응답 기한 48시간이 시작된다.
     * 보류 중에는 구매 확정과 자동 완료가 실행되지 않는다.
     *
     * @param memberId 인증된 회원 ID
     * @param orderId  환불을 요청할 주문 ID
     * @param request  환불 사유 코드, 상세 설명, 선택 값인 요청 식별자
     * @return 201 Created와 보류 상태로 바뀐 주문의 상세
     * @throws org.springframework.web.bind.MethodArgumentNotValidException
     *         사유 코드가 없거나 상세 설명이 비어 있으면 400 {@code VALIDATION_ERROR}로 응답한다.
     *         정의되지 않은 사유 코드는 본문을 읽는 단계에서 400으로 거절된다
     * @throws com.chatservice.marketplace.common.error.BusinessException
     *           - {@code ORDER_NOT_FOUND}(404): 주문이 없다
     *           - {@code NOT_BUYER}(403): 요청한 회원이 구매자가 아니다
     *           - {@code REFUND_ALREADY_REQUESTED}(409): 이미 보류 중이거나 환불 요청이 있다
     *           - {@code TRADE_ALREADY_FINALIZED}(409): 이미 종료된 주문이다
     *           - {@code ORDER_NOT_DELIVERED}(409): 아직 배송 완료 전이다
     *           - {@code INSPECTION_PERIOD_ENDED}(409): 상품 확인 기한이 지났다
     * @see IRefundService#requestRefund
     */
    @PostMapping("/{orderId}/refund-request")
    public ResponseEntity<OrderDetailResponse> requestRefund(@AuthenticationPrincipal String memberId,
                                                             @PathVariable("orderId") Long orderId,
                                                             @Valid @RequestBody RefundRequestBody request) {
        refundService.requestRefund(memberId, orderId, request.reasonCode(), request.detail(), request.requestId());
        return ResponseEntity.status(HttpStatus.CREATED).body(orderQueryService.getDetail(memberId, orderId));
    }

    /**
     * 판매자가 환불 요청에 동의한다(F-018).
     *
     * 동의하면 반품 없이 결제 금액 전액이 구매자 잔액으로 반환되고, 거래가 환불 완료로 종료된다.
     *
     * @param memberId 인증된 회원 ID
     * @param orderId  환불 요청이 있는 주문 ID
     * @return 환불 완료된 주문의 상세
     * @throws com.chatservice.marketplace.common.error.BusinessException
     *         {@link #rejectRefund}와 같은 조건에서 같은 오류 코드로 거절된다.
     *         잔액 범위 검사의 대상만 다르며, 동의할 때는 구매자 잔액을 검사한다
     * @see IRefundService#respond
     */
    @PostMapping("/{orderId}/refund-request/approve")
    public OrderDetailResponse approveRefund(@AuthenticationPrincipal String memberId,
                                             @PathVariable("orderId") Long orderId) {
        refundService.respond(memberId, orderId, true);
        return orderQueryService.getDetail(memberId, orderId);
    }

    /**
     * 판매자가 환불 요청을 거절한다(F-018).
     *
     * 거절하면 거래가 정상 완료로 종료되고, 결제 금액이 판매자 잔액에 판매대금으로 지급된다.
     * 재심이나 이의 제기 절차는 없다.
     *
     * @param memberId 인증된 회원 ID
     * @param orderId  환불 요청이 있는 주문 ID
     * @return 정상 완료된 주문의 상세
     * @throws com.chatservice.marketplace.common.error.BusinessException
     *           - {@code REFUND_REQUEST_NOT_FOUND}(404): 주문이나 환불 요청이 없다
     *           - {@code NOT_SELLER}(403): 요청한 회원이 판매자가 아니다
     *           - {@code REFUND_ALREADY_DECIDED}(409): 이미 판매자가 응답했거나 자동 환불되었다
     *           - {@code ORDER_NOT_ON_HOLD}(409): 주문이 보류 상태가 아니다
     *           - {@code REFUND_RESPONSE_DEADLINE_PASSED}(409): 판매자 응답 기한이 지났다
     *           - {@code VALIDATION_ERROR}(400): 지급 후 판매자 잔액이 저장 가능한 범위를 넘는다
     * @see IRefundService#respond
     */
    @PostMapping("/{orderId}/refund-request/reject")
    public OrderDetailResponse rejectRefund(@AuthenticationPrincipal String memberId,
                                            @PathVariable("orderId") Long orderId) {
        refundService.respond(memberId, orderId, false);
        return orderQueryService.getDetail(memberId, orderId);
    }
}

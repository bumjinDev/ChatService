package com.chatservice.marketplace.order.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.chatservice.marketplace.common.error.BusinessException;
import com.chatservice.marketplace.common.error.ErrorCode;
import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.order.PurchaseOrderRepository;
import com.chatservice.marketplace.order.ShipmentRepository;
import com.chatservice.marketplace.order.domain.OrderEnums.ShippingStatus;
import com.chatservice.marketplace.order.domain.OrderEnums.TradeStatus;
import com.chatservice.marketplace.order.domain.PurchaseOrder;
import com.chatservice.marketplace.order.domain.Shipment;

@Service
public class ShipmentService implements IShipmentService {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentService.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ShipmentRepository shipmentRepository;
    private final TransactionTemplate transactionTemplate;
    private final TimeRules timeRules;
    private final Duration mockDeliveryDuration;

    public ShipmentService(PurchaseOrderRepository purchaseOrderRepository, ShipmentRepository shipmentRepository,
                           TransactionTemplate transactionTemplate, TimeRules timeRules,
                           @Value("${marketplace.mock-delivery.duration}") Duration mockDeliveryDuration) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.shipmentRepository = shipmentRepository;
        this.transactionTemplate = transactionTemplate;
        this.timeRules = timeRules;
        this.mockDeliveryDuration = mockDeliveryDuration;
    }

    /**
     * 판매자가 입력한 발송 정보를 저장하고 주문의 배송 상태를 배송 중(SHIPPING)으로 변경한다.
     *
     * 처리 흐름:
     *   1. 현재 시각을 구한다. 이 값을 발송 기한 검사, 발송 시각, 모의 배송 완료 예정 시각에 함께 사용한다.
     *   2. 주문을 조회한다. 주문이 없으면 {@code ORDER_NOT_FOUND}를 던진다.
     *   3. 요청한 회원이 주문의 판매자인지 확인한다. 판매자가 아니면 {@code NOT_SELLER}를 던진다.
     *   4. 거래 상태가 최종 상태(COMPLETED, REFUNDED, CANCELLED)이면 {@code ORDER_NOT_IN_PROGRESS}를 던진다. 보류(ON_HOLD) 주문은 이 검사를 통과한다.
     *   5. 주문의 발송 정보(SHIPMENT) 행이 이미 있으면 {@code ALREADY_SHIPPED}를 던진다. 기존 발송 정보는 변경하지 않는다.
     *   6. 현재 시각이 발송 기한 이상이면 {@code SHIPMENT_DEADLINE_PASSED}를 던진다. 미발송 자동 취소가 아직 실행되지 않은 주문도 거절한다.
     *   7. 발송 정보를 저장한다. 발송 시각은 현재 시각이고, 모의 배송 완료 예정 시각은 현재 시각에 모의 배송 기간을 더한 값이다.
     *   8. 주문의 배송 상태를 배송 중으로 변경하고, 변경 전후의 배송 상태를 로그로 남긴다.
     *
     * 배송 상태가 발송 대기(WAITING_SHIPMENT)인지 직접 확인하는 검사는 없다.
     * - 배송 상태를 배송 중으로 바꾸는 코드는 이 메서드뿐이며, 같은 트랜잭션에서 발송 정보 행을 함께 저장한다.
     * - 배송 완료와 보류는 배송 중 주문에서만 전환된다.
     * - 그래서 커밋된 데이터에서 배송 중, 배송 완료, 보류 주문은 모두 발송 정보 행이 있고 5번 검사에서 거절된다.
     * - 8번의 {@link PurchaseOrder#markShipped}는 주문이 진행 중·발송 대기 상태가 아니면 {@link IllegalStateException}을 던진다.
     *
     * {@code OrderController.registerShipment}가 호출한다. 컨트롤러에는 트랜잭션이 없으므로 이 메서드의 {@code @Transactional}이 새 트랜잭션을 시작한다.
     * 주문 조회와 발송 정보 존재 확인에는 잠금을 걸지 않는다. SHIPMENT.ORDER_ID에는 UNIQUE 제약이 없다.
     * 같은 주문에 발송 등록 요청이 동시에 처리되는 경우와, 발송 등록과 당사자 취소·미발송 자동 취소가 동시에 처리되는 경우의 제어는 구현되어 있지 않다.
     * 요청 식별자({@code requestId})는 저장만 한다. 같은 요청 식별자로 다시 요청했는지 확인하는 데 사용하지 않는다.
     *
     * @param memberId       요청한 회원 ID. 컨트롤러가 인증 정보에서 꺼내 전달한다.
     * @param orderId        발송 정보를 등록할 주문 ID
     * @param carrierName    택배사명. 요청 DTO에서 비어 있지 않고 100자 이하인지 검증했다.
     * @param trackingNumber 운송장 번호. 요청 DTO에서 비어 있지 않고 100자 이하인지 검증했다.
     * @param requestId      요청 식별자. 선택 값이며 {@code null}일 수 있다.
     * @throws BusinessException
     *           - {@code ORDER_NOT_FOUND}(404): 주문이 없다
     *           - {@code NOT_SELLER}(403): 요청한 회원이 판매자가 아니다
     *           - {@code ORDER_NOT_IN_PROGRESS}(409): 거래 상태가 최종 상태다
     *           - {@code ALREADY_SHIPPED}(409): 발송 정보가 이미 있다
     *           - {@code SHIPMENT_DEADLINE_PASSED}(409): 현재 시각이 발송 기한 이상이다
     * @throws IllegalStateException 주문이 진행 중·발송 대기 상태가 아니면 {@link PurchaseOrder#markShipped}가 던진다.
     */
    @Override
    @Transactional
    public void register(String memberId, Long orderId, String carrierName, String trackingNumber, String requestId) {
        // 1. 현재 시각 조회. 발송 기한 검사, 발송 시각, 모의 배송 완료 예정 시각에 같은 값을 사용한다.
        //    호출 흐름: TimeRules.now()
        Instant now = timeRules.now();
        // 2. 주문 조회. 이 트랜잭션의 영속성 컨텍스트에 엔티티를 적재하며 잠금은 걸지 않는다.
        //    호출 흐름: PurchaseOrderRepository.findById()
        PurchaseOrder order = purchaseOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        // 3. 판매자 확인
        //    호출 흐름: PurchaseOrder.isSeller()
        if (!order.isSeller(memberId)) {
            throw new BusinessException(ErrorCode.NOT_SELLER);
        }
        // 4. 최종 상태 확인. 보류(ON_HOLD) 주문은 통과한다.
        //    호출 흐름: PurchaseOrder.isFinalized() → TradeStatus.isFinal()
        if (order.isFinalized()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_IN_PROGRESS);
        }
        // 5. 발송 정보 중복 확인. 잠금 없이 행 존재 여부만 조회한다.
        //    호출 흐름: ShipmentRepository.existsByOrderId()
        if (shipmentRepository.existsByOrderId(orderId)) {
            throw new BusinessException(ErrorCode.ALREADY_SHIPPED);
        }
        // 6. 발송 기한 확인. 현재 시각이 발송 기한 이상이면 거절한다.
        //    호출 흐름: TimeRules.isBeforeDeadline()
        if (!TimeRules.isBeforeDeadline(now, order.getShipDeadlineAt())) {
            throw new BusinessException(ErrorCode.SHIPMENT_DEADLINE_PASSED);
        }
        // 7. 발송 정보 저장. 식별자 생성 전략이 IDENTITY이므로 save 호출 시점에 INSERT가 실행된다.
        //    호출 흐름: Shipment.register() → ShipmentRepository.save()
        shipmentRepository.save(Shipment.register(orderId, carrierName, trackingNumber, now,
                now.plus(mockDeliveryDuration), requestId));
        // 8. 배송 상태를 배송 중으로 변경. PURCHASE_ORDER의 UPDATE는 커밋 전 플러시에서 변경 감지로 실행된다.
        //    호출 흐름: PurchaseOrder.markShipped() → PurchaseOrder.requireState()
        ShippingStatus before = order.getShippingStatus();
        order.markShipped();
        logger.info("[발송 등록] orderId={}, memberId={}, shippingStatus {} -> {}",
                orderId, memberId, before, order.getShippingStatus());
    }

    /**
     * 모의 배송 완료 예정 시각이 지난 배송 중 주문을 배송 완료로 변경한다.
     *
     * 외부 배송 시스템과 연동하지 않으므로, 발송 등록 뒤 모의 배송 기간이 지나면 배송이 완료된 것으로 처리한다.
     *
     * 처리 흐름:
     *   1. 진행 중(IN_PROGRESS)·배송 중(SHIPPING)이고, 배송 완료 시각이 없으며, 모의 배송 완료 예정 시각이 {@code now} 이하인 주문의 ID 목록을 조회한다. 이 조회는 트랜잭션 밖에서 실행한다.
     *   2. 주문 ID마다 {@link TransactionTemplate}으로 새 트랜잭션을 열고 {@link #deliverIfDue}를 실행한다.
     *   3. 한 건에서 예외가 발생하면 그 건의 트랜잭션만 롤백하고 오류 로그를 남긴 뒤 다음 건을 처리한다.
     *   4. 대상 건수와 배송 완료로 변경한 건수를 로그로 남긴다.
     *
     * 배송 완료는 거래 상태를 바꾸지 않는다. 그래서 거래 완료, 판매대금 지급, 대화 상태 이벤트 전송을 하지 않는다. 대화의 쓰기 가능 여부는 거래 상태로만 정해진다.
     * 이 메서드에는 {@code @Transactional}이 없다. 그래서 2번의 트랜잭션은 주문마다 독립적으로 커밋되거나 롤백된다.
     * 여러 인스턴스에서 동시에 실행되는 경우의 제어는 구현되어 있지 않다.
     *
     * @param now 대상 조회와 상태 재확인에 사용할 현재 시각. {@code TradeScheduler.runJob}이 전달한다.
     * @return 이번 실행에서 배송 완료로 변경한 주문 수
     */
    @Override
    public int completeDueDeliveries(Instant now) {
        // 1. 배송 완료 대상 주문 ID 목록 조회. 트랜잭션 밖에서 실행하며 잠금을 걸지 않는다.
        //    호출 흐름: PurchaseOrderRepository.findIdsByDeliveryDue()
        List<Long> targets = purchaseOrderRepository.findIdsByDeliveryDue(TradeStatus.IN_PROGRESS, ShippingStatus.SHIPPING, now);
        int processed = 0;
        for (Long orderId : targets) {
            try {
                // 2. 주문마다 새 트랜잭션에서 상태를 다시 확인하고 배송 완료로 변경한다. 콜백이 정상 종료하면 커밋하고, 예외를 던지면 롤백한다.
                //    호출 흐름: TransactionTemplate.execute() → deliverIfDue() → PurchaseOrder.markDelivered()
                Boolean delivered = transactionTemplate.execute(status -> deliverIfDue(orderId, now));
                if (Boolean.TRUE.equals(delivered)) {
                    processed++;
                }
            } catch (RuntimeException e) {
                // 3. 한 건의 실패는 오류 로그만 남기고 다음 건으로 넘어간다. 이 건의 트랜잭션은 이미 롤백되었다.
                logger.error("[모의 배송 완료] 처리 실패 orderId={}, reason={}", orderId, e.getMessage());
            }
        }
        // 4. 대상 건수와 배송 완료로 변경한 건수 기록
        logger.info("[모의 배송 완료] 대상={}, 처리={}", targets.size(), processed);
        return processed;
    }

    /**
     * 주문이 아직 모의 배송 완료 조건을 만족하는지 다시 확인하고, 만족하면 배송 완료로 변경한다.
     *
     * 처리 흐름:
     *   1. 주문과 발송 정보를 다시 조회한다.
     *   2. 다음 중 하나라도 해당하면 변경하지 않고 {@code false}를 반환한다.
     *      - 주문 또는 발송 정보가 없다.
     *      - 거래 상태가 진행 중(IN_PROGRESS)이 아니다.
     *      - 배송 상태가 배송 중(SHIPPING)이 아니다.
     *      - 배송 완료 시각이 이미 있다.
     *      - 현재 시각이 모의 배송 완료 예정 시각 전이다.
     *   3. 현재 시각을 배송 완료 시각으로, 그 시각에서 48시간 뒤를 상품 확인 기한으로 저장하고 {@code true}를 반환한다.
     *
     * {@link #completeDueDeliveries}가 연 트랜잭션 안에서 실행한다. 상태 검사는 잠금 없이 조회한 값으로 한다.
     * 배송 완료 시각에는 모의 배송 완료 예정 시각이 아니라 처리한 시각({@code now})을 저장한다.
     * {@link PurchaseOrder#markDelivered}가 배송 상태와 배송 완료 시각을 함께 저장하므로, 커밋된 데이터에서 배송 완료 시각 검사는 배송 상태 검사와 같은 결과를 낸다.
     * 모의 배송 완료 예정 시각 검사는 대상 조회와 같은 {@code now}로 변경되지 않는 {@code deliveryDueAt}을 비교하므로, 대상 조회에 포함된 주문은 이 검사를 항상 통과한다.
     *
     * @param orderId 확인할 주문 ID
     * @param now     배송 완료 여부 판단과 배송 완료 시각에 사용할 현재 시각
     * @return 배송 완료로 변경했으면 {@code true}, 조건을 만족하지 않아 건너뛰었으면 {@code false}
     */
    private boolean deliverIfDue(Long orderId, Instant now) {
        // 1. 주문과 발송 정보 재조회. 잠금은 걸지 않는다.
        //    호출 흐름: PurchaseOrderRepository.findById() → ShipmentRepository.findByOrderId()
        PurchaseOrder order = purchaseOrderRepository.findById(orderId).orElse(null);
        Shipment shipment = shipmentRepository.findByOrderId(orderId).orElse(null);
        // 2. 모의 배송 완료 조건 재확인
        if (order == null || shipment == null
                || order.getTradeStatus() != TradeStatus.IN_PROGRESS
                || order.getShippingStatus() != ShippingStatus.SHIPPING
                || order.getDeliveredAt() != null
                || now.isBefore(shipment.getDeliveryDueAt())) {
            return false;
        }
        // 3. 배송 완료로 변경. 상품 확인 기한은 처리 시각에서 48시간 뒤다.
        //    호출 흐름: TimeRules.inspectionDeadlineAt() → PurchaseOrder.markDelivered()
        order.markDelivered(now, timeRules.inspectionDeadlineAt(now));
        logger.info("[모의 배송 완료] orderId={}, memberId={}, shippingStatus {} -> {}, inspectionDeadlineAt={}",
                orderId, order.getBuyerId(), ShippingStatus.SHIPPING, order.getShippingStatus(),
                order.getInspectionDeadlineAt());
        return true;
    }
}

package com.chatservice.marketplace.order;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatservice.marketplace.order.domain.Shipment;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    /**
     * 주문의 발송 정보를 조회한다.
     *
     * Spring Data JPA가 메서드 이름으로 쿼리를 생성한다. 조건은 {@code orderId}가 일치하는 행이다.
     * 사용하는 곳:
     * - 모의 배송 완료: 모의 배송 완료 예정 시각을 확인한다.
     * - 주문 상세 조회: 발송 정보를 응답에 포함한다.
     * 주문당 발송 정보가 한 행이라는 전제로 {@link Optional}을 반환한다. 같은 주문의 행이 두 개 이상이면 Spring Data JPA가 예외를 던진다.
     *
     * @param orderId 조회할 주문 ID
     * @return 발송 정보. 등록되지 않았으면 빈 {@link Optional}
     */
    Optional<Shipment> findByOrderId(Long orderId);

    /**
     * 주문의 발송 정보 행이 있는지 확인한다.
     *
     * Spring Data JPA가 메서드 이름으로 쿼리를 생성한다. 조건은 {@code orderId}가 일치하는 행이다.
     * 사용하는 곳:
     * - 발송 등록: 발송 정보가 이미 있으면 중복 등록을 거절한다.
     * - 미발송 자동 취소: 발송 정보가 있으면 취소하지 않는다.
     * ORDER_ID 컬럼에 UNIQUE 제약이 없으므로, 주문당 발송 정보 한 행은 이 확인으로만 유지된다.
     *
     * @param orderId 확인할 주문 ID
     * @return 발송 정보가 있으면 {@code true}
     */
    boolean existsByOrderId(Long orderId);
}

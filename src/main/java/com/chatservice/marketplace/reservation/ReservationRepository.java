package com.chatservice.marketplace.reservation;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatservice.marketplace.reservation.domain.Reservation;
import com.chatservice.marketplace.reservation.domain.ReservationStatus;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /* 대화 상세의 예약 표시: 이 상품·구매 희망자의 예약 가운데 예약 ID 가 가장 큰 것 하나 */
    Optional<Reservation> findFirstByProductIdAndBuyerIdOrderByReservationIdDesc(Long productId, String buyerId);

    /* 같은 구매 희망자의 특정 상태 예약. 활성 예약은 보통 하나이고, 경합으로 둘 이상일 수 있다. */
    List<Reservation> findByProductIdAndBuyerIdAndStatus(Long productId, String buyerId, ReservationStatus status);

    /* 구매 가능 수량 계산: 상품 하나의 특정 상태 예약 전체 */
    List<Reservation> findByProductIdAndStatus(Long productId, ReservationStatus status);

    /* 공개 목록의 구매 가능 수량 계산: 여러 상품의 특정 상태 예약을 한 번에 조회한다 */
    List<Reservation> findByProductIdInAndStatus(Collection<Long> productIds, ReservationStatus status);

    /**
     * 만료 시각이 지난 예약의 ID 목록을 조회한다.
     *
     * 조건: 상태가 {@code status}이고 만료 시각이 {@code now} 이하인 예약. 만료 자동 해제는 {@code ACTIVE}를 전달한다.
     * 엔티티가 아니라 ID 만 조회하며, 잠금을 걸지 않는다. 예약 ID 오름차순으로 반환한다.
     *
     * @param status 조회할 상태
     * @param now    기준 시각. 만료 시각이 이 값 이하인 예약을 조회한다.
     * @return 조건에 맞는 예약 ID 목록. 없으면 빈 목록
     */
    @Query("select r.reservationId from Reservation r where r.status = :status and r.expiresAt <= :now order by r.reservationId")
    List<Long> findIdsByExpired(@Param("status") ReservationStatus status, @Param("now") Instant now);
}

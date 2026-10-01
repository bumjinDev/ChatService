package com.chatservice.marketplace.order;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

	Optional<Shipment> findFirstByOrderIdOrderByShipmentIdAsc(Long orderId);

	boolean existsByOrderId(Long orderId);
}

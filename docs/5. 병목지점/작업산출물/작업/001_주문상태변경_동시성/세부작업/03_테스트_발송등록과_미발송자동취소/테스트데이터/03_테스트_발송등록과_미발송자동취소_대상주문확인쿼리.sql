-- 요청 B(미발송 자동 취소)와 테스트케이스2의 이후 자동 처리(모의 배송 완료, 확인 기간 만료 자동 완료)의 대상이 될 주문이
-- 이번 테스트케이스의 주문 하나뿐인지 확인한다. SQL Developer의 TOYCHAT_TEST 접속에서 실행한다.
-- 세 조회의 조건은 각 자동 처리가 대상 주문의 ID 목록을 조회하는 조건(PurchaseOrderRepository의
-- findIdsByDeadlinePassed, findIdsByDeliveryDue, findIdsByInspectionExpired)과 같다.
-- 시각은 작업명세 04 「2.2 기준 시각」과 「4.2.3」의 API에 넣는 값과 같은 값이다.

-- 1. 요청 B(미발송 자동 취소, now=2026-10-12T15:00:05Z)의 대상
--    테스트케이스2와 테스트케이스3의 테스트 전에 각각 실행한다. 결과는 그 테스트케이스의 주문 하나여야 한다.
SELECT ORDER_ID, BUYER_ID, SELLER_ID, TRADE_STATUS, SHIPPING_STATUS, SHIP_DEADLINE_AT
  FROM PURCHASE_ORDER
 WHERE TRADE_STATUS = 'IN_PROGRESS'
   AND SHIPPING_STATUS = 'WAITING_SHIPMENT'
   AND SHIP_DEADLINE_AT <= TIMESTAMP '2026-10-12 15:00:05'
 ORDER BY ORDER_ID;

-- 2. 모의 배송 완료(now=2026-10-12T15:02:00Z)의 대상
--    테스트케이스2의 이후 자동 처리 전에 실행한다. 결과는 테스트케이스2의 주문 하나여야 한다.
SELECT o.ORDER_ID, o.TRADE_STATUS, o.SHIPPING_STATUS, o.DELIVERED_AT, s.DELIVERY_DUE_AT
  FROM PURCHASE_ORDER o
  JOIN SHIPMENT s ON s.ORDER_ID = o.ORDER_ID
 WHERE o.TRADE_STATUS = 'IN_PROGRESS'
   AND o.SHIPPING_STATUS = 'SHIPPING'
   AND o.DELIVERED_AT IS NULL
   AND s.DELIVERY_DUE_AT <= TIMESTAMP '2026-10-12 15:02:00'
 ORDER BY o.ORDER_ID;

-- 3. 확인 기간 만료 자동 완료(now=상품 확인 기한, 예상 2026-10-14T15:02:00Z)의 대상
--    테스트케이스2의 이후 자동 처리 전에 실행한다. 테스트케이스2의 주문은 모의 배송 완료 뒤에야 배송 완료가 되므로,
--    이때는 결과가 없어야 한다. 모의 배송 완료 뒤의 주문 조회 응답에서 얻은 상품 확인 기한이 예상과 다르면 그 값으로 바꾼다.
SELECT ORDER_ID, TRADE_STATUS, SHIPPING_STATUS, INSPECTION_DEADLINE_AT
  FROM PURCHASE_ORDER
 WHERE TRADE_STATUS = 'IN_PROGRESS'
   AND SHIPPING_STATUS = 'DELIVERED'
   AND INSPECTION_DEADLINE_AT <= TIMESTAMP '2026-10-14 15:02:00'
 ORDER BY ORDER_ID;

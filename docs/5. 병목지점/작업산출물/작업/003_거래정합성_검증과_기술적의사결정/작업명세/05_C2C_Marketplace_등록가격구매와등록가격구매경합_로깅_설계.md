# C2C Marketplace 등록 가격 구매–등록 가격 구매 경합 로깅 설계

- 작성일: 2026-11-12
- 작업기록: [작업기록](../작업기록.md)
- 상태: 설계. 코드에는 아직 반영하지 않았다.

## 1. 기록 방식

### 1.1 애플리케이션 로그와 p6spy 기록

애플리케이션 로그는 지점마다 한 줄씩 남긴다. 같은 스레드에서 두 지점 사이에 남은 p6spy 기록은 그 구간의 DB 기록으로 본다. 요청과 스레드는 요청수신 로그의 스레드 항목으로 연결한다. 같은 스레드에서 요청수신부터 요청응답 사이에 남은 p6spy 기록이 그 요청의 것이다.

애플리케이션 로그에는 p6spy 기록에 남지 않는 값을 남긴다.

- SELECT로 읽어 확인에 쓴 값과 판단 결과: 남은 수량, 유효 예약 합계, 구매 가능 수량, 잔액
- 저장할 때 DB가 정한 주문 ID
- 메모리에서 바꿔 커밋할 때 쓸 값: 차감 후 남은 수량, 상품 상태
- 롤백의 실패 사유, 응답의 오류 코드

### 1.2 애플리케이션 로그를 남기지 않는 것

- **처리 기준 시각**: 주문 INSERT의 구매 확정 시각으로 p6spy 기록에 남는다.
- **본인 상품 확인, 판매 중 확인, 같은 구매자의 유효 예약 확인**: 거절되면 롤백완료의 실패사유와 요청응답의 오류코드로 남는다.
- **잔액 차감, 재고 차감, 결제로 끝낼 예약 처리, 대화 확인과 생성, 대화 상태 이벤트 등록**: SQL은 p6spy 기록으로 확인하고, 재고를 차감한 결과는 차감처리완료 로그로 확인한다.
- **테스트용 중지 위치 두 곳**: 이번 테스트에서 쓰지 않는다. 중지 헤더가 없는 요청은 바로 다음 처리로 넘어가므로 코드를 그대로 둔다.
- **커밋 뒤 대화 상태 이벤트 전송**: DB를 바꾸지 않는다. JMeter는 실시간 연결을 열지 않으므로 받을 화면도 없다.

## 2. 로깅 추가 위치

애플리케이션 로그의 모든 줄에는 시각, 테스트명, 기능명, 파일명, 메소드명, 실행ID, 요청ID, 단계가 공통으로 들어간다.

| 순서 | 지점 | 단계 이름 | 기록할 항목 |
|---|---|---|---|
| 1 | 요청 수신 | 요청수신 | HTTP메소드, 경로, 스레드 |
| 2 | 결제 처리의 맨 앞(트랜잭션 시작 뒤, 첫 SQL 전) | 결제시작 | 상품ID, 구매자ID, 요청수량, 제안ID |
| 3 | 남은 수량 확인 | 남은수량판단 | 상품ID, 구매자ID, 요청수량, 남은수량, 판단결과(통과·거절) |
| 4 | 구매 가능 수량 확인 | 구매가능수량판단 | 상품ID, 구매자ID, 요청수량, 남은수량, 유효예약합계, 구매가능수량, 판단기준시각, 판단결과(통과·거절) |
| 5 | 잔액 확인 | 잔액판단 | 구매자ID, 결제금액, 잔액, 판단결과(통과·거절) |
| 6 | 주문 저장 직후 | 주문저장처리완료 | 주문ID, 상품ID, 구매자ID, 주문수량, 결제금액 |
| 7 | 잔액과 재고를 차감한 직후 | 차감처리완료 | 주문ID, 주문수량, 차감후남은수량, 상품상태 |
| 8 | 결제 처리가 값을 돌려주기 직전 | 커밋전 | 주문ID |
| 9 | 커밋·롤백이 끝난 뒤 | 커밋완료, 롤백완료, 확정여부미확인 | 상품ID, 구매자ID. 롤백완료와 확정여부미확인에는 실패사유를 더한다 |
| 10 | 요청 응답 | 요청응답 | 응답상태, 오류코드, 소요시간ms |
| 11 | 구매 요청 18건이 모두 끝난 뒤, 요청마다(JMeter가 실행 폴더에 기록) | 요청별종료후확인 | 구매자ID, 요청수량, 응답상태, 오류코드, 응답구분(성공·수량부족·기타오류·미확인), 주문ID, 주문수량, 결제금액, 거래상태, 잔액, 주문잔액일치 |
| 12 | 구매 요청 18건이 모두 끝난 뒤, 실행 전체(JMeter가 실행 폴더에 기록) | 전체종료후확인 | 주문수량합계, 주문건수, 남은수량, 상품상태, 성공수, 수량부족수, 기타오류수, 미확인수, 결과확인기준위반 |

- 사건 하나를 로그 한 줄로 남긴다.
- 항목은 「이름=값」으로 쓰고, 같은 뜻의 항목은 모든 지점에서 같은 이름을 쓴다. 남은수량, 유효예약합계, 요청수량, 판단기준시각, 판단결과는 예약–예약 경합의 수량판단 로그와 같은 이름이다.
- 단계 이름은 위 표에 있는 이름만 쓴다.
- 「| 실행ID=」가 들어 있는 줄만 뽑으면 이 테스트의 로그만 나온다.
- 실행 ID 헤더와 요청 ID 헤더가 없는 요청에서는 어느 지점도 기록하지 않는다.
- 요청별종료후확인과 전체종료후확인은 서버가 아니라 JMeter가 실행 폴더의 파일에 같은 형식으로 쓴다. 구매 요청이 모두 끝났는지와 어떤 요청의 응답을 받지 못했는지는 JMeter만 알기 때문이다. 로그를 뽑는 스크립트는 서버 로그와 이 파일을 함께 읽는다.

### 2.1 요청 하나에서 로그가 남는 순서

결제가 성공한 요청에서는 다음 순서로 기록된다. 「앞 로그부터 이 로그까지」의 두 열은 바로 앞 로그와 이 로그 사이에 같은 스레드에서 실행되는 처리와, 그 사이에 p6spy 기록에 남는 SQL이다.

| 순서 | 단계 이름 | 기록하는 곳 | 앞 로그부터 이 로그까지 하는 처리 | 앞 로그부터 이 로그까지 p6spy에 남는 SQL |
|---|---|---|---|---|
| 1 | 요청수신 | 필터 | | |
| 2 | 결제시작 | 결제 처리 | 로그인 확인(토큰을 확인하는 정보를 Redis에서 조회), 요청 본문 검사, 트랜잭션 시작 | 없음 |
| 3 | 남은수량판단 | 결제 처리 | 처리 기준 시각 구하기, 상품 조회, 본인 상품·판매 중 확인 | 상품 조회 |
| 4 | 구매가능수량판단 | 결제 처리 | 구매 가능 수량 계산 | 상품의 예약 조회 |
| 5 | 잔액판단 | 결제 처리 | 같은 구매자의 유효 예약 확인, 결제 금액 계산, 잔액 조회 | 같은 구매자의 예약 조회, 지갑 조회 |
| 6 | 주문저장처리완료 | 결제 처리 | 주문 저장 | 주문 INSERT |
| 7 | 차감처리완료 | 결제 처리 | 잔액 차감과 잔액 변동 내역 저장, 메모리에서 재고 차감 | 잔액 변동 내역 INSERT |
| 8 | 커밋전 | 결제 처리 | 결제로 끝낼 예약 처리, 대화 확인과 생성, 대화 상태 이벤트 등록 | 같은 구매자의 예약 조회, 대화 조회, 대화 INSERT(대화가 없을 때), 상품의 대화 조회, 대화마다 예약 조회, 대화마다 주문 조회(상품이 판매 종료로 바뀌었을 때) |
| 9 | 커밋완료 | 트랜잭션이 끝난 뒤 호출되는 콜백 | 트랜잭션 관리자의 커밋 | 상품 UPDATE, 지갑 UPDATE, COMMIT |
| 10 | 요청응답 | 필터 | 새 읽기 전용 트랜잭션에서 응답 본문 조회, 응답 쓰기 | 주문, 상품, 회원 닉네임, 발송 정보, 환불 요청, 잔액 변동 내역 조회와 그 트랜잭션의 커밋 |

- 요청수신부터 결제시작까지는 SQL이 없다. 하지만 트랜잭션을 시작할 때 DB 연결을 얻으므로(추론), 연결을 기다린 시간이 이 구간에 들어간다. 이 구간에는 로그인 확인 시간도 들어 있으므로, 연결 대기만 볼 때는 같은 스레드의 기존 인증 성공 로그부터 결제시작까지를 본다.
- 차감처리완료부터 커밋전까지에는 대화마다 실행되는 예약 조회가 모인다. 조회 횟수는 이 트랜잭션에서 보이는 상품의 대화 수와 같다.
- 커밋전부터 커밋완료까지에는 상품 UPDATE, 지갑 UPDATE, COMMIT이 들어 있다. 다른 요청이 같은 상품 행을 먼저 UPDATE하고 아직 커밋하지 않았으면, 상품 UPDATE는 그 커밋을 기다리고 기다린 시간은 상품 UPDATE의 실행 시간에 남는다고 추론한다. 실행한 뒤 p6spy 기록으로 확인한다.
- 커밋할 때의 SQL 순서(상품 UPDATE, 지갑 UPDATE, COMMIT)는 2026-10-15 작업 001 실행의 p6spy 기록에서 확인한 순서다. 지금 코드에서도 같은지는 실행한 뒤 확인한다.

거절된 요청은 다음과 같이 남는다.

| 거절된 곳 | 남는 로그 |
|---|---|
| 남은 수량 부족 | 요청수신, 결제시작, 남은수량판단(판단결과=거절), 롤백완료(실패사유), 요청응답(409, 오류코드) |
| 구매 가능 수량 부족 | 요청수신부터 구매가능수량판단(판단결과=거절)까지, 그 뒤 롤백완료(실패사유), 요청응답(409, 오류코드) |
| 잔액 부족 | 요청수신부터 잔액판단(판단결과=거절)까지, 그 뒤 롤백완료(실패사유), 요청응답(409, 오류코드) |
| 상품 없음, 본인 상품, 판매 종료, 같은 구매자의 유효 예약 | 요청수신, 결제시작, 거절된 확인까지의 판단 로그, 롤백완료(실패사유), 요청응답(오류 응답, 오류코드) |

- 거절된 요청에서는 판단 로그와 롤백완료 사이에 ROLLBACK이 p6spy 기록에 남는다.
- 남은 수량을 0으로 만든 구매가 커밋한 뒤에 상품을 읽은 요청은, 남은수량판단 앞의 판매 중 확인에서 판매 종료로 거절된다.

### 2.2 로그 줄의 기능명·파일명·메소드명

| 단계 | 기능명 | 파일명 | 메소드명 |
|---|---|---|---|
| 요청수신, 요청응답 | 요청 수신과 응답 | TradeTestLogFilter.java | doFilterInternal |
| 결제시작, 남은수량판단, 구매가능수량판단, 잔액판단, 주문저장처리완료, 차감처리완료, 커밋전, 커밋완료, 롤백완료, 확정여부미확인 | 결제 | OrderService.java | placeOrder |
| 요청별종료후확인, 전체종료후확인 | 결제 | JMeter 파일 이름 | 구매 경합 뒤 저장 상태 확인 |

## 3. 지점별 코드 위치

「현재 코드」는 해당 파일에서 줄 번호와 함께 그대로 발췌한 것이고, 「고친 뒤 코드」는 로그를 넣은 뒤의 코드다. 「고친 뒤 코드」에서 `+`가 붙은 줄은 새로 넣는 줄이고, `-`가 붙은 줄은 그 자리에서 지우는 줄이다. 파일 경로는 `src/main/java/com/chatservice/marketplace/` 아래 경로다.

결제 처리 본문은 「3.9 커밋완료, 롤백완료, 확정여부미확인」에서 예외 처리 블록으로 감싸므로, 실제로는 들여쓰기가 한 단계 깊어진다. 아래 「고친 뒤 코드」는 들여쓰기가 깊어진 모양으로 적었다.

### 3.1 요청수신, 요청응답

- **기록하는 곳**: 필터가 기록한다. 예약–예약 경합에서 넣은 코드를 고치지 않고 쓴다.
- **위치**: `testsupport/TradeTestLogFilter.java` `doFilterInternal`. 요청수신은 51~53줄, 요청응답은 61~70줄이다.
- **오류 코드를 보관하는 곳**: 업무 오류를 응답으로 바꾸는 처리(`common/error/ApiExceptionHandler.java` 37줄)와 요청 값 형식 오류를 응답으로 바꾸는 처리(같은 파일 85줄)가 오류 코드를 MDC에 보관한다. 이 코드도 고치지 않는다.

### 3.2 커밋·롤백 결과 기록 등록과 결제시작

- **넣을 곳**: 결제 처리의 맨 앞이다. 트랜잭션은 이미 시작되었고, 첫 SQL(상품 조회)은 아직 실행되지 않은 자리다.
- **커밋·롤백 결과 기록 등록**: 여기서 등록하므로 상품이 없어 거절된 요청에도 롤백완료가 남는다. 등록할 때 넘기는 항목은 등록할 때 알 수 있는 상품ID와 구매자ID다. 주문ID는 이때 알 수 없다.
- **결제시작의 값**: 모두 요청 본문과 로그인한 구매자 ID에서 가져온다. 등록 가격 구매에는 제안ID가 없으므로 「-」로 남긴다.
- **위치**: `order/service/OrderService.java` `placeOrder`, 87~101줄. import는 27~28줄 다음에 한 줄을 더한다.

현재 코드:

```java
27	import com.chatservice.marketplace.testsupport.PausePoint;
28	import com.chatservice.marketplace.testsupport.TestPause;
...
87	    @Override
88	    @Transactional
89	    public Long placeOrder(String buyerId, PlaceOrderRequest request) {
90	        /*
91	         * [포트폴리오 작업 대상] 동시 결제 시 재고·잔액 정합성
...
98	         */
99	        // 결제 요청이 도착해서 과정을 시작하는 시각. 구매 확정 시각, 발송 기한 계산, 잔액 내역 시각에 같은 값을 쓴다
100	        Instant now = timeRules.now();
101	        long quantity = request.quantity();
```

고친 뒤 코드(28줄 다음, 89줄 다음):

```java
import com.chatservice.marketplace.testsupport.TestPause;
+import com.chatservice.marketplace.testsupport.TradeTestLog;
...
    public Long placeOrder(String buyerId, PlaceOrderRequest request) {
+       TradeTestLog.registerTransactionEnd(logger, "결제", "OrderService.java", "placeOrder",
+               "상품ID", request.productId(), "구매자ID", buyerId);
+       TradeTestLog.event(logger, "결제", "OrderService.java", "placeOrder", "결제시작",
+               "상품ID", request.productId(), "구매자ID", buyerId, "요청수량", request.quantity(),
+               "제안ID", request.offerId() == null ? "-" : request.offerId());
+       try {
            /*
             * [포트폴리오 작업 대상] 동시 결제 시 재고·잔액 정합성
            ... 기존 주석 그대로 ...
             */
            // 결제 요청이 도착해서 과정을 시작하는 시각. ...
            Instant now = timeRules.now();
            long quantity = request.quantity();
```

### 3.3 남은수량판단

- **넣을 곳**: 판매 중 확인을 통과한 뒤, 남은 수량을 비교하는 줄 바로 앞이다. 판단 결과를 기록한 다음, 기존 비교와 예외 처리가 그대로 실행된다.
- **값**: 남은수량은 이 요청이 조회한 상품 객체의 값이고, 다음 줄의 비교에 쓰는 값과 같다.
- **위치**: `order/service/OrderService.java` `placeOrder`, 109~116줄

현재 코드:

```java
109	        // 판매 종료 상품은 구매할 수 없다
110	        if (!product.isOnSale()) {
111	            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
112	        }
113	        // 남은 수량을 넘는 구매는 거절한다
114	        if (quantity > product.getRemainingQuantity()) {
115	            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
116	        }
```

고친 뒤 코드(112줄 다음):

```java
            if (!product.isOnSale()) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
            }
+           TradeTestLog.event(logger, "결제", "OrderService.java", "placeOrder", "남은수량판단",
+                   "상품ID", product.getProductId(), "구매자ID", buyerId, "요청수량", quantity,
+                   "남은수량", product.getRemainingQuantity(),
+                   "판단결과", quantity > product.getRemainingQuantity() ? "거절" : "통과");
            // 남은 수량을 넘는 구매는 거절한다
            if (quantity > product.getRemainingQuantity()) {
```

### 3.4 구매가능수량판단

- **넣을 곳**: 구매 가능 수량을 계산한 뒤, 비교하기 전이다. 지금은 계산과 비교가 한 줄에 있으므로, 계산 결과를 변수에 받도록만 바꾼다. 계산을 부르는 횟수와 비교식은 바꾸지 않는다.
- **값**: 남은수량, 유효예약합계, 판단기준시각은 구매 가능 수량 계산이 실제 계산에 쓴 값이다. 예약–예약 경합에서 넣은 코드(`reservation/service/ReservationQueryService.java` 73줄)가 이 세 값을 MDC에 보관하므로, 그 값을 꺼낸다.
  - 판단기준시각은 구매 가능 수량 계산이 새로 구한 시각이다. 결제 처리가 처음 구해 구매 확정 시각에 쓰는 시각(100줄)과는 다른 값이다.
  - 구매 가능 수량 계산은 같은 요청 안에서 대화 상태 이벤트를 만들 때 대화 수만큼 다시 호출되고, 그때마다 MDC에 보관한 값이 바뀐다. 이 로그는 첫 계산 바로 다음 줄에서 값을 꺼내므로 판단에 쓴 값이 기록된다.
- **위치**: `order/service/OrderService.java` `placeOrder`, 117~121줄

현재 코드:

```java
117	        // 남은 수량 안이지만 다른 회원에게 걸린 활성 예약 때문에 구매 가능 수량을 넘는 구매는 거절한다.
118	        // 결제하는 회원 본인에게 걸린 예약은 빼지 않으므로 예약된 회원은 예약 수량까지 결제할 수 있다.
119	        if (quantity > reservationQueryService.availableQuantity(product, buyerId)) {
120	            throw new BusinessException(ErrorCode.PRODUCT_RESERVED);
121	        }
```

고친 뒤 코드(119줄):

```java
            // 남은 수량 안이지만 다른 회원에게 걸린 활성 예약 때문에 구매 가능 수량을 넘는 구매는 거절한다.
            // 결제하는 회원 본인에게 걸린 예약은 빼지 않으므로 예약된 회원은 예약 수량까지 결제할 수 있다.
-           if (quantity > reservationQueryService.availableQuantity(product, buyerId)) {
+           long available = reservationQueryService.availableQuantity(product, buyerId);
+           TradeTestLog.event(logger, "결제", "OrderService.java", "placeOrder", "구매가능수량판단",
+                   "상품ID", product.getProductId(), "구매자ID", buyerId, "요청수량", quantity,
+                   "남은수량", TradeTestLog.calculatedRemaining(),
+                   "유효예약합계", TradeTestLog.calculatedReservedSum(), "구매가능수량", available,
+                   "판단기준시각", TradeTestLog.calculatedAt(), "판단결과", quantity > available ? "거절" : "통과");
+           if (quantity > available) {
                throw new BusinessException(ErrorCode.PRODUCT_RESERVED);
            }
```

### 3.5 잔액판단

- **넣을 곳**: 잔액을 조회한 뒤, 결제 금액과 비교하기 전이다. 이것도 조회 결과를 변수에 받도록만 바꾼다. 이 지점 바로 뒤가 모든 확인을 통과한 직후의 테스트용 중지 위치다.
- **값**: 결제금액은 바로 위 161줄에서 계산한 값이다. 잔액은 이 요청이 지갑에서 읽은 값이고, 지갑이 없으면 0이다.
- **위치**: `order/service/OrderService.java` `placeOrder`, 160~167줄

현재 코드:

```java
160	        // 총 결제액 = 단가 × 수량. 저장 범위를 넘으면 VALIDATION_ERROR
161	        long paidAmount = AmountRules.totalAmount(unitPrice, quantity, "quantity");
162	        // 잔액 사전 검사. 지갑이 없으면 잔액 0 으로 본다
163	        if (walletService.currentBalance(buyerId) < paidAmount) {
164	            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
165	        }
166	        // 겹침 재현 테스트의 중지 위치. 상품·예약·제안·지갑을 읽어 모든 검사를 통과한 직후, 주문을 저장하기 전이다. 테스트 설정이 꺼져 있거나 테스트 헤더가 없는 요청은 그대로 진행한다.
167	        testPause.reach(PausePoint.PAYMENT_CHECKED);
```

고친 뒤 코드(163줄):

```java
            // 잔액 사전 검사. 지갑이 없으면 잔액 0 으로 본다
-           if (walletService.currentBalance(buyerId) < paidAmount) {
+           long balance = walletService.currentBalance(buyerId);
+           TradeTestLog.event(logger, "결제", "OrderService.java", "placeOrder", "잔액판단",
+                   "구매자ID", buyerId, "결제금액", paidAmount, "잔액", balance,
+                   "판단결과", balance < paidAmount ? "거절" : "통과");
+           if (balance < paidAmount) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
            }
```

### 3.6 주문저장처리완료

- **넣을 곳**: 주문을 저장한 직후, 잔액을 차감하기 전이다.
- **값**: 주문ID는 저장할 때 DB가 정한 값이며, 저장한 주문 객체에서 꺼낸다.
- 이 기록이 남아도 아직 커밋 전이므로 주문이 확정된 것은 아니다. 확정은 커밋완료로 확인한다.
- **위치**: `order/service/OrderService.java` `placeOrder`, 171~177줄

현재 코드:

```java
171	        // 주문 내역 데이터 생성(테이블 "PURCHASE_ORDER") : 주문 생성(발송 대기, 진행 중). IDENTITY 채번이라 저장 시점에 INSERT 되어 주문 ID 가 정해진다
172	        PurchaseOrder order = purchaseOrderRepository.save(PurchaseOrder.place(product.getProductId(), buyerId,
173	                product.getSellerId(), quantity, unitPrice, paidAmount, priceSource, offerId,
174	                request.recipientName(), request.shippingAddress(), now, timeRules.shipDeadlineAt(now),
175	                request.requestId()));
176	        // 구매자의 잔액 차감(테이블 "WALLET") : 잔액 차감과 PURCHASE 내역 기록. 내역이 주문 ID 를 참조하므로 주문 생성 뒤에 호출한다
177	        walletService.debitForPurchase(buyerId, paidAmount, order.getOrderId(), now);
```

고친 뒤 코드(175줄 다음):

```java
            PurchaseOrder order = purchaseOrderRepository.save(PurchaseOrder.place(product.getProductId(), buyerId,
                    ...
                    request.requestId()));
+           TradeTestLog.event(logger, "결제", "OrderService.java", "placeOrder", "주문저장처리완료",
+                   "주문ID", order.getOrderId(), "상품ID", product.getProductId(), "구매자ID", buyerId,
+                   "주문수량", quantity, "결제금액", paidAmount);
            // 구매자의 잔액 차감(테이블 "WALLET") : ...
            walletService.debitForPurchase(buyerId, paidAmount, order.getOrderId(), now);
```

### 3.7 차감처리완료

- **넣을 곳**: 재고를 차감한 직후, 결제로 끝낼 예약을 처리하기 전이다.
- **값**: 차감후남은수량과 상품상태는 재고 차감이 메모리의 상품 객체에서 바꾼 값이다. 커밋할 때 상품 UPDATE가 이 값을 DB에 쓴다. 남은 수량이 0이 되었으면 상품상태는 판매 종료(SOLD)다.
- **위치**: `order/service/OrderService.java` `placeOrder`, 186~189줄

현재 코드:

```java
186	        // 주문 수량만큼 재고 차감. 남은 수량이 0 이 되면 판매 종료로 바뀐다
187	        productService.deductStock(product, quantity, now);
188	        // 결제한 회원에게 걸린 활성 예약(만료 전)은 이 결제로 끝난다. 예약 수량보다 적게 결제해도 끝난다
189	        reservationService.fulfillOnPayment(product, buyerId, order.getOrderId(), now);
```

고친 뒤 코드(187줄 다음):

```java
            productService.deductStock(product, quantity, now);
+           TradeTestLog.event(logger, "결제", "OrderService.java", "placeOrder", "차감처리완료",
+                   "주문ID", order.getOrderId(), "주문수량", quantity,
+                   "차감후남은수량", product.getRemainingQuantity(), "상품상태", product.getStatus());
            // 결제한 회원에게 걸린 활성 예약(만료 전)은 이 결제로 끝난다. ...
            reservationService.fulfillOnPayment(product, buyerId, order.getOrderId(), now);
```

### 3.8 커밋전

- **넣을 곳**: 결제 처리가 주문 ID를 돌려주기 바로 전이다. 대화 상태 이벤트 등록과 커밋 직전의 테스트용 중지 위치가 이 앞에 있다.
- **이 지점의 의미**: 결제 처리의 본문이 모두 끝났지만 아직 커밋하지 않은 시점이다. 메서드가 값을 돌려준 뒤에 트랜잭션 관리자가 커밋하므로, 이 기록은 커밋 성공을 뜻하지 않는다.
- **위치**: `order/service/OrderService.java` `placeOrder`, 198~203줄

현재 코드:

```java
198	        // 상품의 모든 대화에 보낼 상태 이벤트를 등록한다. 실제 전송은 커밋 후에 한다
199	        conversationService.publishStateToProductConversations(product);
200	        // 잠금 대기 관찰용 중지 위치. 여기까지 실행된 UPDATE의 행 잠금을 유지한 채 커밋 직전에 멈춘다. flush를 호출하지 않으므로 SQL 실행 순서는 바꾸지 않는다.
201	        testPause.reach(PausePoint.PAYMENT_BEFORE_COMMIT);
202	        // 메서드가 반환되면 트랜잭션이 커밋된다
203	        return order.getOrderId();
```

고친 뒤 코드(202줄과 203줄 사이):

```java
            testPause.reach(PausePoint.PAYMENT_BEFORE_COMMIT);
            // 메서드가 반환되면 트랜잭션이 커밋된다
+           TradeTestLog.event(logger, "결제", "OrderService.java", "placeOrder", "커밋전",
+                   "주문ID", order.getOrderId());
            return order.getOrderId();
```

### 3.9 커밋완료, 롤백완료, 확정여부미확인

이 지점에 관련된 코드는 세 곳이다.

- **커밋·롤백 결과 기록 등록**: 결제 처리의 맨 앞에서 등록한다(「3.2 커밋·롤백 결과 기록 등록과 결제시작」).
- **실패 사유 보관**: 결제 처리 본문 전체를 예외 처리 블록으로 감싼다. 예외가 메서드 밖으로 나가기 직전에 실패 사유를 MDC에 보관하고, 같은 예외를 그대로 다시 던진다. 업무 오류이면 오류 코드를, 그 밖의 예외이면 예외 이름을 보관한다. 트랜잭션 관리자는 예외가 메서드 밖으로 나간 뒤에 롤백하고 롤백완료를 기록하므로, 이 순서여야 롤백완료에 실패 사유가 남는다. 기존 본문은 들여쓰기만 한 단계 깊어지고, 처리 문장과 주석은 그대로다.
- **커밋·롤백 결과를 기록하는 메서드**: 예약–예약 경합에서 넣은 코드를 고치지 않고 쓴다(`testsupport/TradeTestLog.java` `registerTransactionEnd`, 156~180줄). DB 커밋이 끝나면 커밋완료를, 롤백이 끝나면 롤백완료와 실패사유를, 결과를 알 수 없으면 확정여부미확인을 남긴다.
- **위치**: `order/service/OrderService.java` `placeOrder`, 89줄과 202~204줄. 블록은 89줄 다음에 넣는 두 기록 뒤에서 시작해, 90줄부터 203줄의 return까지 감싼다.

현재 코드:

```java
202	        // 메서드가 반환되면 트랜잭션이 커밋된다
203	        return order.getOrderId();
204	    }
```

고친 뒤 코드:

```java
            // 메서드가 반환되면 트랜잭션이 커밋된다
            ... 「3.8 커밋전」의 기록 ...
            return order.getOrderId();
+       } catch (RuntimeException e) {
+           TradeTestLog.recordFailure(e);   // 업무 오류이면 오류 코드, 그 밖의 예외이면 예외 이름
+           throw e;                         // 같은 예외를 그대로 다시 던짐
+       }
    }
```

### 3.10 요청별종료후확인, 전체종료후확인

이 두 지점은 JMeter 파일에서 기록하므로 애플리케이션 코드에는 넣을 곳이 없다.

구매 요청 18건이 모두 끝난 뒤, 「구매 경합 뒤 저장 상태 확인」 그룹이 다음 두 가지를 조회한다. 그 결과로 두 종료 후 확인을 서버 로그와 같은 형식으로 실행 폴더의 `종료후확인.log`에 쓴다.

- 판매자의 대화 상세 18건: 구매자별 주문, 상품의 남은 수량과 판매 상태
- 등록 가격 구매자의 잔액 조회 18건

**공통 칸**

| 칸 | 값 |
|---|---|
| 테스트명 | 등록 가격 구매–등록 가격 구매 |
| 기능명 | 결제 |
| 파일명 | JMeter 파일 이름 |
| 메소드명 | 구매 경합 뒤 저장 상태 확인 |
| 요청ID | 요청별종료후확인은 등록 가격 구매자 번호에 맞춘 `buy-01`~`buy-18`, 전체종료후확인은 「-」 |
| 응답을 받지 못한 요청의 응답상태 | 「응답없음(예외 이름)」 |

**응답구분**

| 응답구분 | 조건 |
|---|---|
| 성공 | 201 |
| 수량부족 | 409이고 오류 코드가 남은 수량 부족(`INSUFFICIENT_STOCK`), 구매 가능 수량 부족(`PRODUCT_RESERVED`), 판매 종료(`PRODUCT_NOT_ON_SALE`) 가운데 하나 |
| 기타오류 | 그 밖의 HTTP 응답 |
| 미확인 | HTTP 응답을 받지 못함 |

판매 종료 거절도 수량부족에 넣는다. 이 테스트에서 상품을 판매 종료로 바꾸는 것은 남은 수량을 0으로 만든 구매뿐이기 때문이다. 세 가지 거절은 오류코드 항목으로 나눠 볼 수 있다.

**요청별종료후확인(18줄)**

| 항목 | 값 |
|---|---|
| 구매자ID, 요청수량 | 준비 단계에서 정한 값 |
| 응답상태, 오류코드, 응답구분 | 구매 요청의 응답과 위 응답구분 |
| 주문ID, 주문수량, 결제금액, 거래상태 | 판매자의 대화 상세에 있는 그 구매자의 주문. 없으면 「-」 |
| 잔액 | 그 구매자의 잔액 조회 결과 |
| 주문잔액일치 | 주문이 있으면 잔액이 「충전 금액 − 결제금액」이고, 주문이 없으면 잔액이 충전 금액이면 「일치」. 그 밖에는 「불일치」 |

**전체종료후확인(1줄)**

| 항목 | 값 |
|---|---|
| 주문수량합계, 주문건수 | 18건의 대화 상세에 있는 주문의 수량 합계와 건수 |
| 남은수량, 상품상태 | 판매자의 대화 상세에 있는 상품의 저장 값 |
| 성공수, 수량부족수, 기타오류수, 미확인수 | 응답구분별 건수 |
| 결과확인기준위반 | 아래 위반을 「; 」로 이어 쓴다. 없으면 「없음」 |

결과확인기준위반에 기록하는 위반은 다음과 같다.

- 주문 수량 합계가 판매 수량 50개를 넘음
- 상품의 남은 수량이 판매 수량에서 주문 수량 합계를 뺀 값과 다름
- 상품 상태가 남은 수량과 맞지 않음
- 성공한 구매에 주문이 없거나 주문 수량이 요청 수량과 다름(구매자ID)
- 주문과 잔액이 맞지 않음(구매자ID)
- 수량 부족으로 거절된 요청에 주문이 남음(구매자ID)
- 저장 상태를 읽지 못한 대화나 잔액 조회 있음

## 4. 바꾸지 않는 것

- 기존 「결제 성공」, 「재고 차감」, 「구매 차감」 로그 줄
- 검사 순서, 결제 금액과 구매 가능 수량의 계산식, 트랜잭션 범위
- 대화 생성과 대화 상태 이벤트 등록, 테스트용 중지 위치 두 곳
- 공통 로그 파일 두 개, 구매 가능 수량 계산, 오류 응답 처리. 예약–예약 경합에서 넣은 코드를 그대로 쓴다.

고치는 파일은 결제 처리(`order/service/OrderService.java`) 하나다.

# C2C Marketplace 예약–예약 경합 로깅 설계

- 작성일: 2026-11-10
- 작업기록: [작업기록](../작업기록.md)
- 상태: 설계. 코드에는 아직 반영하지 않았다.

## 1. 기록 방식

### 1.1 애플리케이션 로그와 p6spy 기록

서버가 DB에 보낸 SQL과 커밋·롤백은 p6spy 기록으로 확인하고, p6spy 기록에 남지 않는 값만 공통 형식의 애플리케이션 로그로 남긴다.

p6spy 기록 하나에는 다음 정보가 남는다.

- 스레드 이름
- 시각
- SQL 실행에 걸린 시간
- 구분(SQL 실행, 커밋, 롤백)
- 연결 번호
- 바인드 값을 넣은 SQL

| 확인할 것 | 확인하는 기록 |
|---|---|
| 요청 하나가 보낸 SQL의 순서와 각 SQL의 실행 시간 | p6spy 기록 |
| 대화 상태 이벤트를 만들려고 반복하는 예약 조회(수락 한 건에 18회)의 건수와 시간 | p6spy 기록. 예약 INSERT부터 커밋까지의 기록 |
| 가격 제안 상태를 바꾸는 UPDATE가 실행되는 시점 | p6spy 기록. UPDATE가 커밋 직전에 있는지 확인한다 |
| 커밋·롤백 시각과 트랜잭션 경과 시간 | p6spy 기록. 첫 SQL 기록과 커밋·롤백 기록의 시각 차이 |
| DB 연결 대기 | p6spy 기록과 기존 인증 로그. 같은 스레드에서 인증이 성공한 시각부터 첫 SQL이 시작된 시각까지로 추정한다. 연결 대기 시간을 직접 기록하지는 않는다 |
| 수량 판단에 실제로 쓴 남은 수량, 유효 예약 합계, 판단 결과 | 애플리케이션 로그. p6spy 기록에는 SELECT로 읽은 값이 남지 않는다 |
| 저장한 예약의 ID | 애플리케이션 로그. 예약 ID는 저장할 때 DB가 정하므로 p6spy 기록의 INSERT에는 남지 않는다 |
| 롤백의 실패 사유, 응답의 오류 코드 | 애플리케이션 로그 |
| 요청과 p6spy 기록의 연결 | 요청수신 로그에 스레드 이름을 남긴다. 같은 스레드에서 요청수신부터 요청응답 사이에 남은 p6spy 기록이 그 요청의 것이다 |

### 1.2 애플리케이션 로그를 남기지 않는 것

- **가격 제안 이벤트 등록과 커밋 뒤 이벤트 전송**: 가격 제안 이벤트는 수락이 커밋된 뒤 서버가 구매자의 대화 화면에 가격 제안이 수락되었다고 알리는 메시지다. 이 이벤트는 DB를 바꾸지 않고, JMeter는 실시간 연결을 열지 않으므로 받을 화면도 없다.
- **테스트용 중지·재개**: 이번 테스트에서 쓰지 않는다. 중지 위치의 코드는 요청에 중지 헤더가 없으면 바로 다음 처리로 넘어가므로 그대로 둔다.
- **트랜잭션 시작, 상품 조회, 가격 제안 상태 변경, 대화 상태 이벤트 등록, 트랜잭션 경과 시간**: p6spy 기록으로 확인한다.

## 2. 로깅 추가 위치

애플리케이션 로그의 모든 줄에는 시각, 테스트명, 기능명, 파일명, 메소드명, 실행ID, 요청ID, 단계가 공통으로 들어간다.

| 순서 | 지점 | 단계 이름 | 기록할 항목 |
|---|---|---|---|
| 1 | 요청 수신 | 요청수신 | HTTP메소드, 경로, 스레드 |
| 2 | 가격 제안 조회 직후 | 수락시작 | 상품ID, 제안ID, 대화ID, 판매자ID, 구매자ID, 요청수량, 제안상태 |
| 3 | 수량 계산과 비교 | 수량판단 | 상품ID, 제안ID, 구매자ID, 요청수량, 남은수량, 유효예약합계, 예약가능수량, 판단기준시각, 판단결과(통과·거절) |
| 4 | 예약 저장 직후 | 예약저장처리완료 | 예약ID, 제안ID, 상품ID, 구매자ID, 예약수량, 예약시각, 만료시각 |
| 5 | 수락 처리가 값을 돌려주기 직전 | 커밋전 | 제안ID, 제안상태, 예약ID, 예약수량 |
| 6 | 커밋·롤백이 끝난 뒤 | 커밋완료, 롤백완료, 확정여부미확인 | 제안ID. 롤백완료와 확정여부미확인에는 실패사유를 더한다 |
| 7 | 요청 응답 | 요청응답 | 응답상태, 오류코드, 소요시간ms |
| 8 | 수락 요청 18건이 모두 끝난 뒤, 요청마다(JMeter가 실행 폴더에 기록) | 요청별사후확인 | 제안ID, 구매자ID, 요청수량, 응답상태, 오류코드, 응답구분(성공·수량부족·기타오류·미확인), 제안상태, 예약ID, 예약수량, 예약상태, 수락예약일치 |
| 9 | 수락 요청 18건이 모두 끝난 뒤, 실행 전체(JMeter가 실행 폴더에 기록) | 전체사후확인 | 유효예약합계, 남은수량, 주문수량, 성공수, 수량부족수, 기타오류수, 미확인수, 결과확인기준위반 |

- 사건 하나를 로그 한 줄로 남긴다.
- 항목은 「이름=값」으로 쓰고, 같은 뜻의 항목은 모든 지점에서 같은 이름을 쓴다.
- 단계 이름은 위 표에 있는 이름만 쓴다.
- 「| 실행ID=」가 들어 있는 줄만 뽑으면 이 테스트의 로그만 나온다.
- 실행 ID 헤더와 요청 ID 헤더가 없는 요청에서는 어느 지점도 기록하지 않는다.
- 요청별사후확인과 전체사후확인은 서버가 아니라 JMeter가 실행 폴더의 파일에 같은 형식으로 쓴다. 수락 요청이 모두 끝났는지와 어떤 요청의 응답을 받지 못했는지는 JMeter만 알기 때문이다. 로그를 뽑는 스크립트는 서버 로그와 이 파일을 함께 읽는다.

### 2.1 요청 하나에서 로그가 남는 순서

수락이 성공한 요청에서는 다음 순서로 기록된다. 「직전에 실행되는 SQL」 열은 그 기록 직전에 같은 스레드의 p6spy 기록에 남는 SQL이다.

| 순서 | 단계 이름 | 기록하는 곳 | 직전에 실행되는 SQL |
|---|---|---|---|
| 1 | 요청수신 | 필터 | |
| 2 | 수락시작 | 수락 처리 | 가격 제안 조회 |
| 3 | 수량판단 | 예약 생성 처리 | 상품 조회, 대화 조회, 같은 구매자의 예약 조회, 상품의 예약 조회 |
| 4 | 예약저장처리완료 | 예약 생성 처리 | 예약 INSERT |
| 5 | 커밋전 | 수락 처리 | 대화 상태 이벤트를 만들기 위한 조회 19건 |
| 6 | 커밋완료 | 트랜잭션이 끝난 뒤 호출되는 콜백 | 가격 제안 UPDATE, COMMIT(추론) |
| 7 | 요청응답 | 필터 | |

수량이 부족해 거절되는 요청은 요청수신, 수락시작, 수량판단(판단결과=거절)까지 같다. 그 뒤 예약저장처리완료와 커밋전 없이 롤백완료(실패사유)와 요청응답(409, 오류코드)이 남는다.

### 2.2 로그 줄의 기능명·파일명·메소드명

| 단계 | 기능명 | 파일명 | 메소드명 |
|---|---|---|---|
| 요청수신, 요청응답 | 요청 수신과 응답 | TradeTestLogFilter.java | doFilterInternal |
| 수락시작, 커밋전, 커밋완료, 롤백완료, 확정여부미확인 | 가격 제안 수락 | OfferService.java | respond |
| 수량판단, 예약저장처리완료 | 가격 제안 수락 | ReservationService.java | reserve |

## 3. 지점별 코드 위치

「현재 코드」는 해당 파일에서 줄 번호와 함께 그대로 발췌한 것이고, 「고친 뒤 코드」는 로그를 넣은 뒤의 코드다. 「고친 뒤 코드」에서 `+`가 붙은 줄이 새로 넣는 줄이다. 공통 로그 파일 두 개(`TradeTestLog.java`, `TradeTestLogFilter.java`)는 2026-11-10에 지웠으므로, 두 파일의 코드는 「지우기 전 코드」로 발췌했다. 파일 경로는 `src/main/java/com/chatservice/marketplace/` 아래 경로다.

### 3.1 요청수신

- **넣을 곳**: 필터가 요청 헤더의 테스트명·실행 ID·요청 ID를 MDC에 보관한 직후다. MDC는 로깅 라이브러리(SLF4J)가 제공하는, 스레드마다 따로 있는 「이름=값」 저장 공간이다. 이 자리의 요청수신 기록 줄에 스레드 이름 항목만 더한다. 스레드 이름은 p6spy 기록을 이 요청과 연결하는 데 쓴다.
- **필터의 동작**
  - 서버에 들어오는 HTTP 요청을 가장 먼저 받는다.
  - 테스트 설정이 켜져 있을 때만 서버에 등록된다.
  - 등록된 뒤에도, JMeter가 붙이는 실행 ID 헤더와 요청 ID 헤더가 모두 있는 요청만 처리하고, 나머지 요청은 기록하지 않고 통과시킨다.
  - 로그인 확인보다 먼저 실행된다. 처리 순서는 다음과 같다.
    1. 요청을 받으면 헤더의 테스트명·실행 ID·요청 ID를 MDC에 보관하고 요청수신을 기록한다.
    2. 로그인 확인, 수락 처리, 응답 생성이 모두 끝나 응답 코드가 정해지면 요청응답을 기록한다.
    3. MDC에 보관한 값을 지운다.
- **위치**: `testsupport/TradeTestLogFilter.java` `doFilterInternal`, 45~52줄(지우기 전 코드)

지우기 전 코드:

```java
45	    @Override
46	    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
47	            throws ServletException, IOException {
48	        long startedNanos = System.nanoTime();
49	        TradeTestLog.begin(request.getHeader(TradeTestLog.TEST_NAME_HEADER),
50	                request.getHeader(TradeTestLog.RUN_ID_HEADER), request.getHeader(TradeTestLog.REQUEST_ID_HEADER));
51	        TradeTestLog.event(logger, FEATURE, FILE, METHOD, "요청수신",
52	                "HTTP메소드", request.getMethod(), "경로", request.getRequestURI());
```

고친 뒤 코드(51~52줄):

```java
        TradeTestLog.event(logger, FEATURE, FILE, METHOD, "요청수신",
                "HTTP메소드", request.getMethod(), "경로", request.getRequestURI(),
+               "스레드", Thread.currentThread().getName());
```

### 3.2 수락시작

- **넣을 곳**: 수락 처리에서 첫 조회인 가격 제안 조회 바로 다음, 판매자 확인 바로 앞이다. 기록하는 값은 모두 방금 조회한 가격 제안 객체에 들어 있다. 같은 메서드가 거절 요청도 처리하므로, 수락 요청일 때만 기록한다.
- **위치**: `offer/service/OfferService.java` `respond`, 119~128줄

현재 코드:

```java
119	    @Override
120	    @Transactional
121	    public OfferResponse respond(String memberId, Long offerId, boolean accept) {
122	        // 가격 제안 조회(테이블 "PRICE_OFFER"). 없으면 OFFER_NOT_FOUND
123	        PriceOffer offer = priceOfferRepository.findById(offerId)
124	                .orElseThrow(() -> new BusinessException(ErrorCode.OFFER_NOT_FOUND));
125	        // 가격 제안을 받은 판매자 본인만 응답할 수 있다
126	        if (!offer.getSellerId().equals(memberId)) {
127	            throw new BusinessException(ErrorCode.NOT_SELLER);
128	        }
```

고친 뒤 코드(124줄 다음):

```java
        PriceOffer offer = priceOfferRepository.findById(offerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OFFER_NOT_FOUND));
+       if (accept) {
+           TradeTestLog.event(logger, "가격 제안 수락", "OfferService.java", "respond", "수락시작",
+                   "상품ID", offer.getProductId(), "제안ID", offer.getOfferId(), "대화ID", offer.getConversationId(),
+                   "판매자ID", offer.getSellerId(), "구매자ID", offer.getBuyerId(),
+                   "요청수량", offer.getQuantity(), "제안상태", offer.getStatus());
+       }
        // 가격 제안을 받은 판매자 본인만 응답할 수 있다
        if (!offer.getSellerId().equals(memberId)) {
```

### 3.3 수량판단

- **넣을 곳**: 예약 생성 처리에서 예약 가능 수량을 받는 줄과 요청 수량을 비교하는 줄 사이다. 판단 결과를 기록한 다음, 기존 비교와 예외 처리가 그대로 실행된다.
- **위치**: `reservation/service/ReservationService.java` `reserve`, 116~124줄

현재 코드:

```java
116	        // 더 걸 수 있는 예약 수량 = 상품의 남은 수량 − 이 상품에서 만료 전인 예약 수량 전부. 잠금 없이 조회한 값으로 계산한다
117	        // 상품 조회·결제와 같이 쓰는 계산이라 회원 ID 를 받지만, 판매자 본인에게 걸린 예약은 없으므로 판매자 ID 를 넘기면 만료 전인 예약을 누구 것이든 모두 남은 수량에서 뺀다
118	        long limit = reservationQueryService.availableQuantity(product, memberId);
119	        // 더 걸 수 있는 예약 수량을 넘는 예약은 거절한다. 예외로 트랜잭션이 롤백되어 가격 제안의 수락도 반영되지 않는다
120	        if (quantity > limit) {
121	            throw new BusinessException(ErrorCode.RESERVATION_QUANTITY_EXCEEDED);
122	        }
123	        // 겹침 재현 테스트의 중지 위치. 같은 구매자의 예약과 수량 상한 검사를 통과한 직후, 예약을 저장하기 전이다. 테스트 설정이 꺼져 있거나 테스트 헤더가 없는 요청은 그대로 진행한다.
124	        testPause.reach(PausePoint.RESERVATION_CHECKED);
```

고친 뒤 코드(118줄 다음):

```java
        long limit = reservationQueryService.availableQuantity(product, memberId);
+       TradeTestLog.event(logger, "가격 제안 수락", "ReservationService.java", "reserve", "수량판단",
+               "상품ID", product.getProductId(), "제안ID", offerId, "구매자ID", conversation.getBuyerId(),
+               "요청수량", quantity, "남은수량", TradeTestLog.calculatedRemaining(),
+               "유효예약합계", TradeTestLog.calculatedReservedSum(), "예약가능수량", limit,
+               "판단기준시각", TradeTestLog.calculatedAt(), "판단결과", quantity > limit ? "거절" : "통과");
        // 더 걸 수 있는 예약 수량을 넘는 예약은 거절한다. ...
        if (quantity > limit) {
```

#### 수량 계산에 쓴 값을 수량판단 기록에 넘기는 방법

수량판단 로그에는 남은수량, 유효예약합계, 판단기준시각을 남겨야 한다. 그런데 이 세 값은 수량 계산 처리 안에서만 쓰이고, 수량 계산 처리는 예약 가능 수량 하나만 돌려준다. 그래서 예약 생성 처리는 세 값을 MDC를 거쳐 받는다.

1. 수량 계산 처리가 결과를 돌려주기 직전에 세 값을 MDC에 보관한다.
2. 예약 생성 처리가 수량판단 로그를 남길 때 MDC에서 세 값을 꺼낸다.
3. 요청이 끝나면 필터가 MDC의 값을 지운다.

요청 하나는 처음부터 끝까지 같은 스레드에서 처리되므로, 수량 계산 처리에서 보관한 값을 예약 생성 처리에서 꺼낼 수 있다. 다른 요청의 스레드와는 저장 공간이 나뉘어 있어서 값이 섞이지 않는다. 실행 ID, 요청 ID, 오류 코드도 MDC에 보관한다.

값을 얻으려고 다시 조회하지 않는다. 계산식도 바꾸지 않고, 계산 결과를 변수에 담았다가 돌려주도록만 바꾼다.

- **위치**: `reservation/service/ReservationQueryService.java` `availableQuantity`, 55~72줄

현재 코드:

```java
55	    @Override
56	    @Transactional(readOnly = true)
57	    public long availableQuantity(Product product, String viewerId) {
58	        // 처리 기준 시각. 예약의 만료 여부를 이 시각으로 확인한다
59	        Instant now = timeRules.now();
60	
61	        // 이 상품의 예약 가운데 상태가 예약 중(ACTIVE)인 예약을 모두 가져온다(테이블 "RESERVATION")
62	        long reserved = reservationRepository.findByProductIdAndStatus(product.getProductId(), ReservationStatus.ACTIVE).stream()
63	                // 조회하는 회원의 구매를 막는 예약만 고른다 : 만료 전이고, 조회하는 회원 본인에게 걸린 예약이 아닌 것
64	                .filter(reservation -> blocksViewer(reservation, viewerId, now))
65	                // 고른 예약마다 예약 수량을 꺼낸다
66	                .mapToLong(Reservation::getQuantity)
67	                // 꺼낸 수량을 모두 더한다
68	                .sum();
69	
70	        // 남은 수량에서 더한 수량을 뺀 값이 구매 가능 수량이다
71	        return product.getRemainingQuantity() - reserved;
72	    }
```

고친 뒤 코드(71줄):

```java
        // 남은 수량에서 더한 수량을 뺀 값이 구매 가능 수량이다
+       long available = product.getRemainingQuantity() - reserved;
+       TradeTestLog.rememberQuantityCalculation(product.getRemainingQuantity(), reserved, now);
+       return available;
```

- 수량 계산은 같은 요청 안에서 예약을 저장한 뒤에도 대화 상태 이벤트를 만들려고 18번 더 호출되고, 그때마다 MDC에 보관한 값이 바뀐다.
- 수량판단 기록은 첫 호출 바로 다음 줄에서 값을 꺼내므로, 판단에 쓴 값이 기록된다.
- 판단기준시각은 수량 계산 처리가 새로 구한 시각이다. 예약 생성 처리가 처음에 구해 예약시각과 만료시각에 쓰는 시각(`ReservationService.java` 89줄)과는 다른 값이다.

#### MDC에 세 값을 보관하고 꺼내는 메서드

MDC에 보관하는 값의 이름 목록에 세 값의 이름을 더하고, 실패 사유를 보관하는 메서드 다음에 세 값을 보관하는 메서드와 꺼내는 메서드를 넣는다. 요청이 끝날 때 MDC의 값을 지우는 메서드에서 세 값도 함께 지운다.

- 35~39줄: MDC에 보관하는 값의 이름 목록. 세 값의 이름을 더한다.
- 61~74줄: 요청이 끝날 때 MDC의 값을 지우는 메서드가 있는 곳. 세 값도 지우게 한다.
- 89~101줄 다음: 세 값을 보관하는 메서드와 꺼내는 메서드를 넣는 자리.
- **위치**: `testsupport/TradeTestLog.java`, 35~39줄, 61~74줄, 89~101줄(지우기 전 코드)

지우기 전 코드:

```java
35	    private static final String MDC_TEST_NAME = "tradeTestLog.testName";
36	    private static final String MDC_RUN_ID = "tradeTestLog.runId";
37	    private static final String MDC_REQUEST_ID = "tradeTestLog.requestId";
38	    private static final String MDC_ERROR_CODE = "tradeTestLog.errorCode";
39	    private static final String MDC_FAILURE = "tradeTestLog.failure";
...
67	    /** 지금 스레드에 묶은 값을 모두 지운다. 다음 요청이 같은 스레드를 다시 쓸 때 값이 섞이지 않게 한다. */
68	    static void end() {
69	        MDC.remove(MDC_TEST_NAME);
70	        MDC.remove(MDC_RUN_ID);
71	        MDC.remove(MDC_REQUEST_ID);
72	        MDC.remove(MDC_ERROR_CODE);
73	        MDC.remove(MDC_FAILURE);
74	    }
...
93	    public static void recordFailure(RuntimeException e) {
94	        if (!isActive()) {
95	            return;
96	        }
97	        String reason = (e instanceof BusinessException business)
98	                ? business.getErrorCode().name()
99	                : e.getClass().getSimpleName();
100	        MDC.put(MDC_FAILURE, clean(reason));
101	    }
```

고친 뒤 코드:

```java
// 39줄 다음
+   private static final String MDC_CALC_REMAINING = "tradeTestLog.calcRemaining";
+   private static final String MDC_CALC_RESERVED_SUM = "tradeTestLog.calcReservedSum";
+   private static final String MDC_CALC_AT = "tradeTestLog.calcAt";

// 73줄 다음(end 안)
+       MDC.remove(MDC_CALC_REMAINING);
+       MDC.remove(MDC_CALC_RESERVED_SUM);
+       MDC.remove(MDC_CALC_AT);

// 101줄 다음
+   /** 수량 계산에 쓴 남은 수량, 유효 예약 합계, 판단 기준 시각을 적어 둔다. 테스트 요청이 아니면 아무것도 하지 않는다. */
+   public static void rememberQuantityCalculation(long remaining, long reservedSum, Instant at) {
+       if (!isActive()) {
+           return;
+       }
+       MDC.put(MDC_CALC_REMAINING, String.valueOf(remaining));
+       MDC.put(MDC_CALC_RESERVED_SUM, String.valueOf(reservedSum));
+       MDC.put(MDC_CALC_AT, String.valueOf(at));
+   }
+
+   public static String calculatedRemaining()   { return valueOrNone(MDC_CALC_REMAINING); }
+   public static String calculatedReservedSum() { return valueOrNone(MDC_CALC_RESERVED_SUM); }
+   public static String calculatedAt()          { return valueOrNone(MDC_CALC_AT); }
+
+   private static String valueOrNone(String key) {
+       String value = MDC.get(key);
+       return value == null ? NONE : value;
+   }
```

### 3.4 예약저장처리완료

- **넣을 곳**: 예약을 저장하고 기존 「예약 걸기」 로그를 남긴 다음, 대화 상태 이벤트 등록 바로 앞이다. 예약 ID는 저장할 때 DB가 정한 값이며, 저장한 예약 객체에서 꺼낸다.
- **위치**: `reservation/service/ReservationService.java` `reserve`, 126~137줄

현재 코드:

```java
126	        /* 예약이 안되는 기준을 전부 통과 후에 실제로 예약을 생성하는 위치 */
127	
128	        // 예약 내역 데이터 생성(테이블 "RESERVATION") : 예약 생성(예약 중). 만료 시각은 예약 시각에 예약 기간을 더한 값이다. IDENTITY 채번이라 저장 시점에 INSERT 되어 예약 ID 가 정해진다
129	        Reservation reservation = reservationRepository.save(Reservation.reserve(product.getProductId(), conversationId,
130	                conversation.getBuyerId(), memberId, offerId, quantity, now, now.plus(reservationDuration)));
131	
132	        logger.info("[예약 걸기] reservationId={}, conversationId={}, productId={}, memberId={}, offerId={}, quantity={}, expiresAt={}",
133	                reservation.getReservationId(), conversationId, product.getProductId(), memberId, offerId, quantity,
134	                reservation.getExpiresAt());
135	
136	        // 상품의 모든 대화에 보낼 상태 이벤트를 등록한다. 다른 대화의 구매 가능 수량이 바뀌기 때문이다. 실제 전송은 커밋 후에 한다
137	        conversationService.publishStateToProductConversations(product);
```

고친 뒤 코드(134줄 다음):

```java
        logger.info("[예약 걸기] ...", ...);
+       TradeTestLog.event(logger, "가격 제안 수락", "ReservationService.java", "reserve", "예약저장처리완료",
+               "예약ID", reservation.getReservationId(), "제안ID", offerId, "상품ID", product.getProductId(),
+               "구매자ID", conversation.getBuyerId(), "예약수량", quantity,
+               "예약시각", reservation.getReservedAt(), "만료시각", reservation.getExpiresAt());

        // 상품의 모든 대화에 보낼 상태 이벤트를 등록한다. ...
        conversationService.publishStateToProductConversations(product);
```

### 3.5 커밋전

- **넣을 곳**: 수락 처리 메서드가 값을 돌려주기 바로 전이다. 168줄 `return` 바로 위다.
- **이 지점의 의미**
  - 수락 처리의 본문(가격 제안 상태 변경, 예약 저장, 대화 상태 이벤트와 가격 제안 이벤트 등록)이 모두 끝났지만, 아직 커밋하지 않은 시점이다.
  - 메서드가 값을 돌려준 뒤에 트랜잭션 관리자가 커밋한다.
  - 이 기록은 커밋 성공을 뜻하지 않는다. 그래서 단계 이름을 「커밋전」으로 정했다.
- **기록 항목**
  - 제안ID
  - 제안상태: 수락 처리가 바꾼 가격 제안 객체의 상태다. 아직 DB에 반영되기 전의 값이며, 수락이면 수락 상태(ACCEPTED)다.
  - 예약ID, 예약수량
- 수락 요청일 때만 기록한다. 거절 요청이나 중간에 예외가 난 요청은 이 줄에 오지 않는다.
- **이 지점으로 볼 수 있는 것**: 「커밋전」부터 「커밋완료」까지의 시간이다. 이 시간에는 커밋할 때 실행되는 가격 제안 UPDATE와 COMMIT이 들어 있다고 추론한다. 실행한 뒤에는 같은 스레드의 p6spy 기록에서 이 두 SQL이 이 사이에 있는지 확인할 수 있다.
- **위치**: `offer/service/OfferService.java` `respond`, 161~169줄

현재 코드:

```java
161	            reservation = reservationService.reserve(memberId,
162	                    offer.getConversationId(), offer.getQuantity(), offer.getOfferId());
163	        }
164	
165	        // 대화 상대(구매 희망자)의 세션에 보낼 가격 제안 이벤트를 등록한다. 실제 전송은 커밋 후에 한다
166	        publish(offer, memberId);
167	        // 메서드가 반환되면 트랜잭션이 커밋된다
168	        return OfferResponse.of(offer, reservation);
169	    }
```

고친 뒤 코드(167줄과 168줄 사이):

```java
        // 대화 상대(구매 희망자)의 세션에 보낼 가격 제안 이벤트를 등록한다. 실제 전송은 커밋 후에 한다
        publish(offer, memberId);
        // 메서드가 반환되면 트랜잭션이 커밋된다
+       if (accept) {
+           TradeTestLog.event(logger, "가격 제안 수락", "OfferService.java", "respond", "커밋전",
+                   "제안ID", offer.getOfferId(), "제안상태", offer.getStatus(),
+                   "예약ID", reservation.reservationId(), "예약수량", reservation.quantity());
+       }
        return OfferResponse.of(offer, reservation);
```

「3.6 커밋완료, 롤백완료, 확정여부미확인」에서 수락 처리 본문을 예외 처리 블록으로 감싸므로, 실제로는 들여쓰기가 한 단계 더 깊어진다.

### 3.6 커밋완료, 롤백완료, 확정여부미확인

이 지점에 관련된 코드는 세 곳이다.

- **커밋·롤백 결과 기록 등록**: 수락 처리 본문의 맨 앞, 가격 제안 조회보다 앞에서 등록한다. 맨 앞에서 등록해야 가격 제안이 없어서 실패한 요청에도 롤백완료가 남는다. 등록할 때 함께 넘기는 항목은 제안ID다.
- **실패 사유 보관**: 수락 처리 본문 전체를 예외 처리 블록으로 감싼다. 예외가 메서드 밖으로 나가기 직전에 실패 사유를 MDC에 보관하고, 같은 예외를 그대로 다시 던진다. 트랜잭션 관리자는 예외가 메서드 밖으로 나간 뒤에 롤백하고 롤백완료 기록을 호출하므로, 이 순서여야 롤백완료에 실패 사유가 남는다. 기존 본문은 들여쓰기만 한 단계 깊어지고, 처리 문장과 주석은 그대로다.
- **커밋·롤백 결과를 기록하는 메서드**: 고치지 않는다.
  - DB 커밋이 끝나면 커밋완료를 남긴다.
  - 롤백이 끝나면 롤백완료와 실패사유를 남긴다.
  - 결과를 알 수 없으면 확정여부미확인을 남긴다.
  - 커밋하는 중에 난 오류처럼 실패 사유를 보관하지 못한 경우에는 실패사유에 「미상」이 남는다.
- **위치**: `offer/service/OfferService.java` `respond`, 119~124줄과 161~169줄. 그 사이 125~160줄도 블록 안에 들어간다.

현재 코드:

```java
119	    @Override
120	    @Transactional
121	    public OfferResponse respond(String memberId, Long offerId, boolean accept) {
122	        // 가격 제안 조회(테이블 "PRICE_OFFER"). 없으면 OFFER_NOT_FOUND
123	        PriceOffer offer = priceOfferRepository.findById(offerId)
124	                .orElseThrow(() -> new BusinessException(ErrorCode.OFFER_NOT_FOUND));
...
161	            reservation = reservationService.reserve(memberId,
162	                    offer.getConversationId(), offer.getQuantity(), offer.getOfferId());
163	        }
164	
165	        // 대화 상대(구매 희망자)의 세션에 보낼 가격 제안 이벤트를 등록한다. 실제 전송은 커밋 후에 한다
166	        publish(offer, memberId);
167	        // 메서드가 반환되면 트랜잭션이 커밋된다
168	        return OfferResponse.of(offer, reservation);
169	    }
```

고친 뒤 코드:

```java
    @Override
    @Transactional
    public OfferResponse respond(String memberId, Long offerId, boolean accept) {
+       if (accept) {
+           TradeTestLog.registerTransactionEnd(logger, "가격 제안 수락", "OfferService.java", "respond",
+                   "제안ID", offerId);
+       }
+       try {
            // 가격 제안 조회(테이블 "PRICE_OFFER"). 없으면 OFFER_NOT_FOUND
            PriceOffer offer = priceOfferRepository.findById(offerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.OFFER_NOT_FOUND));
            ... 기존 125~167줄(「3.2 수락시작」과 「3.5 커밋전」의 기록 포함), 들여쓰기만 한 단계 깊어짐 ...
            return OfferResponse.of(offer, reservation);
+       } catch (RuntimeException e) {
+           TradeTestLog.recordFailure(e);   // 업무 오류이면 오류 코드, 그 밖의 예외이면 예외 이름
+           throw e;                         // 같은 예외를 그대로 다시 던짐
+       }
    }
```

#### 커밋·롤백 결과를 기록하는 메서드

- **위치**: `testsupport/TradeTestLog.java` `registerTransactionEnd`, 130~154줄(지우기 전 코드)

이 메서드는 「커밋완료」를 실제 DB 커밋이 끝난 뒤에 남긴다. 수락 처리 메서드의 마지막 줄에서 「커밋완료」를 남기면, 그 시점에는 아직 커밋 전이므로 커밋이 실패해도 성공처럼 기록되기 때문이다.

Spring의 트랜잭션 관리자는 트랜잭션이 끝난 뒤 호출할 콜백을 등록받았다가, 커밋이나 롤백을 마친 뒤 호출한다. 이 메서드는 그 콜백을 등록한다.

```java
130	    public static void registerTransactionEnd(Logger logger, String feature, String file, String method, Object... items) {
131	        if (!isActive() || !TransactionSynchronizationManager.isSynchronizationActive()) {
132	            return;
133	        }
134	        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
135	            @Override
136	            public void afterCommit() {
137	                event(logger, feature, file, method, "커밋완료", items);
138	            }
139	
140	            @Override
141	            public void afterCompletion(int status) {
142	                if (status == STATUS_COMMITTED) {
143	                    return;
144	                }
145	                String stage = (status == STATUS_ROLLED_BACK) ? "롤백완료" : "확정여부미확인";
146	                String failure = MDC.get(MDC_FAILURE);
147	                Object[] withFailure = new Object[items.length + 2];
148	                System.arraycopy(items, 0, withFailure, 0, items.length);
149	                withFailure[items.length] = "실패사유";
150	                withFailure[items.length + 1] = (failure == null) ? "미상(커밋 처리 중 오류 또는 사유를 적지 못한 예외)" : failure;
151	                event(logger, feature, file, method, stage, withFailure);
152	            }
153	        });
154	    }
```

줄별로 하는 일은 다음과 같다.

| 줄 | 하는 일 |
|---|---|
| 131~133 | 테스트 요청이 아니거나 진행 중인 트랜잭션이 없으면 아무것도 하지 않는다. |
| 134 | 트랜잭션 관리자에 트랜잭션이 끝난 뒤 호출할 콜백을 등록한다. 등록만 하고 이때는 실행하지 않는다. |
| 136~138 | DB 커밋이 끝난 직후에 트랜잭션 관리자가 호출한다. 「커밋완료」 줄을 남긴다. |
| 141~144 | 커밋이든 롤백이든 트랜잭션이 끝나면 호출된다. 커밋이면 위에서 이미 기록했으므로 아무것도 하지 않는다. |
| 145~151 | 롤백이면 「롤백완료」를, 결과를 알 수 없으면 「확정여부미확인」을 남긴다. MDC에 보관한 실패 사유를 함께 붙인다. |

기존 코드에서는 가격 제안 이벤트와 대화 상태 이벤트를 커밋 뒤에만 보내는 처리가 같은 방식으로 콜백을 등록한다(`conversation/realtime/RealtimePublisher.java` 61~67줄).

```java
61	        if (TransactionSynchronizationManager.isSynchronizationActive()) {
62	            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
63	                @Override
64	                public void afterCommit() {
65	                    send.run();
66	                }
67	            });
```

### 3.7 요청응답

- **기록하는 곳**: 필터가 기록하며, 이 부분은 고치지 않는다. 필터는 응답 코드가 정해진 뒤 응답상태, 오류코드, 소요시간ms를 기록한다. 처리되지 않은 예외가 필터 밖으로 나가면 응답상태 대신 「예외전파」와 예외 이름을 남긴다.
- **위치**: `testsupport/TradeTestLogFilter.java` `doFilterInternal`, 53~71줄(지우기 전 코드)

```java
53	        Throwable propagated = null;
54	        try {
55	            chain.doFilter(request, response);
56	        } catch (IOException | ServletException | RuntimeException e) {
57	            propagated = e;
58	            throw e;
59	        } finally {
60	            long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000;
61	            if (propagated == null) {
62	                TradeTestLog.event(logger, FEATURE, FILE, METHOD, "요청응답",
63	                        "응답상태", response.getStatus(), "오류코드", TradeTestLog.errorCode(), "소요시간ms", elapsedMillis);
64	            } else {
65	                // 예외가 필터 밖으로 나가면 응답 코드는 이 뒤에 서블릿 컨테이너가 정한다. 여기서 읽은 응답 코드는 최종 값이 아니므로 적지 않는다.
66	                TradeTestLog.event(logger, FEATURE, FILE, METHOD, "요청응답",
67	                        "응답상태", "예외전파", "오류코드", TradeTestLog.errorCode(), "소요시간ms", elapsedMillis,
68	                        "예외", rootCause(propagated).getClass().getSimpleName());
69	            }
70	            TradeTestLog.end();
71	        }
```

- **오류 코드를 보관하는 곳**: 새로 넣는다. 오류 코드를 MDC에 보관하는 곳이 없으면 요청응답의 오류코드가 항상 「-」로 남는다. 그래서 업무 오류를 응답으로 바꾸는 처리와 요청 값 형식 오류를 응답으로 바꾸는 처리에서 오류 코드를 MDC에 보관한다. 이 처리들은 트랜잭션이 끝난 뒤, 필터가 요청응답을 기록하기 전에 실행된다.
- **위치**: `common/error/ApiExceptionHandler.java` `handleBusiness` 33~41줄, `validation` 82~85줄

현재 코드:

```java
33	    @ExceptionHandler(BusinessException.class)
34	    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
35	        ErrorCode code = ex.getErrorCode();
36	        logger.info("[업무 오류] code={}, message={}", code, ex.getMessage());
37	        ErrorResponse body = (code == ErrorCode.VALIDATION_ERROR)
38	                ? ErrorResponse.validation(ex.getMessage(), ex.getFieldErrors())
39	                : ErrorResponse.of(code, ex.getMessage());
40	        return ResponseEntity.status(code.getStatus()).body(body);
41	    }
...
82	    private ResponseEntity<ErrorResponse> validation(String message, Map<String, String> fieldErrors) {
83	        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
84	                .body(ErrorResponse.validation(message, fieldErrors));
85	    }
```

고친 뒤 코드:

```java
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
+       TradeTestLog.recordErrorCode(code.name());
        logger.info("[업무 오류] code={}, message={}", code, ex.getMessage());
...
    private ResponseEntity<ErrorResponse> validation(String message, Map<String, String> fieldErrors) {
+       TradeTestLog.recordErrorCode(ErrorCode.VALIDATION_ERROR.name());
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
```

오류 코드를 MDC에 보관하는 메서드와 꺼내는 메서드는 `testsupport/TradeTestLog.java` 76~87줄이다(지우기 전 코드).

```java
76	    /** 업무 오류 코드를 적어 둔다. 요청 응답 로그가 이 값을 쓴다. */
77	    public static void recordErrorCode(String errorCode) {
78	        if (isActive()) {
79	            MDC.put(MDC_ERROR_CODE, clean(errorCode));
80	        }
81	    }
82	
83	    /** 요청 응답 로그에 쓸 업무 오류 코드. 없으면 {@code -}. */
84	    static String errorCode() {
85	        String code = MDC.get(MDC_ERROR_CODE);
86	        return code == null ? NONE : code;
87	    }
```

### 3.8 요청별사후확인, 전체사후확인

이 두 지점은 JMeter 파일에서 기록하므로 애플리케이션 코드에는 넣을 곳이 없다.

## 4. 바꾸지 않는 것

- 기존 「가격 제안 응답」, 「예약 걸기」 로그 줄
- 검사 순서, 수량 계산식, 트랜잭션 범위
- 대화 상태 이벤트 등록, 중지 위치
- 대화 상태 이벤트 파일

고치는 파일은 다음과 같다.

- 수락 처리
- 예약 생성 처리
- 수량 계산 처리
- 오류 응답 처리
- 공통 로그 파일 두 개(2026-11-10에 지움)

서버 로그에서 p6spy 기록만 뽑는 스크립트가 작업 001에 이미 있다. 테스트를 실행한 뒤 이 스크립트를 이어 쓸 수 있다.

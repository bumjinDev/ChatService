# C2C Marketplace 구현 검토 보고서

| 항목 | 내용 |
| --- | --- |
| 검토 대상 | 브랜치 `claude/chatservice-c2c-marketplace-gxdnhh`, 커밋 `fd3681e` |
| 비교 기준 | `main` 브랜치 `f0e49d6`(검토 대상 브랜치와의 merge-base) |
| 기준 문서 | `docs/4. 프로젝트고도화/1. C2C_Marketplace_프로젝트_기획서.md`(이하 기획서), `docs/4. 프로젝트고도화/2. C2C_Marketplace_요구사항_명세서.md`(이하 요구사항 명세서), `docs/4. 프로젝트고도화/3. C2C_Marketplace_설계_명세서.md`(이하 설계 명세서), `docs/C2C_Marketplace_구현_판단_기록.md`(이하 판단 기록) |
| 문서 우선순위 | 설계 명세서 → 요구사항 명세서 → 기획서. 환불 정책은 요구사항 명세서 7.2절 변경 이력 CH-001 |
| 검토일 | 2026-10-01 |
| 검토 방식 | 코드는 수정하지 않았다. 문서와 코드를 대조하고, 테스트를 실행하고, 빌드한 war 를 실행해 요청을 보내 동작과 로그를 확인했다. 테스트가 통과했다는 사실은 명세 일치의 근거로 쓰지 않았다. |

## 요약

| 검토 항목 | 발견 사항 수 | 내용 |
| --- | --- | --- |
| 1. 기능별 명세 일치 | 8 | 응답 필드 추가 3건, WebSocket 404 응답 추가, 오류 코드 5개 추가, 화면 경로가 `/api`·`/ws` 밖에 있음, `/ws` 체인 실패 핸들러 차이, 판매 종료 이벤트 전달 위치 차이. 업무 흐름(검사 순서, 상태 전환, 금전 처리)의 불일치는 찾지 못했다. |
| 2. 테스트의 타당성 | 5 | 9장 표의 모든 대상 항목에 대응 테스트가 있다. 레거시 빈 검사 테스트가 Spring Data 저장소를 잡지 못함, 시간대 의존성을 가리지 못하는 테스트, 일부만 실제 기능을 거치는 테스트, 상태 코드를 문자열 포함 여부로 확인하는 테스트, 문서에 없는 판단을 기대값으로 고정한 테스트 |
| 3. 기존 코드 처리 | 4 | `application.yml` 의 기존 줄 4개 수정, `gradlew` 파일 권한 변경, `build.gradle` 의 문서 밖 추가, 사용 중단 패키지의 JPA 저장소 2개가 여전히 빈으로 등록됨 |
| 4. 범위 준수 | 1 | 트랜잭션 경계가 구현에 들어감(설계 명세서 1.1절은 후속 과제로 정함). 락, 격리 수준 조정, 낙관적 잠금, 재전송 중복 방지, 실패 복구, 스케줄러 중복 실행 방지, 인덱스는 없다. 기획서 6.2·6.3절 기능은 없다. |
| 5. 시간 규칙 | 1 | 업무 규칙은 일치. 테스트 코드가 `System.currentTimeMillis()` 를 쓴다. |
| 6. 권한과 정보 노출 | 1 | `application.yml` 에 주석 처리된 JWT 토큰과 서명키 값이 남아 있음(이번 브랜치 이전부터 있던 줄) |
| 7. 구현 판단 기록 | 5 | 설계 명세서와 충돌 2건(#8, #19), 문서 밖 근거(작업 지시)로 코드·설정을 바꾼 것 3건(#6, #15, #22) |
| 확인 필요 | 4 | 8장 |

## 0. 테스트 실행 결과

**실행 환경**

- Oracle XE: Docker 이미지 `gvenzl/oracle-xe:21-slim-faststart`, `XEPDB1` 의 `TOYCHAT` 계정(README 의 방법)
- Redis 7.0.15(세션 환경에 설치된 것), `--requirepass` 로 비밀번호 설정
- JDK 21.0.11, `LC_ALL=C.UTF-8`
- 접속 정보는 모두 환경변수로 넘겼다.

**`./gradlew clean build`**

- 1~3차 시도는 의존성 다운로드 중 Maven Central 이 HTTP 429(Too Many Requests)를 돌려줘 컴파일·테스트 전에 실패했다. 코드와 관계없는 실패다.
- 4차 시도에서 `BUILD SUCCESSFUL`. 테스트 클래스 23개, 테스트 151개, 실패 0, 오류 0, 건너뜀 0. war 2개(`ChatService-1.0.0-BUILD-SNAPSHOT.war`, `-plain.war`)가 만들어졌다.
- 이 실행의 테스트 JVM 시간대는 UTC 였다(테스트 로그 시각이 `Z` 로 끝남).

**추가 실행 1: JVM 시간대 Asia/Seoul**

- `TZ=Asia/Seoul`, `-Duser.timezone=Asia/Seoul` 로 `./gradlew test --rerun --no-daemon` 을 실행했다. 테스트 로그 시각이 `+09:00` 으로 찍혀 시간대가 바뀐 것을 확인했다.
- 151개 모두 통과했다.

**추가 실행 2: 빌드한 war 실행**

- `java -jar build/libs/ChatService-1.0.0-BUILD-SNAPSHOT.war --marketplace.scheduler.fixed-delay=PT5S --marketplace.mock-delivery.duration=PT10S --logging.level.org.springframework.data.repository.config=DEBUG` 로 띄웠다. `application.yml` 의 로그 수준 설정(`org.springframework.web.socket: DEBUG`)은 바꾸지 않았다.
- 회원 두 명 가입·로그인 → 상품 등록(201) → 대화 시작(201) → 판매자 WebSocket 연결(101) → 구매자 메시지 전송(201) → 판매자 세션이 `MESSAGE` 이벤트 수신(`messageId`, `content` 일치) → 충전(201) → 결제(201, `IN_PROGRESS`/`WAITING_SHIPMENT`) → 판매자 세션이 `CONVERSATION_STATE` 수신 → 발송 등록(201, `SHIPPING`) → 스케줄러가 `deliveryDueAt` 이후 실행에서 `DELIVERED` 로 바꾸고 `inspectionDeadlineAt` 을 48시간 뒤로 기록.
- 스케줄러는 `PT5S` 같은 Duration 문자열 간격으로 4개 작업을 매번 실행하고 대상 건수와 처리 건수를 INFO 로 남겼다.
- 애플리케이션 로그 전체에서 메시지 본문, 수령인, 배송 주소로 보낸 고유 문자열을 검색했고 0건이었다.

---

## 1. 기능별 명세 일치

### 1.1 기능별 판정

대조 범위: 설계 명세서 5.2절(HTTP API), 5.3절(WebSocket), 4.4절(상태 전환), 6장 각 절(검사 항목과 순서, 처리 순서, 함께 반영할 변경), 6.20절(업무 예외 목록).

| 기능 | 판정 |
| --- | --- |
| F-001 상품 등록 | 일치 |
| F-002 상품 목록·상세 조회 | 일치 |
| F-003 테스트 잔액 충전 | 발견 1-1 |
| F-004 잔액·변동 내역 조회 | 일치 |
| F-005 상품별 채팅 시작·이어가기 | 일치 |
| F-006 채팅 메시지 전송 | 발견 1-2, 1-4 |
| F-007 채팅 목록·내역·거래 표시 조회 | 발견 1-4 |
| F-008 가격 제안 | 발견 1-3 |
| F-009 가격 제안 수락·거절 | 일치 |
| F-010 구매·잔액 결제 | 발견 1-8 |
| F-011 발송 정보 등록 | 일치 |
| F-012 발송 전 주문 취소 | 일치 |
| F-013 미발송 자동 취소 | 일치 |
| F-014 모의 배송 완료 | 일치 |
| F-015 정상 수령 확인·판매대금 지급 | 일치 |
| F-016 상품 확인 기간 만료 자동 완료 | 일치 |
| F-017 환불 요청·거래 보류 | 일치 |
| F-018 판매자 환불 응답·무응답 자동 환불 | 일치 |
| F-019 주문·배송·결제·최종 금전 결과 조회 | 일치 |
| 공통 규약(5.1절)과 오류 목록(6.20절) | 발견 1-5, 1-6, 1-7 |

### 1.2 공통 업무 규칙

| 규칙 | 판정 |
| --- | --- |
| BR-001 인증과 당사자 권한, 자기 상품 구매·채팅·제안 금지 | 일치 |
| BR-002 수량 1개, 예약 없음, 판매 종료 복구 없음 | 일치 |
| BR-003 양의 정수 금액, 잔액 초과 금지, 전액 반환·지급, 함께 반영 | 일치 |
| BR-004 순차 반복과 자동 처리 반복의 중복 금지 | 일치(같은 요청 재전송 식별은 설계 명세서가 후속 과제로 정함) |
| BR-005 세 최종 결과의 배타성, 보류 중 완료·지급 금지 | 일치 |
| BR-006 KST, 48시간, 영업일 | 일치(5장) |
| BR-007 대화 쓰기 가능·읽기 전용 | 일치 |
| BR-008 조회 권한 | 일치(6장) |

### 1.3 발견 사항

#### 1-1. 충전 응답의 `transaction` 에 명세에 없는 `orderId` 필드가 들어 있다

- **위치:** `src/main/java/com/chatservice/marketplace/wallet/TransactionResponse.java:11`, `src/main/java/com/chatservice/marketplace/wallet/WalletService.java:37`
- **명세 근거:** 설계 명세서 5.2.4절 「테스트 잔액 충전」은 성공 결과를 "201과 `balance`(변동 후 잔액), `transaction`(`transactionId`, `type = CHARGE`, `amount`, `balanceAfter`, `createdAt`)"으로 정한다.
- **실제 동작:** 충전 응답의 `transaction` 은 내역 조회와 같은 `TransactionResponse` 를 써서 `orderId`(충전이므로 항상 null)가 추가로 나간다.
- **영향:** 명세의 필드는 모두 있다. 필드가 하나 더 있을 뿐 클라이언트 동작에 영향은 없다. 판단 기록에는 이 항목이 없다.

#### 1-2. 메시지 전송 응답에 명세에 없는 `senderNickname` 이 들어 있다

- **위치:** `src/main/java/com/chatservice/marketplace/conversation/MessageResponse.java:9`, `src/main/java/com/chatservice/marketplace/conversation/ConversationController.java:41`
- **명세 근거:** 설계 명세서 5.2.10절 「메시지 전송」은 성공 결과를 "201과 저장된 메시지(`messageId`, `senderId`, `content`, `createdAt`)"로 정한다.
- **실제 동작:** 메시지 내역 조회(5.2.9절)와 같은 형식으로 `senderNickname` 을 더해 돌려준다.
- **영향:** 명세의 필드는 모두 있다. 판단 기록 #27 이 의도한 추가로 적었다.

#### 1-3. 가격 제안 생성 응답에 `respondedAt` 이 들어 있다

- **위치:** `src/main/java/com/chatservice/marketplace/offer/OfferResponse.java:11`, `src/main/java/com/chatservice/marketplace/offer/OfferService.java:74`
- **명세 근거:** 설계 명세서 5.2.11절 「가격 제안」은 성공 결과를 "201과 제안(`offerId`, `amount`, `status = PENDING`, `createdAt`)"으로 정한다.
- **실제 동작:** 대화 상세의 `offers` 항목과 같은 `OfferResponse` 를 써서 `respondedAt`(생성 직후이므로 null)이 추가로 나간다.
- **영향:** 명세의 필드는 모두 있다. 판단 기록에는 이 항목이 없다.

#### 1-4. WebSocket 핸드셰이크가 명세에 없는 404 를 돌려준다

- **위치:** `src/main/java/com/chatservice/marketplace/conversation/realtime/ConversationHandshakeInterceptor.java:58-62`
- **명세 근거:** 설계 명세서 5.3절 「WebSocket」의 오류 결과는 "401: 인증 실패(체인), 403: 참여자가 아님, 400: `conversationId` 가 없거나 숫자가 아님" 세 가지다. 5.1절 공통 규약은 "존재하지 않는 자원은 404다"라고 정한다.
- **실제 동작:** 없는 대화 ID 로 연결하면 404 로 핸드셰이크를 거부한다. 또 인터셉터가 `userId` 요청 속성을 찾지 못하면 401 을 돌려준다(49-52줄).
- **영향:** 5.3절의 목록에는 없지만 5.1절 공통 규약과는 맞는다. 판단 기록 #10 이 같은 근거를 적었다. 기능상 문제는 없다.

#### 1-5. 6.20절 오류 목록에 없는 오류 코드 5개가 추가되었다

- **위치:** `src/main/java/com/chatservice/marketplace/common/ErrorCode.java:19`(`ACCESS_DENIED`), `:32`(`MEMBER_NOT_FOUND`), `:54`(`USER_ID_ALREADY_EXISTS`), `:55`(`NICKNAME_ALREADY_EXISTS`), `:58`(`INTERNAL_ERROR`). 사용처는 `ApiAccessDeniedHandler.java:29`, `ApiExceptionHandler.java:84-109`
- **명세 근거:** 설계 명세서 6.20절 「업무 예외 목록」은 `ErrorCode` 열거형이 코드, HTTP 상태, 기본 메시지를 가진다고 하고, 표에 31개 코드(VALIDATION_ERROR ~ TRADE_ALREADY_FINALIZED)를 나열한다. 2.2.2절은 기존 회원 예외 핸들러 두 개를 새 `ApiExceptionHandler` 로 대체한다고 정한다.
- **실제 동작:** 표의 31개 코드는 모두 같은 상태 코드로 있다. 여기에 보안 체인의 접근 거부(403), 회원 기능의 아이디·닉네임 중복(409)과 회원 없음(404), 처리하지 못한 예외(500) 코드가 더해졌다. 회원 API(`/members/join`, `/members/edit`)의 오류 응답이 `{code, status, message}` 형식으로 바뀌었다.
- **영향:** 마켓플레이스 기능의 오류 응답에는 영향이 없다. 회원 가입 화면(`join.js`, 수정하지 않음)이 받는 오류 본문 형식이 바뀐다(판단 기록 #7 이 적음).

#### 1-6. 대화 화면 경로가 `/api/**`, `/ws/**` 밖에 있고 인증 없이 열린다

- **위치:** `src/main/java/com/chatservice/marketplace/conversation/ConversationViewController.java:15`
- **명세 근거:** 설계 명세서 5.1절은 "어느 체인에도 속하지 않는 경로는 기존과 같이 보안 필터 없이 처리된다. 새 기능은 전부 `/api/**` 와 `/ws/**` 아래에 둔다"고 정한다. 8.2절은 `conversation.jsp`, `conversation.js` 를 만들라고 하지만 화면 경로는 정하지 않았다.
- **실제 동작:** `GET /conversations/{conversationId}` 가 추가되었다. 실행 확인에서 인증 쿠키 없이 요청해도 200 이었다. 응답 화면에는 경로의 대화 ID 만 들어 있고, 대화 내용은 `conversation.js` 가 인증이 필요한 `/api/**`, `/ws/**` 로 가져온다.
- **영향:** 5.1절의 "새 기능은 전부 `/api/**` 와 `/ws/**` 아래에 둔다"와 다르다. 화면 자체에는 대화 ID 외의 데이터가 없어 정보 노출은 확인되지 않았다. 판단 기록 #19 가 이 결정을 적었다(7장 7-4).

#### 1-7. `/ws/**` 보안 체인의 실패 핸들러가 기존 `chatWebSocketFilterChain` 과 다르다

- **위치:** `src/main/java/com/chatservice/auth/config/SecurityConfig.java:196-206`
- **명세 근거:** 설계 명세서 5.1절은 `conversationWebSocketFilterChain` 을 "인증 필요, `JwtAuthProcessorFilter` 적용, 기존 `chatWebSocketFilterChain` 과 같은 구성"으로 정하고, 바로 뒤에 "기존 핸들러가 돌려주는 HTML `<script>alert()` 응답은 API 에 쓰지 않는다"고 적는다. 5.3절은 인증 실패를 401 로 정한다.
- **실제 동작:** 경로 매처, 인증 요구, CSRF·세션 설정, JWT 필터는 기존 체인과 같다. 인증 실패와 접근 거부 핸들러는 기존 `JwtAuthenticationFailureHandler`, `JwtAccessDeniedHandler` 대신 JSON 을 돌려주는 `ApiAuthenticationEntryPoint`, `ApiAccessDeniedHandler` 를 쓴다.
- **영향:** 5.3절이 요구하는 401 상태 코드는 같다. "같은 구성"이라는 문장과는 핸들러 두 개가 다르다. 판단 기록 #9 가 적었다.

#### 1-8. 판매 종료 시 상품의 모든 대화에 보내는 처리가 `RealtimePublisher` 가 아닌 다른 클래스에 있다

- **위치:** `src/main/java/com/chatservice/marketplace/conversation/ConversationNotifier.java:27-31`, `src/main/java/com/chatservice/marketplace/conversation/realtime/RealtimePublisher.java`
- **명세 근거:** 설계 명세서 7.2절은 `RealtimePublisher` 가 `publish(conversationId, event, excludeMemberId)` 와 "`publishToProductConversations(productId, ...)`: 상품이 판매 종료되었을 때 그 상품의 모든 대화에 보낸다"를 가진다고 정한다.
- **실제 동작:** `RealtimePublisher` 에는 `publish` 만 있다. 상품의 모든 대화에 보내는 처리는 `ConversationNotifier.notifyProductConversations` 가 대화를 조회해 `publish` 를 대화마다 호출한다. 결제한 구매자의 세션은 뺀다(판단 기록 #18).
- **영향:** 전달 대상과 이벤트 내용은 5.3절, 6.10절과 같다(실행 확인에서 판매자 세션이 `CONVERSATION_STATE` 를 받았다). 클래스 책임 배치만 다르다.

---

## 2. 테스트의 타당성

### 2.1 9장 「설계 검증」 표와 테스트의 대응

"후속 과제에서 검증한다"로 표시된 F-003-AC-01, F-005-AC-01, F-006-AC-02, F-010-AC-01·AC-02·AC-04 는 제외했고, 이 항목들에 대한 테스트가 없는 것도 확인했다. 아래 "대응"은 검증문이 표의 기대 결과를 확인한다는 뜻이다.

| 9장 항목 | 테스트(클래스) | 판정 |
| --- | --- | --- |
| F-001-AC-01 | `ProductRegisterApiTest.F001_AC01_…`, `ProductQueryTest.F001_AC01_…` | 대응 |
| F-001-AC-02 | `ProductRegisterApiTest.F001_AC02_…`(가격 0·음수·소수, 카테고리 오류, 이름 누락 등 8건) | 대응 |
| F-002-AC-01 | `ProductQueryTest.F002_AC01_…` | 대응 |
| F-002-AC-02 | `ProductQueryTest.F002_AC02_…` | 대응 |
| F-003-AC-02 | `WalletChargeTest.F003_AC02_…` | 대응 |
| F-004-AC-01 | `WalletQueryTest.F004_AC01_…` | 일부(2-3) |
| F-004-AC-02 | `WalletQueryTest.F004_AC02_…` | 대응 |
| F-005 순차 재시작 | `ConversationStartTest.F005_같은_회원이_…` | 대응 |
| F-005-AC-02 | `ConversationStartTest.F005_AC02_…` | 대응 |
| F-005 거절 | `ConversationStartTest.F005_본인_상품에_…`, `F005_기존_대화_없이_…` | 대응 |
| F-006-AC-01 | `MessageSendTest.F006_AC01_…`, `ConversationQueryTest.F006_AC01_…`(WebSocket) | 대응 |
| F-006-AC-03 | `MessageSendTest.F006_AC03_…` | 대응 |
| F-006 거절 | `MessageSendTest.F006_빈_메시지는_400`, `F006_참여자가_아닌_…`, `F006_다른_구매자가_…` | 대응 |
| F-007-AC-01 | `ConversationQueryTest.F007_AC01_…` | 대응 |
| F-007-AC-02 | `ConversationQueryTest.F007_AC02_…` | 대응 |
| F-007 순서 | `ConversationQueryTest.F007_순서_…` | 대응 |
| F-008-AC-01 | `OfferProposeTest.F008_AC01_…` | 대응 |
| F-008-AC-02 | `OfferProposeTest.F008_AC02_…` | 대응 |
| F-008-AC-03 | `OfferProposeTest.F008_AC03_…` | 대응 |
| F-008 거절 | `OfferProposeTest.F008_등록가_이상의_금액은_400`, `F008_판매자가_…`, `F008_수락_제안이_…` | 대응 |
| F-009-AC-01 | `OfferRespondTest.F009_AC01_…`(상세의 ACCEPTED), `OrderPlaceTest.F009_AC01_…`(offerId 결제) | 대응(두 테스트로 나뉨) |
| F-009-AC-02 | `OfferRespondTest.F009_AC02_…`(응답), `OrderPlaceTest.F009_AC02_…`(결제) | 대응(두 테스트로 나뉨) |
| F-009 거절 | `OfferRespondTest.F009_이미_응답한_…` | 대응 |
| F-010 정상 | `OrderPlaceTest.F010_정상_…` | 대응 |
| F-010-AC-03 | `OrderPlaceTest.F010_AC03_…` | 대응 |
| F-010 거절 | `OrderPlaceTest.F010_거절_…` 5개, `결제_API_오류_응답의_상태_코드` | 대응 |
| F-011-AC-01~03 | `ShipmentRegisterTest.F011_AC01~03_…` | 대응 |
| F-012-AC-01~03 | `OrderCancelTest.F012_AC01~03_…` | 대응 |
| F-013-AC-01·02 | `AutoCancelTest.F013_AC01·02_…` | 대응 |
| F-014-AC-01·02 | `MockDeliveryTest.F014_AC01·02_…` | 대응 |
| F-015-AC-01~03, 반복 | `ConfirmReceiptTest.F015_…` 4개 | 대응 |
| F-016-AC-01·02 | `AutoCompleteTest.F016_AC01·02_…` | 대응 |
| F-017-AC-01~03 | `RefundRequestTest.F017_AC01~03_…` | 대응 |
| F-018-AC-01~04 | `RefundDecisionTest.F018_AC01~04_…` | 대응 |
| F-019-AC-01~03 | `OrderQueryTest.F019_AC01~03_…` | 대응 |
| NFR-001 | `ConversationWebSocketTest.NFR001_…` | 대응 |
| NFR-002 | `ConversationQueryTest.NFR002_…` | 대응 |
| NFR-003 | `ConversationWebSocketTest.NFR003_…`, `ConversationQueryTest.NFR003_…`, `OrderQueryTest.F019_AC03_…` | 대응(WebSocket 상태 확인 방식은 2-4) |
| 중복 접속 | `ConversationWebSocketTest.중복_접속_…` | 대응 |
| BR-006 영업일 | `TimeRulesTest`(월·금·토 확정) | 대응 |

### 2.2 발견 사항

#### 2-1. 레거시 빈 검사 테스트가 Spring Data 저장소 빈을 잡지 못한다

- **위치:** `src/test/java/com/chatservice/marketplace/common/FoundationIntegrationTest.java:33-42`
- **명세 근거:** 설계 명세서 2.2.3절은 `scheduler` 패키지(`ChatServiceScheduler`, `RoomQueueEntityJpa`)와 `createroom` 등을 "스캔에서 제외한다"고 정하고, 8.2절은 `ChatServiceApplication` 에 제외 필터를 추가한다고 정한다.
- **실제 동작:** 테스트는 모든 빈의 `bean.getClass().getName()` 이 레거시 패키지 정규식과 맞지 않는지 확인한다. Spring Data 저장소 빈의 클래스는 JDK 프록시라 레거시 인터페이스 이름이 나오지 않는다. 같은 실행의 로그에 "Found 13 JPA repository interfaces"가 찍혔고, `src/main` 의 JPA 저장소 인터페이스는 레거시 2개(`createroom.dao.RoomQueueJpa`, `scheduler.RoomQueueEntityJpa`)를 포함해 13개다. 테스트는 통과했다.
- **영향:** 테스트 이름("사용을 중단한 레거시 패키지는 빈으로 등록되지 않는다")과 달리 레거시 저장소 빈 등록을 걸러내지 못한다. 코드 쪽 사실은 3-4 에 적었다.

#### 2-2. Instant 매핑 테스트가 서버 시간대에 따른 차이를 가려내지 못한다

- **위치:** `src/test/java/com/chatservice/marketplace/common/FoundationIntegrationTest.java:72-84`
- **명세 근거:** 설계 명세서 4.3절은 UTC 로 저장하는 이유를 "서버의 시간대 설정에 결과가 좌우되지 않게 하기 위해서"라고 적고, 8.1절은 "JVM 과 DB 의 시간대에 의존하는지는 구현을 시작할 때 확인한다"고 정한다.
- **실제 동작:** 테스트는 JVM 기본 시간대를 정하지 않고 실행 환경의 시간대를 그대로 쓴다. 이 검토의 1차 실행은 JVM 시간대가 UTC 였다. 이 조건에서는 저장값과 기대값(UTC 시각)이 같은 것만 확인된다.
- **영향:** 이 테스트 하나로는 서버 시간대가 다를 때의 결과를 확인하지 못한다. 검토에서 JVM 시간대를 Asia/Seoul 로 바꿔 전체 테스트를 다시 실행했고 151개가 모두 통과했다(0장). 따라서 구현이 시간대에 좌우된다는 근거는 찾지 못했다.

#### 2-3. F-004-AC-01 테스트의 구매·반환 내역은 결제·취소 기능을 거치지 않고 테스트가 직접 만든다

- **위치:** `src/test/java/com/chatservice/marketplace/wallet/WalletQueryTest.java:48-74`(구매 내역 57-61줄, 반환 내역 65줄)
- **명세 근거:** 설계 명세서 9장 F-004-AC-01 행은 "충전, 구매, 반환 뒤 본인이 조회한다 → 유형, 금액, 변동 후 잔액, 시각, `orderId` 가 맞다"를 기대 결과로 정한다.
- **실제 동작:** 충전만 `walletService.charge` 를 거친다. 구매 내역은 테스트가 지갑에서 직접 빼고 `BalanceTransaction.record(..., -30_000L, ...)` 로 저장하며, 반환 내역은 `walletService.credit` 을 직접 호출한다. 검증문은 조회 결과가 이 저장값과 같은지 확인한다.
- **영향:** 이 테스트만으로는 결제와 취소가 실제로 남기는 내역의 금액 부호와 변동 후 잔액을 확인하지 못한다. 그 부분은 `OrderPlaceTest.F010_정상_…`(PURCHASE −30,000, `balanceAfter` 20,000, `orderId`)와 `OrderCancelTest.F012_AC01_…`(CANCEL_REFUND, `orderId`)가 확인한다. 테스트 전체로 보면 기대 결과는 확인된다.

#### 2-4. WebSocket 핸드셰이크 상태 코드를 예외 스택 문자열 포함 여부로 확인한다

- **위치:** `src/test/java/com/chatservice/marketplace/conversation/ConversationWebSocketTest.java:129`(403), `:141`(401), `:143`, `:145`(400)
- **명세 근거:** 설계 명세서 9장 NFR-003 행은 "참여자가 아닌 회원이 WebSocket 에 연결 → 403 을 받고 데이터가 바뀌지 않는다"를, 5.3절은 401·403·400 을 오류 결과로 정한다.
- **실제 동작:** `assertThatThrownBy(...).hasStackTraceContaining("403")` 처럼 예외 체인의 문자열 어딘가에 "403" 등이 들어 있는지만 본다. 응답 상태 코드 값을 직접 비교하지 않는다. 403 테스트는 레지스트리에 세션이 없다는 것도 함께 확인한다.
- **영향:** 상태 코드 외의 문자열에 같은 숫자가 들어가도 통과하는 검증문이다. 이번 실행에서는 통과했고, 핸드셰이크 코드(`ConversationHandshakeInterceptor.java:44-66`)는 각 경우에 해당 상태 코드를 설정한다.

#### 2-5. 문서에 없는 판단을 기대값으로 고정한 테스트가 있다

- **위치:** `src/test/java/com/chatservice/marketplace/conversation/ConversationStartTest.java:103-117`
- **명세 근거:** 설계 명세서 6.6절의 쓰기 가능 판단 규칙은 "상품이 `SOLD` 이면 그 상품의 `PURCHASE_ORDER` 를 조회한다. 판매 종료된 상품에 주문은 하나다"라고 전제하며, 주문 없이 판매 종료인 경우는 정하지 않는다.
- **실제 동작:** 테스트는 주문 없이 상품만 판매 종료로 바꾼 뒤 `readOnlyReason` 이 `PRODUCT_SOLD_TO_OTHER` 인지 확인한다. 이 값은 판단 기록 #14 의 결정이다.
- **영향:** 정상 흐름에서는 생기지 않는 상태(판단 기록 #14)에 대한 구현 선택을 확인하는 테스트다. 이 테스트의 주된 확인 대상(기존 대화가 있으면 판매 종료 상품이라도 200, 5.2.6절)은 명세와 맞는다.

---

## 3. 기존 코드 처리

### 3.1 main 대비 바뀐 기존 파일

새로 추가된 파일(마켓플레이스 패키지, 테스트, DDL, 화면, 판단 기록)을 뺀, `main` 에 이미 있던 파일의 변경이다(`git diff --numstat origin/main...HEAD`).

| 파일 | 추가 | 삭제 | 설계 명세서 2.2절 분류 | 판정 |
| --- | --- | --- | --- | --- |
| `auth/config/SecurityConfig.java` | 45 | 1 | 변경 | 일치. 삭제 1줄은 생성자 매개변수 줄에 쉼표가 붙은 것이다 |
| `ChatServiceApplication.java` | 21 | 0 | 변경 | 일치 |
| `user/exceptionhandler/MemberAPIControllerExceptionHandler.java` | 0 | 92(파일 삭제) | 변경("새 `ApiExceptionHandler` 로 대체") | 일치 |
| `user/exceptionhandler/MemberViewControllerExceptionHandler.java` | 0 | 96(파일 삭제) | 변경("새 `ApiExceptionHandler` 로 대체") | 일치 |
| `src/main/resources/application.yml` | 24 | 4 | 2.2절에 없음(8.2절에 추가 설정 3개만 있음) | 발견 3-1 |
| `gradlew` | 0 | 0(파일 권한 변경) | 없음 | 발견 3-2 |
| `build.gradle` | 20 | 0 | 2.2절에 없음(2.1·8.2절에 toolchain 명시만 있음) | 발견 3-3 |
| `README.md` | 145 | 0 | 없음 | 삭제·수정한 줄 없음 |

### 3.2 사용 중단 패키지와 스캔 제외 필터

| 확인 항목 | 판정 |
| --- | --- |
| 사용 중단 대상 패키지(`createroom`, `joinroom`, `concurrency`, `scheduler`, `roomlist`, `web`, `websocketcore`, `redis.controller`, `redis.service`)의 파일이 수정되지 않았는가 | 일치(diff 에 해당 경로 없음) |
| `ChatServiceApplication` 의 제외 필터에 8.2절의 9개 패키지가 들어갔는가 | 일치(`ChatServiceApplication.java:19-30`) |
| `websocketcore` 원본을 두고 `conversation.realtime` 에 새 구현을 만들었는가 | 일치 |
| `WebController` 를 빼고 `MainPageController` 를 만들었는가 | 일치(`marketplace/web/MainPageController.java`) |
| `CHAT_ROOM_CREATION_QUEUE` DDL 을 남겼는가 | 일치(`ddl_toychat.sql` 변경 없음) |
| 사용 중단 패키지의 JPA 저장소 | 발견 3-4 |

### 3.3 발견 사항

#### 3-1. `application.yml` 의 기존 줄 4개가 바뀌었고, 문서에 없는 설정이 추가되었다

- **위치:** `src/main/resources/application.yml:13-14`(`url`, `username`), `:34-35`(Redis `host`, `port`), 추가 설정 `:62`(`type.preferred_instant_jdbc_type`), `:68`(`accept-float-as-int`), `:78`(`marketplace.scheduler.enabled`)
- **명세 근거:** 설계 명세서 2.2절은 기존 구현을 재사용(파일을 바꾸지 않음), 변경, 사용 중단으로 나누고 "변경" 대상으로 `SecurityConfig`, `ChatServiceApplication`, 두 회원 예외 핸들러, 예외 응답 형식, 메인 화면 컨트롤러만 든다. 8.2절은 `application.yml` 의 변경 내용을 "`spring.jpa.properties.hibernate.jdbc.time_zone: UTC`, `marketplace.scheduler.fixed-delay`, `marketplace.mock-delivery.duration`" 세 가지로 정하고, 비밀값은 "기존처럼 환경변수로만 넣는다"고 정한다.
- **실제 동작:** DB URL 과 계정 이름이 고정값에서 `${ORACLE_URL}`, `${ORACLE_USERNAME}`(기본값 없음)으로, Redis 호스트·포트가 `${REDIS_HOST:127.0.0.1}`, `${REDIS_PORT:6379}` 로 바뀌었다. 8.2절에 없는 설정 3개도 추가되었다.
- **영향:** `ORACLE_URL`, `ORACLE_USERNAME` 환경변수가 없으면 애플리케이션이 데이터소스를 만들지 못한다. Jenkins 배포는 systemd 유닛으로 실행하므로(`Jenkinsfile`) 그 유닛에 두 변수가 있어야 한다. 판단 기록 #6 은 근거로 문서가 아닌 "작업 지시"를 들었다. 추가 설정 3개는 판단 기록 #4, #5, #16 이 적었고 기존 동작을 바꾸지 않는다.

#### 3-2. `gradlew` 의 파일 권한이 바뀌었다

- **위치:** `gradlew`(mode `100644` → `100755`)
- **명세 근거:** 설계 명세서 2.2절과 8.2절에 `gradlew` 변경은 없다.
- **실제 동작:** 내용은 그대로이고 실행 권한만 추가되었다.
- **영향:** `./gradlew` 를 바로 실행할 수 있게 된다. Jenkins 의 `chmod +x` 는 그대로 동작한다. 판단 기록 #22 는 근거로 "작업 지시"를 들었다.

#### 3-3. `build.gradle` 에 문서에 없는 항목이 추가되었다(삭제·수정한 줄은 없음)

- **위치:** `build.gradle:14-18`(toolchain), `:82`(`junit-platform-launcher`), `:86-91`(`processTestResources` 에 DDL 복사), `:96-99`(`testLogging`)
- **명세 근거:** 설계 명세서 2.1절과 8.2절은 `build.gradle` 에 `java.toolchain.languageVersion = 21` 을 명시한다고 정한다. 그 밖의 `build.gradle` 변경은 문서에 없다. 8.2절은 테스트 DB 구성을 "구현 시작 시 정한다"고 남겼다.
- **실제 동작:** toolchain 외에 테스트 실행용 항목 3개가 추가되었다. 기존 줄은 지우거나 바꾸지 않았다.
- **영향:** 운영 빌드 산출물에는 영향이 없다. 테스트가 `docs/1. 프로젝트개발/2. db` 의 DDL 을 클래스패스로 복사해 쓰므로 그 경로의 한글 때문에 Linux 에서 UTF-8 로케일이 필요하다(README 에 적혀 있음).

#### 3-4. 사용 중단 패키지의 JPA 저장소 2개가 여전히 빈으로 등록된다

- **위치:** `src/main/java/com/chatservice/scheduler/RoomQueueEntityJpa.java`, `src/main/java/com/chatservice/createroom/dao/RoomQueueJpa.java`, `src/main/java/com/chatservice/ChatServiceApplication.java:17-26`
- **명세 근거:** 설계 명세서 2.2.3절은 "joinroom, createroom, concurrency(`SemaphoreRegistry`), scheduler(`ChatServiceScheduler`, `RoomQueueEntityJpa`), roomlist, web 패키지"의 처리를 "스캔에서 제외한다"로 정한다. `RoomQueueEntityJpa` 를 이름으로 든다. 같은 절은 엔티티 `RoomQueueEntity` 에 대해서만 "엔티티 스캔에 남지만 `ddl-auto: none` 이라 동작에 영향이 없다"고 적는다.
- **실제 동작:** 제외 필터는 `@ComponentScan` 에만 걸려 있다. Spring Data JPA 저장소 스캔은 이 필터와 별개로 `com.chatservice` 전체를 본다. 테스트 실행과 war 실행 로그 모두 "Found 13 JPA repository interfaces"였다. war 실행에서 `org.springframework.data.repository.config` 로그를 DEBUG 로 올려 보니 "Bootstrapping Spring Data JPA repositories"와 "Found 13 JPA repository interfaces" 사이에 `scheduler/RoomQueueEntityJpa.class`, `createroom/dao/RoomQueueJpa.class` 가 저장소 후보로 찍혔다.
- **영향:** 두 저장소는 호출하는 곳이 없어 기능 동작에는 영향이 없다. 설계 명세서가 이름을 들어 스캔 제외로 정한 `RoomQueueEntityJpa` 가 빈으로 남는다는 점이 명세와 다르다. 판단 기록 #8 이 이 사실을 적고 그대로 두었다(7장 7-3).

---

## 4. 범위 준수

### 4.1 설계 명세서 1.1절이 후속 과제로 정한 내용

| 후속 과제 | 구현 여부 | 확인 근거 |
| --- | --- | --- |
| 락(비관적 잠금, `SELECT … FOR UPDATE`) | 없음 | `@Lock`, `LockModeType`, `FOR UPDATE` 검색 결과 없음 |
| 격리 수준 조정 | 없음 | `isolation` 지정 없음. `@Transactional`, `TransactionTemplate` 모두 기본값 |
| 낙관적 잠금 | 없음 | `@Version` 없음 |
| 같은 요청 재전송 중복 방지 | 없음 | `requestId` 는 저장만 하고 조회·비교하는 코드가 없다. UNIQUE 제약 없음 |
| 실패 복구 | 없음 | 재시도·보상 처리 없음. `TradeScheduler.java:53-59` 는 작업별 예외를 로그로 남기고 다음 작업을 실행할 뿐이다 |
| 스케줄러 중복 실행 방지 | 없음 | ShedLock 류 없음 |
| 인덱스 설계 | 없음 | `ddl_marketplace.sql` 에 `CREATE INDEX` 없음(19줄 주석으로 후속 과제라 적음) |
| 트랜잭션 경계 | 있음 | 발견 4-1 |

참고: 코드 안의 `synchronized` 는 `RealtimePublisher.java:68` 하나다. 같은 WebSocket 세션에 동시에 보내지 않도록 세션 단위로 전송을 묶는 것이며 업무 상태를 잠그지 않는다(판단 기록 #12).

### 4.2 기획서 6.2절·6.3절의 기능

| 항목 | 구현 여부 |
| --- | --- |
| 6.2 묶음 구매, 추천, 부분 환불, 사진 증빙 첨부 | 없음 |
| 6.3 국제배송·세금·사기 탐지, 실제 결제·은행 지급, 운영자 개입, 반품 후 환불, 외부 연동, 채팅 부가 기능(사진·파일, 읽음, 입력 중, 접속 상태, 푸시), 역제안·재협상, 일정 예약·배송 방법 협의 | 없음 |
| 6.3 합의 실패 거래의 강제 종결 | 판매자 거절 시 정상 완료와 무응답 48시간 자동 환불이 있다. 요구사항 명세서 1.2절과 7.2절 CH-001 이 이 두 처리를 이번 범위로 확정하고 기획서 6.3절의 해당 항목을 대체했으므로 문서 우선순위와 일치한다 |

요구사항 명세서 1.2절의 "제외"(상품 수정·삭제·판매 중단, 예약, 하위 카테고리, 합의 철회, 발송 정보 수정·재등록·기한 연장, 환불 요청 수정·철회, 추가 환불 사유, 재심)에 해당하는 API 도 없다.

### 4.3 발견 사항

#### 4-1. 트랜잭션 경계가 구현에 들어갔다

- **위치:** 예: `src/main/java/com/chatservice/marketplace/order/OrderService.java:64`, `RefundService.java:66,104`, `TradeCompletionService.java:58,118`, `OrderCancellationService.java:61,87`, `ShipmentService.java:58,91`, `wallet/WalletService.java:33-76`(마켓플레이스 main 코드에 `@Transactional` 28곳, 자동 처리의 `transactionTemplate.execute` 4곳)
- **명세 근거:** 설계 명세서 1.1절은 "이번 설계에서 다루지 않는 내용은 다음과 같다. 모두 후속 과제다"라며 첫 항목으로 "트랜잭션 경계, 전파, 격리 수준"을 든다. 4.4.3절과 6장 머리는 "함께 반영할 변경" 묶음을 "실제로 하나로 반영되게 하는 트랜잭션 설계는 후속 과제다"라고 적는다.
- **실제 동작:** 사용자 요청 서비스 메서드는 `@Transactional`(기본 전파·격리)로, 자동 처리 네 메서드는 대상 한 건마다 `TransactionTemplate` 으로 트랜잭션을 연다. 실시간 이벤트는 커밋 뒤에 보낸다.
- **영향:** 6장의 "함께 반영할 변경"이 한 트랜잭션으로 반영된다. 설계 명세서가 후속 과제로 미룬 결정을 기본값으로 먼저 정한 것이다. 이번 검토 요청의 범위 항목(락, 격리 수준 조정 등)에는 포함되지 않지만 설계 명세서 1.1절과는 다르다. 판단 기록 #15 는 근거로 문서가 아닌 "작업 지시"를 들었다(7장 7-2).

---

## 5. 시간 규칙

| 확인 항목 | 설계 명세서 근거 | 구현 | 판정 |
| --- | --- | --- | --- |
| 발송 기한 계산 | 4.3절: 확정 시각의 KST 날짜에서 하루씩 늘려 월~금이면 센다. 5번째 영업일 다음 날 00:00 KST 가 기한이 지나는 시점 | `TimeRules.java:25-35`, `ZoneId.of("Asia/Seoul")` | 일치 |
| 발송 기한 경계 | 4.3절: 발송 등록은 현재 시각이 기한보다 작을 때 허용, 자동 취소는 기한 이상일 때 대상 | `ShipmentService.java:68`(`!now.isBefore`→거절), `PurchaseOrderRepository.java:21-24`(`<= :now`), `OrderCancellationService.java:101` | 일치 |
| 상품 확인 기한의 시작 시점 | 4.4.2절 F-014: 배송 완료 안내 시점(`DELIVERED_AT`)부터 48시간 | `ShipmentService.java:113`(`markDelivered(now, now+48h)`) | 일치 |
| 판매자 응답 기한의 시작 시점 | 4.4.2절 F-017: 최초 접수 시각(`REQUESTED_AT`)부터 48시간 | `RefundService.java:85-86` | 일치 |
| 정확히 48시간일 때 | 4.3절: 수동 요청(수령 확인, 환불 요청, 판매자 응답)은 기한보다 작을 때만 허용, 자동 처리(확인 기간 만료, 무응답 자동 환불)는 기한 이상일 때 대상 | 수동: `TradeCompletionService.java:71`, `RefundService.java:79,116`. 자동: `PurchaseOrderRepository.java:34-37`, `RefundRequestRepository.java:18-23`, 재확인 `TradeCompletionService.java:104`, `RefundService.java:153` | 일치 |
| 보류 후 원래 상품 확인 기한 무시 | 요구사항 명세서 2.4절: 환불 보류 후에는 원래 상품 확인 기한이 지나도 자동 완료하지 않는다 | 자동 완료 대상이 `IN_PROGRESS` 만이고 환불 요청 존재를 재확인(`TradeCompletionService.java:101,105`) | 일치 |
| `Instant` 저장, UTC 설정 | 4.3절, 8.2절: 모든 시각을 `Instant` 로 저장, `hibernate.jdbc.time_zone=UTC`, `Clock` 빈은 `Clock.systemUTC()`, 서비스는 `Clock` 에서만 현재 시각을 읽음 | 엔티티 시각 필드 전부 `Instant`, `application.yml:61-62`, `ClockConfig.java:16-18`. 마켓플레이스 main 코드(`src/main/java/com/chatservice/marketplace`)에 `LocalDateTime.now()`, `Instant.now()`, `System.currentTimeMillis()` 없음 | 일치 |
| API 응답 시각 형식 | 4.3절: ISO-8601 UTC 문자열 | 테스트와 실행 확인에서 `2026-10-01T03:38:46.500042Z` 형식 | 일치 |
| 서버 시간대 비의존 | 4.3절 | JVM 시간대 UTC, Asia/Seoul 두 실행에서 151개 통과(0장) | 일치 |

### 5.1 발견 사항

#### 5-1. 테스트 코드가 `System.currentTimeMillis()` 를 쓴다

- **위치:** `src/test/java/com/chatservice/marketplace/order/OrderPlaceTest.java:284,287`, `conversation/ConversationWebSocketTest.java:34-35`, `conversation/ConversationQueryTest.java:177-178`, `offer/OfferProposeTest.java:175-176`, `offer/OfferRespondTest.java:154-155`
- **명세 근거:** 설계 명세서 4.3절 「현재 시각」은 "서비스는 `java.time.Clock` 빈에서만 현재 시각을 읽는다"와 "새 코드에서는 `LocalDateTime.now()` 와 `System.currentTimeMillis()` 를 쓰지 않는다"를 정한다.
- **실제 동작:** WebSocket 세션 등록을 기다리는 테스트의 대기 시간 한도(5초)를 계산할 때 쓴다. 업무 시각 계산에는 쓰지 않는다. 마켓플레이스 main 코드에는 없다.
- **영향:** 업무 규칙 검증에는 영향이 없다. "새 코드"를 테스트 코드까지 포함해 읽으면 문장과 다르다.

---

## 6. 권한과 정보 노출

### 6.1 당사자가 아닌 회원의 요청

| API | 당사자 검사 | 결과 | 판정 |
| --- | --- | --- | --- |
| 대화 상세·메시지 조회·메시지 전송 | `ConversationAccess.requireParticipant`(`ConversationAccess.java:18-25`) | 403 `NOT_CONVERSATION_MEMBER` | 일치 |
| 가격 제안 | 참여자 검사 후 구매 희망자 검사(`OfferService.java:51-54`) | 403 `NOT_CONVERSATION_MEMBER` / `NOT_BUYER` | 일치 |
| 제안 수락·거절 | 제안의 판매자 검사(`OfferService.java:90-92`) | 403 `NOT_SELLER` | 일치 |
| 발송 등록, 환불 동의·거절 | `OrderAccess.requireSeller` | 403 `NOT_SELLER` | 일치 |
| 수령 확인, 환불 요청 | `OrderAccess.requireBuyer` | 403 `NOT_BUYER` | 일치 |
| 주문 취소, 주문 상세 | `OrderAccess.requireParty` | 403 `NOT_TRADE_PARTY` | 일치 |
| 잔액·내역 | 경로에 회원 ID 를 받지 않음(`WalletController.java:27-41`) | 본인 것만 | 일치 |
| WebSocket 연결 | 핸드셰이크 참여자 검사 | 403 | 일치 |

### 6.2 수령인과 주소의 노출 범위

| 응답 | 수령인·주소 포함 | 판정 |
| --- | --- | --- |
| 주문 상세(5.2.15절)와 같은 형식의 응답(결제, 발송, 취소, 수령 확인, 환불 요청·응답) | 포함. 모든 경로가 당사자 검사를 먼저 통과해야 만들어진다 | 일치 |
| 구매·판매 목록(5.2.14절) | 미포함(`OrderSummaryResponse.java`) | 일치 |
| 대화 상세의 `order` 요약 | `orderId`, `tradeStatus`, `shippingStatus` 만. 실제 당사자 대화에만 붙는다 | 일치 |
| 제3자의 주문 상세 요청 | 403 본문에 수령인·주소 없음 | 일치 |

### 6.3 로그 규칙(설계 명세서 8.3절)

설계 명세서 8.3절은 "새 코드는 slf4j 만 쓴다", "상태가 바뀌는 사건마다 `orderId`, `memberId`, 이전 상태, 다음 상태, 사건의 종류를 INFO 로 남긴다", "메시지 본문, 주소, 수령인은 로그에 남기지 않는다", "자동 처리는 실행할 때마다 대상 건수와 처리 건수를 남긴다"를 정한다.

| 확인 항목 | 판정 |
| --- | --- |
| 새 코드의 로그 API | 일치(slf4j 만 사용. `System.out` 없음) |
| 상태 변경 사건 로그 | 일치(결제, 발송, 취소, 배송 완료, 정상 완료, 환불 요청, 환불 판단, 자동 처리 각 건에 `orderId`, 회원 ID 또는 `SYSTEM`, 이전→다음 상태가 있다. 자동 취소 로그는 시스템일 때 `memberId=null` 과 `cancelledBy=SYSTEM` 을 찍는다) |
| 메시지 본문, 주소, 수령인 | 일치. 코드의 로그 문장에 해당 값이 없고, war 실행 로그 전체에서 실제로 보낸 본문·수령인·주소 문자열이 0건이었다. WebSocket 핸들러는 받은 텍스트 프레임의 길이만 남긴다(`ConversationWebSocketHandler.java:47-48`) |
| 자동 처리의 대상·처리 건수 | 일치 |

### 6.4 발견 사항

#### 6-1. `application.yml` 에 주석 처리된 JWT 토큰과 서명키 값이 있다(이번 브랜치 이전부터 있던 줄)

- **위치:** `src/main/resources/application.yml:105-107`(`#TestJWT:` 아래의 `token`, `secretKey` 주석 줄). 값은 이 보고서에 옮기지 않는다.
- **명세 근거:** 설계 명세서 8.2절 「비밀값」은 "기존처럼 환경변수로만 넣는다"고 정한다.
- **실제 동작:** 해당 줄은 `main` 의 커밋 `9a95f61`(2025-07-19)부터 있었고 이번 브랜치는 바꾸지 않았다(`git blame`). 이번 브랜치가 추가한 코드·설정·테스트·문서에는 비밀값이 없다. DB·Redis 비밀번호는 `${ORACLE_PASSWORD}`, `${REDIS_PASSWORD}` 로만 받고, README 는 자리표시자(`'<TOYCHAT 비밀번호>'` 등)만 쓴다. 테스트의 회원 비밀번호는 더미 값 `'x'` 다.
- **영향:** 주석이라 실행에는 쓰이지 않지만 서명키로 보이는 값이 저장소에 남아 있다. 이 값이 현재 어디에서도 쓰이지 않는지는 저장소 밖 환경이라 확인하지 못했다.

---

## 7. 구현 판단 기록 검토

판정 기준: "문서 범위 안"은 기준 문서가 구현 단계로 넘겼거나 문서 내용으로 판단할 수 있는 결정, "문서 밖 근거"는 기준 문서가 아닌 작업 지시를 근거로 든 결정, "충돌"은 기준 문서의 문장과 다른 결정이다.

| # | 판단 | 판정 |
| --- | --- | --- |
| 1 | 구현 순서(부속 문서 없음) | 문서 범위 안. 부속 문서 「C2C_Marketplace_설계_결정_기록.md」가 저장소에 없음을 확인했다 |
| 2 | DDL·엔티티를 기반 커밋에 작성 | 커밋 구성에 관한 결정(코드 동작과 무관) |
| 3 | `DESCRIPTION` 을 CLOB 으로 | 문서 범위 안(4.2절이 구현 시 정한다고 함) |
| 4 | `preferred_instant_jdbc_type: TIMESTAMP` 추가 | 문서 범위 안(4.3절이 구현 시작 시 확인하라고 함) |
| 5 | 소수 금액을 400 으로 | 문서 범위 안(4.3절) |
| 6 | DB URL·계정을 환경변수로만 | 문서 밖 근거 → 7-1 |
| 7 | 회원 핸들러 삭제, 오류 코드 추가 | 문서 범위 안(2.2.2절 대체 지시의 공백을 채움). 1-5 참고 |
| 8 | 컴포넌트 스캔 방법, 레거시 JPA 저장소 유지 | 충돌 → 7-3 |
| 9 | `/ws` 체인 JSON 실패 응답 | 문서 범위 안(5.1절의 두 문장을 해석). 1-7 참고 |
| 10 | 없는 대화 핸드셰이크 404 | 문서 범위 안(5.1절 공통 규약). 1-4 참고 |
| 11 | WebSocket 허용 Origin 기본값 | 문서 범위 안(7.2절은 등록 구조만 정함) |
| 12 | 커밋 뒤 전송, 세션 단위 전송 직렬화 | 문서 범위 안(5.3절 "저장이 끝난 뒤 한 번") |
| 13 | 조립 코드를 앞선 기능 커밋에 넣음 | 커밋 구성에 관한 결정 |
| 14 | 주문 없는 판매 종료 상품의 대화는 읽기 전용 | 문서 범위 안(6.6절이 정하지 않은 경우). 2-5 참고 |
| 15 | 자동 처리의 건별 트랜잭션, 예외 시 중단 | 문서 밖 근거, 설계 명세서 1.1절과 다름 → 7-2 |
| 16 | `marketplace.scheduler.enabled` 추가 | 문서 범위 안(7.1·8.2절 설정 목록에 하나를 더함, 운영 동작 불변) |
| 17 | 간격 `PT30S`, 모의 배송 `PT2M` | 문서 범위 안(7.1절, TBD-005) |
| 18 | 판매 종료 이벤트에서 결제한 구매자 제외 | 문서 범위 안(5.3절 전달 규칙) |
| 19 | 대화 화면 경로 `/conversations/{id}` | 충돌 → 7-4 |
| 20 | 화면을 별도 커밋으로 | 커밋 구성에 관한 결정 |
| 21 | `index.jsp`, `indexUser.js` 미수정 | 문서 범위 안(2.2.1절 재사용, 5.1절 `/rooms*` 404 예상) |
| 22 | `gradlew` 실행 권한 | 문서 밖 근거 → 7-5 |
| 23 | Java toolchain 명시 | 문서 범위 안(2.1·8.2절) |
| 24 | 테스트 DB 구성과 데이터 정리 | 문서 범위 안(8.2절이 구현 시작 시 정한다고 함) |
| 25 | Gradle 실행 로케일 | 실행 환경 설정(코드 변경 없음) |
| 26 | 문자열 필수 입력에 `@NotBlank` | 문서 범위 안(요구사항 명세서 F-001 "누락 불가") |
| 27 | 메시지 응답 `senderNickname`, 내역 조회 시 지갑 생성 | 문서 범위 안(5.2.5절 재호출 처리). 1-2 참고 |
| 28 | 환불 합의 실패는 CH-001 을 따름 | 문서 범위 안(요구사항 명세서 7.2절 CH-001, 설계 명세서 5.2.20·6.18절) |
| 29 | 동시성·중복 방지 미구현 | 문서 범위 안(설계 명세서 1.1절) |
| 30 | 화면의 재접속 복구 방식 | 문서 범위 안(5.3·7.2·8.1절). 브라우저 동작은 확인 필요(8장) |

### 7.1 발견 사항

#### 7-1. 판단 #6: DB URL·계정을 환경변수로 바꾼 근거가 기준 문서에 없다

- **위치:** `docs/C2C_Marketplace_구현_판단_기록.md:46-51`, 코드는 `src/main/resources/application.yml:13-14`
- **명세 근거:** 설계 명세서 8.2절 「비밀값」은 "기존처럼 환경변수로만 넣는다"고 정하고, `application.yml` 변경 내용은 시간대 설정과 마켓플레이스 설정 두 개로 한정한다. 2.2절의 "변경" 대상에 `application.yml` 은 없다.
- **실제 동작:** 판단 기록은 근거로 "작업 지시"를 든다. DB URL 과 계정 이름은 비밀값이 아니며 기존에는 고정값이었다.
- **영향:** 3-1 과 같다. 배포 환경에 `ORACLE_URL`, `ORACLE_USERNAME` 이 없으면 기동하지 못한다.

#### 7-2. 판단 #15: 트랜잭션 경계를 정한 근거가 기준 문서 밖에 있고 설계 명세서 1.1절과 다르다

- **위치:** `docs/C2C_Marketplace_구현_판단_기록.md:112-117`, 코드는 4-1 의 위치
- **명세 근거:** 설계 명세서 1.1절은 "트랜잭션 경계, 전파, 격리 수준"을 후속 과제로, 7.1절 「재실행과 실패 처리의 범위」는 "한 건에서 예외가 발생하면 다음 건을 계속 처리할지 … 그 단계에서 결정한다"고 정한다.
- **실제 동작:** 판단 기록은 "작업 지시는 '함께 반영할 변경'을 한 트랜잭션으로 반영하라고 하고"를 근거로 건별 `TransactionTemplate` 을 쓰고, 한 건에서 예외가 나면 그 작업의 나머지 건을 처리하지 않는 동작을 정했다(`OrderCancellationService.java:86-91` 등에서 예외가 루프 밖으로 나감, `TradeScheduler.java:53-59` 가 받아 로그만 남김).
- **영향:** 7.1절이 후속 단계로 미룬 "한 건 실패 시 다음 건 처리 여부"가 "그 작업은 멈춘다"로 정해졌다. 후속 단계 설계는 이 동작을 출발점으로 삼게 된다.

#### 7-3. 판단 #8: 레거시 JPA 저장소를 빈으로 남긴 결정이 설계 명세서 2.2.3절과 충돌한다

- **위치:** `docs/C2C_Marketplace_구현_판단_기록.md:63-68`
- **명세 근거:** 설계 명세서 2.2.3절은 `scheduler` 패키지의 `RoomQueueEntityJpa` 를 이름으로 들어 "스캔에서 제외한다"고 정한다.
- **실제 동작:** 판단 기록은 2.2.3절의 `RoomQueueEntity` 문장("엔티티 스캔에 남지만 `ddl-auto: none` 이라 동작에 영향이 없다")을 "같은 취지"로 들어 저장소 빈을 그대로 두었다. 그러나 그 문장은 엔티티에 대한 것이고, 저장소 `RoomQueueEntityJpa` 는 같은 절에서 스캔 제외 대상으로 따로 이름이 나온다. 실행 로그로 저장소 13개 등록을 확인했다(3-4).
- **영향:** 3-4 와 같다. 판단 기록은 "레거시 컨트롤러·서비스 빈이 없다는 것을 테스트로 확인했다"고 적었지만 그 테스트는 저장소 빈을 확인하지 못한다(2-1).

#### 7-4. 판단 #19: 대화 화면 경로가 설계 명세서 5.1절과 충돌한다

- **위치:** `docs/C2C_Marketplace_구현_판단_기록.md:140-145`
- **명세 근거:** 설계 명세서 5.1절은 "새 기능은 전부 `/api/**` 와 `/ws/**` 아래에 둔다"고 정한다.
- **실제 동작:** 판단 기록도 이 문장을 인용한 뒤 `/conversations/{id}` 를 택했다. 근거로 화면이 데이터를 담지 않는다는 점과 새 보안 체인 추가가 `SecurityConfig` 변경 범위를 넘는다는 점을 들었다.
- **영향:** 1-6 과 같다. 정보 노출은 확인되지 않았다.

#### 7-5. 판단 #22: `gradlew` 권한 변경의 근거가 기준 문서에 없다

- **위치:** `docs/C2C_Marketplace_구현_판단_기록.md:161-166`
- **명세 근거:** 설계 명세서 2.2절과 8.2절에 `gradlew` 변경은 없다.
- **실제 동작:** 근거로 "작업 지시(검증: `./gradlew build` 통과)"를 든다.
- **영향:** 3-2 와 같다. 기능 영향은 없다.

판단 기록 #2, #13, #20 도 "작업 지시"를 근거로 들지만 커밋을 나누는 방식에 관한 결정이라 코드 동작과 문서 일치에는 영향이 없어 발견 사항으로 세지 않았다.

---

## 8. 확인하지 못한 항목(확인 필요)

| 항목 | 확인하지 못한 이유 |
| --- | --- |
| 대화 화면(`conversation.jsp`, `conversation.js`)의 브라우저 동작: 재연결 후 `afterId` 재조회, 종료 코드 3000 안내, 제안 버튼(판단 기록 #19, #30) | 브라우저로 화면을 실행하지 않았다. 코드에서 메시지·제안 표시는 `textContent` 로 넣는 것만 확인했다 |
| 처리하지 못한 예외의 로그 내용(`ApiExceptionHandler.java:107` 은 예외 전체를 ERROR 로 남김)에 요청 값(본문, 주소 등)이 들어가는 경우가 있는지 | 예외 종류마다 메시지가 달라 모든 경우를 만들어 보지 못했다. 정상 흐름의 실행 로그에서는 0건이었다(6.3) |
| 운영 systemd 유닛에 `ORACLE_URL`, `ORACLE_USERNAME` 이 설정되어 있는지(3-1, 7-1), `application.yml:105-107` 의 서명키 값이 현재 쓰이는지(6-1) | 저장소 밖 운영 환경이다 |
| 판단 기록 #17(`PT5S`/`PT10S` 실행 확인), #19(Playwright 실행 확인)에 적힌 구현 세션의 실행 결과 | 그 실행 기록이 저장소에 없다. #17 의 스케줄러 동작은 이 검토의 war 실행으로 따로 확인했다(0장) |

## 부록. 검토 중 관찰한 검토 범위 밖 사항

- 기존 `LoginFilter`(재사용 대상, 이번 브랜치에서 변경 없음)에 JSON 본문(`{"userid":…, "password":…}`)으로 로그인하면 500 이 난다. `LoginFilter.LoginRequest`(`src/main/java/com/chatservice/auth/filter/customfilter/LoginFilter.java:170-175`)에 기본 생성자가 없어 Jackson 역직렬화가 실패하고, 이어서 `loginRequest` 가 null 이라 `NullPointerException` 이 난다(war 실행 로그로 확인). 폼 파라미터(`userid`, `password`) 로그인은 정상이다. 설계 명세서의 기능 범위가 아니라 발견 사항으로 세지 않았다.

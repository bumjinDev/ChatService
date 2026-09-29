# Rent the Runway형 프로젝트 심층 검증 문서
작성일: 2026-09-28

## 1. 문서 목적과 현재 결론

이 문서는 Rent the Runway형 구독 의류 렌탈 서비스를 신입 백엔드 포트폴리오 프로젝트 후보로 검토하기 위해 작성한다.

현재 결론은 **백업 후보로 유지**다.

제품 정체성 자체는 충분히 강하지만, 기존 후보 문서의 중심이었던 **“창고 수령 확인 → 이용 가능량 복구 → 다음 대여 허용”** 모델을 실제 Rent the Runway 정책처럼 사용하면 안 된다.

현재 공개된 RTR Membership 흐름은 다음에 가깝다.

> 회원이 기존 상품 중 swap할 상품을 선택
> → 새 shipment를 먼저 확정
> → 기존 상품에 반환 의무가 생김
> → 기존 상품을 반환
> → 이전 swap에서 반환하기로 한 상품이 아직 돌아오지 않으면 다음 swap이 제한될 수 있음

따라서 이 후보를 살리려면 심층 모델을 **수령확인형 entitlement 복구**에서 **조건부 선교환 + 반환 의무 + overdue + 다음 swap 제한 + 반환 증거** 쪽으로 바꾸는 것이 적절하다.

표기는 다음과 같다.

- **[공식 확인]**: RTR의 공개 제품 안내, return 안내, Terms에서 확인한 내용
- **[설계 추론]**: 공식 업무 정책에서 개인 프로젝트의 백엔드 문제로 도출한 내용
- **[프로젝트 가정]**: 개인 구현에서 직접 정해야 하는 상태나 정책
- **[미확인]**: 요금제, 내부 상태, 실제 이벤트 계약처럼 공개 자료만으로 확정할 수 없는 내용

## 2. 제품 정체성

### 2.1 사용자가 이 서비스를 쓰는 이유

[공식 확인] Rent the Runway의 Membership은 회원이 정해진 spot과 shipment 조건 안에서 의류와 액세서리를 빌려 사용하고, 원하는 상품은 계속 보유하거나 구매하며, 바꾸고 싶은 상품은 swap하는 서비스다.

회원의 목적은 옷 하나를 일정 날짜에 예약하는 것이 아니라, 구독 기간 동안 여러 상품을 순환해 이용하는 것이다.

사업자의 목적은 회원에게 약속한 shipment와 spot 이용을 제공하면서 빌려간 개별 상품의 반환 책임을 추적하는 것이다.

따라서 프로젝트 정체성은 다음처럼 잡을 수 있다.

> 구독자가 정해진 shipment와 spot 조건 안에서 상품을 빌리고 일부는 계속 보유하며 일부는 교환하고, 교환 과정에서 새 이용 권리와 기존 상품 반환 책임을 함께 관리하는 구독 렌탈 서비스

### 2.2 심층 기능을 제거해도 서비스 세계가 남는가

남는다.

반환 이벤트 심층 실험을 제거해도 다음 흐름이 남는다.

- Membership
- 상품 조회
- 상품 선택
- Shipment 주문
- Rental
- 계속 보유
- Swap
- 구매

따라서 제품 자체는 기능 확대형이 아니다.

문제는 개인 프로젝트의 심층 범위를 실제 제품 정책과 얼마나 가깝게 잡을 수 있는가다.

## 3. 기존 초안의 보정

### 3.1 수령 확인 후에만 다음 대여를 허용하는 모델은 실제 RTR 정책이 아니다

기존 초안은 두 모델을 구분했다.

- A: 창고 수령 확인 후 이용 권한 복구
- B: swap 확정 등 정책상 인정한 시점에 먼저 다음 이용을 허용하고 반환 의무를 별도로 관리

이번 재검증 결과 A를 RTR의 실제 Membership 정책 중심 모델로 사용하면 안 된다.

[공식 확인] RTR의 현재 Swap 안내는 회원이 반환할 상품을 선택하고 새 shipment를 먼저 확정한 뒤 기존 상품을 돌려보내는 흐름을 설명한다.

[공식 확인] How Rent the Runway Works 페이지는 새 shipment를 주문한 뒤 보유하지 않을 상품을 24시간 안에 반환하도록 안내한다.

따라서 실제 서비스에 가까운 프로젝트 모델은 B다.

### 3.2 다음 swap 제한

[공식 확인] RTR의 Swap 페이지는 최근 주문에서 `swapping`으로 표시한 상품을 아직 보내지 않았다면 다시 swap할 수 없다고 안내한다.

이 규칙은 중요하다.

이 서비스에서는 “한 상품을 반납했으니 spot +1”이라는 단순 카운터보다 다음 관계가 더 실제 업무에 가깝다.

```text
새 shipment 권리
+
기존 상품 반환 의무
+
반환 진행 상태
+
다음 swap 허용 여부
```

### 3.3 return evidence

[공식 확인] RTR Return 안내는 회원이 기존 상품에 `Swap`을 누르고 새 주문을 한 뒤, **다음에 scan된 shipping label이 해당 반환 상품을 포함한다고 시스템이 가정**한다고 설명한다.

다른 계정과 연결된 shipping label을 사용하면 drop-off가 실시간 반영되지 않을 수 있고, RTR에 실제 도착한 뒤에야 반환이 인식될 수 있다.

이 정책은 외부 배송 증거와 내부 반환 책임의 연결을 프로젝트 문제로 만들 수 있는 근거다.

### 3.4 Terms의 반환 책임

[공식 확인] RTR Terms는 Membership 상품을 swap 또는 return으로 표시하거나 Membership을 cancel/pause할 때 반환 책임이 생기며, 기한 내 반환하지 않으면 late fee나 계정 조치 등이 가능하다고 설명한다.

개인 프로젝트에서는 실제 요금 부과를 구현할 필요가 없다. 그러나 **swap이 새 상품 이용 권리만 만드는 것이 아니라 기존 Rental의 반환 의무를 함께 만든다는 사실**을 모델에 반영할 근거가 된다.

## 4. 실제 서비스 흐름

### 4.1 Membership과 Shipment

1. Subscriber가 Membership을 이용한다.
2. 현재 billing cycle에서 사용할 수 있는 shipment와 spot 조건을 확인한다.
3. 상품을 선택해 shipment를 구성한다.
4. 일부 상품은 계속 보유할 수 있다.
5. 보유 중인 상품은 현재 이용 spot을 계속 차지한다.

### 4.2 Swap

1. Subscriber가 현재 Rental 중 반환할 상품을 선택한다.
2. 반환할 상품을 `Swap` 대상으로 표시한다.
3. 새 상품을 선택해 다음 Shipment를 확정한다.
4. 새 Shipment가 먼저 진행된다.
5. 기존 상품에는 ReturnObligation이 남는다.

### 4.3 Return

1. Subscriber가 기존 상품을 반환 포장한다.
2. 정해진 label을 사용해 UPS 또는 지원되는 pickup 경로로 보낸다.
3. drop-off scan이나 이후 배송 사건이 들어온다.
4. RTR 쪽에서 반환 상품이 실제로 인식된다.
5. ReturnObligation이 해제된다.

### 4.4 미반납과 다음 swap

1. 이전 swap에서 반환하기로 한 상품이 남아 있다.
2. 다음 swap 시점이 온다.
3. 서버는 이전 ReturnObligation이 해소됐는지 확인한다.
4. 미반납 상태라면 다음 swap을 제한할 수 있다.
5. 반환이 확인되면 overdue 또는 block 상태를 해제한다.

이 흐름이 이 후보의 심층 대상이 되어야 한다.

## 5. 개인 프로젝트에서 깊게 팔 흐름

프로젝트의 중심 흐름은 다음과 같이 수정한다.

> Swap 확정
> → 새 Shipment 생성
> → 기존 Rental에 ReturnObligation 생성
> → 외부 ReturnEvidence 수신
> → ReturnObligation 해제
> → 다음 Swap 가능 여부 갱신
> → 중복·지연·부분 반환·잘못된 label
> → BillingCycle 변경과 미해결 반환 의무의 공존
> → 재시작 후 replay와 대사

## 6. 문제의 연속성

### 6.1 문제 1: 새 shipment와 반환 의무를 어떻게 함께 확정할 것인가

Subscriber가 R1, R2, R3을 보유하고 R1과 R2를 swap한다고 가정한다.

새 Shipment S2를 만들면서 R1과 R2의 반환 의무를 생성해야 한다.

잘못된 구현은 새 Shipment만 성공하고 ReturnObligation 생성에 실패할 수 있다.

그 결과 사용자는 새 상품을 받지만 기존 상품을 반환해야 한다는 서버 상태가 없다.

따라서 첫 질문은 다음이다.

> 새 이용 권리를 먼저 열어 주는 정책에서 기존 Rental의 책임을 어떻게 잃지 않을 것인가.

### 6.2 문제 2: 반환 증거와 업무상 반환 완료는 같은가

R1과 R2를 하나의 가방에 넣기로 했다고 가정한다.

shipping label scan 하나가 발생했다고 두 Rental을 모두 반환 완료로 처리하면 실제 내용물과 불일치할 수 있다.

RTR 공개 안내도 label scan과 반환 상품 연결을 사용하지만, 잘못된 계정 label을 사용할 경우 실시간 tracking이 반영되지 않을 수 있다고 설명한다.

따라서 프로젝트에서는 다음을 구분해야 한다.

- `ReturnParcel`
- `ReturnLine`
- `ReturnEvidence`

그리고 한 parcel의 scan이 모든 line의 최종 완료를 자동 의미하는지 여부를 프로젝트 정책으로 명시해야 한다.

### 6.3 문제 3: 동일 반환의 중복·지연 이벤트

CarrierAdapter가 같은 drop-off 또는 delivery 사실을 여러 번 전달할 수 있다.

단순히 `available += 1` 또는 `obligation_count -= 1`로 구현하면 같은 Rental이 두 번 해제될 수 있다.

따라서 이벤트 ID 중복 제거뿐 아니라 `Rental R1의 ReturnObligation은 한 번만 완료될 수 있다`는 업무 멱등성이 필요하다.

### 6.4 문제 4: BillingCycle 변경과 미해결 ReturnObligation

새 billing cycle이 시작됐다고 해서 이전 cycle에서 발생한 미반납 책임을 초기화하면 안 된다.

Shipment allowance와 outstanding return obligation은 서로 다른 상태다.

따라서 다음 질문이 생긴다.

> 새 월 한도를 생성하는 작업과 이전 Rental의 책임을 어떻게 분리할 것인가.

### 6.5 문제 5: 다음 swap 제한과 늦은 반환 인식

Subscriber는 실제로 상품을 UPS에 보냈지만 잘못된 label 때문에 실시간 drop-off 인식이 되지 않을 수 있다.

서버는 아직 overdue로 판단해 다음 swap을 막을 수 있다.

그 뒤 RTR 도착 시 반환이 인식된다.

여기서 프로젝트가 볼 수 있는 것은 실제 택배 정확도가 아니라 다음이다.

- 어떤 ReturnEvidence를 신뢰할 것인가.
- 차단 상태를 언제 해제할 것인가.
- 지연된 외부 사건을 받았을 때 현재 Membership 상태를 어떻게 다시 계산할 것인가.
- 잘못된 매핑은 자동 처리할지 Operator 검토로 격리할지.

## 7. Actor / Entity / State

이 절은 [프로젝트 가정]이다.

### 7.1 Actor

- `Subscriber`: 상품 선택, Shipment 확정, Swap, Return
- `CarrierAdapter`: drop-off, transit, delivery와 같은 배송 증거
- `WarehouseAdapter`: 프로젝트에서 필요하면 실제 수령 확인 역할
- `BillingScheduler`: cycle 갱신
- `Operator`: 잘못된 매핑, 부분 반환, 수동 정정

### 7.2 Entity

- `Membership`
- `BillingCycle`
- `Shipment`
- `Rental`
- `SwapCommitment`
- `ReturnObligation`
- `ReturnParcel`
- `ReturnLine`
- `ReturnEvidence`
- `IncomingEvent`

### 7.3 상태 예시

`Rental`

```text
ALLOCATED
→ AT_HOME
→ MARKED_FOR_SWAP
→ RETURNING
→ RECEIVED
```

`ReturnObligation`

```text
OPEN
→ IN_TRANSIT
→ SATISFIED
```

예외:

```text
OPEN / IN_TRANSIT
→ OVERDUE
```

정정이 필요한 경우:

```text
→ QUARANTINED
→ OPERATOR_RESOLVED
```

`BillingCycle`

```text
OPEN
→ CLOSED
```

새 cycle은 기존 Rental과 ReturnObligation을 삭제하지 않고 별도 생성한다.

## 8. 지켜야 할 불변조건

1. Swap을 확정했다고 기존 Rental의 책임을 즉시 삭제하지 않는다.
2. 새 Shipment 생성과 반환 책임 생성 사이에 업무상 누락이 없어야 한다.
3. 하나의 Rental에 대한 ReturnObligation은 한 번만 만족될 수 있다.
4. 같은 의미의 반환 증거가 여러 event ID로 도착해도 같은 Rental을 두 번 해제해서는 안 된다.
5. BillingCycle 갱신은 이전 Rental과 outstanding ReturnObligation을 초기화하지 않는다.
6. Membership이 pause/cancel 상태가 되어도 기존 상품의 반환 사건은 계속 처리할 수 있어야 한다.
7. 다음 swap 허용 판단은 현재 shipment allowance뿐 아니라 unresolved return obligation도 함께 검사해야 한다.
8. 잘못된 label이나 매핑 불명 사건을 임의의 Rental에 자동 연결하지 않는다.
9. 과거 반환 사건이 현재 새 Rental의 상태를 뒤로 돌리거나 잘못 해제해서는 안 된다.
10. 실물 item은 동시에 두 활성 Rental에 배정되지 않는다.

## 9. 적시성이 필요한 이유

RTR형에서 적시성은 Vinted나 Instawork보다 약간 다른 형태다.

택배 운송 자체는 초 단위 처리가 필요한 업무가 아니다.

그러나 서버가 인정 가능한 반환 증거를 이미 받았는데 내부 상태 갱신이 오래 지연되면 Subscriber는 실제로 반환했음에도 다음 swap을 계속 제한받을 수 있다.

반대로 반환 책임이 아직 해소되지 않았는데 잘못된 중복 사건 때문에 block을 풀어 버리면 서비스 정책보다 많은 swap을 허용할 수 있다.

따라서 필요한 적시성은 다음에 있다.

> 외부 반환 사실을 서버가 인정한 뒤 Membership의 다음 행동 가능 여부에 반영하는 시간

이는 hard real-time이 아니다.

화면 polling, SSE, WebSocket을 먼저 정할 필요도 없다. 서버의 원본 상태가 정확하게 확정되고, 다음 명령에서 최신 상태를 검사하는 것이 우선이다.

## 10. 기술 탐구 경로

### 10.1 Swap transaction

업무 조건:

> 새 Shipment를 허용했다면 기존 Rental의 ReturnObligation도 잃지 않아야 한다.

먼저 볼 대안:

- 동일 RDB transaction에서 Shipment와 ReturnObligation 함께 생성
- 상태 변경과 event/outbox를 같은 DB에 기록

관측:

- Shipment만 존재하고 obligation이 없는 건수
- 중복 SwapCommitment
- transaction rollback

### 10.2 반환 멱등성

업무 조건:

> 한 Rental의 반환 의무는 한 번만 해제된다.

대안:

- `ReturnObligation` unique state transition
- Rental ID 기반 업무 멱등키
- event ID 중복키와 업무 키 분리

측정:

- duplicate satisfaction
- outstanding count 불일치
- replay 후 state 차이

### 10.3 외부 event 수신

초기 대안:

- HTTP 수신과 같은 transaction에서 즉시 처리
- IncomingEvent를 DB에 먼저 저장하고 worker가 처리

비교할 값:

- 처리 latency
- backlog
- 수신 성공 후 업무 반영 지연
- 프로세스 종료 후 유실
- replay 복구시간

처리량이나 격리 필요성이 실제로 확인되기 전에는 broker를 필수로 두지 않는다.

### 10.4 BillingCycle

cycle 갱신은 monthly allowance를 새로 만드는 작업이고, 이전 Rental 책임을 초기화하는 작업이 아니다.

따라서 cycle row와 Rental/ReturnObligation을 분리해 관리한다.

event sourcing을 사용해야 할 이유는 아직 없다. RDB row와 history table로도 충분히 검증할 수 있는지 먼저 본다.

### 10.5 JVM

RTR형에서 JVM 탐구 가능성은 주로 event burst와 외부 adapter 지연에서 생긴다.

관측할 값:

- worker queue
- event parsing allocation
- DB connection wait
- lock wait
- 처리 지연
- GC

Virtual Thread나 Kafka는 관측된 병목이 있을 때만 비교한다.

## 11. 실험 방향

아래 수치는 실제 RTR 트래픽이 아니라 [프로젝트 가정]이다.

### 11.1 기본 데이터

- Subscriber 100명
- 각 Subscriber의 활성 Rental 3~5개
- ReturnObligation 수백 건
- 초당 ReturnEvidence 5개
- 동시 Swap 명령 10개

### 11.2 반환 event burst

짧은 시간 안에 ReturnEvidence 100개를 주입한다.

동일 Subscriber의 반환과 새 Swap을 함께 집중시켜 회원 단위 경합을 만든다.

### 11.3 중복과 역전

- event 중복 0%, 10%, 50%
- 동일 업무 의미 + 다른 event ID
- 역전 event
- 0초, 5초, 60초 지연
- 매핑 불명
- 부분 반환

측정:

- duplicate obligation release
- 잘못 해제된 Rental
- unresolved obligation 수
- next swap 오판
- quarantine 수
- replay 후 불일치

### 11.4 cycle boundary

이전 cycle에서 생성한 outstanding ReturnObligation을 남긴 상태에서 새 BillingCycle을 생성한다.

확인:

- 월 한도만 갱신되는가.
- 이전 반환 의무가 사라지지 않는가.
- 다음 Swap 제한 판단이 올바른가.

### 11.5 프로세스 종료

다음 지점에서 종료한다.

1. IncomingEvent 저장 후
2. obligation 상태 변경 전
3. obligation commit 후 응답 전
4. cycle 갱신 중

재시작 후 미처리 event를 다시 찾아 처리할 수 있는지 확인한다.

## 12. 기존 ChatService와의 차이

공통점은 있다.

- 사용 한도
- 상태 해제
- 시간 기반 복구
- 중복 요청

따라서 단순히 `available spot` 카운터를 증가·감소하는 프로젝트로 만들면 ChatService와 차이가 약하다.

차이를 만드는 부분은 다음이다.

ChatService:

> 클라이언트 연결 상태와 서버가 관리하는 좌석 permit

RTR형:

> 외부 세계의 반환 증거와 내부 Rental 책임, 새 Shipment 권리, 다음 Swap 제한의 연결

즉 연결 lifecycle이 아니라 **물품 책임과 사용자 권한의 lifecycle**을 다룬다.

다만 세 후보 중에서는 외부 반환 증거 simulator가 심층 문제에 가장 가깝게 들어온다. 이 점은 분명한 약점이다.

## 13. 개인 프로젝트 축소 범위

### 얇게 구현

- 고정 Membership plan
- 의류 목록
- Shipment
- Rental
- 보유 상품 조회
- Swap
- 현재 shipment allowance 표시

### 깊게 구현

- SwapCommitment
- ReturnObligation
- ReturnEvidence 수신
- 중복·지연·매핑 불명
- 다음 Swap 제한
- BillingCycle과 outstanding obligation 분리
- replay와 복구

### 후속 실험

- Membership pause/cancel과 outstanding Rental
- 여러 event worker
- Operator 보정
- WarehouseAdapter 추가
- 더 복잡한 partial return

### 제외

- 날짜별 Reserve 예약
- 창고 WMS
- 실제 세탁
- 검수 품질
- 재고 최적화
- 운송 경로 최적화
- 실제 결제
- late fee 계산
- 추천
- 사이즈 예측

## 14. 장점과 위험

### 장점

1. 구독, 대여, 보유, swap이라는 제품 정체성이 분명하다.
2. 새 이용 권리와 이전 Rental 책임을 동시에 다뤄야 하는 업무 규칙이 있다.
3. 외부 event의 중복·지연·매핑 문제를 자연스럽게 다룰 수 있다.
4. BillingCycle과 장기 Rental 책임이 서로 다른 시간축을 가지므로 단순 TTL과 다른 문제가 생긴다.

### 위험

1. 기존 수령확인형 A안을 그대로 쓰면 실제 RTR Membership 정책과 거리가 크다.
2. 실제 정책에 가깝게 만들수록 ReturnObligation과 외부 배송 증거 모델이 필요해져 범위가 커진다.
3. Carrier/Warehouse simulator가 프로젝트의 핵심 경계에 가까이 들어온다.
4. 택배 자체는 느린 업무이므로 “실시간 서비스”라는 설명을 과장하면 안 된다.
5. 창고·세탁·재고까지 확장하면 사용자가 이미 제외한 WMS 도메인으로 이동한다.

## 15. 현재 평가

현재 RTR형은 **조건부 통과 / 백업 후보**로 본다.

기존 문서의 A안을 주력으로 삼는 조건에서는 우선순위를 낮춰야 한다.

반대로 다음 구조를 받아들인다면 충분히 프로젝트가 될 수 있다.

```text
Swap 확정
→ 새 Shipment 허용
→ 이전 Rental 반환 의무 유지
→ ReturnEvidence 수신
→ obligation 해제
→ overdue/다음 swap 제한
→ cycle 변경과 미해결 책임 공존
→ replay/복구
```

다만 Vinted와 Instawork가 이미 탐색 종료 조건을 충족하기 때문에, 더 많은 외부 증거 모델과 정책 가정이 필요한 RTR을 굳이 1순위로 올릴 근거는 현재 부족하다.

## 16. 공식 자료

확인일: 2026-09-28

- Rent the Runway, Swapping for new items with your membership  
  https://www.renttherunway.com/swap
- Rent the Runway, How to Return  
  https://www.renttherunway.com/returns
- Rent the Runway, How Rent the Runway Works  
  https://www.renttherunway.com/how_renting_works
- Rent the Runway, Clothing Subscription  
  https://www.renttherunway.com/clothing-subscription
- Rent the Runway, Terms of Service  
  https://www.renttherunway.com/pages/termsofservice

## 17. 기준 자료와 남은 확인 사항

이 문서는 다음 첨부 자료 전체를 참고했다.

- `서비스 도메인 우선 실시간 백엔드 웹 프로젝트 조사 — 새 세션용 기획 프롬프트 v5.md`
- `프로젝트_기획의_실제_고민과_의도.txt`
- `1_새프로젝트기획_참고_커뮤니티글정리.txt`
- `ChatService_포트폴리오.pptx`
- `03_Rent_the_Runway형_우선선택지.md`
- `5. 문서 작성 양식.md`

남은 확인 사항:

- 요금제별 정확한 spot/shipment 규칙
- swap을 확정했을 때 내부적으로 어떤 시점에 어떤 권리를 차감하는지
- `overdue` 판정의 정확한 내부 조건
- drop-off scan과 실제 개별 Rental 매핑의 내부 계약
- Warehouse 수령과 item 단위 검수의 실제 이벤트 구조
- 실제 event ID와 재전달 보장
- 실제 트래픽
- 내부 원장 구조

이 부분은 공개 자료로 확인하지 못했으므로 실제 RTR의 내부 구현처럼 문서에 확정해서 쓰지 않는다.

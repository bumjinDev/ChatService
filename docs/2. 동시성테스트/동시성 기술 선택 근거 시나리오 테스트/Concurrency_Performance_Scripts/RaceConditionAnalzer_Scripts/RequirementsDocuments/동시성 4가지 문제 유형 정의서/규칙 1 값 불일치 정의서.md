# 규칙 1: 값 불일치 (Lost Update) 정의서

> [!IMPORTANT]
> **문서 목적**: 경쟁 상태 분석에서 '값 불일치(Lost Update)' 현상의 완전한 정의, 발생 원인, 탐지 방법을 명확히 정의합니다.

---

## 1. 개념 정의

### 1.1 Lost Update란?

> [!WARNING]
> **Lost Update**: 어떤 스레드가 자신의 연산을 수행한 후 데이터가 특정 상태가 될 것이라고 예상했지만, 그 사이에 다른 스레드의 개입으로 인해 자신이 기대했던 값과 다른 값으로 최종 기록되는 현상

### 1.2 핵심 특징
- **기대값과 실제값의 불일치**: `curr_people ≠ expected_people`
  - `curr_people`: JOIN_SUCCESS_EXISTING 이벤트에서 기록된 최종 인원수
  - `expected_people`: PRE_JOIN_CURRENT_STATE에서 읽은 prev_people + 1 (스레드가 기대한 결과값)
- **동시성 문제의 전형적 사례**: 여러 스레드가 같은 데이터를 동시 수정
- **데이터 정합성 위반**: 실제 발생한 변경사항이 누락되거나 덮어쓰여짐

---

## 2. 발생 원인 및 메커니즘

### 2.1 근본 원인

#### 2.1.1 동시성 제어 부재
- **락(Lock) 없는 읽기-수정-쓰기 패턴**: 여러 스레드가 동시에 같은 데이터를 읽고 수정
- **원자적 연산 부족**: 읽기와 쓰기가 분리되어 중간에 다른 스레드가 개입 가능
- **격리 수준 부족**: 데이터베이스 트랜잭션 격리가 불충분

#### 2.1.2 타이밍 의존성
```
경합 상황에서의 타이밍:
Thread A: READ(5) → COMPUTE(6) → WRITE(6)
Thread B:     READ(5) → COMPUTE(6) → WRITE(6)
결과: 2명이 입장했지만 1명만 증가
```

### 2.2 발생 메커니즘

#### 2.2.1 전형적인 Lost Update 시나리오
1. **초기 상태**: `curr_people = 5`
2. **스레드 A**: `prev_people = 5` 읽기 → `expected_people = 6` 계산
3. **스레드 B**: `prev_people = 5` 읽기 → `expected_people = 6` 계산  
4. **스레드 A**: `curr_people = 6` 저장
5. **스레드 B**: `curr_people = 6` 저장 (A의 업데이트 덮어쓰기)
6. **결과**: 2명 입장했지만 최종값은 6 (1명 증가만 반영)

#### 2.2.2 시스템 레벨에서의 원인
- **캐시 일관성 문제**: CPU 캐시 간 데이터 동기화 지연 (`synchronized` 키워드로 해결 가능)
- **메모리 가시성**: 한 스레드의 변경이 다른 스레드에게 즉시 보이지 않음 (`volatile` 키워드로 해결 가능)

---

## 3. 실제 사례 및 시나리오

### 3.1 RoomJoinServiceIfElse에서의 발생 상황

#### 3.1.1 3개 핵심 이벤트 기반 Lost Update
```java
// 1. PRE_JOIN_CURRENT_STATE: 임계 구역 진입하여 현재 상태 읽기
Thread A: PRE_JOIN_CURRENT_STATE currentPeople=5 maxPeople=20
Thread B: PRE_JOIN_CURRENT_STATE currentPeople=5 maxPeople=20
// 두 스레드 모두 expected_people = 6으로 기대

// 2-A. JOIN_SUCCESS_EXISTING: 성공적으로 입장 완료
Thread A: JOIN_SUCCESS_EXISTING currentPeople=6 maxPeople=20
Thread B: JOIN_SUCCESS_EXISTING currentPeople=6 maxPeople=20
// 결과: 2명이 입장했지만 최종값은 6 (Lost Update 발생!)
```

#### 3.1.2 파싱 대상 이벤트만으로 동시성 분석
- **PRE_JOIN_CURRENT_STATE**: 임계 구역 진입 후 현재 상태 읽기 (모든 스레드가 성공적으로 임계 구역 진입)
- **JOIN_SUCCESS_EXISTING**: 임계 구역 종료 - 정원 내 여유 있음 (getCurrentPeople() < getMaxPeople(), 입장 허용)
- **JOIN_FAIL_OVER_CAPACITY_EXISTING**: 임계 구역 종료 - 정원 초과 상태 (getCurrentPeople() >= getMaxPeople(), 입장 거부)

### 3.2 실제 로그 데이터 패턴

#### 3.2.1 전형적인 Lost Update 로그 시퀀스
```
Thread-A: PRE_JOIN_CURRENT_STATE currentPeople=5 maxPeople=20
Thread-B: PRE_JOIN_CURRENT_STATE currentPeople=5 maxPeople=20
Thread-A: JOIN_SUCCESS_EXISTING currentPeople=6 maxPeople=20
Thread-B: JOIN_SUCCESS_EXISTING currentPeople=6 maxPeople=20  ← Lost Update!
```

#### 3.2.2 Python 파서가 탐지하는 패턴
- **expected_people vs curr_people 불일치**: `expected_people (= prev_people + 1) ≠ curr_people`
- **동일한 prev_people 읽기**: 여러 스레드가 같은 getCurrentPeople() 값 확인
- **임계 구역 겹침**: `prev_entry_time ~ curr_entry_time` 구간 중복

### 3.3 방별 발생 패턴

#### 3.3.1 인기 방에서의 집중 발생
- **1135번 방**: 테스트에서 가장 많은 동시 접속 시도
- **소규모 방 (정원 3-5명)**: 상대적으로 높은 Lost Update 비율
- **대형 방 (정원 100명+)**: 절대 건수는 많지만 비율은 낮음

#### 3.3.2 시간대별 특성
```
높은 동시 접속 구간:
- Bin 1-3: 테스트 초기 대량 요청 몰림
- Bin 8-10: 테스트 후반 마지막 입장 시도
- 특정 시간 구간: 정확히 같은 시점에 여러 스레드 진입
```

---

## 4. 비즈니스 영향 분석

### 4.1 직접적 영향

#### 4.1.1 채팅방 서비스 운영상 문제
- **실제 입장자 vs 기록 불일치**: 2명이 입장했는데 1명만 증가로 기록
- **채팅방 상태 정보 부정확**: 실제 참여자 수와 표시되는 currentPeople 값의 차이
- **사용자 혼란**: 같은 시점에 입장한 여러 사용자가 서로 다른 인원수를 확인

#### 4.1.2 ChatSessionRegistryIfElse 데이터 정합성 문제
- **메모리 기반 방 상태 불일치**: 실제 입장 횟수와 저장된 currentPeople 값의 차이
- **동시 접속 통계 왜곡**: 실제 이용률과 기록된 이용률의 불일치
- **방 정원 관리 혼란**: 실제보다 적게 카운트되어 추가 입장이 잘못 허용될 가능성

### 4.2 시스템 확장성 영향

#### 4.2.1 RoomJoinServiceIfElse의 근본적 설계 문제
```java
// 🚨 Lost Update 발생 지점 1: 읽기-검증-수정 패턴
if (chatRoom.get().getCurrentPeople() >= chatRoom.get().getMaxPeople()) {
    // 여러 스레드가 동시에 같은 currentPeople 값을 읽음
    // → 모두 false로 평가되어 동시 진행
    logStructured(logger, "JOIN_FAIL_OVER_CAPACITY_EXISTING", ...);
    throw new RoomBadJoinFullException("입장 실패: 방 정원이 가득 찼습니다.");
} else {
    // 🚨 Lost Update 발생 지점 2: 비원자적 증가 연산
    chatSessionRegistry.incrementCurrentPeople(roomNumber);
    // → 여러 스레드가 같은 초기값 기준으로 +1 수행
}
```

**나노초 정밀도 측정의 역할:**
```java
// 🔧 나노초 정밀도로 매우 정확한 타이밍 측정
HighPrecisionTimestamp criticalEnter = new HighPrecisionTimestamp();
logStructured(logger, "PRE_JOIN_CURRENT_STATE", ..., prev_people, ...);

// 🚨 하지만 정작 핵심 연산은 여전히 비원자적
chatSessionRegistry.incrementCurrentPeople(roomNumber);  // ← Lost Update 발생 지점

HighPrecisionTimestamp criticalLeave = new HighPrecisionTimestamp();
logStructured(logger, "JOIN_SUCCESS_EXISTING", ..., curr_people, ...);
```

**측정의 정밀성 vs 실제 동시성 제어의 한계**: 
- **고정밀 측정**: 나노초 단위로 매우 정확하게 언제 뭐가 일어났는지 기록
- **부분적 동기화**: `incrementCurrentPeople()`은 동기화했지만 조건 검사는 여전히 분리됨
- **근본 문제 지속**: 정밀한 측정으로 문제를 더 명확히 볼 수 있지만, 설계상 문제는 여전함

### 4.3 3개 이벤트로 측정되는 실제 손실

#### 4.3.1 동시성 분석 범위
- **임계 구역**: `PRE_JOIN_CURRENT_STATE` ~ `JOIN_SUCCESS_EXISTING`
- **탐지 정확도**: 나노초 정밀도 타임스탬프 기반
- **분석 단위**: 방별, 구간별 집계

#### 4.3.2 비즈니스 메트릭 영향
```
예상 손실 규모 (3개 이벤트 기준):
- 총 입장 시도: PRE_JOIN_CURRENT_STATE 이벤트 수
- 성공적 완료: JOIN_SUCCESS_EXISTING 이벤트 수
- Lost Update: expected_people != curr_people 케이스
```

---

## 5. 탐지 및 시각화 도구

### 5.1 로그 전처리 도구
- **파일**: `racecondition_event_preprocessor.py`
- **목적**: 원시 로그에서 핵심 이벤트 추출 및 페어링
- **문서 유형**: 데이터 전처리 도구 (상세 사용법은 별도 매뉴얼 참조)

### 5.2 Lost Update 탐지 도구
- **파일**: `racecondition_anomaly_detector.py`
- **목적**: 전처리된 데이터에서 Lost Update 패턴 탐지
- **문서 유형**: 이상 현상 분석 도구 (상세 사용법은 별도 매뉴얼 참조)

### 5.3 시각화 도구
- **파일**: `generate_race_condition_report.py`
- **목적**: Lost Update 발생 패턴을 시각적으로 분석
- **문서 유형**: 차트 생성 도구 (상세 사용법은 별도 매뉴얼 참조)

---

## 6. 분석 방법론

### 6.1 개별 케이스 분석

#### 6.1.1 분석 요소
- **발생 시점**: `curr_entry_time` (임계 구역 종료 시각)
- **영향받은 스레드**: `user_id`
- **오차 크기**: `lost_update_diff` (기대값과 실제값의 차이)
- **방별 집중도**: `roomNumber` 기준 분포 분석

#### 6.1.2 분석 지표
- **불일치 크기**: `|lost_update_diff|`의 절댓값으로 오차 정도 파악
- **빈도 패턴**: 특정 시간대나 방에서의 집중 발생 여부
- **연관성 분석**: 다른 규칙(경합 발생, 정원 초과)과의 상관관계

### 6.2 통계적 분석

#### 6.2.1 방별/구간별 집계
```python
# generate_race_condition_report.py에서 실제 수행하는 집계
self.rule_stats['lost_update'] = len(self.df_result[
    self.df_result['anomaly_type'].str.contains('값 불일치', na=False)
])

# 방별 통계 계산
room_datasets = {}
for room in rooms:
    room_subset = self.df_preprocessor[
        self.df_preprocessor['roomNumber'] == room
    ].sort_values('curr_entry_time')
```

#### 6.2.2 집계 분석
- **방별 집계**: Lost Update 발생 빈도의 방별 분포
- **구간별 집계**: `bin` 단위 시간 구간별 발생 패턴
- **통계적 요약**: 평균, 최소값, 최대값, 표준편차 등 기본 통계

---

## 7. 예방 및 해결책

### 7.1 동시성 제어 메커니즘

#### 7.1.1 ReentrantLock을 이용한 원자적 연산
```java
// ChatSessionRegistryIfElse 개선 방안
private final ReentrantLock roomLock = new ReentrantLock();

public boolean tryJoinRoom(int roomNumber) {
    roomLock.lock();
    try {
        ChatRoom room = getRoom(roomNumber);
        if (room != null && room.getCurrentPeople() < room.getMaxPeople()) {
            room.setCurrentPeople(room.getCurrentPeople() + 1);
            return true;  // 입장 성공
        }
        return false;  // 입장 실패
    } finally {
        roomLock.unlock();
    }
}
```

#### 7.1.2 synchronized 키워드 활용
```java
// RoomJoinServiceIfElse 개선 방안
public synchronized boolean confirmJoinRoom(int roomNumber, String userId) {
    ChatRoom chatRoom = chatSessionRegistry.getRoom(roomNumber);
    if (chatRoom == null) return false;
    
    // 조건 검사와 상태 변경을 하나의 synchronized 블록에서 처리
    if (chatRoom.getCurrentPeople() >= chatRoom.getMaxPeople()) {
        return false;  // 입장 실패
    } else {
        chatSessionRegistry.incrementCurrentPeople(roomNumber);
        return true;   // 입장 성공
    }
}
```

#### 7.1.3 Semaphore를 이용한 동시 접근 제어
```java
// 방별 세마포어를 이용한 동시성 제어
private final Map<Integer, Semaphore> roomSemaphores = new ConcurrentHashMap<>();

public boolean tryJoinRoomWithSemaphore(int roomNumber) {
    Semaphore roomSemaphore = roomSemaphores.computeIfAbsent(
        roomNumber, k -> new Semaphore(1)  // 방당 1개 permit
    );
    
    try {
        if (roomSemaphore.tryAcquire()) {  // 세마포어 획득 시도
            try {
                ChatRoom room = getRoom(roomNumber);
                if (room != null && room.getCurrentPeople() < room.getMaxPeople()) {
                    room.setCurrentPeople(room.getCurrentPeople() + 1);
                    return true;  // 입장 성공
                }
                return false;  // 입장 실패
            } finally {
                roomSemaphore.release();  // 세마포어 해제
            }
        }
        return false;  // 세마포어 획득 실패
    } catch (Exception e) {
        return false;
    }
}
```

---

## 8. 관련 문서 및 참조

### 8.1 연관 규칙
- **규칙 2 (경합 발생)**: Lost Update의 원인이 되는 경합 상황
- **규칙 3 (정원 초과)**: Lost Update로 인한 비즈니스 규칙 위반
- **규칙 4 (상태 전이)**: Lost Update의 또 다른 형태

### 8.2 구현 참조
- `racecondition_anomaly_detector.py`: 실제 탐지 로직 구현
- `generate_race_condition_report.py`: 시각화 및 리포팅
- `anomaly_result.csv`: 탐지 결과 데이터

---

> [!NOTE]
> **문서 상태**: 최종 버전  
> **담당자**: 정범진
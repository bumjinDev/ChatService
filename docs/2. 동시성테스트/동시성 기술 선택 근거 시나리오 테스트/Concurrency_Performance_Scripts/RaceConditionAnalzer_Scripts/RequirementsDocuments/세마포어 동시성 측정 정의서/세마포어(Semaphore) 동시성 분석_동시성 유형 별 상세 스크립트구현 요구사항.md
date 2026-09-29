# Semaphore 전용 분석기 수정 요구사항 정의서

## 개요

기존의 4가지 동시성 규칙을 모두 검사하던 분석기를 Semaphore의 특성에 맞게 수정하여, 오직 정원 초과 검증에만 집중하는 전용 분석기로 변경한다. 이는 Semaphore의 CAS(Compare-And-Swap) 기반 동작 특성과 허가(Permit) 시스템의 고유한 설계 철학을 반영한 결정이다.

## 1. 분석 규칙의 재정의

### 1.1 기존 4가지 규칙에서 1가지 규칙으로 축소

기존 synchronized와 ReentrantLock 분석에서 사용하던 4가지 규칙 중 오직 규칙 3(정원 초과)만을 유지한다. 나머지 3가지 규칙은 Semaphore의 설계 철학과 맞지 않거나 당연한 현상이므로 제외한다.

**규칙 1 (값 불일치) 제외 사유:** CAS 메커니즘의 특성상 여러 스레드가 동시에 허가를 획득하고 임계구역에서 비선형적으로 공유 변수를 갱신하는 것은 자연스러운 현상이다. 이를 오류로 분류하는 것은 Semaphore의 본질을 왜곡한다.

**규칙 2 (경합 발생) 제외 사유:** Semaphore는 애초에 N개 스레드의 동시 실행을 허용하는 설계이므로, 임계구역의 시간적 겹침은 정상 동작이다. 상호 배제를 목적으로 하는 Lock 방식과는 근본적으로 다른 패러다임이다.

**규칙 4 (상태 전이 오류) 제외 사유:** 이는 본질적으로 규칙 1과 동일한 현상의 다른 관점일 뿐이다. CAS 기반 동시성 제어에서는 순차적 상태 전이보다는 최종 일관성이 더 중요한 지표이다.

**규칙 3 (정원 초과) 유지 사유:** Semaphore의 핵심 목적은 최대 동시 접근 수를 제한하는 것이다. 따라서 curr_people이 max_people을 초과하는 것은 Semaphore가 제대로 작동하지 않았음을 의미하는 심각한 오류이다.

### 1.2 Semaphore 전용 검증 로직

정원 초과 검증은 단순하고 명확한 조건으로 수행한다. curr_people > max_people 조건을 만족하는 모든 레코드를 정원 초과 오류로 분류하며, 이때 추가적인 복잡한 조건 검사는 수행하지 않는다.

## 2. 입력 데이터 구조 변경 대응

### 2.1 입력 파일 스펙 및 구조 차이점

**입력 파일 정보:**
- 파일명: `preprocessor_semaphore.csv`
- 총 행수: 200행
- 총 컬럼수: 10개 컬럼
- 정확한 컬럼 구성: `roomNumber, bin, user_id, prev_people, curr_people, max_people, room_entry_sequence, join_result, true_critical_section_nanoTime_start, true_critical_section_nanoTime_end`

**기존 전처리 파일과의 핵심 차이점:**

Semaphore 전처리 파일에서는 `expected_people` 컬럼이 완전히 제거되었다. 이는 CAS 기반 시스템에서는 개별 스레드의 기대값이라는 개념 자체가 의미가 없기 때문이다. 기존 분석기에서 이 컬럼에 의존하던 모든 로직은 삭제되어야 한다.

**이벤트 매핑 변경사항:**
- 기존: `PRE_JOIN_CURRENT_STATE` → 새로운: `JOIN_PERMIT_ATTEMPT`
- 기존: `JOIN_SUCCESS_EXISTING` → 새로운: `JOIN_PERMIT_SUCCESS`  
- 기존: `JOIN_FAIL_OVER_CAPACITY_EXISTING` → 새로운: `JOIN_PERMIT_FAIL`

이러한 이벤트 명칭 변경은 허가 기반 시스템의 특성을 반영한 것이며, 분석기에서 이벤트 타입을 참조하는 모든 로직을 새로운 명칭에 맞게 수정해야 한다.

`join_result` 컬럼이 SUCCESS 또는 FAIL 값으로 최종 결과를 명시적으로 나타내며, 이는 tryAcquire()의 즉시 반환 특성을 잘 보여준다.

### 2.2 데이터 호환성 처리 및 컬럼 매핑

기존 분석기에서 `expected_people` 컬럼에 의존하던 모든 로직은 완전히 제거해야 한다. 이 컬럼을 참조하는 규칙 1(값 불일치) 검사 로직과 관련 계산식들을 모두 삭제한다.

`room_entry_sequence`를 활용한 순차적 분석도 Semaphore에서는 의미가 없으므로 관련 로직을 삭제한다. 기존 분석기에서 이 값을 사용해 expected_curr_by_sequence를 계산하던 규칙 4 로직도 완전히 제거한다.

나노초 정밀도 데이터인 `true_critical_section_nanoTime_start`와 `true_critical_section_nanoTime_end`는 여전히 유효하므로 유지한다. 이는 tryAcquire() 실행 시간 측정에 활용될 수 있다.

## 3. 컬럼 구조 최적화

기존 32개 컬럼에서 대폭 축소하여 15개 내외의 필수 컬럼만 유지한다. 제거할 컬럼들은 lost_update 관련 모든 컬럼, contention 관련 모든 컬럼, sequence 관련 모든 컬럼, intervening_users 관련 모든 컬럼이다.

유지할 컬럼들은 기본 정보인 roomNumber, bin, user_id, prev_people, curr_people, max_people과 Semaphore 특화 정보인 join_result, 나노초 타임스탬프, 그리고 정원 초과 관련 컬럼들인 over_capacity_amount, over_capacity_curr, over_capacity_max이다.

## 4. 출력 결과 변경 사항

### 4.1 Excel 파일 구조 단순화

정원 초과가 발생한 레코드만 별도 행으로 기록하고, 정상적으로 처리된 레코드들은 기본값으로 채워 넣는다. 이는 분석의 초점을 정원 초과 사례에만 맞추기 위함이다.

anomaly_type 컬럼에는 정원 초과 오류만 기록되거나 빈 값이 들어간다. 복잡한 복합 오류 타입은 더 이상 존재하지 않는다.

### 4.2 Semaphore 전용 통계 출력 구성

기존의 4가지 규칙별 발생 건수와 비율 대신, Semaphore의 핵심 지표들을 출력한다. 

**핵심 통계 지표:**
- **허가 획득 성공률**: `join_result == 'SUCCESS'` 건수 / 전체 요청 수
- **허가 획득 실패율**: `join_result == 'FAIL'` 건수 / 전체 요청 수  
- **정원 초과 오류 발생률**: `curr_people > max_people` 건수 / 전체 요청 수

**성능 관련 통계:**
- **총 소요 시간 통계**: `true_critical_section_nanoTime_end - true_critical_section_nanoTime_start`의 평균, 최소, 최대, 중앙값
- **tryAcquire() 실행 시간**: 나노초 단위로 측정된 실행 시간의 기본 통계
- **CPU 사용량 관련 지표**: 가능하다면 시스템 리소스 사용량 포함

**출력 형태 예시:**
```
=== Semaphore 분석 결과 ===
전체 요청 수: 200건
허가 획득 성공: 180건 (90.0%)
허가 획득 실패: 20건 (10.0%)
정원 초과 오류: 0건 (0.0%)

=== 성능 지표 ===
평균 실행 시간: 1,250 ns
최소 실행 시간: 800 ns  
최대 실행 시간: 2,100 ns
중앙값 실행 시간: 1,200 ns
```

## 5. 검증 및 테스트 방향

### 5.1 정원 초과 검증의 정확성

수정된 분석기가 실제로 정원 초과 사례를 정확히 탐지하는지 확인해야 한다. 특히 curr_people > max_people 조건이 올바르게 작동하는지, 그리고 경계값에서의 처리가 정확한지 검증한다.

### 5.2 성능 및 효율성

불필요한 로직들을 제거함으로써 분석기의 실행 속도가 향상되어야 한다. 또한 메모리 사용량도 줄어들 것으로 예상된다.

### 5.3 결과 파일 검증

생성된 Excel 파일이 올바른 구조를 가지는지, 그리고 실제 Semaphore 테스트 결과를 정확히 반영하는지 확인한다. 특히 정원 초과가 0건으로 나타나야 한다는 예상 결과와 일치하는지 중점적으로 확인한다.

## 6. 기대 효과 및 의의

이러한 수정을 통해 Semaphore의 고유한 특성을 올바르게 분석할 수 있는 전용 도구를 확보할 수 있다. 기존 Lock 방식과 동일한 기준으로 평가하던 한계를 벗어나, Semaphore 본연의 목적인 동시 접근 수 제어 능력에만 집중할 수 있게 된다.

또한 분석 결과의 해석도 더욱 명확해진다. 정원 초과 오류가 발생하지 않는다면 Semaphore가 제대로 작동하고 있다는 것이고, 발생한다면 구현상의 문제가 있다는 것으로 단순하고 명확하게 판단할 수 있다.

이는 궁극적으로 각 동시성 제어 방식의 본질적 차이를 더 정확히 이해하고, 적절한 상황에서 올바른 선택을 할 수 있는 기반을 제공한다는 점에서 의미가 있다.
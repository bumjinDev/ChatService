# RaceCondition 분석 결과 파일 요구사항 명세 - 1번째 섹션 (시트 1)

## 1. 배경 및 목적

Race Condition 발생 상황에서 나타나는 이상 현상(Anomaly)을 체계적으로 분석하여, 각 동시성 제어 방식의 효과성을 검증하고 시스템의 데이터 정합성 문제를 명확히 파악한다. 원본 데이터의 무결성을 유지하면서 요구사항에 명시된 4가지 규칙에 따라 이상 현상을 탐지하고 분류한다.

## 2. 핵심 분석 원칙

### 2.1 데이터 처리 원칙
- **원본 데이터 보존**: 입력 CSV 데이터의 모든 항목을 수정 없이 그대로 사용
- **계산 최소화**: 요구사항에 명시된 계산만 수행하고 추가적인 데이터 변환이나 수정 금지
- **완전성 보장**: 탐지된 모든 이상 현상을 빠짐없이 기록

### 2.2 분석 범위
- **분석 단위**: 각 방(roomNumber)별로 독립적으로 분석
- **시간 기준**: prev_entry_time과 curr_entry_time을 기준으로 임계 구역 판별
- **정밀도**: 나노초 단위 데이터가 있을 경우 추가 정밀 분석 수행

## 3. 이상 현상 탐지 규칙 상세

### 3.1 규칙 1: 값 불일치 (Lost Update)

#### 3.1.1 탐지 조건
- **조건**: `curr_people` ≠ `expected_people` (단, expected_people이 max_people을 초과하는 경우 max_people로 제한)
- **의미**: 다른 스레드의 작업으로 인해 내가 의도한 갱신이 누락되거나 덮어쓰여진 상태를 식별한다

#### 3.1.2 기록 항목
| 항목명 | 데이터 타입 | 설명 | 계산식 |
|--------|-------------|------|--------|
| lost_update_expected | Float | 기대했던 결과값 | `MIN(expected_people, max_people)` |
| lost_update_actual | Float | 실제 기록된 값 | `curr_people` |
| lost_update_diff | Float | 기대값과 실제값의 차이 | `curr_people - expected` |

### 3.2 규칙 2: 경합 발생 자체 (Contention Detected)

#### 3.2.1 탐지 조건
- **조건**: 두 개 이상의 스레드가 자신들의 '데이터 갱신 추적 구간' (`PRE_JOIN_CURRENT_STATE` ~ `JOIN_SUCCESS_EXISTING`)을 1 나노초라도 서로 공유하는 경우
- **의미**: 최종 결과 값에 오류가 없더라도, 동시성 제어의 부재로 인해 잠재적 위험에 노출된 모든 상황을 식별한다

#### 3.2.2 경합 그룹 판별 로직
```
FOR each thread A:
  FOR each other thread B:
    IF NOT (A.curr_entry_time < B.prev_entry_time OR B.curr_entry_time < A.prev_entry_time):
      THEN A and B are in contention
```

#### 3.2.3 기록 항목
| 항목명 | 데이터 타입 | 설명 | 계산식 |
|--------|-------------|------|--------|
| contention_group_size | Float | 경합 그룹의 크기 | 겹치는 스레드 수 |
| contention_user_ids | String | 경합 그룹의 user_id 목록 | 쉼표로 구분된 user_id 리스트 |

### 3.3 규칙 3: 정원 초과 오류 (Capacity Exceeded Error)

#### 3.3.1 탐지 조건
- **조건**: 최종 갱신된 `curr_people` 값이 방의 `max_people` 값을 초과한 경우
- **의미**: 시스템이 허용해서는 안 되는, 비즈니스 규칙을 명백히 위반한 심각한 오류를 식별한다

#### 3.3.2 기록 항목
| 항목명 | 데이터 타입 | 설명 | 계산식 |
|--------|-------------|------|--------|
| over_capacity_amount | Float | 초과 인원수 | `curr_people - max_people` |
| over_capacity_curr | Float | 실제 인원수 | `curr_people` |
| over_capacity_max | Float | 최대 정원 | `max_people` |

### 3.4 규칙 4: 상태 전이 오류 (Stale Read / Inconsistent State)

#### 3.4.1 탐지 조건
- **조건**: 해당 방의 모든 요청을 `curr_entry_time` 기준으로 정렬하여 순번(N)을 부여했을 때, 특정 스레드의 `curr_people` 값이 (N-1) + 초기 인원 값과 다른 경우
- **실제 구현**: `curr_people` ≠ `1 + room_entry_sequence` (room_entry_sequence는 이미 원본 데이터에 있는 값 사용)
- **의미**: 스레드가 본인 위치에서 갱신을 하였을 때 올바르게 갱신되어야 할 값으로 갱신이 안된 상태를 의미한다

#### 3.4.2 기록 항목
| 항목명 | 데이터 타입 | 설명 | 계산식 |
|--------|-------------|------|--------|
| expected_curr_by_sequence | Float | 시간 순서상 기대되었던 결과값 | `1 + room_entry_sequence` |
| actual_curr_people | Float | 실제로 기록된 결과값 | `curr_people` |
| curr_sequence_diff | Float | 시간 순서 기대값과 실제값의 차이 | `curr_people - expected_curr_by_sequence` |
| sorted_sequence_position | Float | curr_entry_time 기준 실제 처리 완료 순번 | `room_entry_sequence` |

## 4. 임계 구역 상세 분석

### 4.1 진짜 임계 구역 (True Critical Section) 정의
- **정의**: 한 스레드가 `PRE_JOIN_CURRENT_STATE` 로그를 남기는 시점부터 `JOIN_SUCCESS_EXISTING` 로그를 남기는 시점까지의 전체 로직 수행 구간을 의미한다
- **시작**: `prev_entry_time` 
- **종료**: `curr_entry_time` 
- **역할**: 다른 스레드의 개입 여부를 판단하는 경합의 범위가 된다

### 4.2 임계 구역 분석 항목
| 항목명 | 데이터 타입 | 설명 | 계산식 |
|--------|-------------|------|--------|
| true_critical_section_start | DateTime | 임계 구역 진입 시각 | `prev_entry_time` |
| true_critical_section_end | DateTime | 임계 구역 종료 시각 | `curr_entry_time` |
| true_critical_section_duration | Float | 임계 구역 지속 시간(초) | `(curr_entry_time - prev_entry_time).total_seconds()` |
| intervening_users_in_critical_section | String | 임계 구역에 개입한 사용자 ID 목록 | 임계 구역이 겹치는 다른 스레드들 |
| intervening_user_count_critical | Integer | 개입한 사용자 수 | 겹치는 스레드 개수 |

### 4.3 나노초 정밀도 분석 (선택적)
| 항목명 | 데이터 타입 | 설명 | 계산식 |
|--------|-------------|------|--------|
| true_critical_section_duration_nanos | Float | 나노초 단위 지속 시간 | `nanoTime_end - nanoTime_start` |
| true_critical_section_epoch_duration_nanos | Float | Epoch 나노초 지속 시간 | `epochNano_end - epochNano_start` |

## 5. 출력 파일 구조

### 5.1 시트 1: 이상 현상 상세 데이터
- **시트명**: `Anomaly_Details` 또는 기본 시트
- **파일명 예시**: `racecondition_analysis_IF_ELSE.csv`
- **내용**: 탐지된 모든 이상 현상의 상세 정보

### 5.2 컬럼 구성 (총 34개 컬럼)
#### 5.2.1 기본 정보 컬럼 (원본 데이터에서 가져옴)
1. roomNumber - 분석 대상 방 번호
2. bin - 분석 구간 번호
3. user_id - 사용자 ID
4. prev_people - 임계 구역 진입 시점에 확인된 현재 인원수
5. curr_people - 실제로 기록된 결과값
6. expected_people - 스레드가 기대했던 결과값
7. max_people - 방의 최대 정원
8. prev_entry_time - 임계 구역 진입 시각
9. curr_entry_time - 임계 구역 종료 시각
10. true_critical_section_nanoTime_start - 나노초 정밀도 시작 시각
11. true_critical_section_epochNano_start - Epoch 나노초 시작 시각
12. true_critical_section_nanoTime_end - 나노초 정밀도 종료 시각
13. true_critical_section_epochNano_end - Epoch 나노초 종료 시각

#### 5.2.2 이상 현상 분류 컬럼
14. anomaly_type - 발견된 이상 현상 유형 (복수 유형은 쉼표로 구분)

#### 5.2.3 순서 관련 컬럼
15. room_entry_sequence - 방 입장 순서 (원본 데이터의 값 그대로 사용)

#### 5.2.4 규칙 1 (값 불일치) 관련 컬럼
16. lost_update_expected - 스레드가 기대했던 결과값
17. lost_update_actual - 실제로 기록된 결과값
18. lost_update_diff - 기대값과 실제값의 차이

#### 5.2.5 규칙 2 (경합 발생) 관련 컬럼
19. contention_group_size - 해당 경합에 포함된 총 스레드 수
20. contention_user_ids - 경합에 포함된 모든 스레드의 ID 목록

#### 5.2.6 규칙 3 (정원 초과) 관련 컬럼
21. over_capacity_amount - 정원을 초과한 인원수
22. over_capacity_curr - 정원을 초과한 실제 인원수
23. over_capacity_max - 방의 최대 정원

#### 5.2.7 규칙 4 (상태 전이 오류) 관련 컬럼
24. expected_curr_by_sequence - 시간 순서상 기대되었던 결과값
25. actual_curr_people - 실제로 기록된 결과값
26. curr_sequence_diff - 시간 순서 기대값과 실제값의 차이
27. sorted_sequence_position - curr_entry_time 기준 실제 처리 완료 순번

#### 5.2.8 임계 구역 분석 컬럼
28. true_critical_section_start - 임계 구역 진입 시각
29. true_critical_section_end - 임계 구역 종료 시각
30. true_critical_section_duration - 임계 구역 지속 시간(초)
31. intervening_users_in_critical_section - 개입한 사용자 ID 목록
32. intervening_user_count_critical - 개입한 사용자 수
33. true_critical_section_duration_nanos - 나노초 단위 지속 시간
34. true_critical_section_epoch_duration_nanos - Epoch 나노초 지속 시간

### 5.3 데이터 정렬 순서
- 1차: roomNumber (오름차순)
- 2차: bin (오름차순)  
- 3차: room_entry_sequence (오름차순)

## 6. 상세 분석 텍스트 파일

### 6.1 파일명
`detailed_analysis.txt`

### 6.2 내용 구성
각 이상 현상에 대한 인간 친화적인 설명 텍스트로, 다음 정보 포함:
- 헤더: "경쟁 상태 이상 현상 분석 (원본 데이터 그대로 사용)"
- 기본 정보 (방 번호, 사용자 ID, Bin, 발생 시각 등)
- 발견된 이상 현상 유형
- 원본 방 입장 순번
- 각 규칙별 상세 분석 내용
- 진짜 임계구역 정보 (시작, 끝, 나노초 시작, 나노초 끝)
- 타이밍 상세 정보
- 개입한 다른 스레드 정보

## 7. 통계 정보 출력

### 7.1 콘솔 출력 통계
- 전체 레코드 수
- 이상 현상 발견 수 및 비율
- 나노초 정밀도 데이터 통계 (있을 경우)
- 4가지 규칙별 이상 현상 분포
  - 규칙 1: 값 불일치
  - 규칙 2: 경합 발생 자체
  - 규칙 3: 정원 초과 오류
  - 규칙 4: 상태 전이 오류

### 7.2 Excel 파일 추가 정보 (선택적)
xlsx 출력 시 10열(J열)부터 규칙 설명 테이블 추가:
| 규칙 | 조건 | 의미 |
|------|------|------|
| 규칙 1: 값 불일치 | curr_people ≠ expected_people | 다른 사용자 작업으로 의도한 갱신이 누락/덮어쓰여짐 |
| 규칙 2: 경합 발생 자체 | 진짜 임계구역이 1나노초라도 겹침 | 동시성 제어 부재로 잠재적 위험 노출 |
| 규칙 3: 정원 초과 오류 | curr_people > max_people | 비즈니스 규칙을 명백히 위반한 심각한 오류 |
| 규칙 4: 상태 전이 오류 | curr_people ≠ 1+room_entry_sequence | 올바른 상태를 읽지 못하고 오염된 상태로 작업 |

## 8. 구현 세부사항

### 8.1 입력 파일
- **파일명 예시**: `racecondition_event_preprocessor_result_IF_ELSE.csv`
- **인코딩**: UTF-8

### 8.2 필수 입력 컬럼
- roomNumber, bin, user_id
- prev_people, curr_people, expected_people, max_people
- prev_entry_time, curr_entry_time
- room_entry_sequence

### 8.3 선택적 입력 컬럼
- true_critical_section_nanoTime_start/end
- true_critical_section_epochNano_start/end

### 8.4 오류 처리
- 필수 컬럼 누락 시 프로그램 종료
- NaN 값은 빈 문자열('')로 처리
- 시간 값 누락 시 해당 분석 건너뛰기

### 8.5 명령줄 인터페이스
```bash
python racecondition_anomaly_detector.py input_csv output_csv [옵션]
```
- `--detailed_output`: 상세 분석 텍스트 파일명 (기본값: detailed_analysis.txt)
- `--rooms`: 분석할 방 번호 (쉼표로 구분)
- `--xlsx_output`: Excel 출력 파일 (선택사항)
# racecondition_statistical_bin_analyzer.py 요구사항 명세

## 1. 배경 및 목적

기존 RaceCondition 분석표를 개선하여, 각 동시성 제어 방식이 단순히 오류를 발생시켰는지 여부뿐만 아니라 **'그 오류가 얼마나 심각했는가'**까지 한눈에 파악할 수 있도록 분석의 깊이를 효과적으로 전달한다.

## 2. 핵심 문제점 및 개선 방향

### 2.1 기존 분석의 한계
- 각 스레드별로는 개별적인 값들(`lost_update_expected`, `lost_update_actual`, `lost_update_diff`, `contention_group_size`, `contention_user_ids`)인데, 이를 bin 단위로 집계식을 정의함
- 단순한 발생 건수만으로는 문제의 심각성을 파악하기 어려움

### 2.2 해결 방안
- **통계적 집계 및 비교 분석**: bin 단위로 총합, 평균, 최소값, 최대값, 중간값 등의 집계식을 정의하여 계산하고, 전체 bin 내 요청 개수 대비 잘못된 요청 결과를 수치화함
- **세부 통계**: 각 항목별로 절대값 비중, 평균값 비중, 최소/최대 비중 제공

## 3. 분석 대상 및 범위

### 3.1 데이터 소스
- **입력 파일 1**: `racecondition_event_preprocessor_result_IF_ELSE.csv` - 전처리된 전체 요청 데이터
- **입력 파일 2**: `racecondition_analysis_IF_ELSE.csv` - 이상현상 분석 결과
- **분석 단위**: 각 방 번호별 bin 단위로써 계산하며, 각 레이스 컨디션 지표별 계산 방식을 준수한다

### 3.2 분석 기준
스레드별 현재 인원수 갱신은 개별적으로 1개만 증가 시켜야 되는데, 이때 몇 건이 실제로 증가 되었는지에 대한 비교 속성이 핵심 분석 포인트가 됨. 전체 bin 내 요청 개수는 `racecondition_event_preprocessor_result_IF_ELSE.csv`에서 각 방 별 bin 단위로 세부적으로 집계한다.

## 4. 세부 분석 항목

### 4.1 규칙 1: 값 불일치 (Lost Update) 상세 분석

#### 4.1.1 발생 건수 및 비율
- **발생 건수**: `anomaly_type`에 '값 불일치'가 포함된 레코드 수
- **각 방 별 빈 단위로 집계**: `racecondition_event_preprocessor_result_IF_ELSE.csv`에서 각 bin 번호 내 포함되어 있는 각각의 bin별 스레드 총 개수  
- **발생률**: 해당 방 내 각 bin별 개수 / 발생 건수

#### 4.1.2 오차 분석 (`lost_update_diff` 기준)
- **오차 누적 총합 (Sum of diff)**: `lost_update_diff`의 총합으로, 데이터 불일치로 인해 발생한 절대적인 오차 수치 값
- **평균 오차 (Avg diff)**: 해당 bin 구간 내 값 불일치가 발생한 모든 스레드 요청들의 `lost_update_diff` 평균값
- **최소 오차**: `lost_update_diff` 최소값
- **최대 오차**: `lost_update_diff` 최대값
- **오차 표준편차**: 해당 bin 구간 내 값 불일치가 발생한 모든 스레드 요청들의 `lost_update_diff` 값들에 대한 표준편차로, 오차 값들이 평균 주변에 얼마나 집중되어 있는지 또는 분산되어 있는지를 나타냄

#### 4.1.3 엑셀 표 구성 형식 (Lost Update 분석표)

| 컬럼명 | 데이터 타입 | 설명 | 계산식 |
|--------|-------------|------|--------|
| 방 번호 | Integer | 분석 대상 방 번호 | `roomNumber` |
| Bin | Integer | 분석 구간 번호 | `bin` |
| 전체 요청수 | Integer | 해당 방×bin의 총 스레드 요청 수 | `preprocessor_result에서 COUNT(roomNumber, bin)` |
| 발생 건수 | Integer | 값 불일치 발생한 스레드 수 | `COUNT(anomaly_type LIKE '%값 불일치%')` |
| 발생률 (%) | Decimal(5,2) | 값 불일치 발생 비율 | `(발생 건수 / 전체 요청수) × 100` |
| 오차 누적 총합 | Decimal(10,2) | lost_update_diff 총합 | `SUM(lost_update_diff)` |
| 평균 오차 | Decimal(8,2) | 평균 오차값 | `AVG(lost_update_diff)` |
| 최소 오차 | Decimal(8,2) | 최소 오차값 | `MIN(lost_update_diff)` |
| 최대 오차 | Decimal(8,2) | 최대 오차값 | `MAX(lost_update_diff)` |
| 중간값 오차 | Decimal(8,2) | 중간값 오차 | `MEDIAN(lost_update_diff)` |
| 오차 표준편차 | Decimal(8,4) | 오차값들의 표준편차 | `STDEV(lost_update_diff)` |

### 4.2 규칙 2: 경합 발생 (Contention) 상세 분석

#### 4.2.1 발생 건수 및 비율
- **발생 건수**: `racecondition_analysis_IF_ELSE.csv`에서 `anomaly_type`에 '경합 발생 오류'가 포함된 레코드 수
- **각 방 별 빈 단위로 집계**: `racecondition_event_preprocessor_result_IF_ELSE.csv`에서 각 bin 번호 내 포함되어 있는 각각의 bin별 스레드 총 개수  
- **발생률**: 해당 방 내 각 bin별 개수 / 발생 건수

#### 4.2.2 경합 강도 분석 (`contention_group_size` 기준)
- **평균 경합 스레드 수 (Avg group size)**: 경합이 발생했을 때, 평균 몇 개의 스레드가 얽혀 있었는지
- **최소 경합 그룹 크기**: 가장 작은 경합 그룹의 크기
- **최대 경합 스레드 수 (Max group size)**: 가장 심각했던 단일 경합에 몇 개의 스레드가 동시에 진입했는지
- **중간값 경합 그룹 크기**: 경합 그룹 크기의 중간값

#### 4.2.3 엑셀 표 구성 형식 (Contention 분석표)

| 컬럼명 | 데이터 타입 | 설명 | 계산식 |
|--------|-------------|------|--------|
| 방 번호 | Integer | 분석 대상 방 번호 | `roomNumber` |
| Bin | Integer | 분석 구간 번호 | `bin` |
| 전체 요청수 | Integer | 해당 방×bin의 총 스레드 요청 수 | `preprocessor_result에서 COUNT(roomNumber, bin)` |
| 발생 건수 | Integer | 경합이 발생한 총 스레드 개수 | `COUNT(anomaly_type LIKE '%경합 발생 오류%')` |
| 발생률 (%) | Decimal(5,2) | 경합 발생 비율 | `(발생 건수 / 전체 요청수) × 100` |
| 총 경합 스레드 수 | Decimal(10,2) | contention_group_size 총합 | `SUM(contention_group_size)` |
| 평균 경합 스레드 수 | Decimal(8,2) | 평균 경합 그룹 크기 | `AVG(contention_group_size)` |
| 최소 경합 그룹 크기 | Decimal(8,2) | 최소 경합 그룹 크기 | `MIN(contention_group_size)` |
| 최대 경합 스레드 수 | Decimal(8,2) | 최대 경합 그룹 크기 | `MAX(contention_group_size)` |
| 중간값 경합 그룹 크기 | Decimal(8,2) | 중간값 경합 그룹 크기 | `MEDIAN(contention_group_size)` |
| 경합 강도 표준편차 | Decimal(8,4) | 경합 그룹 크기의 표준편차 | `STDEV(contention_group_size)` |

### 4.3 규칙 3: 정원 초과 (Capacity Exceeded) 상세 분석

#### 4.3.1 발생 건수 및 비율
- **발생 건수**: `racecondition_analysis_IF_ELSE.csv`에서 `anomaly_type`에 '정원 초과 오류'가 포함된 레코드 수
- **각 방 별 빈 단위로 집계**: `racecondition_event_preprocessor_result_IF_ELSE.csv`에서 각 bin 번호 내 포함되어 있는 각각의 bin별 스레드 총 개수  
- **발생률**: 해당 방 내 각 bin별 개수 / 발생 건수

#### 4.3.2 초과 규모 분석 (`over_capacity_amount` 기준)
- **총 초과 인원 (Sum of amount)**: 정원을 초과하여 입장한 모든 인원의 누적 합계
- **평균 초과 인원**: 정원 초과 발생 시 평균 초과 인원 수
- **최소 초과 인원**: 가장 작은 정원 초과 사례
- **최대 초과 인원 (Max amount)**: 한 번에 가장 많이 정원을 초과한 인원수
- **중간 초과 인원**: 정원 초과량의 중간값

#### 4.3.3 엑셀 표 구성 형식 (Capacity Exceeded 분석표)

| 컬럼명 | 데이터 타입 | 설명 | 계산식 |
|--------|-------------|------|--------|
| 방 번호 | Integer | 분석 대상 방 번호 | `roomNumber` |
| Bin | Integer | 분석 구간 번호 | `bin` |
| 전체 요청수 | Integer | 해당 방×bin의 총 스레드 요청 수 | `preprocessor_result에서 COUNT(roomNumber, bin)` |
| 발생 건수 | Integer | 정원 초과가 발생한 총 스레드 개수 | `COUNT(anomaly_type LIKE '%정원 초과 오류%')` |
| 발생률 (%) | Decimal(5,2) | 정원 초과 발생 비율 | `(발생 건수 / 전체 요청수) × 100` |
| 총 초과 인원 | Decimal(10,2) | over_capacity_amount 총합 | `SUM(over_capacity_amount)` |
| 평균 초과 인원 | Decimal(8,2) | 평균 초과 인원 수 | `AVG(over_capacity_amount)` |
| 최소 초과 인원 | Decimal(8,2) | 최소 초과 인원 | `MIN(over_capacity_amount)` |
| 최대 초과 인원 | Decimal(8,2) | 최대 초과 인원 수 | `MAX(over_capacity_amount)` |
| 중간값 초과 인원 | Decimal(8,2) | 중간값 초과 인원 | `MEDIAN(over_capacity_amount)` |
| 초과 규모 표준편차 | Decimal(8,4) | 초과 인원의 표준편차 | `STDEV(over_capacity_amount)` |

### 4.4 규칙 4: 상태 전이 오류 (State Transition) 상세 분석

#### 4.4.1 발생 건수 및 비율
- **발생 건수**: `racecondition_analysis_IF_ELSE.csv`에서 `anomaly_type`에 '상태 전이 오류'가 포함된 레코드 수
- **각 방 별 빈 단위로 집계**: `racecondition_event_preprocessor_result_IF_ELSE.csv`에서 각 bin 번호 내 포함되어 있는 각각의 bin별 스레드 총 개수  
- **발생률**: 해당 방 내 각 bin별 개수 / 발생 건수

#### 4.4.2 상태 오염 분석 (`curr_sequence_diff` 기준)
- **시간 순서 기대값**: 테스트 시작 시 방에 1명이 존재한다고 가정하고, 각 스레드 요청 인덱스(i)마다 기대값 = i + 2로 계산
- **실제 기록값**: 동시 요청 상황에서 기록된 `curr_people` 값
- **순서 차이 (curr_sequence_diff)**: 시간 순서상 기대되었던 결과값과 실제 결과값의 불일치 정도. 상태가 순차적으로 올바르게 변경되었다면 모든 지표가 0에 가까워야 하며, 평균/최대 순서 차이는 시스템의 데이터 정합성 수준을 보여줌
- **평균 순서 차이**: 해당 bin 구간 내 상태 전이 오류가 발생한 모든 스레드 요청들의 `curr_sequence_diff` 평균값
- **최소 순서 차이**: `curr_sequence_diff` 최소값
- **최대 순서 차이**: `curr_sequence_diff` 최대값
- **중간값 순서 차이**: `curr_sequence_diff` 중간값

#### 4.4.3 엑셀 표 구성 형식 (State Transition 분석표)

| 컬럼명 | 데이터 타입 | 설명 | 계산식 |
|--------|-------------|------|--------|
| 방 번호 | Integer | 분석 대상 방 번호 | `roomNumber` |
| Bin | Integer | 분석 구간 번호 | `bin` |
| 전체 요청수 | Integer | 해당 방×bin의 총 스레드 요청 수 | `preprocessor_result에서 COUNT(roomNumber, bin)` |
| 발생 건수 | Integer | 상태 전이 오류가 발생한 총 스레드 개수 | `COUNT(anomaly_type LIKE '%상태 전이 오류%')` |
| 발생률 (%) | Decimal(5,2) | 상태 전이 오류 발생 비율 | `(발생 건수 / 전체 요청수) × 100` |
| 총 순서 차이 | Decimal(10,2) | curr_sequence_diff 총합 | `SUM(curr_sequence_diff)` |
| 평균 순서 차이 | Decimal(8,2) | 평균 순서 차이값 | `AVG(curr_sequence_diff)` |
| 최소 순서 차이 | Decimal(8,2) | 최소 순서 차이 | `MIN(curr_sequence_diff)` |
| 최대 순서 차이 | Decimal(8,2) | 최대 순서 차이값 | `MAX(curr_sequence_diff)` |
| 중간값 순서 차이 | Decimal(8,2) | 중간값 순서 차이 | `MEDIAN(curr_sequence_diff)` |
| 순서 차이 표준편차 | Decimal(8,4) | 순서 차이의 표준편차 | `STDEV(curr_sequence_diff)` |

## 5. 출력 형태 및 표 구성

### 5.1 표 분리 방식
하나의 거대한 표 대신 **각 규칙별로 분석 표를 분리**하여 제시하는 것이 명확성과 가독성 측면에서 효과적함.

### 5.2 4개 분석표 구성
1. **표 1: 값 불일치 (Lost Update) 분석** - `lost_update_diff` 값의 분포 분석
2. **표 2: 경합 발생 (Contention) 분석** - `contention_group_size` 값의 분포 분석  
3. **표 3: 정원 초과 (Capacity Exceeded) 분석** - `over_capacity_amount` 값의 분포 분석
4. **표 4: 상태 전이 오류 (State Transition) 분석** - `curr_sequence_diff` 값의 분포 분석

각 표는 해당 규칙의 특성을 상세히 보여주는 방식으로 구성되며, 모든 통계적 지표와 비교 분석 항목을 포함한다.

### 5.3 2번째 시트 구성
- **시트명**: `Analysis_Requirements`
- **용도**: 본 요구사항 명세서 전체 내용을 CSV 파일의 두 번째 시트로 추가
- **목적**: 분석 결과와 요구사항을 하나의 파일에서 통합 관리
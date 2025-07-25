# Race Condition 방별 통계 분석기 (Statistical Room Analyzer)

Race Condition Event Detector의 탐지 결과를 바탕으로 4가지 규칙별 방 단위 통계적 집계 분석을 수행하는 Python 도구입니다. 방별 발생률, 심각도, 분포 분석을 제공합니다.

## 개요

이 도구는 Race Condition Event Detector가 탐지한 이상현상 결과를 입력받아 방 단위로 통합하여 다음과 같은 통계 분석을 수행합니다:

1. **방별 전체 요청수 집계**
2. **4가지 규칙별 방 단위 통계적 분석**
   - 규칙 1: 값 불일치 (Lost Update) 방별 통합 분석
   - 규칙 2: 경합 발생 (Contention) 방별 통합 분석  
   - 규칙 3: 정원 초과 (Capacity Exceeded) 방별 통합 분석
   - 규칙 4: 상태 전이 오류 (State Transition) 방별 통합 분석
3. **포괄적인 Excel 보고서 생성** (4개 시트로 구성)
4. **방별 전체 발생률, 심각도, 분포 분석**

## 주요 기능

- **방별 통합 집계**: 방 단위로 통합하여 전체 현황 제공
- **완전한 방 커버리지**: 이상현상이 없는 방도 0값으로 포함하여 전체 현황 제공
- **규칙별 세분화 분석**: 각 규칙에 특화된 방별 통계 지표 계산
- **Excel 다중 시트 출력**: 규칙별로 분리된 워크시트와 전문적인 스타일링
- **방별 종합 발생률**: 방 전체 요청 대비 이상현상 발생률 제공
- **통계적 지표**: 평균, 최대/최소, 중간값, 표준편차 등 포괄적 통계
- **방별 필터링**: 특정 방만 선택적으로 분석 가능

## 시스템 요구사항

```bash
pip install pandas numpy openpyxl
```

## 사용법

### 기본 사용법

```cmd
py -3 racecondition_event_statistical_room_analyzer.py preprocessor.csv analysis.csv room_output.xlsx
```

### 옵션 사용법

```cmd
py -3 racecondition_event_statistical_room_analyzer.py preprocessor.csv analysis.csv room_output.xlsx --rooms 101,102,103
```

### 명령행 인수

| 인수 | 설명 | 필수 여부 |
|------|------|-----------|
| `preprocessor_csv` | 전처리 결과 CSV 파일 경로 | 필수 |
| `analysis_csv` | 이상현상 분석 결과 CSV 파일 경로 | 필수 |
| `output_xlsx` | 방별 통계 분석 Excel 출력 파일 경로 | 필수 |
| `--rooms` | 분석할 방 번호 (쉼표로 구분) | 선택 |

## 사용 예시

### 예시 1: 전체 방 통합 분석 (현재 폴더)
```cmd
py -3 racecondition_event_statistical_room_analyzer.py preprocessed_data.csv detected_anomalies.csv room_statistics.xlsx
```

### 예시 2: 특정 방 분석 (상대 경로)
```cmd
py -3 racecondition_event_statistical_room_analyzer.py ./data/preprocessed.csv ./results/anomalies.csv ./reports/room_stats.xlsx --rooms 101,102,103
```

### 예시 3: 절대 경로 사용
```cmd
py -3 racecondition_event_statistical_room_analyzer.py C:\data\preprocessed_room_data.csv C:\results\race_condition_anomalies.csv C:\reports\room_analysis.xlsx
```

### 예시 4: 단일 방 상세 분석
```cmd
py -3 racecondition_event_statistical_room_analyzer.py room_data\preprocessed.csv anomaly_results\detected.csv analysis_output\room_101_comprehensive.xlsx --rooms 101
```

## 입력 데이터 형식

### 1. 전처리 데이터 (preprocessor.csv)

전처리 파일은 방별 전체 요청수 집계를 위해 사용됩니다.

#### 필수 컬럼

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `roomNumber` | int | 방 식별자 |
| `bin` | int | 데이터 빈 식별자 |
| `user_id` | string | 사용자 식별자 |

### 2. 이상현상 분석 데이터 (analysis.csv)

Race Condition Event Detector의 출력 파일입니다.

#### 필수 컬럼

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `roomNumber` | int | 방 식별자 |
| `bin` | int | 데이터 빈 식별자 |
| `anomaly_type` | string | 탐지된 이상현상 유형 |
| `lost_update_diff` | float | 값 불일치의 차이값 |
| `contention_group_size` | int | 경합 그룹의 크기 |
| `over_capacity_amount` | int | 정원 초과 인원 수 |
| `curr_sequence_diff` | int | 순서 차이값 |

#### anomaly_type 값 형식
- `"값 불일치"` 포함: 규칙 1 분석 대상
- `"경합 발생 오류"` 포함: 규칙 2 분석 대상  
- `"정원 초과 오류"` 포함: 규칙 3 분석 대상
- `"상태 전이 오류"` 포함: 규칙 4 분석 대상

## 출력 형식

### Excel 파일 구조

출력 Excel 파일은 4개의 워크시트로 구성됩니다:

#### 시트 1: Room_LostUpdate_Analysis (방별 규칙 1: 값 불일치)

| 컬럼명 | 설명 |
|--------|------|
| `방 번호` | 방 식별자 |
| `전체 요청수` | 해당 방의 총 요청 건수 |
| `발생 건수` | 값 불일치 발생 건수 |
| `발생률 (%)` | 방 전체 요청 대비 발생률 |
| `오차 누적 총합` | lost_update_diff 합계 |
| `평균 오차` | lost_update_diff 평균 |
| `최소 오차` | lost_update_diff 최솟값 |
| `최대 오차` | lost_update_diff 최댓값 |
| `중간값 오차` | lost_update_diff 중간값 |
| `오차 표준편차` | lost_update_diff 표준편차 |

#### 시트 2: Room_Contention_Analysis (방별 규칙 2: 경합 발생)

| 컬럼명 | 설명 |
|--------|------|
| `방 번호` | 방 식별자 |
| `전체 요청수` | 해당 방의 총 요청 건수 |
| `발생 건수` | 경합 발생 건수 |
| `발생률 (%)` | 방 전체 요청 대비 발생률 |
| `총 경합 스레드 수` | contention_group_size 합계 |
| `평균 경합 스레드 수` | contention_group_size 평균 |
| `최소 경합 그룹 크기` | contention_group_size 최솟값 |
| `최대 경합 스레드 수` | contention_group_size 최댓값 |
| `중간값 경합 그룹 크기` | contention_group_size 중간값 |
| `경합 강도 표준편차` | contention_group_size 표준편차 |

#### 시트 3: Room_Capacity_Analysis (방별 규칙 3: 정원 초과)

| 컬럼명 | 설명 |
|--------|------|
| `방 번호` | 방 식별자 |
| `전체 요청수` | 해당 방의 총 요청 건수 |
| `발생 건수` | 정원 초과 발생 건수 |
| `발생률 (%)` | 방 전체 요청 대비 발생률 |
| `총 초과 인원` | over_capacity_amount 합계 |
| `평균 초과 인원` | over_capacity_amount 평균 |
| `최소 초과 인원` | over_capacity_amount 최솟값 |
| `최대 초과 인원` | over_capacity_amount 최댓값 |
| `중간값 초과 인원` | over_capacity_amount 중간값 |
| `초과 규모 표준편차` | over_capacity_amount 표준편차 |

#### 시트 4: Room_StateTransition_Analysis (방별 규칙 4: 상태 전이 오류)

| 컬럼명 | 설명 |
|--------|------|
| `방 번호` | 방 식별자 |
| `전체 요청수` | 해당 방의 총 요청 건수 |
| `발생 건수` | 상태 전이 오류 발생 건수 |
| `발생률 (%)` | 방 전체 요청 대비 발생률 |
| `총 순서 차이` | curr_sequence_diff 합계 |
| `평균 순서 차이` | curr_sequence_diff 평균 |
| `최소 순서 차이` | curr_sequence_diff 최솟값 |
| `최대 순서 차이` | curr_sequence_diff 최댓값 |
| `중간값 순서 차이` | curr_sequence_diff 중간값 |
| `순서 차이 표준편차` | curr_sequence_diff 표준편차 |

### Excel 스타일링

- **제목 행**: 크기 14, 볼드, 흰색 글꼴, 네이비 배경 (#366092)
- **헤더 행**: 볼드, 연한 파란색 배경 (#D9E1F2), 가운데 정렬
- **컬럼 너비**: 내용에 맞게 자동 조정 (최대 30자)
- **데이터 정밀도**: 소수점 2자리 (표준편차는 4자리)

## 분석 로직

### 1. 방별 전체 요청수 집계
```python
# roomNumber 단위로 그룹화하여 전체 요청수 계산
total_requests = preprocessor_df.groupby(['roomNumber']).size()
```

### 2. 규칙별 필터링
각 규칙별로 anomaly_type 컬럼의 문자열 포함 여부로 필터링:
- 규칙 1: `anomaly_type.str.contains('값 불일치')`
- 규칙 2: `anomaly_type.str.contains('경합 발생 오류')`
- 규칙 3: `anomaly_type.str.contains('정원 초과 오류')`
- 규칙 4: `anomaly_type.str.contains('상태 전이 오류')`

### 3. 방별 통계 계산 방식
```python
# 각 roomNumber별로:
occurrence_count = len(values)                           # 발생 건수
occurrence_rate = (occurrence_count / total_requests * 100)     # 발생률
sum_value = values.sum()                                # 합계
avg_value = values.mean()                               # 평균
min_value = values.min()                                # 최솟값
max_value = values.max()                                # 최댓값
median_value = values.median()                          # 중간값
std_value = values.std()                                # 표준편차
```

### 4. 완전한 방 커버리지 보장
- 이상현상이 없는 방도 0값으로 포함
- 모든 통계가 방별 전체 데이터 기준으로 계산됨

## 샘플 출력

```
🚀 Race Condition 방별 통계 분석기 시작...
입력 파일 1: preprocessed_data.csv
입력 파일 2: detected_anomalies.csv  
출력 파일: room_statistics.xlsx

📂 데이터 파일 로드 중...
✅ 전처리 데이터 로드 완료: 5000행
✅ 이상현상 분석 데이터 로드 완료: 125행
✅ 데이터 검증 및 타입 변환 완료

📊 방별 전체 요청수 집계 중...
✅ 집계 완료: 12개 방

🔍 규칙 1: 값 불일치 (Lost Update) 방별 분석 중...
  - 값 불일치 발생 레코드: 32건
✅ 값 불일치 분석 완료: 12개 방

🔍 규칙 2: 경합 발생 (Contention) 방별 분석 중...
  - 경합 발생 레코드: 48건
✅ 경합 발생 분석 완료: 12개 방

🔍 규칙 3: 정원 초과 (Capacity Exceeded) 방별 분석 중...
  - 정원 초과 발생 레코드: 15건
✅ 정원 초과 분석 완료: 12개 방

🔍 규칙 4: 상태 전이 오류 (State Transition) 방별 분석 중...
  - 상태 전이 오류 발생 레코드: 30건
✅ 상태 전이 오류 분석 완료: 12개 방

📊 Excel 파일 생성 중...
✅ Excel 파일 저장 완료: room_statistics.xlsx

================================================================================
📈 RACE CONDITION 방별 통계 분석 결과 요약
================================================================================
전체 분석 대상: 12개 방
전체 요청 수: 5,000건

--- 규칙별 분석 결과 ---
규칙 1 (값 불일치): 12개 방에서 32건 발생
규칙 2 (경합 발생): 12개 방에서 48건 발생
규칙 3 (정원 초과): 12개 방에서 15건 발생
규칙 4 (상태 전이): 12개 방에서 30건 발생

전체 이상현상 발생률: 2.50% (125/5,000)

🎉 방별 통계 분석 완료!
```

## 활용 방안

### 1. 방별 성능 비교
- 방별 이상현상 발생률 비교 분석
- 성능이 낮은 방 식별 및 우선순위 설정

### 2. 시스템 최적화
- 높은 발생률을 보이는 방의 집중 개선
- 방별 특성에 맞는 동시성 제어 전략 수립

### 3. 용량 계획
- 방별 정원 초과 패턴 분석
- 방별 수용인원 조정 및 리소스 배분

### 4. 품질 관리
- 방별 품질 지표 설정 및 모니터링
- 방별 성능 기준선 설정

## 기술적 세부사항

### 데이터 처리 방식
- **방별 통합 집계**: roomNumber로 그룹화하여 데이터 통합
- **완전 외부 조인**: 모든 방을 기준으로 통계 생성
- **결측값 처리**: 이상현상이 없는 방은 0 또는 NaN으로 적절히 처리
- **타입 안정성**: 정수형 컬럼은 명시적 타입 변환 수행

## 오류 처리

### 필수 컬럼 검증
- 전처리 데이터: `roomNumber`, `bin`, `user_id`
- 분석 데이터: `roomNumber`, `bin`, `anomaly_type`, 통계 관련 컬럼들

### 데이터 타입 검증
- 자동 타입 변환 및 오류 발생 시 명확한 메시지 제공
- traceback을 통한 상세 오류 정보 출력
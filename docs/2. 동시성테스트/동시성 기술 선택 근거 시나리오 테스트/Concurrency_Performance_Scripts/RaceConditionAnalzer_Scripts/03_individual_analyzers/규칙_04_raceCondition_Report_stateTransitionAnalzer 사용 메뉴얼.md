# Race Condition Rule 4 State Transition 분석기 사용 메뉴얼

상태 전이 오류(State Transition Error) 분석 및 시각화를 위한 완전 독립 실행 가능한 Python 도구입니다. Rule 4 규칙에 특화된 분석과 차트 생성 기능을 제공합니다.

## 개요

이 도구는 Race Condition에서 발생하는 상태 전이 오류를 분석하고 시각화합니다:

1. **상태 전이 오류 탐지**: 이상적 기대값과 실제 기록값 간의 불일치 분석
2. **시각화 차트 생성**: 단일 방 분석 또는 전체 방 종합 분석 차트
3. **CSV 보고서 생성**: 상태 전이 오류 발생 건에 대한 상세 보고서
4. **이중 데이터 소스**: 차트용 전처리 데이터 + CSV용 결과 데이터

## 주요 기능

- **완전 독립 실행**: 다른 분석기와 독립적으로 실행 가능
- **Rule 4 전용 분석**: 상태 전이 오류에만 특화된 분석
- **이중 차트 모드**: 단일 방 상세 분석 / 전체 방 종합 분석
- **이중 데이터 입력**: 전처리 파일(차트용) + 결과 파일(CSV용)
- **고정밀도 데이터 지원**: 나노초 정밀도 데이터 자동 감지
- **알고리즘 기반 기대값**: 순차적 증가 패턴 (index + 2) 기반 이상적 기대값 생성

## 시스템 요구사항

```cmd
pip install pandas matplotlib numpy openpyxl
```

## 사용법

### 기본 사용법

```cmd
py -3 raceCondition_Report_stateTransitionAnalzer.py --preprocessor_file preprocessor.csv --result_file result.csv --output_dir output\
```

### 특정 방 분석

```cmd
py -3 raceCondition_Report_stateTransitionAnalzer.py --room_number 1135 --preprocessor_file preprocessor.csv --result_file result.csv --output_dir output\
```

### 명령행 인수

| 인수 | 설명 | 필수 여부 | 기본값 |
|------|------|-----------|--------|
| `--room_number` | 분석할 특정 방 번호 | 선택 | 전체 방 |
| `--preprocessor_file` | 전처리 데이터 CSV 파일 경로 (차트용) | 필수 | - |
| `--result_file` | 분석 결과 CSV 파일 경로 (CSV 보고서용) | 필수 | - |
| `--output_dir` | 분석 결과 저장 디렉토리 경로 | 필수 | - |

## 사용 예시

### 예시 1: 전체 방 종합 분석 (현재 폴더)
```cmd
py -3 raceCondition_Report_stateTransitionAnalzer.py --preprocessor_file preprocessed_data.csv --result_file analysis_result.csv --output_dir .\output\
```

### 예시 2: 특정 방 상세 분석 (상대 경로)
```cmd
py -3 raceCondition_Report_stateTransitionAnalzer.py --room_number 101 --preprocessor_file .\data\preprocessed.csv --result_file .\data\result.csv --output_dir .\reports\
```

### 예시 3: 절대 경로 사용
```cmd
py -3 raceCondition_Report_stateTransitionAnalzer.py --room_number 1135 --preprocessor_file C:\data\preprocessor.csv --result_file C:\data\analysis.csv --output_dir C:\reports\state_transition\
```

### 예시 4: 복잡한 디렉토리 구조
```cmd
py -3 raceCondition_Report_stateTransitionAnalzer.py --preprocessor_file data\room_logs\preprocessed_2024.csv --result_file results\anomaly_detection\detected.csv --output_dir analysis\rule4_results\ --room_number 202
```

## 입력 데이터 형식

### 1. 전처리 데이터 파일 (preprocessor_file)

차트 생성에 사용되는 전처리 데이터입니다.

#### 필수 컬럼

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `roomNumber` | int | 방 식별자 |
| `curr_people` | int | 실제 기록된 현재 인원수 |
| `max_people` | int | 최대 방 수용인원 |
| `curr_entry_time` | datetime | 현재 입장 시각 |

#### 선택적 컬럼

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `prev_entry_time` | datetime | 이전 입장 시각 |
| `*nanoTime*` | long | 나노초 정밀도 시간 데이터 |
| `*epochNano*` | long | 나노초 정밀도 에포크 시간 |

### 2. 분석 결과 데이터 파일 (result_file)

CSV 보고서 생성에 사용되는 이상현상 분석 결과 데이터입니다.

#### 필수 컬럼

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `roomNumber` | int | 방 식별자 |
| `anomaly_type` | string | 탐지된 이상현상 유형 |
| `bin` | int | 데이터 빈 식별자 |
| `room_entry_sequence` | int | 방 입장 순서 |

#### anomaly_type 값 형식
- `"상태 전이 오류"` 포함: Rule 4 분석 대상으로 필터링

#### 상태 전이 오류 탐지 조건
```python
# 이상적 기대값 = min(index + 2, max_people)
ideal_expected = min(request_index + 2, max_people)

# 실제 기록값과 이상적 기대값이 다른 경우
if ideal_expected != curr_people:
    # 상태 전이 오류 발생
```

## 출력 형식

### 1. 차트 파일 (PNG)

#### 단일 방 분석 차트
- **파일명**: `rule4_state_transition_analysis_room{방번호}.png`
- **구성 요소**:
  - X축: 스레드 요청 순서 (Index)
  - Y축: 측정값 (인원 수)
  - 파란색 점선: 이상적 기대 인원수 (알고리즘 생성)
  - 주황색 실선: 실제 기록된 인원수 (curr_people)
  - 빨간색 수직 음영: 상태 전이 오류 발생 시점

#### 전체 방 종합 분석 차트
- **파일명**: `rule4_state_transition_analysis_all_rooms.png`
- **구성 요소**:
  - X축: 스레드 요청 순서 (Index)
  - Y축: 측정값 (인원 수)
  - 파란색 점선: 이상적 기대 인원수 (평균 최대 정원 기준)
  - 주황색 실선: 평균 실제 기록된 인원수
  - 주황색 음영: 실제값 신뢰구간 (±1σ)
  - 빨간색 수직 음영: 상태 전이 오류 발생 시점

#### 차트 공통 요소
- **범례**: 좌측 상단 배치
- **통계 박스**: 범례 우측 약 2cm 간격
- **격자**: 연한 점선 격자 (alpha=0.3)
- **X축 눈금**: 10개 동일 간격 + 마지막 지점

### 2. CSV 보고서

#### 단일 방 보고서
- **파일명**: `report_rule4_state_transition_errors_room{방번호}.csv`

#### 전체 방 보고서
- **파일명**: `report_rule4_state_transition_errors.csv`

#### CSV 컬럼 구성

| 컬럼명 | 설명 |
|--------|------|
| `roomNumber` | 방 식별자 |
| `bin` | 데이터 빈 식별자 |
| `room_entry_sequence` | 방 입장 순서 |
| `sorted_sequence_position` | 정렬된 순서 위치 |
| `user_id` | 사용자 식별자 |
| `true_critical_section_start` | 임계구역 시작 시각 |
| `true_critical_section_end` | 임계구역 종료 시각 |
| `prev_people` | 이전 인원 수 |
| `expected_curr_by_sequence` | 순서 기반 기대값 |
| `actual_curr_people` | 실제 현재 인원수 |
| `curr_sequence_diff` | 순서 차이값 |

- **인코딩**: UTF-8 with BOM (utf-8-sig)
- **정렬**: roomNumber, bin, room_entry_sequence 순서
- **필터링**: anomaly_type에 "상태 전이 오류" 포함된 행만

## 분석 로직

### 1. 데이터 로딩 및 전처리
```python
# 두 개 CSV 파일 로드
df_preprocessor = pd.read_csv(preprocessor_file)  # 차트용
df_result = pd.read_csv(result_file)              # CSV용

# 날짜 컬럼 변환
df_preprocessor['curr_entry_time'] = pd.to_datetime(df_preprocessor['curr_entry_time'])

# 방 번호 필터링 (지정된 경우)
if room_number:
    df_preprocessor = df_preprocessor[df_preprocessor['roomNumber'] == room_number]
    df_result = df_result[df_result['roomNumber'] == room_number]

# 시간순 정렬 및 인덱스 부여
df_preprocessor = df_preprocessor.sort_values('curr_entry_time').reset_index(drop=True)
df_preprocessor['request_index'] = range(len(df_preprocessor))
```

### 2. 이상적 기대값 생성
```python
# 단일 방: 해당 방의 최대 정원 적용
max_people = room_data['max_people'].iloc[0]
ideal_expected_values = [min(i + 2, max_people) for i in range(total_requests)]

# 전체 방: 평균 최대 정원 적용
avg_max_people = int(df_preprocessor['max_people'].mean())
ideal_expected_values = [min(i + 2, avg_max_people) for i in range(max_requests)]
```

### 3. 상태 전이 오류 탐지
```python
# 차트용: 전처리 데이터에서 직접 비교
for i in range(total_requests):
    ideal_val = ideal_expected_values[i]
    actual_val = curr_people_values[i]
    
    if ideal_val != actual_val:
        # 상태 전이 오류 발생

# CSV용: 결과 데이터에서 필터링
state_transition_anomalies = df_result[
    df_result['anomaly_type'].str.contains('상태 전이 오류', na=False)
]
```

### 4. 단일 방 차트 생성
- X축: 요청 순서 인덱스 (0부터 총 요청수-1까지)
- Y축: 동적 계산 (max(ideal_values, curr_people) × 1.2)
- 상태 전이 오류 지점: 빨간색 수직 음영
- 통계 정보: 총 요청, 상태 전이 오류, 오류 비율

### 5. 전체 방 차트 생성
- 방별 데이터셋 분리 및 정렬
- 평균값과 표준편차 계산 (각 인덱스별)
- 신뢰구간 표시 (±1σ)
- 전체 통계: 분석 방 수, 총 요청, 전체 오류 비율

## 클래스 구조

### Rule4StateTransitionAnalyzer 클래스

#### 초기화 매개변수
- `room_number`: 분석할 특정 방 번호 (선택)
- `preprocessor_file`: 전처리 데이터 파일 경로 (차트용)
- `result_file`: 분석 결과 파일 경로 (CSV용)
- `output_dir`: 출력 디렉토리 경로

#### 주요 메서드

| 메서드명 | 기능 |
|----------|------|
| `load_data()` | 두 개 CSV 파일 로드 및 전처리 |
| `create_output_folders()` | 출력 폴더 생성 |
| `_create_rule4_single_room_chart()` | 단일 방 상세 분석 차트 |
| `_create_rule4_multi_room_chart()` | 전체 방 종합 분석 차트 |
| `generate_rule4_csv_report()` | CSV 보고서 생성 |
| `run_analysis()` | 전체 분석 실행 |

## 특징

### 1. 이중 데이터 소스
- **차트 생성**: preprocessor_file 사용
- **CSV 보고서**: result_file 사용
- 각각 다른 목적과 구조

### 2. 알고리즘 기반 기대값
- 이상적 상태 전이 패턴: index + 2
- 최대 정원 제한 적용
- 순차적 증가 패턴 기반

### 3. 유연한 실행 모드
- 단일 방 / 전체 방 자동 선택
- room_number 유무에 따른 분기

### 4. 고정밀도 데이터 지원
- 나노초 정밀도 데이터 자동 감지
- nanoTime, epochNano 컬럼 인식

## 샘플 출력

```
🚀 Rule 4: State Transition 분석 시작
✅ 전처리 파일 로드 완료: 2000건
✅ 결과 파일 로드 완료: 180건
✅ 고정밀도 나노초 데이터 감지
✅ 전처리 데이터 curr_entry_time 컬럼 datetime 변환 완료
✅ 결과 데이터 curr_entry_time 컬럼 datetime 변환 완료
✅ 방 1135 필터링 완료:
   - 전처리 데이터: 2000 → 200건
   - 결과 데이터: 180 → 18건
✅ 시간순 정렬 및 request_index 컬럼 추가 완료
✅ 출력 디렉토리 생성: .\output\

🎯 단일 방 1135 Rule4 차트 생성 시작
   - 총 요청 수: 200
   - 최대 정원: 50
   - 이상적 기대값 범위: 2 ~ 50
   - 실제값 범위: 1 ~ 48
   - Y축 최댓값: 60.0
   - 상태 전이 오류 발생: 25건
✅ 단일 방 차트 저장 완료: .\output\rule4_state_transition_analysis_room1135.png

📋 Rule4 CSV 보고서 생성 시작
   - 상태 전이 오류: 18건
   - CSV 보고서 생성 완료: 18건 → .\output\report_rule4_state_transition_errors_room1135.csv

✅ Rule 4 분석 완료!
```

## 활용 방안

### 1. 상태 일관성 분석
- 이상적 상태 전이와 실제 상태의 차이 패턴 분석
- 상태 불일치 발생 빈도와 심각도 평가

### 2. 동시성 제어 평가
- 상태 전이 오류 발생률 모니터링
- 임계구역 보호 메커니즘 효과성 검증

### 3. 시스템 안정성 진단
- 높은 오류율을 보이는 방 식별
- 상태 관리 로직 개선 방향 제시

### 4. 성능 최적화
- 상태 전이 패턴 분석을 통한 알고리즘 개선
- 동시 접근 제어 전략 수립

## 기술적 세부사항

### 데이터 처리
- **이중 파일 처리**: 차트용과 CSV용 파일 분리 관리
- **시간순 정렬**: curr_entry_time 기준 정렬 후 request_index 부여
- **알고리즘 기반 생성**: 순차적 증가 패턴 (index + 2) 기반 기대값
- **동적 Y축**: 데이터 범위에 따른 자동 스케일링
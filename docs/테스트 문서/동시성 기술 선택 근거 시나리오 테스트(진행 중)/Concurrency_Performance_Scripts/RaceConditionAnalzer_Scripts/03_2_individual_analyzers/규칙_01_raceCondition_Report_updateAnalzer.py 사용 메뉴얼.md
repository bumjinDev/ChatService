# Race Condition Rule 1 Lost Update 분석기 사용 메뉴얼

값 불일치(Lost Update) 분석 및 시각화를 위한 완전 독립 실행 가능한 Python 도구입니다. Rule 1 규칙에 특화된 분석과 차트 생성 기능을 제공합니다.

## 개요

이 도구는 Race Condition에서 발생하는 값 불일치(Lost Update) 오류를 분석하고 시각화합니다:

1. **값 불일치 오류 탐지**: `expected_people ≠ curr_people` 조건 기반 분석
2. **시각화 차트 생성**: 단일 방 분석 또는 전체 방 종합 분석 차트
3. **CSV 보고서 생성**: 값 불일치 발생 건에 대한 상세 보고서
4. **이중 데이터 소스**: 차트용 전처리 데이터 + CSV용 결과 데이터

## 주요 기능

- **완전 독립 실행**: 다른 분석기와 독립적으로 실행 가능
- **Rule 1 전용 분석**: 값 불일치 오류에만 특화된 분석
- **이중 차트 모드**: 단일 방 상세 분석 / 전체 방 종합 분석
- **이중 데이터 입력**: 전처리 파일(차트용) + 결과 파일(CSV용)
- **고정밀도 데이터 지원**: 나노초 정밀도 데이터 자동 감지
- **NaN 값 처리**: expected_people의 NaN 값을 max_people로 자동 대체

## 시스템 요구사항

```cmd
pip install pandas matplotlib numpy openpyxl
```

## 사용법

### 기본 사용법

```cmd
py -3 raceCondition_Report_updateAnalzer.py --preprocessor_file preprocessor.csv --result_file result.csv --output_dir output\
```

### 특정 방 분석

```cmd
py -3 raceCondition_Report_updateAnalzer.py --room_number 1135 --preprocessor_file preprocessor.csv --result_file result.csv --output_dir output\
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
py -3 raceCondition_Report_updateAnalzer.py --preprocessor_file preprocessed_data.csv --result_file analysis_result.csv --output_dir .\output\
```

### 예시 2: 특정 방 상세 분석 (상대 경로)
```cmd
py -3 raceCondition_Report_updateAnalzer.py --room_number 101 --preprocessor_file .\data\preprocessed.csv --result_file .\data\result.csv --output_dir .\reports\
```

### 예시 3: 절대 경로 사용
```cmd
py -3 raceCondition_Report_updateAnalzer.py --room_number 1135 --preprocessor_file C:\data\preprocessor.csv --result_file C:\data\analysis.csv --output_dir C:\reports\lost_update\
```

### 예시 4: 복잡한 디렉토리 구조
```cmd
py -3 raceCondition_Report_updateAnalzer.py --preprocessor_file data\room_logs\preprocessed_2024.csv --result_file results\anomaly_detection\detected.csv --output_dir analysis\rule1_results\ --room_number 202
```

## 입력 데이터 형식

### 1. 전처리 데이터 파일 (preprocessor_file)

차트 생성에 사용되는 전처리 데이터입니다.

#### 필수 컬럼

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `roomNumber` | int | 방 식별자 |
| `expected_people` | float | 연산 시점의 기대값 (NaN 허용) |
| `curr_people` | int | 실제 기록된 최종값 |
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
| `lost_update_diff` | float | 값 불일치의 차이값 |

#### anomaly_type 값 형식
- `"값 불일치"` 포함: Rule 1 분석 대상으로 필터링

#### 값 불일치 탐지 조건
```python
# NaN이 아닌 expected_people과 curr_people이 다른 경우
if pd.notna(expected_people) and expected_people != curr_people:
    # 값 불일치 발생
```

## 출력 형식

### 1. 차트 파일 (PNG)

#### 단일 방 분석 차트
- **파일명**: `rule1_lost_update_analysis_room{방번호}.png`
- **구성 요소**:
  - X축: 스레드 요청 순서 (Index)
  - Y축: 측정값 (인원 수)
  - 파란색 실선: 연산 시점의 기대값 (expected_people)
  - 주황색 실선: 실제 기록된 최종값 (curr_people)
  - 빨간색 수직 음영: 값 불일치 발생 시점

#### 전체 방 종합 분석 차트
- **파일명**: `rule1_lost_update_analysis_all_rooms.png`
- **구성 요소**:
  - X축: 스레드 요청 순서 (Index)
  - Y축: 측정값 (인원 수)
  - 파란색 실선: 평균 연산 시점의 기대값
  - 주황색 실선: 평균 실제 기록된 최종값
  - 파란색 음영: 기대값 표준편차 범위 (±1σ)
  - 주황색 음영: 실제값 표준편차 범위 (±1σ)
  - 빨간색 수직 음영: 값 불일치 발생 시점

#### 차트 공통 요소
- **범례**: 좌측 상단 배치
- **통계 박스**: 범례 우측 약 2cm 간격
- **격자**: 연한 점선 격자 (alpha=0.3)
- **X축 눈금**: 10개 동일 간격 + 마지막 지점

### 2. CSV 보고서

#### 단일 방 보고서
- **파일명**: `report_rule1_lost_update_errors_room{방번호}.csv`

#### 전체 방 보고서
- **파일명**: `report_rule1_lost_update_errors.csv`

#### CSV 컬럼 구성

| 컬럼명 | 설명 |
|--------|------|
| `roomNumber` | 방 식별자 |
| `bin` | 데이터 빈 식별자 |
| `room_entry_sequence` | 방 입장 순서 |
| `user_id` | 사용자 식별자 |
| `true_critical_section_start` | 임계구역 시작 시각 |
| `true_critical_section_end` | 임계구역 종료 시각 |
| `prev_people` | 이전 인원 수 |
| `expected_people` | 연산 시점의 기대값 |
| `curr_people` | 실제 기록된 최종값 |
| `lost_update_diff` | 값 불일치 차이 |

- **인코딩**: UTF-8 with BOM (utf-8-sig)
- **정렬**: roomNumber, bin, room_entry_sequence 순서
- **필터링**: anomaly_type에 "값 불일치" 포함된 행만

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

### 2. 값 불일치 탐지
```python
# 차트용: 전처리 데이터에서 직접 비교
for i in range(total_requests):
    original_expected = room_data.iloc[i]['expected_people']
    actual = curr_people_values[i]
    
    if pd.notna(original_expected) and original_expected != actual:
        # 값 불일치 발생

# CSV용: 결과 데이터에서 필터링
lost_update_anomalies = df_result[
    df_result['anomaly_type'].str.contains('값 불일치', na=False)
]
```

### 3. NaN 값 처리
```python
# expected_people의 NaN 값을 max_people로 대체 (시각화용)
expected_people_viz = [max_people if pd.isna(x) else x for x in expected_people_raw]
```

### 4. 단일 방 차트 생성
- X축: 요청 순서 인덱스 (0부터 총 요청수-1까지)
- Y축: 동적 계산 (max(expected_people, curr_people) × 1.2)
- 값 불일치 지점: 빨간색 수직 음영
- 통계 정보: 총 요청, 유효 기대값, 값 불일치, 불일치 비율

### 5. 전체 방 차트 생성
- 방별 데이터셋 분리 및 정렬
- 평균값과 표준편차 계산 (각 인덱스별)
- 신뢰구간 표시 (±1σ)
- 전체 통계: 분석 방 수, 총 요청, 전체 불일치 비율

## 클래스 구조

### Rule1LostUpdateAnalyzer 클래스

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
| `create_rule1_single_room_chart()` | 단일 방 상세 분석 차트 |
| `create_rule1_multi_room_chart()` | 전체 방 종합 분석 차트 |
| `generate_rule1_csv_report()` | CSV 보고서 생성 |
| `run_analysis()` | 전체 분석 실행 |

## 특징

### 1. 이중 데이터 소스
- **차트 생성**: preprocessor_file 사용
- **CSV 보고서**: result_file 사용
- 각각 다른 목적과 구조

### 2. NaN 값 안전 처리
- expected_people의 NaN 값을 max_people로 대체
- 시각화에서 연속성 보장

### 3. 유연한 실행 모드
- 단일 방 / 전체 방 자동 선택
- room_number 유무에 따른 분기

## 샘플 출력

```
🚀 Rule 1: Lost Update 분석 시작
✅ 전처리 파일 로드 완료: 2000건
✅ 결과 파일 로드 완료: 150건
✅ 고정밀도 나노초 데이터 감지
✅ 전처리 데이터 curr_entry_time 컬럼 datetime 변환 완료
✅ 결과 데이터 curr_entry_time 컬럼 datetime 변환 완료
✅ 방 1135 필터링 완료:
   - 전처리 데이터: 2000 → 200건
   - 결과 데이터: 150 → 15건
✅ 시간순 정렬 및 request_index 컬럼 추가 완료
✅ 출력 디렉토리 생성: .\output\

🎯 단일 방 1135 Rule1 차트 생성 시작
   - 총 요청 수: 200
   - 최대 정원: 50
   - NaN 값 5개를 max_people(50)로 대체
   - Y축 최댓값: 60.0
   - 값 불일치 발생: 12건
✅ 단일 방 차트 저장 완료: .\output\rule1_lost_update_analysis_room1135.png

📋 Rule1 CSV 보고서 생성 시작
   - 값 불일치 이상 현상: 15건
   - CSV 보고서 생성 완료: 15건 → .\output\report_rule1_lost_update_errors_room1135.csv

✅ Rule 1 분석 완료!
```

## 활용 방안

### 1. 동시성 제어 분석
- 예상값과 실제값의 차이 패턴 분석
- Lost Update 발생 빈도와 심각도 평가

### 2. 시스템 안정성 평가
- 값 불일치 발생률 모니터링
- 임계 상황 예측 및 대응

### 3. 성능 튜닝
- 높은 불일치율을 보이는 방 식별
- 동시성 제어 메커니즘 개선 방향 제시

## 기술적 세부사항

### 데이터 처리
- **이중 파일 처리**: 차트용과 CSV용 파일 분리 관리
- **시간순 정렬**: curr_entry_time 기준 정렬 후 request_index 부여
- **NaN 안전 처리**: expected_people NaN 값의 안전한 대체
- **동적 Y축**: 데이터 범위에 따른 자동 스케일링

### 오류 처리
- **파일 누락**: FileNotFoundError 처리
- **컬럼 누락**: 필수 컬럼 확인 및 빈 값 처리
- **빈 데이터**: 빈 DataFrame에 대한 안전한 처리
- **타입 변환**: datetime 변환 실패 시 안전한 처리
# Race Condition Rule 3 Capacity 분석기 사용 메뉴얼

정원 초과 오류 분석 및 시각화를 위한 완전 독립 실행 가능한 Python 도구입니다. Rule 3 규칙에 특화된 분석과 차트 생성 기능을 제공합니다.

## 개요

이 도구는 Race Condition에서 발생하는 정원 초과 오류(Capacity Exceeded Error)를 분석하고 시각화합니다:

1. **정원 초과 오류 탐지**: `curr_people > max_people` 조건 기반 분석
2. **시각화 차트 생성**: 단일 방 분석 또는 전체 방 종합 분석 차트
3. **CSV 보고서 생성**: 정원 초과 발생 건에 대한 상세 보고서

## 주요 기능

- **완전 독립 실행**: 다른 분석기와 독립적으로 실행 가능
- **Rule 3 전용 분석**: 정원 초과 오류에만 특화된 분석
- **이중 차트 모드**: 단일 방 상세 분석 / 전체 방 종합 분석
- **고정밀도 데이터 지원**: 나노초 정밀도 데이터 자동 감지

## 시스템 요구사항

```bash
pip install pandas matplotlib numpy
```

## 사용법

### 기본 사용법

```cmd
py -3 raceCondition_Report_capacityAnalzer.py --preprocessor_file data.csv --output_dir output\
```

### 특정 방 분석

```cmd
py -3 raceCondition_Report_capacityAnalzer.py --room_number 1135 --preprocessor_file data.csv --output_dir output\
```

### 명령행 인수

| 인수 | 설명 | 필수 여부 | 기본값 |
|------|------|-----------|--------|
| `--room_number` | 분석할 특정 방 번호 | 선택 | 전체 방 |
| `--preprocessor_file` | 전처리 데이터 CSV 파일 경로 | 필수 | - |
| `--result_file` | 분석 결과 CSV 파일 경로 | 선택 | 사용 안함 |
| `--output_dir` | 분석 결과 저장 디렉토리 경로 | 필수 | - |

## 사용 예시

### 예시 1: 전체 방 종합 분석 (현재 폴더)
```cmd
py -3 raceCondition_Report_capacityAnalzer.py --preprocessor_file preprocessed_data.csv --output_dir .\output\
```

### 예시 2: 특정 방 상세 분석 (상대 경로)
```cmd
py -3 raceCondition_Report_capacityAnalzer.py --room_number 101 --preprocessor_file .\data\preprocessed.csv --output_dir .\reports\
```

### 예시 3: 절대 경로 사용
```cmd
py -3 raceCondition_Report_capacityAnalzer.py --room_number 1135 --preprocessor_file C:\data\room_data.csv --output_dir C:\reports\capacity_analysis\
```

### 예시 4: 복잡한 디렉토리 구조
```cmd
py -3 raceCondition_Report_capacityAnalzer.py --preprocessor_file data\room_logs\preprocessed_2024.csv --output_dir results\rule3_analysis\ --room_number 202
```

## 입력 데이터 형식

### 전처리 데이터 파일 (preprocessor_file)

차트 생성과 CSV 보고서 작성에 사용되는 전처리 데이터입니다.

#### 필수 컬럼

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `roomNumber` | int | 방 식별자 |
| `curr_people` | int | 현재 인원 수 |
| `max_people` | int | 최대 방 수용인원 |
| `curr_entry_time` | datetime | 현재 입장 시각 |

#### 선택적 컬럼

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `bin` | int | 데이터 빈 식별자 |
| `room_entry_sequence` | int | 방 입장 순서 |
| `user_id` | string | 사용자 식별자 |
| `prev_entry_time` | datetime | 이전 입장 시각 |
| `*nanoTime*` | long | 나노초 정밀도 시간 데이터 |
| `*epochNano*` | long | 나노초 정밀도 에포크 시간 |

#### 정원 초과 탐지 조건
```python
# Rule 3 조건: curr_people > max_people
capacity_exceeded = df[df['curr_people'] > df['max_people']]
```

## 출력 형식

### 1. 차트 파일 (PNG)

#### 단일 방 분석 차트
- **파일명**: `rule3_capacity_exceeded_analysis_room{방번호}.png`
- **구성 요소**:
  - X축: 스레드 요청 순서 (Index)
  - Y축: 측정값 (인원 수)
  - 붉은색 점선: 최대 정원 한계선 (max_people)
  - 파란색 실선: 실제 기록된 인원수 (curr_people)
  - 자홍색 음영: 정원 초과 발생 시점
  - 빨간색 강조점: 초과 지점 표시

#### 전체 방 종합 분석 차트
- **파일명**: `rule3_capacity_exceeded_analysis_all_rooms.png`
- **구성 요소**:
  - X축: 스레드 요청 순서 (Index)
  - Y축: 측정값 (인원 수)
  - 붉은색 점선: 평균 최대 정원 한계선
  - 파란색 실선: 평균 실제 기록된 인원수
  - 파란색 음영: 실제값 신뢰구간 (±1σ)
  - 자홍색 음영: 정원 초과 발생 시점

#### 차트 공통 요소
- **한글 폰트**: 환경별 자동 설정 (Malgun Gothic, AppleGothic, NanumGothic 등)
- **범례**: 좌측 상단 배치
- **통계 박스**: 범례 우측 약 2cm 간격
- **격자**: 연한 점선 격자 (alpha=0.3)
- **X축 눈금**: 10개 동일 간격 + 마지막 지점

### 2. CSV 보고서

#### 단일 방 보고서
- **파일명**: `report_rule3_capacity_exceeded_errors_room{방번호}.csv`

#### 전체 방 보고서
- **파일명**: `report_rule3_capacity_exceeded_errors.csv`

#### CSV 컬럼 구성

| 컬럼명 | 설명 |
|--------|------|
| `roomNumber` | 방 식별자 |
| `bin` | 데이터 빈 식별자 |
| `room_entry_sequence` | 방 입장 순서 |
| `user_id` | 사용자 식별자 |
| `prev_entry_time` | 이전 입장 시각 |
| `curr_entry_time` | 현재 입장 시각 |
| `curr_people` | 현재 인원 수 |
| `max_people` | 최대 방 수용인원 |

- **인코딩**: UTF-8 with BOM (utf-8-sig)
- **정렬**: curr_entry_time 순서
- **필터링**: curr_people > max_people 조건만 포함

## 분석 로직

### 1. 데이터 로딩 및 전처리
```python
# CSV 파일 로드
df_preprocessor = pd.read_csv(preprocessor_file)

# 날짜 컬럼 변환
df_preprocessor['curr_entry_time'] = pd.to_datetime(df_preprocessor['curr_entry_time'])

# 방 번호 필터링 (지정된 경우)
if room_number:
    df_preprocessor = df_preprocessor[df_preprocessor['roomNumber'] == room_number]

# 시간순 정렬 및 인덱스 부여
df_preprocessor = df_preprocessor.sort_values('curr_entry_time').reset_index(drop=True)
df_preprocessor['request_index'] = range(len(df_preprocessor))
```

### 2. 정원 초과 탐지
```python
# Rule 3 조건: curr_people > max_people
capacity_exceeded = df_preprocessor[
    df_preprocessor['curr_people'] > df_preprocessor['max_people']
]
```

### 3. 단일 방 차트 생성
- X축: 요청 순서 인덱스 (0부터 총 요청수-1까지)
- Y축: 동적 계산 (max(curr_people, max_people) × 1.2)
- 정원 초과 지점: 자홍색 음영 + 빨간색 강조점
- 통계 정보: 총 요청, 최대 정원, 초과 건수, 초과 비율, 최대 초과량

### 4. 전체 방 차트 생성
- 방별 데이터셋 분리 및 정렬
- 평균값과 표준편차 계산 (각 인덱스별)
- 신뢰구간 표시 (±1σ)
- 전체 통계: 분석 방 수, 총 요청, 전체 초과 비율

### 5. 한글 폰트 설정
```python
# 운영체제별 폰트 자동 선택
system = platform.system()
font_mapping = {
    'Windows': 'Malgun Gothic',
    'Darwin': 'AppleGothic',  # macOS
    'Linux': 'NanumGothic'    # 또는 DejaVu Sans
}
```

## 클래스 구조

### Rule3CapacityAnalyzer 클래스

#### 초기화 매개변수
- `room_number`: 분석할 특정 방 번호 (선택)
- `preprocessor_file`: 전처리 데이터 파일 경로
- `result_file`: 분석 결과 파일 경로 (Rule3에서 사용 안함)
- `output_dir`: 출력 디렉토리 경로

#### 주요 메서드

| 메서드명 | 기능 |
|----------|------|
| `load_data()` | CSV 파일 로드 및 전처리 |
| `create_output_folders()` | 출력 폴더 생성 |
| `create_rule3_capacity_exceeded_chart()` | Rule 3 차트 생성 (분기 처리) |
| `_create_rule3_single_room_chart()` | 단일 방 상세 분석 차트 |
| `_create_rule3_multi_room_chart()` | 전체 방 종합 분석 차트 |
| `generate_rule3_csv_report()` | CSV 보고서 생성 |
| `run_analysis()` | 전체 분석 실행 |

## 샘플 출력

```
🚀 Rule 3: Capacity 분석 시작
✅ 전처리 파일 로드 완료: 5000건
✅ 고정밀도 나노초 데이터 감지
✅ curr_entry_time 컬럼 datetime 변환 완료
✅ prev_entry_time 컬럼 datetime 변환 완료
✅ 방 1135 필터링 완료: 5000 → 847건
✅ 시간순 정렬 및 request_index 컬럼 추가 완료
✅ 출력 디렉토리 생성: .\output\

🎯 단일 방 1135 Rule3 차트 생성 시작
   - 총 요청 수: 847
   - 최대 정원: 50
   - Y축 최댓값: 66.0
   - 정원 초과 발생: 23건
✅ 단일 방 차트 저장 완료: .\output\rule3_capacity_exceeded_analysis_room1135.png

📋 Rule3 CSV 보고서 생성 시작
   - 정원 초과 오류: 23건
   - CSV 보고서 생성 완료: 23건 → .\output\report_rule3_capacity_exceeded_errors_room1135.csv

✅ Rule 3 분석 완료!
```

## 활용 방안

### 1. 정원 관리 최적화
- 방별 정원 초과 패턴 분석
- 적정 수용인원 재설정 근거 제공

### 2. 시스템 안정성 평가
- 정원 초과 발생률 모니터링
- 임계 상황 예측 및 대응

### 3. 성능 튜닝
- 높은 초과율을 보이는 방 식별
- 동시성 제어 메커니즘 개선 방향 제시

## 기술적 세부사항

### 데이터 처리
- **시간순 정렬**: curr_entry_time 기준 정렬 후 request_index 부여
- **동적 Y축**: 데이터 범위에 따른 자동 스케일링
- **메모리 효율**: 방별 분리 처리로 대용량 데이터 처리 가능

### 오류 처리
- **파일 누락**: FileNotFoundError 처리
- **컬럼 누락**: 필수 컬럼 확인 및 빈 값 처리
- **빈 데이터**: 빈 DataFrame에 대한 안전한 처리
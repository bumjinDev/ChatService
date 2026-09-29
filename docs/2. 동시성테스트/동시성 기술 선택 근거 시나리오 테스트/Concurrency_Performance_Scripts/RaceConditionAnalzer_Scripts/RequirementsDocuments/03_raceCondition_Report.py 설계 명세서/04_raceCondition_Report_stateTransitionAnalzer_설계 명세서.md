# '경쟁 상태 정의.4' - 상태 전이 오류 분석 차트 요구사항

## 1. 핵심 목적

> [!NOTE]
> 경쟁 상태가 없을 때의 이상적인(Ideal) 데이터 증가 패턴과, 실제 부하 상황에서 기록된 현실 데이터를 하나의 차트에서 비교합니다.

> [!IMPORTANT]
> 두 데이터 간의 불일치가 발생하는 모든 지점을 시각적으로 강조하여, '데이터 정합성(Data Consistency)'이 언제, 어떻게 깨지는지를 명확하게 증명하는 것을 목표로 합니다.

---

## 2. 기본 구성

> [!IMPORTANT]
> 이 차트는 **"3. '경쟁 상태 정의.X' 유형별 차트 - 공통 기본 요구사항 명세"**의 모든 규칙을 그대로 상속받아 구현합니다.

### 차트 생성 파일 및 함수
- **구현 파일**: `generate_race_condition_report.py`
- **생성 함수**: `create_rule4_state_transition_chart()`, `_create_rule4_single_room_chart()`, `_create_rule4_multi_room_chart()`
- **CSV 생성**: `generate_rule4_csv_report()`

### 생성되는 파일명
- **단일방 차트**: `rule4_state_transition_analysis_room{room_number}.png`
- **전체방 차트**: `rule4_state_transition_analysis_all_rooms.png`
- **단일방 CSV**: `report_rule4_state_transition_errors_room{room_number}.csv`
- **전체방 CSV**: `report_rule4_state_transition_errors.csv`

---

## 3. 시각화 대상 데이터

### 데이터 1: 이상적 기대 인원수 (Programmatically Generated Expected Count)

| 속성 | 값 |
|------|-----|
| **데이터 소스** | N/A (알고리즘으로 동적 생성) |
| **시각화 방식** | 파란색 점선(Dashed Line) + 작은 원점 |

**생성 로직**:

테스트 시작 시, 방에는 이미 1명이 존재하는 것을 기본 상태로 가정합니다.

따라서 X축의 첫 번째 스레드 요청(인덱스 0)에 대한 기대 인원수는 2명이 됩니다.

이후 각 스레드 요청(인덱스 i)마다 기대 인원수는 1씩 선형적으로 증가합니다. 즉, 기대값은 인덱스 번호에 2를 더한 값입니다.

**최대 정원 제한**: 
- **단일방 분석**: 기대값과 해당 방의 최대 정원 중 작은 값을 선택하여 최대 정원 초과를 방지합니다.
- **전체방 분석**: 기대값과 평균 최대 정원 중 작은 값을 선택하여 평균 최대 정원 초과를 방지합니다.

> **팁**: **의미**: 어떠한 경쟁 상태도 발생하지 않았을 경우의 완벽한 데이터 변화를 나타내는 '기준선' 역할을 합니다.

### 데이터 2: 실제 기록 인원수 (Actual People Count)

| 속성 | 값 |
|------|-----|
| **데이터 소스** | racecondition_event_preprocessor_result.csv의 curr_people 컬럼 |
| **시각화 방식** | 주황색 실선(Solid Line) + 작은 원점 |

> [!TIP]
> **의미**: 동시 요청 상황에서 실제로 DB에 기록된 curr_people 값의 변화를 보여줍니다.

### 데이터 3: 불일치 발생 강조 표식 (Discrepancy Highlight)

**표시 조건**: 각 스레드 요청(X축 인덱스)에서 curr_people 값과 알고리즘으로 생성된 '이상적 기대 인원수' 값이 일치하지 않는 모든 경우에 표시합니다.

**시각화 방식**: 불일치가 발생한 X축 위치에 **수직 음영 영역(Vertical Shaded Span)**을 그립니다.

**색상**: 붉은색(Red) 계열의 반투명한 강조색을 사용합니다.

**실제 구현**: `axvspan(i-0.3, i+0.3, ymin=0, ymax=1, color='red', alpha=0.3)`

> [!TIP]
> **의미**: 'Stale Read' 또는 '순차 처리 실패' 등으로 인해 데이터 정합성이 깨진 지점과, 그 오차의 방향 및 크기를 시각적으로 즉시 인지할 수 있도록 강조합니다.

---

## 4. 분석적 의미

> [!WARNING]
> 이 차트에서 빨간색 음영 영역이 나타나는 모든 지점이 바로 '상태 전이 오류'가 발생한 명백한 증거입니다.

> [!NOTE]
> 스레드가 자신의 순서에 맞는 정상적인 상태 전이를 수행하지 못하고, 다른 스레드의 개입이나 비순차적 처리로 인해 예상과 다른 curr_people 값이 기록되었음을 의미합니다.

---

## 5. 데이터 소스 및 처리 흐름

### 차트 생성용 데이터
| 시각화 요소 | 데이터 소스 파일 | 사용 컬럼 | 비고 |
|-------------|------------------|-----------|------|
| 이상적 기대값 (점선) | N/A | (알고리즘으로 생성) | 로직: 인덱스 + 2와 최대 정원 중 작은 값 |
| 실제 기록값 (실선) | racecondition_event_preprocessor_result.csv | curr_people | 최종적으로 기록된 실제 값 |
| 상태 전이 오류 표식 (음영) | racecondition_event_preprocessor_result.csv & 알고리즘 | curr_people | curr_people이 기대값과 다른 경우 |

### CSV 보고서 생성용 데이터
| 처리 단계 | 데이터 소스 파일 | 필터링 조건 | 비고 |
|-----------|------------------|-------------|------|
| 이상 현상 필터링 | anomaly_result.csv | anomaly_type에 '상태 전이 오류' 포함 | 문자열 검색 방식 |
| CSV 출력 | 필터링된 데이터 | required_columns 선택 | 아래 컬럼 명세 참조 |

---

## 6. CSV 출력 명세

### 파일명 및 경로
- **구현 함수**: `generate_rule4_csv_report()`
- **room_number 지정 시**: `{rule4_folder}/report_rule4_state_transition_errors_room{room_number}.csv`
- **room_number 미지정 시**: `{rule4_folder}/report_rule4_state_transition_errors.csv`
- **저장 위치**: `race_condition_analysis_results/5_rule4_state_transition/`

### 데이터 필터링 및 처리
- **소스 데이터**: `self.df_result` (anomaly_result.csv)
- **필터링 조건**: `anomaly_type` 컬럼에 '상태 전이 오류' 문자열 포함
- **정렬 기준**: `roomNumber`, `bin`, `room_entry_sequence` 순
- **인코딩**: `utf-8-sig`

### 출력 컬럼 명세 (실제 구현 기준)

| 컬럼명 | 원본 파일 | 설명 | 실제 구현에서 처리 |
|--------|-----------|------|------------------|
| `roomNumber` | anomaly_result.csv | 오류가 발생한 방 번호 | 필수 컬럼 |
| `bin` | anomaly_result.csv | 오류가 발생한 테스트 구간 | 필수 컬럼 |
| `room_entry_sequence` | anomaly_result.csv | 방 입장 순서 (오류 발생 순번) | 필수 컬럼 |
| `sorted_sequence_position` | anomaly_result.csv | curr_entry_time 기준 실제 처리 완료 순번 | 존재 시 포함 |
| `user_id` | anomaly_result.csv | 오류를 유발한 사용자 ID | 필수 컬럼 |
| `true_critical_section_start` | anomaly_result.csv | 임계 구역 진입 시각 | 존재 시 포함 |
| `true_critical_section_end` | anomaly_result.csv | 임계 구역 종료 시각 | 존재 시 포함 |
| `prev_people` | anomaly_result.csv | 임계 구역 진입 시점에 확인된 현재 인원수 | 필수 컬럼 |
| `expected_curr_by_sequence` | anomaly_result.csv | 시간 순서상 기대되었던 결과값 | 필수 컬럼 |
| `actual_curr_people` | anomaly_result.csv | 실제로 기록된 결과값 | 필수 컬럼 |
| `curr_sequence_diff` | anomaly_result.csv | 시간 순서 기대값과 실제값의 차이 | 필수 컬럼 |

---

## 7. 실제 구현 세부사항

### 7.1 단일방 차트 구현 (`_create_rule4_single_room_chart`)

**이상적 기대값 계산**:
```python
# 해당 방의 최대 정원 확인
max_people = room_data['max_people'].iloc[0] if not room_data.empty else 20

# 이상적 기대값 (알고리즘으로 생성: 인덱스 + 2, 단 최대 정원 초과 안함)
ideal_expected_values = [min(i + 2, max_people) for i in x_positions]
```

**시각화 스타일**:
- 이상적 기대값: 파란색 점선 + 작은 원점
- 실제 기록 인원수: 주황색 실선 + 작은 원점
- 상태 전이 오류 표식: 수직 음영 영역

### 7.2 전체방 차트 구현 (`_create_rule4_multi_room_chart`)

**평균 최대 정원 계산**:
```python
# 전체 방의 평균 최대 정원 계산
avg_max_people = int(self.df_preprocessor['max_people'].mean()) if not self.df_preprocessor.empty else 20

# 이상적 기대값 (평균 최대 정원 초과 안함)
ideal_expected_values = [min(i + 2, avg_max_people) for i in x_positions]
```

**신뢰구간 시각화**:
- 실제값 신뢰구간: `fill_between()`으로 평균 ± 표준편차 영역 표시
- 평균 실제 인원수: 주황색 실선 + 작은 원점

### 7.3 차트 공통 설정
- **차트 크기**: `figsize=(20, 12)`
- **Y축 범위**: `max_val * 1.2`로 동적 계산
- **범례 위치**: `loc='upper left'`
- **통계 정보 박스**: `transform=ax.transAxes, x=0.2, y=0.98`로 범례 우측 배치

---

## 8. 차트 제목 및 통계 정보

### 8.1 차트 제목
- **단일방**: `f'규칙 4: 상태 전이 오류 분석 - 방 {self.room_number}'`
- **전체방**: `f"규칙 4: 상태 전이 오류 분석 - 전체 {len(rooms)}개 방 평균 및 신뢰구간"`

### 8.2 통계 정보 박스 내용
**단일방 분석**:
- 총 요청수, 상태 전이 오류 건수, 오류 비율

**전체방 분석**:
- 분석 방 수, 총 요청수, 상태 전이 오류 건수, 전체 오류 비율
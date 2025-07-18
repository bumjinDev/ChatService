# '경쟁 상태 정의.3' - 정원 초과 오류 분석 차트 요구사항

## 1. 핵심 목적

> **참고**: 이 문서는 **규칙 3: 정원 초과 오류 (Capacity Exceeded Error)**를 시각적으로 분석하고 증명하기 위한 2차원 선형 그래프의 상세 요구사항을 정의합니다.

> **중요**: 이 차트의 핵심 목표는 **'실제 기록된 인원수' 실선**이 **'최대 정원 한계선' 점선**을 초과하는 지점을 식별하는 것입니다.

> **경고**: 정원 초과가 발생한 정확한 시점(스레드 요청 순서)을 명확하게 강조하여, 어느 요청에서 비즈니스 규칙 위반이 발생했는지 즉시 인지할 수 있도록 합니다.

## 2. 기본 구성

> **중요**: 이 차트는 **"3. '경쟁 상태 정의.X' 유형별 차트 - 공통 기본 요구사항 명세"**의 모든 규칙을 그대로 상속받아 구현합니다.

### 차트 생성 파일 및 함수
- **구현 파일**: `generate_race_condition_report.py`
- **생성 함수**: `_create_rule3_single_room_chart()`, `_create_rule3_multi_room_chart()`
- **CSV 생성**: `generate_rule3_csv_report()`

### 생성되는 파일명
- **단일방 차트**: `rule3_capacity_exceeded_analysis_room{room_number}.png`
- **전체방 차트**: `rule3_capacity_exceeded_analysis_all_rooms.png`
- **단일방 CSV**: `report_rule3_capacity_exceeded_errors_room{room_number}.csv`
- **전체방 CSV**: `report_rule3_capacity_exceeded_errors.csv`

---

## 3. 시각화 대상 데이터

### 데이터 1: 최대 정원 한계선 (Max Capacity Limit)

| 속성 | 값 |
|------|-----|
| **데이터 소스** | `racecondition_event_preprocessor_result.csv`의 `max_people` 컬럼 |
| **시각화 방식** | 붉은색 수평 점선 (Red Dashed Line) |

> **팁**: **설명**: 방의 최대 수용 인원을 나타내는 기준선입니다.

### 데이터 2: 실제 기록된 인원수 (Actual People Count)

| 속성 | 값 |
|------|-----|
| **데이터 소스** | `racecondition_event_preprocessor_result.csv`의 `curr_people` 컬럼 |
| **시각화 방식** | 파란색 실선 + 작은 원점 (Blue Solid Line + Markers) |

**실제 구현**: 파란색 실선과 원형 마커로 구현됩니다.

> **팁**: **설명**: 각 스레드 요청이 처리된 후, DB에 실제로 기록된 curr_people 값의 변화를 보여주는 선입니다.

### 데이터 3: 정원 초과 발생 시점 강조 (Over-Capacity Point Highlight)

**표시 조건**: 각 스레드 요청(X축 인덱스)에서 `curr_people` 값이 `max_people` 값을 초과하는 경우에만 표시합니다.

**시각화 방식**: 오류가 발생한 해당 X축 위치에 **수직 음영 영역(Vertical Shaded Span)**을 그립니다.

**색상**: 자홍색(Magenta) 계열의 반투명한 강조색을 사용합니다.

**실제 구현**: 수직 음영 영역과 추가로 초과 지점에 빨간색 강조점을 표시합니다.

---

## 4. 분석적 의미

> **경고**: 이 차트에서 자홍색 음영 영역이 나타나는 모든 지점이 바로 **비즈니스 규칙을 명백히 위반한 심각한 오류**의 증거입니다.

> **참고**: 시스템이 허용해서는 안 되는 정원 초과 상황이 발생했음을 의미하며, 경쟁 상태로 인한 동시성 제어 실패를 나타냅니다.

---

## 5. 데이터 소스 및 처리 흐름

### 차트 생성용 데이터
| 시각화 요소 | 데이터 소스 파일 | 사용 컬럼 | 비고 |
|-------------|------------------|-----------|------|
| 최대 정원 한계선 | racecondition_event_preprocessor_result.csv | max_people | 방의 최대 수용 인원 기준선 |
| 실제 기록 인원수 | racecondition_event_preprocessor_result.csv | curr_people | 최종적으로 기록된 실제 값 |
| 정원 초과 표식 | racecondition_event_preprocessor_result.csv | curr_people, max_people | curr_people > max_people 조건 |

### CSV 보고서 생성용 데이터
| 처리 단계 | 데이터 소스 파일 | 필터링 조건 | 비고 |
|-----------|------------------|-------------|------|
| 정원 초과 필터링 | racecondition_event_preprocessor_result.csv | curr_people > max_people | 직접 조건 비교 |
| CSV 출력 | 필터링된 데이터 | required_columns 선택 | 아래 컬럼 명세 참조 |

### 다중 방 분석 처리
- **curr_people 평균 계산**: 각 X축 지점마다 모든 방의 curr_people 평균 산출
- **max_people 평균 계산**: 각 X축 지점마다 모든 방의 max_people 평균 산출  
- **신뢰구간**: `fill_between()`으로 평균 ± 표준편차 영역 표시
- **범례**: "평균 실제 기록된 인원수", "실제값 신뢰구간 (±1σ)", "평균 최대 정원 한계선" 등

### 차트 스타일 세부사항
- **X축 눈금**: 전체 데이터를 10개 동일 간격으로 자동 분할
- **Y축 범위**: 최댓값의 1.2배로 동적 계산 (`max_val * 1.2`)
- **범례 위치**: 좌측 상단 (`loc='upper left'`)
- **통계 정보 박스**: 범례 우측 약 2cm 간격으로 배치
  - 포함 정보: 총 요청수, 최대 정원, 정원 초과 건수, 초과 비율, 최대 초과 인원
- **한글 폰트**: `setup_korean_font()` 함수로 자동 설정
- **강조 표시**: 정원 초과 지점에 빨간색 강조점 추가

---

## 6. CSV 출력 명세

### 파일명 및 경로
- **구현 함수**: `generate_rule3_csv_report()`
- **room_number 지정 시**: `{rule3_folder}/report_rule3_capacity_exceeded_errors_room{room_number}.csv`
- **room_number 미지정 시**: `{rule3_folder}/report_rule3_capacity_exceeded_errors.csv`
- **저장 위치**: `race_condition_analysis_results/4_rule3_capacity_exceeded/`

### 데이터 필터링 및 처리
- **소스 데이터**: `self.df_preprocessor` (racecondition_event_preprocessor_result.csv)
- **필터링 조건**: `curr_people > max_people` 직접 비교
- **정렬 기준**: `curr_entry_time` 순 (원본 정렬 유지)
- **인코딩**: `utf-8-sig`

### 출력 컬럼 명세

| 컬럼명 | 원본 파일 | 설명 | 실제 구현에서 처리 |
|--------|-----------|------|------------------|
| `roomNumber` | racecondition_event_preprocessor_result.csv | 오류가 발생한 방 번호 | 필수 컬럼 |
| `bin` | racecondition_event_preprocessor_result.csv | 오류가 발생한 테스트 구간 | 필수 컬럼 |
| `room_entry_sequence` | racecondition_event_preprocessor_result.csv | 방 입장 순서 (오류 발생 순번) | 필수 컬럼 |
| `user_id` | racecondition_event_preprocessor_result.csv | 정원 초과를 유발한 사용자 ID | 필수 컬럼 |
| `prev_entry_time` | racecondition_event_preprocessor_result.csv | 임계 구역 진입 시각 | 필수 컬럼 |
| `curr_entry_time` | racecondition_event_preprocessor_result.csv | 임계 구역 종료 시각 | 필수 컬럼 |
| `curr_people` | racecondition_event_preprocessor_result.csv | 정원을 초과한 실제 인원수 | 필수 컬럼 |
| `max_people` | racecondition_event_preprocessor_result.csv | 방의 최대 정원 | 필수 컬럼 |

> **참고**: 실제 구현에서는 `curr_people > max_people` 조건으로 필터링하여, 정원 초과가 발생한 레코드만 추출합니다.
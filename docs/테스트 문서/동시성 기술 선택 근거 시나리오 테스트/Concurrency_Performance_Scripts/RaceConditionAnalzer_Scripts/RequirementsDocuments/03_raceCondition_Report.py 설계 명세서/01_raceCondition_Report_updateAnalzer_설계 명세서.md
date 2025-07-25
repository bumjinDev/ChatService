# '경쟁 상태 정의.1' - 값 불일치(Lost Update) 분석 차트 요구사항

## 1. 핵심 목적

> **중요**: **'Lost Update'**란, 어떤 스레드가 자신의 연산을 수행한 후 데이터가 특정 상태가 될 것이라고 예상했지만, 그 사이에 다른 스레드의 개입으로 인해 자신이 기대했던 값과 다른 값으로 최종 기록되는 현상입니다.

> **참고**: 이 차트는 각 스레드가 작업을 시작하던 시점에 기대했던 결과와, 실제로 최종 기록된 결과를 비교하여 'Lost Update'가 발생하는 모든 순간을 시각적으로 증명하는 것을 목표로 합니다.

## 2. 기본 구성

> **중요**: 이 차트는 **"3. '경쟁 상태 정의.X' 유형별 차트 - 공통 기본 요구사항 명세"**의 모든 규칙을 그대로 상속받아 구현합니다.

### 차트 생성 파일 및 함수
- **구현 파일**: `generate_race_condition_report.py`
- **생성 함수**: `_create_rule1_single_room_chart()`, `_create_rule1_multi_room_chart()`
- **CSV 생성**: `generate_rule1_csv_report()`

### 생성되는 파일명
- **단일방 차트**: `rule1_lost_update_analysis_room{room_number}.png`
- **전체방 차트**: `rule1_lost_update_analysis_all_rooms.png`
- **단일방 CSV**: `report_rule1_lost_update_errors_room{room_number}.csv`
- **전체방 CSV**: `report_rule1_lost_update_errors.csv`

---

## 3. 시각화 대상 데이터

### 데이터 1: 연산 시점의 기대값 (Expected Value at Operation Time)

| 속성 | 값 |
|------|-----|
| **데이터 소스** | `racecondition_event_preprocessor_result.csv`의 `expected_people` 컬럼 |
| **시각화 방식** | 파란색 실선 + 작은 원점 (Blue Solid Line + Markers) |

> **팁**: **의미**: 이 값은 각 스레드가 임계 구역에 진입하여 값을 읽었을 때(prev_people), 그 값을 기반으로 "정상이라면 prev_people + 1이 되어야 한다"고 기대했던 값입니다.

### 데이터 2: 실제 기록된 최종값 (Actual Result Value)

| 속성 | 값 |
|------|-----|
| **데이터 소스** | `racecondition_event_preprocessor_result.csv`의 `curr_people` 컬럼 |
| **시각화 방식** | 주황색 실선 + 작은 원점 (Orange Solid Line + Markers) |

> **팁**: **의미**: 모든 연산이 끝나고 최종적으로 기록된 값의 변화를 보여줍니다.

### 데이터 3: 값 불일치(Lost Update) 강조 표식

**표시 조건**: 각 스레드 요청(X축 인덱스)에서 `curr_people` 값과 `expected_people` 값이 일치하지 않는 모든 경우에 표시합니다.

**시각화 방식**: 불일치가 발생한 X축 위치에 **수직 음영 영역(Vertical Shaded Span)**을 그립니다.

**색상**: 빨간색(Red) 계열의 반투명한 강조색을 사용합니다.

**실제 구현**: 불일치가 발생한 X축 위치에 수직 음영 영역을 그리며, 개수를 추적하여 통계 정보에 반영합니다.

**NaN 처리**: `expected_people`이 NaN인 경우 `max_people` 값으로 대체하여 시각화합니다.

---

## 4. 분석적 의미

> **경고**: 이 차트에서 빨간색 음영 영역이 나타나는 모든 지점이 바로 'Lost Update'가 발생한 명백한 증거입니다.

> **참고**: 스레드가 prev_people를 읽고 +1을 하여 expected_people을 기대했지만, 그 사이에 다른 스레드가 개입하여 자신이 기대한 값과 다른 curr_people 값이 최종적으로 기록되었음을 의미합니다.

---

## 5. 데이터 소스 및 처리 흐름

### 차트 생성용 데이터
| 시각화 요소 | 데이터 소스 파일 | 사용 컬럼 | 비고 |
|-------------|------------------|-----------|------|
| 기대값 선 | racecondition_event_preprocessor_result.csv | expected_people | NaN 값은 max_people로 대체 |
| 실제값 선 | racecondition_event_preprocessor_result.csv | curr_people | 최종적으로 기록된 실제 값 |
| 불일치 표식 | racecondition_event_preprocessor_result.csv | expected_people, curr_people | curr_people ≠ expected_people 조건 |

### CSV 보고서 생성용 데이터
| 처리 단계 | 데이터 소스 파일 | 필터링 조건 | 비고 |
|-----------|------------------|-------------|------|
| 이상 현상 필터링 | racecondition_analysis.csv | anomaly_type에 '값 불일치' 포함 | 문자열 검색 방식 |
| CSV 출력 | 필터링된 데이터 | required_columns 선택 | 아래 컬럼 명세 참조 |

---

## 6. CSV 출력 명세

### 파일명 및 경로
- **구현 함수**: `generate_rule1_csv_report()`
- **room_number 지정 시**: `{rule1_folder}/report_rule1_lost_update_errors_room{room_number}.csv`
- **room_number 미지정 시**: `{rule1_folder}/report_rule1_lost_update_errors.csv`
- **저장 위치**: `race_condition_analysis_results/2_rule1_lost_update/`

### 데이터 필터링 및 처리
- **소스 데이터**: `self.df_result` (racecondition_analysis.csv)
- **필터링 조건**: `anomaly_type` 컬럼에 '값 불일치' 문자열 포함
- **정렬 기준**: `roomNumber`, `bin`, `room_entry_sequence` 순
- **인코딩**: `utf-8-sig`

### 출력 컬럼 명세

| 컬럼명 | 원본 파일 | 설명 | 실제 구현에서 처리 |
|--------|-----------|------|------------------|
| `roomNumber` | racecondition_analysis.csv | 오류가 발생한 방 번호 | 필수 컬럼 |
| `bin` | racecondition_analysis.csv | 오류가 발생한 테스트 구간 | 필수 컬럼 |
| `room_entry_sequence` | racecondition_analysis.csv | 방 입장 순서 (오류 발생 순번) | 필수 컬럼 |
| `user_id` | racecondition_analysis.csv | 오류를 유발한 사용자 ID | 필수 컬럼 |
| `true_critical_section_start` | racecondition_analysis.csv | 임계 구역 진입 시각 | 존재 시 포함 |
| `true_critical_section_end` | racecondition_analysis.csv | 임계 구역 종료 시각 | 존재 시 포함 |
| `prev_people` | racecondition_analysis.csv | 임계 구역 진입 시점에 확인된 현재 인원수 | 필수 컬럼 |
| `expected_people` | racecondition_analysis.csv | 스레드가 기대했던 결과값 | 필수 컬럼 |
| `curr_people` | racecondition_analysis.csv | 실제로 기록된 결과값 | 필수 컬럼 |
| `lost_update_diff` | racecondition_analysis.csv | 기대값과 실제값의 차이 | 필수 컬럼 |

> **참고**: 실제 구현에서는 `available_columns`를 확인하여 존재하는 컬럼만 선택하고, 누락된 컬럼은 빈 문자열로 채웁니다.
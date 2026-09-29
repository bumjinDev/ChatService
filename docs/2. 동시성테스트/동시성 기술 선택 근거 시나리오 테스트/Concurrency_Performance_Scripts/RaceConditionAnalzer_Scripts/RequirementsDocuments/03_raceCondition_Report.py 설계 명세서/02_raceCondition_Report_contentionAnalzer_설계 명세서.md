# '경쟁 상태 정의.2' - 경합 발생 분석 차트 요구사항

## 1. 핵심 목적

> **참고**: 각 스레드의 임계 구역(Critical Section)이 실제 시간 축 위에서 언제 시작하고 언제 끝나는지를 시각적으로 명확하게 표현합니다.

> **중요**: 시간 축 상에서 **겹치는 막대(Overlapping Bars)**를 통해 어느 스레드들이 서로 경합했는지 직관적으로 식별합니다.

> **팁**: 각 경합의 **심각도(경합 스레드 수)**와 **상세 정보(경합 스레드 목록)**를 제공하여, 문제의 원인을 심층적으로 분석할 수 있도록 지원합니다.

## 2. 기본 구성

> **중요**: 이 차트는 **"3. '경쟁 상태 정의.X' 유형별 차트 - 공통 기본 요구사항 명세"**의 모든 규칙을 그대로 상속받아 구현합니다.

### 차트 생성 파일 및 함수
- **구현 파일**: `generate_race_condition_report.py`
- **생성 함수**: `create_rule2_contention_gantt_chart()`
- **CSV 생성**: `generate_rule2_csv_report()`

### 생성되는 파일명
- **차트**: `contention_gantt_chart_room{room_number}.png` (단일방만 지원)
- **CSV**: `report_rule2_contention_details_room{room_number}.csv`

### 중요한 제약사항
> **경고**: 이 간트 차트는 개별 방의 상세 동작을 분석하기 위한 '드릴다운' 목적이므로, **room_number 파라미터가 반드시 지정되어야만 생성됩니다**. room_number가 지정되지 않은 경우, 이 차트는 생성하지 않습니다.

**Y축 정렬**: 스레드들은 `true_critical_section_start`, `room_entry_sequence` 순으로 정렬되어 Y축에 배치됩니다.

---

## 3. 시각화 대상 데이터

### 간트 차트 요소

**차트 종류**: 간트 차트 (Gantt Chart) 형식의 수평 막대 그래프

**X축 (시간)**: 분석 대상이 되는 전체 요청 중 가장 이른 임계 구역 시작 시각에서 가장 늦은 임계 구역 종료 시각까지로 동적으로 설정합니다.

**Y축 (스레드)**: 경합에 연루된 각 스레드(user_id)를 Y축의 개별 항목으로 표시합니다.

### 시각화 요소 1: 임계 구역 막대 (Critical Section Bar)

**표시 내용**: 각 user_id마다, true_critical_section_start에서 true_critical_section_end까지 이어지는 수평 막대를 그립니다.

**실제 구현**: 수평 막대 그래프로 구현되며, 높이와 투명도, 색상, 테두리 스타일이 적용됩니다.

**Duration 보정**: 0 이하의 duration은 0.001초로 보정하여 시각적으로 표현됩니다.

### 시각화 요소 2: 경합 스레드 수 표기 (Contention Count Annotation)

**표시 내용**: 각 수평 막대의 끝 지점 바로 우측에, 해당 임계 구역에 동시에 접근한 총 스레드의 개수를 작은 숫자로 표기합니다.

**데이터 소스**: contention_group_size 컬럼 값을 사용합니다.

**텍스트 스타일**: 폰트 크기와 굵기가 설정되어 막대 종료 시점 기준으로 배치됩니다.

---

## 4. 분석적 의미

> **참고**: 시간 축에서 겹치는 막대들은 동시에 임계 구역에 접근한 스레드들을 의미하며, 이는 경쟁 상태 발생의 직접적인 증거입니다.

> **팁**: 막대 우측의 숫자가 클수록 더 많은 스레드가 동시에 경합했음을 의미하여, 문제의 심각도를 나타냅니다.

---

## 5. 데이터 소스 및 처리 흐름

### 차트 생성용 데이터
| 시각화 요소 | 데이터 소스 파일 | 사용 컬럼 | 비고 |
|-------------|------------------|-----------|------|
| 임계 구역 막대 | racecondition_analysis.csv | true_critical_section_start, true_critical_section_end | 각 스레드의 임계 구역 지속 시간 |
| 경합 스레드 수 | racecondition_analysis.csv | contention_group_size | 막대 우측에 표기되는 숫자 |

### CSV 보고서 생성용 데이터
| 처리 단계 | 데이터 소스 파일 | 필터링 조건 | 비고 |
|-----------|------------------|-------------|------|
| 이상 현상 필터링 | racecondition_analysis.csv | anomaly_type에 '경합 발생' 포함 | 문자열 검색 방식 |
| CSV 출력 | 필터링된 데이터 | required_columns 선택 | 아래 컬럼 명세 참조 |

### 차트 스타일 세부사항
- **차트 제목**: `f'규칙 2: 경합 발생 간트 차트 - 방 {room_number}'`
- **Y축 설정**: 사용자 ID를 fontsize=10으로 표시
- **X축 설정**: 시간 라벨을 45도 회전하여 표시
- **차트 크기**: 20x12 inch로 설정
- **범례 위치**: 기본 설정 사용
- **한글 폰트**: 자동 설정

---

## 6. CSV 출력 명세

### 파일명 및 경로
- **구현 함수**: `generate_rule2_csv_report()`
- **파일명**: `{rule2_folder}/report_rule2_contention_details_room{room_number}.csv`
- **저장 위치**: `race_condition_analysis_results/3_rule2_contention/`

### 데이터 필터링 및 처리
- **소스 데이터**: `self.df_result` (racecondition_analysis.csv)
- **필터링 조건**: `anomaly_type` 컬럼에 '경합 발생' 문자열 포함
- **정렬 기준**: `roomNumber`, `bin`, `room_entry_sequence` 순
- **인코딩**: `utf-8-sig`
- **duration 계산**: 종료 시각에서 시작 시각을 뺀 초 단위 값
- **예외 처리**: datetime 변환 실패 시 빈 문자열로 처리

### 출력 컬럼 명세

| 컬럼명 | 원본 파일 | 설명 | 실제 구현에서 처리 |
|--------|-----------|------|------------------|
| `roomNumber` | racecondition_analysis.csv | 경합이 발생한 방 번호 | 필수 컬럼 |
| `bin` | racecondition_analysis.csv | 경합이 발생한 테스트 구간 | 필수 컬럼 |
| `user_id` | racecondition_analysis.csv | 기준 사용자 ID | 필수 컬럼 |
| `contention_group_size` | racecondition_analysis.csv | 해당 경합에 포함된 총 스레드 수 | 필수 컬럼 |
| `contention_user_ids` | racecondition_analysis.csv | 경합에 포함된 모든 스레드의 user_id 목록 | 필수 컬럼 |
| `true_critical_section_start` | racecondition_analysis.csv | 임계 구역 진입 시각 | 필수 컬럼 |
| `true_critical_section_end` | racecondition_analysis.csv | 임계 구역 종료 시각 | 필수 컬럼 |
| `true_critical_section_duration` | racecondition_analysis.csv | 임계 구역 지속 시간(초) | 동적 계산됨 |

> **참고**: 실제 구현에서는 datetime 변환 후 초 단위로 지속 시간을 계산합니다.
# Race Condition Event Detector

동시성 시스템에서 발생하는 경쟁 상태 이상 현상을 탐지하고 분석하는 Python 도구입니다. 원본 CSV 데이터를 그대로 사용하여 분석합니다.

## 개요

이 도구는 다중 사용자 환경(예: 방 입장 시스템)에서 경쟁 상태 이벤트를 분석하고 4가지 유형의 이상 현상을 식별합니다:

1. **값 불일치 (Lost Update)** - 의도한 갱신이 누락되거나 덮어쓰여지는 경우
2. **경합 발생 탐지** - 임계구역이 겹쳐서 동시성 제어 부재를 나타내는 경우
3. **정원 초과 오류** - 비즈니스 규칙 위반 (예: 방 정원 초과)
4. **상태 전이 오류** - 작업 중 오염된 상태나 일관성 없는 상태를 읽는 경우

## 주요 기능

- **원본 데이터 보존**: 제공된 데이터를 계산이나 수정 없이 그대로 분석
- **나노초 정밀도**: 임계구역에 대한 고정밀도 타이밍 분석 지원
- **다양한 출력 형식**: CSV 및 Excel 내보내기와 상세 분석 제공
- **포괄적인 보고**: 타이밍 정보가 포함된 상세 텍스트 보고서 생성
- **유연한 필터링**: 특정 방 또는 전체 데이터셋 분석 가능

## 시스템 요구사항

```bash
pip install pandas numpy openpyxl
```

## 사용법

### 기본 사용법

```cmd
py -3 racecondition_event_detector.py input.csv output.csv
```

### 옵션 사용법

```cmd
py -3 racecondition_event_detector.py input.csv output.csv --detailed_output detailed_analysis.txt --rooms 1,2,3 --xlsx_output results.xlsx
```

### 명령행 옵션

| 옵션 | 설명 | 기본값 |
|-----|------|--------|
| `input_csv` | 입력 CSV 파일 경로 | 필수 |
| `output_csv` | 출력 CSV 파일 경로 (이상 현상만) | 필수 |
| `--detailed_output` | 상세 분석 텍스트 파일 | `detailed_analysis.txt` |
| `--rooms` | 분석할 방 번호 (쉼표로 구분) | 전체 방 |
| `--xlsx_output` | Excel 출력 파일 경로 | 없음 |

## 사용 예시

### 예시 1: 전체 방 분석 (현재 폴더)
```cmd
py -3 racecondition_event_detector.py data.csv anomalies.csv
```

### 예시 2: 특정 방 분석 및 Excel 출력 (상대 경로)
```cmd
py -3 racecondition_event_detector.py ./input/data.csv ./output/anomalies.csv --rooms 101,102,103 --xlsx_output ./output/analysis.xlsx --detailed_output ./reports/detailed_report.txt
```

### 예시 3: 절대 경로 사용
```cmd
py -3 racecondition_event_detector.py C:\data\race_condition_data.csv C:\results\detected_anomalies.csv --detailed_output C:\reports\race_condition_analysis.txt
```

### 예시 4: 하위 폴더 구조 활용
```cmd
py -3 racecondition_event_detector.py data\room_logs\2024_log.csv results\anomaly_detection\output.csv --xlsx_output results\excel\analysis_report.xlsx
```

## 입력 데이터 형식

입력 CSV는 다음 컬럼들을 포함해야 합니다:

### 필수 컬럼

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `roomNumber` | int | 방 식별자 |
| `bin` | string | 데이터 빈 식별자 |
| `user_id` | string | 사용자 식별자 |
| `prev_people` | int | 이전 인원 수 |
| `curr_people` | int | 현재 인원 수 |
| `expected_people` | int | 예상 인원 수 |
| `max_people` | int | 최대 방 수용인원 |
| `prev_entry_time` | datetime | 이전 입장 시각 |
| `curr_entry_time` | datetime | 현재 입장 시각 |
| `room_entry_sequence` | int | 원본 방 입장 순서 |

### 선택적 컬럼

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `true_critical_section_nanoTime_start` | long | 나노초 정밀도 시작 시각 |
| `true_critical_section_nanoTime_end` | long | 나노초 정밀도 종료 시각 |

## 탐지 규칙

### 규칙 1: 값 불일치 (Lost Update)
```
조건: curr_people ≠ expected_people
의미: 다른 사용자의 작업으로 의도한 갱신이 누락되거나 덮어쓰여짐
```

### 규칙 2: 경합 발생 탐지
```
조건: 임계구역이 1나노초라도 겹침
의미: 동시성 제어 부재로 잠재적 위험 노출
```

### 규칙 3: 정원 초과 오류
```
조건: curr_people > max_people
의미: 비즈니스 규칙을 명백히 위반한 심각한 오류
```

### 규칙 4: 상태 전이 오류
```
조건: curr_people ≠ 1 + room_entry_sequence
의미: 올바른 상태를 읽지 못하고 오염된 상태로 작업
```

## 출력 형식

### CSV 출력

출력 CSV는 모든 원본 컬럼에 추가 분석 컬럼들을 포함합니다:

#### 이상 현상 상세 정보
- `anomaly_type`: 탐지된 이상 현상 유형
- `lost_update_expected`: 손실된 갱신의 예상값
- `lost_update_actual`: 손실된 갱신의 실제값
- `lost_update_diff`: 예상값과 실제값의 차이
- `contention_group_size`: 경합 그룹의 사용자 수
- `contention_user_ids`: 충돌하는 사용자 ID 목록
- `over_capacity_amount`: 정원 초과 인원 수
- `expected_curr_by_sequence`: 순서에 따른 예상 현재 인원
- `actual_curr_people`: 실제 현재 인원 수
- `curr_sequence_diff`: 순서 예상값과의 차이

#### 타이밍 분석
- `true_critical_section_start`: 임계구역 시작 시각
- `true_critical_section_end`: 임계구역 종료 시각
- `true_critical_section_duration`: 지속 시간 (초)
- `true_critical_section_duration_nanos`: 지속 시간 (나노초)
- `intervening_users_in_critical_section`: 임계구역 개입 사용자들
- `intervening_user_count_critical`: 개입 사용자 수

### 상세 텍스트 보고서

상세 분석에는 다음이 포함됩니다:
- 요약 통계
- 개별 이상 현상 분석
- 타이밍 정보
- 임계구역 겹침 상세 정보
- 비즈니스 규칙 위반 사항

## 샘플 출력

```
🚀 Race Condition 분석기 시작...
✅ CSV 파일 읽기 완료: 1000행, 12컬럼
✅ 필수 컬럼 확인 완료
🔍 이상 현상 탐지 시작...
  방 101 분석 중...
  방 102 분석 중...
  방 103 분석 중...
✅ 이상 현상 탐지 완료: 25건 발견
💾 이상 현상 25개가 anomalies.csv에 저장되었습니다.
📊 Excel 파일도 저장됨: analysis.xlsx
📄 상세 분석 결과 저장: detailed_analysis.txt

=== 🔧 경쟁 상태 탐지 결과 ===
전체 레코드 수: 1000
이상 현상 발견 수: 25
이상 현상 비율: 2.50%
나노초 정밀도 데이터: 980건 (98.0%)

=== 4가지 규칙별 이상 현상 분포 ===
  - 값 불일치: 8건 (32.0%)
  - 경합 발생 자체: 12건 (48.0%)
  - 정원 초과 오류: 3건 (12.0%)
  - 상태 전이 오류: 7건 (28.0%)

🎉 분석 완료!
```

## 기술적 세부사항

### 임계구역 분석
도구는 다음을 기반으로 진짜 임계구역을 분석합니다:
- 정확한 타임스탬프 겹침
- 가능한 경우 나노초 정밀도
- 사용자 개입 탐지
- 지속 시간 계산

### 경합 그룹 탐지
다음 조건에서 그룹이 형성됩니다:
- 두 명 이상의 사용자가 임계구역을 겹침
- 1나노초 겹침도 경합으로 간주
- 겹치는 모든 사용자가 함께 그룹화

## 기여 방법

이 도구는 동시성 시스템의 경쟁 상태 분석을 위해 설계되었습니다. 이슈나 개선사항이 있는 경우 다음을 확인해주세요:

1. 원본 데이터 보존이 유지되는지
2. 4가지 탐지 규칙이 올바르게 구현되었는지
3. 나노초 정밀도가 지원되는지
4. 출력 형식이 일관성을 유지하는지

## 라이선스

이 도구는 동시성 시스템의 경쟁 상태 분석을 위한 교육 및 연구 목적으로 제공됩니다.
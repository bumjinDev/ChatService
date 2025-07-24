# Race Condition Rule 2 Contention 분석기 사용 메뉴얼

경합 발생 분석 및 간트 차트 시각화를 위한 완전 독립 실행 가능한 Python 도구입니다. Rule 2 규칙에 특화된 분석과 간트 차트 생성 기능을 제공합니다.

## 개요

이 도구는 Race Condition에서 발생하는 경합(Contention) 오류를 분석하고 간트 차트로 시각화합니다:

1. **경합 발생 오류 탐지**: `anomaly_type`에 "경합 발생" 포함된 조건 기반 분석
2. **간트 차트 시각화**: 임계구역의 시간별 겹침 현상을 시각적으로 표현
3. **CSV 보고서 생성**: 경합 발생 건에 대한 상세 보고서
4. **단일 방 전용**: 간트 차트 특성상 단일 방만 지원

## 주요 기능

- **완전 독립 실행**: 다른 분석기와 독립적으로 실행 가능
- **Rule 2 전용 분석**: 경합 발생 오류에만 특화된 분석
- **간트 차트 시각화**: 사용자별 임계구역 시간 겹침 표시
- **단일 데이터 소스**: result_file만 사용 (preprocessor_file 사용 안함)
- **고정밀도 시간 지원**: 나노초 정밀도 시간 데이터 자동 감지
- **필수 방 번호**: room_number 매개변수 필수 (단일 방만 지원)

## 시스템 요구사항

```cmd
pip install pandas matplotlib numpy
```

## 사용법

### 기본 사용법 (room_number 필수)

```cmd
py -3 raceCondition_Report_contentionAnalzer.py --room_number 1135 --result_file analysis.csv --output_dir output\
```

### 명령행 인수

| 인수 | 설명 | 필수 여부 | 기본값 |
|------|------|-----------|--------|
| `--room_number` | 분석할 특정 방 번호 | **필수** | - |
| `--preprocessor_file` | 전처리 데이터 CSV 파일 경로 | 선택 | 사용 안함 |
| `--result_file` | 분석 결과 CSV 파일 경로 | 필수 | - |
| `--output_dir` | 분석 결과 저장 디렉토리 경로 | 필수 | - |

## 사용 예시

### 예시 1: 방 1135 경합 분석 (현재 폴더)
```cmd
py -3 raceCondition_Report_contentionAnalzer.py --room_number 1135 --result_file analysis_result.csv --output_dir .\output\
```

### 예시 2: 상대 경로 사용
```cmd
py -3 raceCondition_Report_contentionAnalzer.py --room_number 101 --result_file .\data\result.csv --output_dir .\reports\
```

### 예시 3: 절대 경로 사용
```cmd
py -3 raceCondition_Report_contentionAnalzer.py --room_number 1176 --result_file C:\data\analysis.csv --output_dir C:\reports\contention_analysis\
```

### 예시 4: 복잡한 디렉토리 구조
```cmd
py -3 raceCondition_Report_contentionAnalzer.py --room_number 202 --result_file results\anomaly_detection\detected.csv --output_dir analysis\rule2_results\
```

## 입력 데이터 형식

### 분석 결과 데이터 파일 (result_file)

간트 차트 생성과 CSV 보고서 작성에 사용되는 이상현상 분석 결과 데이터입니다.

#### 필수 컬럼

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `roomNumber` | int | 방 식별자 |
| `anomaly_type` | string | 탐지된 이상현상 유형 |
| `user_id` | string | 사용자 식별자 |
| `true_critical_section_start` | datetime | 임계구역 시작 시각 |
| `true_critical_section_end` | datetime | 임계구역 종료 시각 |

#### 선택적 컬럼

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `bin` | int | 데이터 빈 식별자 |
| `contention_group_size` | int | 경합 그룹의 크기 |
| `contention_user_ids` | string | 충돌하는 사용자 ID 목록 |
| `room_entry_sequence` | int | 방 입장 순서 |
| `*nanoTime*` | long | 나노초 정밀도 시간 데이터 |
| `*epochNano*` | long | 나노초 정밀도 에포크 시간 |

#### anomaly_type 값 형식
- `"경합 발생"` 포함: Rule 2 분석 대상으로 필터링

#### 경합 발생 탐지 조건
```python
# anomaly_type에 "경합 발생" 포함된 행들
contention_anomalies = df[df['anomaly_type'].str.contains('경합 발생', na=False)]
```

## 출력 형식

### 1. 간트 차트 파일 (PNG)

#### 파일명
- **파일명**: `contention_gantt_chart_room{방번호}.png`

#### 차트 구성 요소
- **X축**: 시간 (Timestamp) - 임계구역 시작/종료 시간
- **Y축**: 사용자 ID (user_id) - 각 사용자별 행
- **빨간색 막대**: 임계구역 (Critical Section) 지속 시간
- **막대 끝 숫자**: 경합 그룹 크기 (contention_group_size)
- **검은색 테두리**: 막대 경계선

#### 간트 차트 특징
- **시간 겹침 시각화**: 여러 사용자의 임계구역이 겹치는 구간 표시
- **경합 규모 표시**: 막대 끝에 경합에 참여한 스레드 수 표기
- **사용자별 분리**: Y축으로 사용자를 구분하여 개별 임계구역 추적 가능

### 2. CSV 보고서

#### 파일명
- **파일명**: `report_rule2_contention_details_room{방번호}.csv`

#### CSV 컬럼 구성

| 컬럼명 | 설명 |
|--------|------|
| `roomNumber` | 방 식별자 |
| `bin` | 데이터 빈 식별자 |
| `user_id` | 사용자 식별자 |
| `contention_group_size` | 경합 그룹의 크기 |
| `contention_user_ids` | 충돌하는 사용자 ID 목록 |
| `true_critical_section_start` | 임계구역 시작 시각 |
| `true_critical_section_end` | 임계구역 종료 시각 |
| `true_critical_section_duration` | 임계구역 지속 시간 (초) |

- **인코딩**: UTF-8 with BOM (utf-8-sig)
- **정렬**: roomNumber, bin, room_entry_sequence 순서 (가능한 컬럼만)
- **필터링**: anomaly_type에 "경합 발생" 포함된 행만
- **지속시간 계산**: 자동으로 시작/종료 시각 차이 계산

## 분석 로직

### 1. 데이터 로딩 및 전처리
결과 파일을 로드하고 시간 관련 컬럼들을 datetime 형식으로 변환합니다. 방 번호로 필터링하여 지정된 단일 방의 데이터만 추출합니다.

### 2. 경합 발생 탐지
anomaly_type 컬럼에서 "경합 발생" 문자열을 포함하는 레코드들을 필터링하여 실제 경합이 발생한 임계구역들만 추출합니다.

### 3. 간트 차트 생성
각 사용자별로 Y축 위치를 할당하고, 임계구역의 시작과 종료 시간을 기반으로 수평 막대를 그립니다. 지속 시간을 막대의 길이로 표현하고, 경합 그룹 크기를 막대 끝에 숫자로 표시합니다.

### 4. 유효성 검사
시간 데이터가 모두 존재하는 레코드만 사용하며, 0 이하의 지속시간은 0.001초로 보정합니다. 경합 발생 데이터가 없는 경우 안전하게 처리합니다.

## 클래스 구조

### Rule2ContentionAnalyzer 클래스

#### 초기화 매개변수
- `room_number`: 분석할 특정 방 번호 (**필수**)
- `preprocessor_file`: 전처리 데이터 파일 경로 (사용 안함)
- `result_file`: 분석 결과 파일 경로
- `output_dir`: 출력 디렉토리 경로

#### 주요 메서드

| 메서드명 | 기능 |
|----------|------|
| `load_data()` | 결과 CSV 파일 로드 및 전처리 |
| `create_output_folders()` | 출력 폴더 생성 |
| `create_rule2_contention_gantt_chart()` | 경합 발생 간트 차트 생성 |
| `generate_rule2_csv_report()` | CSV 보고서 생성 |
| `run_analysis()` | 전체 분석 실행 |

## 특징

### 1. 단일 방 전용
- **간트 차트 특성**: 시간축 기반 시각화로 단일 방만 의미있음
- **room_number 필수**: 전체 방 분석 지원하지 않음
- **명확한 시간축**: 하나의 방 내에서 시간 흐름 분석

### 2. 단일 데이터 소스
- **result_file만 사용**: 이미 분석된 결과 데이터만 필요

### 3. 고정밀도 시간 분석
- **나노초 정밀도**: 매우 짧은 임계구역도 정확히 표시
- **시간 겹침 탐지**: 정확한 경합 발생 시점 식별
- **지속시간 계산**: 자동으로 임계구역 지속 시간 계산

## 제약사항

### 1. 필수 요구사항
- **room_number 필수**: 생략 시 실행 불가
- **시간 데이터 필수**: true_critical_section_start/end 컬럼 필요
- **경합 데이터 필수**: "경합 발생" 포함된 anomaly_type 필요

### 2. 지원하지 않는 기능
- **전체 방 분석**: 간트 차트 특성상 지원 안함
- **통계적 집계**: 개별 경합 상세 분석에 집중
- **다중 파일 입력**: result_file 하나만 사용

## 샘플 출력

```
🚀 Rule 2: Contention 분석 시작
✅ 결과 파일 로드 완료: 150건
✅ 고정밀도 나노초 데이터 감지
✅ true_critical_section_start 컬럼 datetime 변환 완료
✅ true_critical_section_end 컬럼 datetime 변환 완료
✅ 방 1135 필터링 완료: 150 → 25건
✅ 출력 디렉토리 생성: .\output\

🎯 Rule2 경합 발생 간트 차트 생성 시작 (방 1135)
   - 경합 발생 레코드: 25건
   - 유효한 시간 데이터: 25건
   - 고유 사용자 수: 12
✅ 간트 차트 저장 완료: .\output\contention_gantt_chart_room1135.png

📋 Rule2 CSV 보고서 생성 시작
   - 경합 발생 이상 현상: 25건
   - 임계 구역 지속시간 계산 완료
   - CSV 보고서 생성 완료: 25건 → .\output\report_rule2_contention_details_room1135.csv

✅ Rule 2 분석 완료!
```

## 활용 방안

### 1. 동시성 문제 진단
- 임계구역 겹침 패턴 분석
- 경합 발생 빈도와 지속시간 평가

### 2. 성능 최적화
- 병목 지점 식별
- 임계구역 최적화 방향 제시

### 3. 시스템 안정성 평가
- 동시 접근 패턴 분석
- 리소스 경합 심각도 평가

## 기술적 세부사항

### 데이터 처리
- **시간 기반 정렬**: true_critical_section_start 기준 정렬
- **동적 Y축**: 사용자 수에 따른 자동 Y축 조정
- **시간 유효성 검사**: NaN 시간 데이터 자동 제외
- **지속시간 보정**: 비정상적인 지속시간 자동 수정

### 시각화 최적화
- **막대 투명도**: alpha=0.7로 겹침 구간 시각적 표현
- **경계선**: 검은색 테두리로 막대 구분 명확화
- **텍스트 표시**: 경합 그룹 크기를 막대 끝에 표시
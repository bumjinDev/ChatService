# Calculate Stats - 성능 분석 지표 계산기 (v6.0)

성능 테스트 결과 CSV 파일을 분석하여 나노초 단위의 정밀한 통계 지표를 계산하고 Excel로 저장하는 Python 도구입니다. 임계 영역 성능 분석을 위한 다양한 통계와 시간 단위별 비교 분석을 제공합니다.

## 개요

이 도구는 멀티스레드 환경에서 수집된 성능 테스트 데이터를 종합적으로 분석하고 다음과 같은 통계를 생성합니다:

1. **전체 요약 통계** - 성공/실패 비율 및 전체 현황
2. **성공 케이스 분석** - 대기 시간 및 처리 시간 통계
3. **실패 케이스 분석** - 정원 초과 실패 및 진입 실패 분석
4. **방별 상세 통계** - 각 방의 성능 지표
5. **구간별 세부 분석** - 시간대별 성능 변화
6. **스레드별 상세 내역** - 각 스레드의 임계구역 접근 상세 정보
7. **시간 단위 비교** - 나노초/마이크로초/밀리초 단위 변환

## 주요 기능

- **나노초 정밀도 유지**: 모든 시간 계산을 나노초 단위로 처리
- **다중 파일 처리**: 여러 CSV 파일을 일괄 분석
- **자동 결과 분류**: SUCCESS/FAIL_OVER_CAPACITY/FAIL_ENTRY 자동 분류
- **포괄적 통계**: 평균, 중앙값, 최댓값 등 다양한 통계 지표
- **Excel 다중 시트**: 7개 시트로 구성된 상세 보고서
- **시각적 포맷**: 천 단위 구분자 및 백분율 표시
- **데이터 검증**: 입력 데이터 무결성 검증

## 시스템 요구사항

```bash
pip install pandas numpy openpyxl
```

## 사용법

### 기본 사용법

```cmd
py -3 calculate_stats.py --inputs input.csv --labels TestCase1
```

### 다중 파일 분석

```cmd
py -3 calculate_stats.py --inputs "file1.csv,file2.csv,file3.csv" --labels "Test1,Test2,Test3"
```

### 명령행 옵션

| 옵션 | 타입 | 설명 | 필수 여부 |
|-----|------|------|----------|
| `--inputs` | string | 분석할 CSV 파일 경로들 (콤마로 구분) | 필수 |
| `--labels` | string | 각 CSV 파일의 출력 레이블 (콤마로 구분) | 필수 |
| `--compare` | string | 비교할 참조 Excel 파일 경로 (현재 미사용) | 선택 |

## 사용 예시

### 예시 1: 단일 파일 분석
```cmd
py -3 calculate_stats.py --inputs "results/performance_data.csv" --labels "Baseline_Test"
```
- 출력: `performance_reports/Baseline_Test_stats_nano.xlsx`

### 예시 2: 다중 테스트 케이스 비교
```cmd
py -3 calculate_stats.py --inputs "test1.csv,test2.csv,test3.csv" --labels "Load_100,Load_500,Load_1000"
```
- 출력: 
  - `performance_reports/Load_100_stats_nano.xlsx`
  - `performance_reports/Load_500_stats_nano.xlsx`
  - `performance_reports/Load_1000_stats_nano.xlsx`

### 예시 3: 절대 경로 사용
```cmd
py -3 calculate_stats.py --inputs "C:\data\room_test.csv,D:\results\stress_test.csv" --labels "RoomTest,StressTest"
```

### 예시 4: 복잡한 시나리오 분석
```cmd
py -3 calculate_stats.py --inputs "baseline.csv,optimized.csv,concurrent.csv" --labels "Baseline_v1.0,Optimized_v2.0,Concurrent_v3.0"
```

### 예시 5: 방별 성능 비교
```cmd
py -3 calculate_stats.py --inputs "room1_data.csv,room2_data.csv,all_rooms.csv" --labels "Room1_Only,Room2_Only,All_Rooms"
```

## 입력 데이터 형식

### 필수 컬럼

입력 CSV는 다음 컬럼들을 포함해야 합니다:

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `roomNumber` | int | 방 번호 |
| `waiting_start_nanoTime` | string | 대기 시작 나노초 시간 |
| `critical_enter_nanoTime` | string | 임계구역 진입 나노초 시간 |
| `critical_leave_nanoTime` | string | 임계구역 퇴장 나노초 시간 |
| `critical_leave_event_type` | string | 이벤트 결과 타입 |

### 선택적 컬럼

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `bin` | int | 분석 구간 번호 |
| `user_id` | string | 사용자/스레드 식별자 |
| `join_result` | string | 기존 결과 분류 |
| `increment_before_nanoTime` | string | 증가 작업 시작 시간 |
| `increment_after_nanoTime` | string | 증가 작업 완료 시간 |
| `currentPeople` | int | 현재 인원 수 |
| `maxPeople` | int | 최대 인원 수 |

### 데이터 분류 규칙

도구는 다음 규칙에 따라 요청을 자동 분류합니다:

#### SUCCESS (성공)
```
조건: critical_leave_event_type == "SUCCESS"
특징: 임계구역 진입 및 작업 성공 완료
측정: 대기 시간, 처리 시간
```

#### FAIL_OVER_CAPACITY (정원 초과 실패)
```
조건: critical_leave_event_type == "FAIL_OVER_CAPACITY"
특징: 임계구역 진입했으나 정원 초과로 실패
측정: 대기 시간, 실패 처리 시간
```

#### FAIL_ENTRY (진입 실패)
```
조건: critical_leave_event_type가 없거나 빈 값
특징: 임계구역 진입 자체를 실패 (락 획득 실패 등)
측정: 대기 시간만 측정 가능
```

## 출력 형식

### Excel 파일 구조

출력 Excel 파일은 7개 시트로 구성됩니다:

#### 1. Overall_Summary
| 컬럼 | 설명 |
|------|------|
| Category | 요청 분류 (Total/Success/Failed 등) |
| Count | 각 분류별 요청 수 |
| Percentage (%) | 전체 대비 비율 |

#### 2. Overall_Success_Stats
| 컬럼 | 설명 |
|------|------|
| Metric | 측정 지표 (Wait Time/Dwell Time) |
| Statistic | 통계 유형 (Mean/Median/Max) |
| Value | 통계값 (나노초) |
| Unit | 시간 단위 (ns) |

#### 3. Overall_Capacity_Failed_Stats
| 컬럼 | 설명 |
|------|------|
| Metric | 측정 지표 (Wait Time/Fail Processing Time) |
| Statistic | 통계 유형 (Mean/Median/Max) |
| Value | 통계값 (나노초) |
| Unit | 시간 단위 (ns) |

#### 4. Per_Room_Stats
방별 상세 통계 (19개 컬럼):
- `roomNumber`: 방 번호
- `total_requests`: 총 요청 수
- `success_count`: 성공 요청 수
- `capacity_failed_count`: 정원 초과 실패 수
- `entry_failed_count`: 진입 실패 수
- `success_rate(%)`: 성공률
- `capacity_failed_rate(%)`: 정원 초과 실패율
- `entry_failed_rate(%)`: 진입 실패율
- `success_avg_wait_time(ns)`: 성공 케이스 평균 대기 시간
- `success_median_wait_time(ns)`: 성공 케이스 중앙값 대기 시간
- `success_max_wait_time(ns)`: 성공 케이스 최대 대기 시간
- `success_avg_dwell_time(ns)`: 성공 케이스 평균 처리 시간
- `success_median_dwell_time(ns)`: 성공 케이스 중앙값 처리 시간
- `success_max_dwell_time(ns)`: 성공 케이스 최대 처리 시간
- `capacity_failed_avg_wait_time(ns)`: 정원 초과 실패 평균 대기 시간
- `capacity_failed_max_wait_time(ns)`: 정원 초과 실패 최대 대기 시간
- `capacity_failed_avg_fail_processing_time(ns)`: 정원 초과 실패 평균 처리 시간
- `capacity_failed_max_fail_processing_time(ns)`: 정원 초과 실패 최대 처리 시간

#### 5. Per_Bin_Stats
방-구간별 상세 통계 (Per_Room_Stats와 동일한 구조에 `bin` 컬럼 추가)

#### 6. Per_Thread_Critical_Details
각 스레드별 임계구역 접근 상세 내역:
- `roomNumber`: 방 번호
- `bin`: 구간 번호
- `thread_id`: 스레드 식별자
- `critical_section_entered`: 임계구역 진입 여부 (TRUE/FALSE)
- `critical_section_exited`: 임계구역 퇴장 여부 (TRUE/FALSE)
- `critical_section_success`: 임계구역 작업 성공 여부 (TRUE/FALSE)
- `failure_reason`: 실패 사유 (CAPACITY_EXCEEDED/ENTRY_FAILED/UNKNOWN_FAILURE)
- `join_result`: 최종 결과 (SUCCESS/FAIL_OVER_CAPACITY/FAIL_ENTRY)
- `wait_time_ns`: 대기 시간 (나노초)
- `dwell_time_ns`: 성공 처리 시간 (나노초)
- `fail_processing_time_ns`: 실패 처리 시간 (나노초)
- `waiting_start_nanoTime`: 대기 시작 절대 시간
- `critical_enter_nanoTime`: 임계구역 진입 절대 시간
- `critical_leave_nanoTime`: 임계구역 퇴장 절대 시간

#### 7. Time_Unit_Comparison
시간 단위별 비교 통계:
- `Metric`: 측정 지표
- `Unit`: 시간 단위 (nanoseconds/microseconds/milliseconds)
- `Mean`: 평균값
- `Median`: 중앙값
- `Max`: 최댓값

### 시간 계산 공식

#### 대기 시간 (Wait Time)
```
wait_time_ns = critical_enter_nanoTime - waiting_start_nanoTime
```

#### 성공 처리 시간 (Dwell Time)
```
dwell_time_ns = critical_leave_nanoTime - critical_enter_nanoTime
(SUCCESS 케이스에만 적용)
```

#### 실패 처리 시간 (Fail Processing Time)
```
fail_processing_time_ns = critical_leave_nanoTime - critical_enter_nanoTime
(FAIL_OVER_CAPACITY 케이스에만 적용)
```

## 샘플 출력

```
성능 분석 시작: 2024-07-14 15:30:45
버전: v6.0 - 나노초 단위 분석

처리 중: test_data.csv (레이블: Performance_Test)
  - 총 1500개의 레코드 로드됨
  - 성공: 1350, 진입실패: 50, 정원초과실패: 100
  - 성공 그룹 시간 계산 완료 (유효 데이터: 1350개/1350개)
  - 정원초과 실패 그룹 시간 계산 완료 (유효 데이터: 100개/100개)

  - 데이터 검증:
    전체 요청 수: 1500
    성공: 1350 (90.00%)
    정원초과 실패: 100 (6.67%)
    진입 실패: 50 (3.33%)
  - Excel 파일 저장 완료: performance_reports/Performance_Test_stats_nano.xlsx

성능 분석 완료: 2024-07-14 15:30:47
소요 시간: 0:00:02.123456
처리 결과: 1/1 파일 성공
```

## 통계 지표 설명

### 성공 케이스 지표

#### Wait Time (대기 시간)
```
의미: 임계구역 진입을 위해 대기한 시간
측정: 락 경합 강도 및 동시성 수준
단위: 나노초 (ns)
```

#### Dwell Time (처리 시간)
```
의미: 임계구역 내에서 작업을 수행한 시간
측정: 비즈니스 로직 처리 성능
단위: 나노초 (ns)
```

### 실패 케이스 지표

#### Capacity Failed Wait Time
```
의미: 정원 초과 실패 케이스의 대기 시간
측정: 실패 전까지의 경합 시간
단위: 나노초 (ns)
```

#### Fail Processing Time
```
의미: 정원 초과 확인 및 실패 처리 시간
측정: 실패 로직 처리 성능
단위: 나노초 (ns)
```

### 시간 단위 변환

| 단위 | 변환 계수 | 설명 |
|------|-----------|------|
| 나노초 (ns) | 1.0 | 기본 단위 |
| 마이크로초 (μs) | 0.001 | 1μs = 1,000ns |
| 밀리초 (ms) | 0.000001 | 1ms = 1,000,000ns |

## 기술적 세부사항

### 나노초 정밀도 처리
- **문자열 읽기**: CSV에서 나노초 값을 문자열로 읽어 정밀도 보존
- **Decimal 변환**: 과학적 표기법 (1.23E+9) 정확 처리
- **정수 변환**: 계산 시 정수로 변환하여 정확한 연산

### 데이터 검증
```python
# 유효성 검사 조건
valid_data = data[
    (data['wait_time_ns'].notna()) & 
    (data['dwell_time_ns'].notna()) &
    (data['wait_time_ns'] >= 0) & 
    (data['dwell_time_ns'] >= 0)
]
```

### Excel 포맷팅
- **백분율**: `0.00%` 형식
- **나노초**: `#,##0` 형식 (천 단위 구분자)
- **마이크로초**: `#,##0.000` 형식
- **밀리초**: `#,##0.000000` 형식

### 자동 결과 분류 로직
```python
def determine_join_result(row):
    if critical_leave_event_type == 'SUCCESS':
        return 'SUCCESS'
    elif critical_leave_event_type == 'FAIL_OVER_CAPACITY':
        return 'FAIL_OVER_CAPACITY'
    elif critical_leave_event_type가 없음:
        return 'FAIL_ENTRY'
    else:
        return 'UNKNOWN'
```

## 성능 최적화

### 통계 계산 최적화
- **단일 패스**: 한 번의 순회로 여러 통계 동시 계산
- **캐시 활용**: 중복 계산 방지
- **조건부 계산**: 데이터가 있는 경우에만 통계 계산
# 성능 분석 결과 시각화 스크립트 요구사항 (v2.0 - 실제 데이터 구조 반영)

## 1. 개요 (Overview)

- **문서 목표:** 성능 테스트 결과로 생성된 `*_stats_nano.xlsx` 파일을 입력받아, 동시성 제어 기법별 성능(효율, 안정성, 부하 대응)을 직관적으로 비교하고 분석하기 위한 차트를 생성하는 Python 스크립트의 요구사항을 정의한다.
- **스크립트명:** `visualize_performance_results.py`
- **출력 위치:** 스크립트 실행 위치에 `performance_charts` 디렉토리를 생성하고, 그 안에 각 차트 이미지를 저장한다.

## 2. 입력 데이터 및 라이브러리

### 2.1. 입력 데이터 (Input Data)

- **파일명 패턴:** `{label}_stats_nano.xlsx` (예: `synchronized_stats_nano.xlsx`, `semaphore_stats_nano.xlsx`)
- **입력 방식:** 
  - 단일 파일 분석 모드: 하나의 동시성 기법 결과만 시각화
  - 다중 파일 비교 모드: 여러 동시성 기법의 결과를 병합하여 비교 시각화
- **역할:** 각 파일에 포함된 시트의 데이터를 기반으로 시각화를 수행한다.

### 2.2. 라이브러리 (Libraries)

- **pandas:**
  - **역할:** Excel 파일의 특정 시트를 데이터프레임으로 읽어와, 시각화에 필요한 데이터를 가공하고 준비하는 역할을 수행한다.
- **matplotlib / seaborn:**
  - **역할:** 가공된 데이터프레임을 기반으로 막대, 라인, Box Plot 등 다양한 형태의 차트를 생성하고, 제목, 레이블, 범례 등 시각적 요소를 커스터마이징하는 역할을 수행한다.
- **glob / os:**
  - **역할:** 디렉토리에서 `*_stats_nano.xlsx` 패턴의 파일들을 자동으로 찾아 처리한다.

## 3. 핵심 요구사항: 차트 생성

### 3.1. 기술 간 종합 성능 비교 (다각도 분석)

**분석 근거:** 'Top-Down' 분석 전략에 따라, 시스템의 거시적인 현상(처리 성공률)부터 파악한 뒤, 각 작업 경로(성공/실패)의 미시적인 처리 비용(시간)을 분해하여 분석한다.

#### ✅ 차트 1-1: 요청 처리 결과 분포

**시각화 목표:** 전체 요청 중 성공과 정원 초과 실패가 각각 어느 정도의 비율을 차지하는지 비교하여, 각 기술의 처리 성공률을 한눈에 파악한다.

**사용 데이터:** Overall_Summary 시트

**구현 가이드:** 
- 각 파일명에서 label을 추출 (예: `synchronized_stats_nano.xlsx` → `synchronized`)
- Overall_Summary 시트에서 'Success'와 'Capacity Failed' 행의 Percentage (%) 값을 추출
- label별로 100% 누적 막대 차트로 표현

#### ✅ 차트 1-2: 성공 요청의 처리 비용 분석

**시각화 목표:** 성공한 요청들만을 기준으로, 평균 처리 비용(대기 시간 + 임계 영역 실행 시간)을 비교하여 각 기술의 순수 효율성을 분석한다.

**사용 데이터:** Overall_Success_Stats 시트 (Statistic = 'Mean')

**구현 가이드:** 
- 각 파일에서 Metric이 'Wait Time'이고 Statistic이 'Mean'인 행의 Value 추출
- 각 파일에서 Metric이 'Dwell Time (Critical Section)'이고 Statistic이 'Mean'인 행의 Value 추출
- 파일명 기반 label별로 누적 막대 차트로 표현하고, 총합이 낮은 순으로 정렬

#### ✅ 차트 1-3: 실패 요청의 처리 비용 분석

**시각화 목표:** 정원 초과로 실패한 요청들을 기준으로, 실패 처리 비용(대기 시간 + 실패 처리 시간)을 비교하여, 각 기술이 얼마나 '빠르게' 실패를 처리하는지 분석한다.

**사용 데이터:** Overall_Capacity_Failed_Stats 시트 (Statistic = 'Mean')

**구현 가이드:** 
- 각 파일에서 Metric이 'Wait Time'이고 Statistic이 'Mean'인 행의 Value 추출
- 각 파일에서 Metric이 'Fail Processing Time'이고 Statistic이 'Mean'인 행의 Value 추출
- 파일명 기반 label별로 누적 막대 차트로 표현하고, 총합이 낮은 순으로 정렬

### 3.2. 기술별 성능 안정성 분석 (Box Plot 대안)

**분석 근거:** 평균값에 가려진 성능 변동성을 파악하기 위해, 사용자 관점에서의 체감 성능을 분석한다. 원래 Box Plot은 데이터의 중심 경향(중앙값), 분산도, 전체 범위를 한 번에 보여주어 해당 기술의 성능이 얼마나 예측 가능한지를 정량적으로 증명하는 데 가장 적합하나, Raw 데이터 부재로 대안 방법을 사용한다.

#### ✅ 차트 2-1: 성공 요청 대기 시간 분포 (대안)

**시각화 목표:** 성공한 요청들의 Wait Time 분포를 Mean, Median, Max 통계값으로 간접 분석한다.

**사용 데이터:** Overall_Success_Stats 시트 (Wait Time 관련 행)

**구현 가이드:**
1. 각 label별로 Wait Time의 Mean, Median, Max를 막대 그래프로 표현
2. Y축은 로그 스케일(Log Scale)로 설정
3. 각 막대 상단에 수치를 표기(annotate)
4. 원본 요구사항: Raw_Success_Wait_Times 시트를 사용한 Box Plot이었으나 데이터 부재로 변경

#### ✅ 차트 2-2: 실패 요청 대기 시간 분포 (대안)

**시각화 목표:** 정원 초과로 실패한 요청들의 Wait Time 분포를 Mean, Median, Max 통계값으로 간접 분석한다.

**사용 데이터:** Overall_Capacity_Failed_Stats 시트 (Wait Time 관련 행)

**구현 가이드:**
1. 각 label별로 Wait Time의 Mean, Median, Max를 막대 그래프로 표현
2. Y축은 로그 스케일(Log Scale)로 설정
3. 각 막대 상단에 수치를 표기(annotate)
4. 원본 요구사항: Raw_Capacity_Failed_Wait_Times 시트를 사용한 Box Plot이었으나 데이터 부재로 변경

### 3.3. 부하 누적 추이 분석 (성공/실패 케이스)

**분석 근거:** 이 차트는 '대기 시간이 길어지면 후속 실행 시간도 길어질 수 있다'는 복합적인 시스템 동작을 거시적으로 보여주는 증거 자료다. 부하 증가로 스레드들이 BLOCKED 상태에 더 오래 머무르게 되면, 컨텍스트 스위칭 비용과 CPU 캐시 미스 확률이 증가하여 시스템 전체 효율성이 급격히 저하된다. 이 차트는 그 임계점을 시각적으로 찾아내는 역할을 한다.

#### ✅ 차트 3: 시간에 따른 성공/실패 요청의 평균 대기 시간 변화

**시각화 목표:** 부하가 누적될 때, 성공 요청의 대기 시간과 실패 요청의 대기 시간이 각각 어떻게 변화하는지 그 추세를 함께 비교하여 시스템의 동작 특성을 심층적으로 분석한다.

**사용 데이터:** Per_Bin_Stats 시트

#### 📝 구현 가이드

1. X축을 bin으로 하여, 각 동시성 기술(파일명 기반 label)마다 아래 두 개의 라인을 그린다.
   - **라인 1 (성공):** success_avg_wait_time(ns) (실선 스타일)
   - **라인 2 (실패):** capacity_failed_avg_wait_time(ns) (점선 스타일)
2. 범례를 통해 각 라인이 '성공'인지 '실패'인지 명확히 구분한다.
3. success_rate(%)가 특정 임계값(예: 80%) 이하로 처음 떨어지는 bin 지점에 수직 점선과 주석으로 '성능 저하 임계점'을 강조한다.
4. 차트 스타일은 config.yaml 설정을 따른다.

### 3.4. 단일 파일 분석 모드 추가 차트

단일 파일만 입력된 경우, 다음 추가 차트를 생성한다:

#### ✅ 차트 4: Room별 성능 비교

**사용 데이터:** Per_Room_Stats 시트

**구현 가이드:**
- roomNumber별로 success_rate(%), success_avg_wait_time(ns), capacity_failed_avg_wait_time(ns)를 시각화
- 서브플롯을 활용하여 3개 지표를 함께 표현

## 4. 추가 고려사항

### 4.1. 파일 네이밍 규칙

- 다중 파일 비교 모드:
  - 차트 1-1: `comparison_success_failure_distribution.png`
  - 차트 1-2: `comparison_success_cost_analysis.png`
  - 차트 1-3: `comparison_failure_cost_analysis.png`
  - 차트 2: `comparison_wait_time_statistics.png`
  - 차트 3: `comparison_load_trend_analysis.png`

- 단일 파일 분석 모드:
  - 차트 파일명 앞에 label 추가: `{label}_success_failure_distribution.png`
  - 차트 4: `{label}_per_room_analysis.png`

### 4.2. 차트 스타일링

- 모든 차트는 config.yaml 설정을 우선 적용한다.
- 색상 팔레트는 동시성 기법별로 일관되게 유지한다.
- 폰트 크기와 레이아웃은 가독성을 최우선으로 설정한다.

### 4.3. 데이터 검증

- 각 차트 생성 전에 필요한 데이터 시트와 컬럼의 존재 여부를 확인한다.
- 누락된 데이터나 시트가 있을 경우 명확한 오류 메시지를 출력한다.
- 파일명에서 label 추출 시 `_stats_nano.xlsx` 패턴을 정확히 처리한다.

### 4.5. 원본 요구사항과의 차이점 명시

본 문서는 실제 데이터 구조에 맞춰 수정되었으며, 원본 요구사항과 다음과 같은 차이가 있습니다:

1. **Raw 데이터 시트 부재:**
   - 원본: Raw_Success_Wait_Times, Raw_Capacity_Failed_Wait_Times 시트를 사용한 Box Plot
   - 수정: 통계값(Mean, Median, Max)을 활용한 대안 시각화

2. **label 컬럼 부재:**
   - 원본: 데이터 내 label 컬럼으로 동시성 기법 구분
   - 수정: 파일명에서 label 추출 방식으로 변경

3. **다중 파일 처리:**
   - 원본: 단일 통합 파일 가정
   - 수정: 여러 개별 파일을 병합하여 비교하는 방식

### 4.6. Per_Bin_Stats 데이터 활용 주의사항

Per_Bin_Stats 시트는 roomNumber별로 bin 데이터가 분리되어 있으므로:
- 차트 3 생성 시 모든 roomNumber의 데이터를 집계하여 평균값 계산 필요
- 또는 특정 roomNumber를 대표값으로 선택하여 시각화
- capacity_failed_avg_wait_time(ns) 컬럼의 0값 처리 방안 필요 (초기 bin에서는 실패가 없음)
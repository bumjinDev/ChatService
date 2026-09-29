# Create Charts - 성능 분석 시각화 도구 (v3.1)

동시성 제어 기법별 성능 분석 결과를 전문적인 차트로 시각화하는 Python 도구입니다. Excel 통계 파일을 입력받아 포트폴리오급 성능 분석 차트를 자동 생성합니다.

## 개요

이 도구는 `calculate_stats.py`로 생성된 Excel 통계 파일들을 분석하여 다음과 같은 전문적인 차트를 생성합니다:

1. **요청 처리 결과 분포** - 성공률 vs 실패율 비교
2. **처리 비용 분석** - 성공/실패 요청의 시간 비용 분석
3. **성능 분포 분석** - Box Plot을 통한 상세 성능 분포
4. **부하 누적 추이** - 시간 경과에 따른 성능 저하 분석
5. **룸별 성능 비교** - 개별 룸의 성능 특성 분석
6. **동시성 기법 비교** - 여러 기법의 성능 특성 비교

## 주요 기능

- **다중 기법 비교**: 여러 동시성 기법 동시 분석
- **상세 범례**: 차트 해석을 위한 상세 범례 제공

## 시스템 요구사항

```bash
pip install pandas matplotlib numpy pyyaml openpyxl
```

## 사용법

### 기본 사용법

```bash
# 현재 디렉토리의 *_stats_nano.xlsx 파일들을 자동 탐색
python create_charts.py

# 또는 특정 파일들을 직접 지정
python create_charts.py synchronized_stats_nano.xlsx semaphore_stats_nano.xlsx
```

### 명령행 옵션

| 사용법 | 설명 | 예시 |
|--------|------|------|
| 자동 탐색 | 현재 디렉토리에서 `*_stats_nano.xlsx` 파일 자동 검색 | `python create_charts.py` |
| 파일 지정 | 분석할 Excel 파일들을 직접 명시 | `python create_charts.py file1.xlsx file2.xlsx` |

## 사용 예시

### 예시 1: 단일 기법 분석
```bash
python create_charts.py synchronized_stats_nano.xlsx
```
**출력 결과:**
- `performance_charts/synchronized_차트1-1_요청처리결과분포.png`
- `performance_charts/synchronized_차트1-2_성공요청처리비용분석.png`
- `performance_charts/synchronized_차트1-3_실패요청처리비용분석.png`
- `performance_charts/synchronized_차트2-1_성공요청대기시간분포.png`
- `performance_charts/synchronized_차트2-2_실패요청대기시간분포.png`
- `performance_charts/synchronized_차트3_부하누적추이분석.png`
- `performance_charts/synchronized_차트4_룸별성능비교분석.png`

### 예시 2: 다중 기법 비교 분석
```bash
python create_charts.py synchronized_stats_nano.xlsx reentrantlock_stats_nano.xlsx semaphore_stats_nano.xlsx
```
**출력 결과:**
- `performance_charts/comparison_차트1-1_요청처리결과분포.png`
- `performance_charts/comparison_차트1-2_성공요청처리비용분석.png`
- `performance_charts/comparison_차트1-3_실패요청처리비용분석.png`
- `performance_charts/comparison_차트2-1_성공요청대기시간분포.png`
- `performance_charts/comparison_차트2-2_실패요청대기시간분포.png`
- `performance_charts/comparison_차트3_부하누적추이분석.png`
- (차트4는 단일 파일 분석 시에만 생성)

### 예시 3: 자동 탐색 사용
```bash
# 현재 디렉토리에 다음 파일들이 있는 경우:
# - ifelse_stats_nano.xlsx
# - synchronized_stats_nano.xlsx  
# - reentrantlock_fair_stats_nano.xlsx
# - semaphore_stats_nano.xlsx

python create_charts.py
```
**결과:** 모든 파일이 자동으로 감지되어 비교 차트 생성

### 예시 4: 절대 경로 사용
```bash
python create_charts.py "C:\analysis\baseline_stats_nano.xlsx" "D:\results\optimized_stats_nano.xlsx"
```

### 예시 5: 파일명 패턴 활용
```bash
# 권장 파일명 패턴으로 의미 있는 라벨 자동 생성
python create_charts.py ifelse_baseline_stats_nano.xlsx synchronized_v1_stats_nano.xlsx reentrantlock_optimized_stats_nano.xlsx
```

## 입력 데이터 형식

### 필수 Excel 시트

입력 Excel 파일은 다음 시트들을 포함해야 합니다:

| 시트명 | 설명 | 필수 여부 |
|--------|------|----------|
| `Overall_Summary` | 전체 요약 통계 | 필수 |
| `Overall_Success_Stats` | 성공 케이스 통계 | 필수 |
| `Overall_Capacity_Failed_Stats` | 실패 케이스 통계 | 필수 |
| `Per_Bin_Stats` | 구간별 통계 | 필수 |
| `Per_Room_Stats` | 룸별 통계 | 선택 |

### 파일명 규칙

도구는 파일명에서 자동으로 기법명을 추출합니다:

| 파일명 패턴 | 추출된 라벨 | 설명 |
|-------------|-------------|------|
| `synchronized_stats_nano.xlsx` | `synchronized` | 권장 패턴 |
| `reentrantlock_fair_stats_nano.xlsx` | `reentrantlock_fair` | 세부 옵션 포함 |
| `baseline_v1.0_stats_nano.xlsx` | `baseline_v1.0` | 버전 정보 포함 |
| `performance_test.xlsx` | `performance_test` | 일반 xlsx 파일 |
| `stats_nano.xlsx` | `테스트기법` | 라벨 추출 실패 시 기본값 |

## 출력 형식

### 차트 파일 구조

```
performance_charts/
├── [prefix]_차트1-1_요청처리결과분포.png
├── [prefix]_차트1-2_성공요청처리비용분석.png  
├── [prefix]_차트1-3_실패요청처리비용분석.png
├── [prefix]_차트2-1_성공요청대기시간분포.png
├── [prefix]_차트2-2_실패요청대기시간분포.png
├── [prefix]_차트3_부하누적추이분석.png
└── [prefix]_차트4_룸별성능비교분석.png (단일 파일인 경우만)
```

**prefix 규칙:**
- 단일 파일: 추출된 라벨명 (예: `synchronized`)
- 다중 파일: `comparison`

### 생성되는 차트 상세

#### 차트 1-1: 요청 처리 결과 분포
```
차트 유형: 누적 막대 차트
X축: 동시성 제어 기법
Y축: 비율 (%) - 0~100%
데이터: 성공/실패 비율
색상: 성공(녹색), 실패(빨간색)
```

#### 차트 1-2: 성공 요청 처리 비용 분석
```
차트 유형: 누적 막대 차트
X축: 기법 (처리시간 순 정렬)
Y축: 시간 (자동 단위: ns/μs/ms)
데이터: 대기시간 + 실행시간
정렬: 총 처리시간 오름차순
```

#### 차트 1-3: 실패 요청 처리 비용 분석
```
차트 유형: 누적 막대 차트
X축: 기법 (처리시간 순 정렬)
Y축: 시간 (자동 단위: ns/μs/ms)
데이터: 대기시간 + 실패처리시간
정렬: 총 처리시간 오름차순
```

#### 차트 2-1: 성공 요청 대기시간 분포
```
차트 유형: Box Plot
X축: 기법별 성능 지표
Y축: 시간 (자동 단위: ns/μs/ms, 로그스케일)
데이터: Wait Time, Dwell Time 분포
범례: 데이터 타입 + Box Plot 구성 요소
```

#### 차트 2-2: 실패 요청 대기시간 분포
```
차트 유형: Box Plot  
X축: 기법별 성능 지표
Y축: 시간 (자동 단위: ns/μs/ms, 로그스케일)
데이터: Wait Time, Fail Processing Time 분포
범례: 데이터 타입 + Box Plot 구성 요소
```

#### 차트 3: 부하 누적 추이 분석
```
차트 유형: 라인 차트
X축: 시간 진행 (구간)
Y축: 평균 대기시간 (자동 단위: ns/μs/ms)
데이터: 성공/실패 요청들의 평균 대기시간
라인: 성공(실선), 실패(실선, 다른 색상)
```

#### 차트 4: 룸별 성능 비교 분석 (단일 파일만)
```
차트 유형: 3개 서브플롯 막대 차트
서브플롯 1: 룸별 성공률 (%)
서브플롯 2: 룸별 대기시간 - 성공
서브플롯 3: 룸별 대기시간 - 실패
X축: 룸 번호
```

### 시간 단위 자동 변환

| 나노초 범위 | 표시 단위 | 포맷 예시 |
|-------------|-----------|----------|
| 0 ~ 999 | ns | `500ns` |
| 1,000 ~ 999,999 | μs | `15.5μs` |
| 1,000,000 이상 | ms | `2.3ms` |

## 샘플 출력

```
🚀 Performance Analysis Visualization Script v3.1 (Refactored)
======================================================================

🔧 Creating font test chart...
📊 Font test chart saved: font_test_results.png

🎨 Initializing visualizer...

🔄 Processing 3 file(s)...

🔍 Found 3 files to process:
  - synchronized_stats_nano.xlsx → label: 'synchronized'
  - reentrantlock_stats_nano.xlsx → label: 'reentrantlock'
  - semaphore_stats_nano.xlsx → label: 'semaphore'

📊 Processing file: synchronized_stats_nano.xlsx (label: synchronized)
✅ Data extraction completed for synchronized

📊 Processing file: reentrantlock_stats_nano.xlsx (label: reentrantlock)  
✅ Data extraction completed for reentrantlock

📊 Processing file: semaphore_stats_nano.xlsx (label: semaphore)
✅ Data extraction completed for semaphore

📈 Generating charts...

🎉 Successfully generated 6 charts:
  ✅ 차트 1-1: 요청 처리 결과 분포 (성공률 vs 실패율)
      📁 performance_charts/comparison_차트1-1_요청처리결과분포.png
  ✅ 차트 1-2: 성공 요청의 처리 비용 분석 (대기+실행)
      📁 performance_charts/comparison_차트1-2_성공요청처리비용분석.png
  ✅ 차트 1-3: 실패 요청의 처리 비용 분석 (대기+거부처리)
      📁 performance_charts/comparison_차트1-3_실패요청처리비용분석.png
  ✅ 차트 2-1: 성공 요청 대기시간 분포 (Box Plot)
      📁 performance_charts/comparison_차트2-1_성공요청대기시간분포.png
  ✅ 차트 2-2: 실패 요청 대기시간 분포 (Box Plot)
      📁 performance_charts/comparison_차트2-2_실패요청대기시간분포.png
  ✅ 차트 3: 부하 누적에 따른 성능 저하 추이
      📁 performance_charts/comparison_차트3_부하누적추이분석.png

📁 All charts saved to 'performance_charts' directory.

📊 차트별 X/Y축 정리:
==================================================
차트1-1 (요청처리결과분포)
  X축: 동시성 제어 기법
  Y축: 비율 (%) - 성공/실패 누적 막대

차트1-2 (성공요청처리비용)
  X축: 기법 (처리시간 순 정렬)
  Y축: 시간 (자동단위: ns/μs/ms) - 대기+실행 누적 막대

차트1-3 (실패요청처리비용)
  X축: 기법 (처리시간 순 정렬)
  Y축: 시간 (자동단위: ns/μs/ms) - 대기+실패처리 누적 막대

차트2-1 (성공요청대기시간분포)
  X축: 기법별 성능 지표
  Y축: 시간 (자동단위: ns/μs/ms, 로그스케일) - Box Plot

차트2-2 (실패요청대기시간분포)
  X축: 기법별 성능 지표
  Y축: 시간 (자동단위: ns/μs/ms, 로그스케일) - Box Plot

차트3 (부하누적추이)
  X축: 시간 진행 (구간)
  Y축: 평균 대기시간 (자동단위: ns/μs/ms) - 성공/실패 라인

차트4 (룸별성능비교)
  X축: 룸 번호
  Y축: 성공률(%), 대기시간(자동단위: ns/μs/ms) - 3개 서브플롯

==================================================

🏁 Visualization completed!
```

## 기술적 세부사항

### 데이터 검증
```python
def validate_sheets(sheets_dict):
    required_sheets = [
        'Overall_Summary',
        'Overall_Success_Stats', 
        'Overall_Capacity_Failed_Stats',
        'Per_Bin_Stats'
    ]
    missing_sheets = [sheet for sheet in required_sheets if sheet not in sheets_dict]
    if missing_sheets:
        raise ValueError(f"Missing required sheets: {missing_sheets}")
```

### Box Plot 데이터 생성
통계값(평균, 중앙값, 최댓값)으로부터 현실적인 분포 데이터 생성:
```python
def create_boxplot_data_from_stats(stats):
    mean = stats['mean']
    median = stats['median'] 
    maximum = stats['max']
    
    # 사분위수 추정 및 현실적 분포 생성
    # 20개 추가 데이터 포인트로 자연스러운 Box Plot 구현
```

## 워크플로우 통합

### calculate_stats.py → create_charts.py 연동

```bash
# 1단계: 통계 계산
py -3 calculate_stats.py --inputs "test1.csv,test2.csv" --labels "baseline,optimized"

# 2단계: 차트 생성  
python create_charts.py baseline_stats_nano.xlsx optimized_stats_nano.xlsx
```

### 자동화 스크립트 예시
```bash
#!/bin/bash
# 전체 분석 파이프라인

echo "1단계: 통계 계산"
py -3 calculate_stats.py --inputs "ifelse.csv,synchronized.csv,reentrantlock.csv" --labels "ifelse,synchronized,reentrantlock"

echo "2단계: 차트 생성"
python create_charts.py ifelse_stats_nano.xlsx synchronized_stats_nano.xlsx reentrantlock_stats_nano.xlsx

echo "3단계: 결과 확인"
ls -la performance_charts/
```

## 포트폴리오 활용 가이드

### 차트 해석 방법

#### 성능 비교 분석
1. **차트 1-1**에서 성공률 확인
2. **차트 1-2, 1-3**에서 처리 비용 비교
3. **차트 2-1, 2-2**에서 성능 분포 안정성 분석
4. **차트 3**에서 시간에 따른 성능 저하 패턴 분석

#### 포트폴리오 스토리 구성
```
"동시성 제어 기법별 성능 분석 결과"
├── 성공률 비교: synchronized 95% vs ifelse 60%
├── 처리 비용: ReentrantLock이 가장 효율적
├── 안정성: Fair Lock이 p99 지연시간 50% 개선
└── 확장성: Semaphore가 부하 증가 시 가장 안정적
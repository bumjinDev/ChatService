# Preprocess Logs - 임계 영역 성능 측정 로그 전처리기

멀티스레드 환경에서 임계 영역(Critical Section) 접근 로그를 파싱하여 성능 분석용 데이터로 변환하는 Python 도구입니다. 5가지 핵심 이벤트를 추출하고 사용자별, 방별로 그룹화하여 분석합니다.

## 개요

이 도구는 채팅 서비스 등의 멀티스레드 시스템에서 발생하는 임계 영역 접근 패턴을 분석하고 5가지 핵심 이벤트를 추출합니다:

1. **WAITING_START** - 임계 영역 진입 대기 시작
2. **CRITICAL_ENTER** - 임계 영역 진입 성공  
3. **CRITICAL_LEAVE** - 임계 영역 퇴장
4. **INCREMENT_BEFORE** - 카운터 증가 작업 시작
5. **INCREMENT_AFTER** - 카운터 증가 작업 완료

## 주요 기능

- **로그 파일 자동 교체**: 최신 로그 파일로 자동 업데이트
- **5가지 이벤트 추출**: 정규식 기반 정밀한 이벤트 파싱
- **나노초 정밀도**: 고정밀도 타이밍 분석을 위한 나노초 데이터 보존
- **사용자별 그룹화**: 각 사용자의 완전한 이벤트 시퀀스 구성
- **시간순 정렬**: 타임스탬프, 나노초 기준 정확한 순서 정렬
- **구간 분할**: 각 방의 요청을 10개 구간으로 균등 분할
- **사용자 지정 출력 디렉토리**: `--output_dir` 옵션으로 원하는 위치에 저장
- **다중 출력 형식**: CSV 및 Excel 형식으로 결과 저장
- **상세 분석**: 완성도, 성공률, 방별 통계 제공

## 시스템 요구사항

```bash
pip install pandas openpyxl
```

## 사용법

### 기본 사용법

```cmd
py -3 preprocess_logs.py
```

### 옵션 사용법

```cmd
py -3 preprocess_logs.py --output_dir C:\my_analysis\ --room 1 --csv additional_output.csv --xlsx detailed_report.xlsx
```

### 명령행 옵션

| 옵션 | 타입 | 설명 | 기본값 |
|-----|------|------|--------|
| `--output_dir` | string | 출력 디렉토리 경로 | `results` |
| `--room` | int | 특정 방 번호만 처리 | 전체 방 |
| `--csv` | string | 추가 CSV 파일명 | 없음 |
| `--xlsx` | string | Excel 파일명 (설명 테이블 포함) | 없음 |

## 사용 예시

### 예시 1: 전체 방 분석 (기본)
```cmd
py -3 preprocess_logs.py
```
- 출력: `results/clean_five_events_performance_all_rooms.csv`

### 예시 2: 사용자 지정 디렉토리
```cmd
py -3 preprocess_logs.py --output_dir C:\my_analysis\
```
- 출력: `C:\my_analysis\clean_five_events_performance_all_rooms.csv`

### 예시 3: 특정 방 분석
```cmd
py -3 preprocess_logs.py --output_dir D:\room_data\ --room 101
```
- 출력: `D:\room_data\clean_five_events_performance_room101.csv`

### 예시 4: Excel 보고서 생성
```cmd
py -3 preprocess_logs.py --output_dir C:\reports\ --xlsx performance_report.xlsx
```
- 출력: `C:\reports\clean_five_events_performance_all_rooms.csv` (기본)
- 출력: `C:\reports\performance_report.xlsx` (설명 테이블 포함)

### 예시 5: 다중 출력 파일
```cmd
py -3 preprocess_logs.py --output_dir E:\analysis\ --room 5 --csv room5_backup.csv --xlsx room5_analysis.xlsx
```
- 출력: `E:\analysis\clean_five_events_performance_room5.csv` (기본)
- 출력: `E:\analysis\room5_backup.csv` (추가 CSV)
- 출력: `E:\analysis\room5_analysis.xlsx` (Excel 보고서)

### 예시 6: 전체 분석 및 상세 보고서
```cmd
py -3 preprocess_logs.py --output_dir C:\comprehensive_analysis\ --csv complete_data.csv --xlsx comprehensive_report.xlsx
```
- 출력: `C:\comprehensive_analysis\clean_five_events_performance_all_rooms.csv` (기본)
- 출력: `C:\comprehensive_analysis\complete_data.csv` (추가 CSV)
- 출력: `C:\comprehensive_analysis\comprehensive_report.xlsx` (Excel)

## 입력 데이터 형식

### 로그 파일 구조

도구는 `ChatService.log` 파일을 처리하며, 다음 두 가지 로그 패턴을 인식합니다:

#### CRITICAL_SECTION_MARK 패턴
```
CRITICAL_SECTION_MARK tag=WAITING_START timestampIso=2024-01-15T10:30:45.123Z event=SUCCESS roomNumber=101 userId=user_001 nanoTime=1234567890123456 epochNano=1705312245123456789
```

#### INCREMENT 이벤트 패턴  
```
timestampIso=2024-01-15T10:30:45.124Z event=INCREMENT_BEFORE roomNumber=101 userId=user_001 epochNano=1705312245124456789 nanoTime=1234567890124456
```

### 필수 로그 필드

| 필드명 | 타입 | 설명 |
|--------|------|------|
| `tag` | string | 이벤트 타입 (5가지 중 하나) |
| `timestampIso` | datetime | ISO 형식 타임스탬프 |
| `event` | string | 이벤트 결과 (SUCCESS/FAIL_OVER_CAPACITY) |
| `roomNumber` | int | 방 식별자 |
| `userId` | string | 사용자 식별자 |
| `nanoTime` | long | System.nanoTime() 값 |
| `epochNano` | long | Epoch 기준 나노초 |

## 처리 과정

### 1단계: 로그 파일 교체
```python
# 최신 로그 파일로 자동 교체
E:\devSpace\ChatServiceTest\log\ChatService.log → ChatService.log
```

### 2단계: 이벤트 파싱
```python
# 정규식 기반 5가지 이벤트 추출
- WAITING_START: 대기 시작
- CRITICAL_ENTER: 임계 영역 진입  
- CRITICAL_LEAVE: 임계 영역 퇴장
- INCREMENT_BEFORE: 증가 작업 시작
- INCREMENT_AFTER: 증가 작업 완료
```

### 3단계: 데이터 정렬 및 그룹화
```python
# 시간순 정렬: timestamp → nanoTime → epochNano
# 사용자별 그룹화: 각 사용자의 완전한 이벤트 시퀀스 구성
# 방별 진입 순서: room_entry_sequence 할당
```

### 4단계: 성능 프로필 생성
```python
# 각 사용자별 성능 측정 프로필 생성
- 5가지 이벤트별 타임스탬프
- 나노초 정밀도 시간 정보
- 이벤트 결과 타입
- 방별 진입 순서
```

### 5단계: 구간 분할 및 저장
```python
# 각 방을 10개 구간으로 균등 분할
# CSV 및 Excel 형식으로 저장
# 나노초 정밀도 유지
```

## 출력 형식

### CSV 출력

기본 출력 CSV는 다음 컬럼들을 포함합니다:

#### 기본 정보
| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `roomNumber` | int | 방 번호 |
| `bin` | int | 분석 구간 (1-10) |
| `user_id` | string | 사용자 ID |
| `room_entry_sequence` | int | 방별 처리 순번 |
| `join_result` | string | 입장 결과 (SUCCESS/FAIL_OVER_CAPACITY/UNKNOWN) |

#### 이벤트별 상세 정보 (5가지 × 4개 속성)
각 이벤트마다 다음 4개 속성이 포함됩니다:

| 패턴 | 설명 | 예시 |
|------|------|------|
| `{event}_time` | ISO 타임스탬프 | `waiting_start_time` |
| `{event}_nanoTime` | 나노초 시간 | `critical_enter_nanoTime` |
| `{event}_epochNano` | Epoch 나노초 | `critical_leave_epochNano` |
| `{event}_event_type` | 이벤트 타입 | `critical_leave_event_type` |

### Excel 출력 (옵션)

Excel 파일에는 데이터와 함께 설명 테이블이 포함됩니다:

#### 설명 테이블 내용
| 속성명 | 측정 목적 | 도출 방법 |
|--------|-----------|-----------|
| roomNumber | 방 번호 식별 | 로그 필드: roomNumber |
| bin | 방별 분석 구간 | 각 방의 요청을 10개 구간으로 균등 분할 |
| waiting_start_* | 대기 시작 시점 | WAITING_START 이벤트 속성들 |
| critical_enter_* | 임계구역 진입 시점 | CRITICAL_ENTER 이벤트 속성들 |
| *_nanoTime | 나노초 정밀도 시간 | System.nanoTime() |
| *_epochNano | Epoch 나노초 시간 | Epoch 기준 나노초 |

## 이벤트 설명

### 1. WAITING_START
```
의미: 임계 영역 진입 대기 시작
용도: 대기 시간 측정의 시작점
측정: 대기 지연 시간 분석
```

### 2. CRITICAL_ENTER  
```
의미: 임계 영역 진입 성공
용도: 실제 임계 영역 접근 시점
측정: 대기 시간 = CRITICAL_ENTER - WAITING_START
```

### 3. CRITICAL_LEAVE
```
의미: 임계 영역 퇴장
용도: 임계 영역 사용 완료 시점  
측정: 점유 시간 = CRITICAL_LEAVE - CRITICAL_ENTER
```

### 4. INCREMENT_BEFORE
```
의미: 카운터 증가 작업 시작
용도: 비즈니스 로직 시작 시점
측정: 진입 후 작업 지연 = INCREMENT_BEFORE - CRITICAL_ENTER  
```

### 5. INCREMENT_AFTER
```
의미: 카운터 증가 작업 완료
용도: 비즈니스 로직 완료 시점
측정: 작업 시간 = INCREMENT_AFTER - INCREMENT_BEFORE
```

## 성능 분석 항목

### 완성도 분석
- **전체 처리 작업 수**: 파싱된 총 세션 수
- **이벤트별 완성도**: 각 이벤트의 존재 비율
- **완전한 5개 이벤트 세션**: 모든 이벤트가 존재하는 세션 비율

### 결과별 분석
- **SUCCESS**: 정상 처리된 요청 수
- **FAIL_OVER_CAPACITY**: 정원 초과로 실패한 요청 수  
- **UNKNOWN**: 결과 불명인 요청 수

### 방별 통계
- **총 작업 수**: 방별 처리된 요청 수
- **구간 수**: 실제 생성된 bin 개수
- **성공/실패 분포**: 방별 결과 분포

### 나노초 정밀도 확인
- **정밀도 샘플**: 첫 3개 세션의 나노초 값 표시
- **데이터 무결성**: 나노초 값의 문자열 보존 확인

## 샘플 출력

```
로그 파일 교체 완료: E:\devSpace\ChatServiceTest\log\ChatService.log → ChatService.log

로그 파일 파싱 중...
파싱 완료: 15420개 이벤트

성능 데이터 구축 중...
구축 완료: 3084개 세션
출력 디렉토리 생성: C:\my_analysis\
CSV 파일 저장 완료: C:\my_analysis\clean_five_events_performance_all_rooms.csv
Excel 파일 저장 완료: C:\my_analysis\performance_report.xlsx

============================================================
레이스 컨디션 지표 제거된 5개 이벤트 성능 측정 분석 결과
============================================================
전체 처리 작업 수: 3084

[입장 결과별 분석]
  SUCCESS: 2856건 (92.6%)
  FAIL_OVER_CAPACITY: 228건 (7.4%)

[이벤트 완성도 분석]
  waiting_start_time: 3084건 (100.0%)
  critical_enter_time: 3081건 (99.9%)
  critical_leave_time: 3081건 (99.9%)
  increment_before_time: 2856건 (92.6%)
  increment_after_time: 2856건 (92.6%)

[CRITICAL_LEAVE 이벤트 타입별 분석]
  SUCCESS: 2856건
  FAIL_OVER_CAPACITY: 225건

[완전한 5개 이벤트 세션 분석]
  완전한 세션: 2856건 (92.6%)
    - 성공: 2856건
    - 실패: 0건

[방별 성능 통계]
  방 101: 총 1542회, 구간 수: 10
    - SUCCESS: 1428건
    - FAIL_OVER_CAPACITY: 114건
  방 102: 총 1542회, 구간 수: 10  
    - SUCCESS: 1428건
    - FAIL_OVER_CAPACITY: 114건

[나노초 정밀도 확인 (첫 3개 샘플)]
  샘플 1:
    waiting_start_nanoTime: 1234567890123456
    critical_enter_nanoTime: 1234567890124567
  샘플 2:
    waiting_start_nanoTime: 1234567890125678
    critical_enter_nanoTime: 1234567890126789
  샘플 3:
    waiting_start_nanoTime: 1234567890127890
    critical_enter_nanoTime: 1234567890128901

============================================================
처리 완료!
출력 디렉토리: C:\my_analysis\
============================================================
```

## 기술적 세부사항

### 나노초 정밀도 보존
- **문자열 저장**: 나노초 값을 문자열로 보존하여 정밀도 손실 방지
- **64비트 제한 회피**: pandas/numpy의 64비트 제한을 우회
- **Excel 텍스트 형식**: Excel에서 나노초 값을 텍스트로 표시

### 구간 분할 알고리즘
```python
# 각 방별로 10개 구간으로 균등 분할
if total_requests <= 10:
    bins = range(1, total_requests + 1)  # 각각을 하나의 bin으로
else:
    bins = pd.cut(range(total_requests), bins=10)  # 10개 구간으로 분할
```

### 정규식 패턴
```python
CRITICAL_PATTERN = re.compile(
    r'CRITICAL_SECTION_MARK tag=(?P<tag>WAITING_START|CRITICAL_ENTER|CRITICAL_LEAVE)'
    r' timestampIso=(?P<timestamp>[\w\-\:\.TZ]+)'
    r' event=(?P<event>\w+)'
    r'.* roomNumber=(?P<roomNumber>\d+)'
    r' userId=(?P<userId>[\w\-\_]+)'
    r'.* nanoTime=(?P<nanoTime>\d+)'
    r'.* epochNano=(?P<epochNano>\d+)'
)
```

### 사용자별 이벤트 병합
- **시간순 정렬**: timestamp → nanoTime → epochNano 순으로 정렬
- **이벤트 매핑**: 각 사용자의 5가지 이벤트를 하나의 프로필로 병합
- **진입 순서**: 방별 첫 이벤트 시간 기준으로 순서 할당

## 오류 처리

### 데이터 관련 오류
- **빈 데이터**: 파싱 결과가 없는 경우 빈 DataFrame 반환
- **불완전한 이벤트**: 일부 이벤트만 있는 사용자도 포함
- **나노초 변환 오류**: 안전한 문자열 변환으로 처리

## 성능 최적화

### 정규식 컴파일
- **사전 컴파일**: 정규식을 미리 컴파일하여 성능 향상
- **두 패턴 분리**: CRITICAL과 INCREMENT 패턴을 별도로 처리

## 라이선스

이 도구는 멀티스레드 시스템의 임계 영역 성능 분석을 위한 교육 및 연구 목적으로 제공됩니다.
# 로그 전처리 스크립트 요구사항 (v1.0)

## 1. 개요 (Overview)

- **목표:** 멀티스레드 환경에서 임계 영역(Critical Section) 접근 로그를 파싱하여 성능 분석용 데이터로 변환하는 Python 스크립트를 개발한다.
- **스크립트명:** `preprocess_logs.py`
- **출력 위치:** 스크립트 실행 위치에 `results` 디렉토리를 생성하고, 그 안에 결과물을 저장한다.

---

## 2. 입력 데이터 및 라이브러리

### 2.1. 입력 데이터 (Input Data)
- **파일명:** `ChatService.log` 
- **소스 경로:** `E:\devSpace\ChatServiceTest\log\ChatService.log`
- **역할:** 멀티스레드 환경에서 임계 영역 접근과 관련된 5가지 핵심 이벤트 로그를 담고 있는 원시 로그 파일입니다.

### 2.2. 라이브러리 (Libraries)
- **pandas:**
  - **역할:** 파싱된 로그 데이터를 DataFrame으로 구조화하고, 데이터 필터링, 정렬, 그룹화 등의 분석 작업을 수행합니다.
- **re (정규표현식):**
  - **역할:** 로그 라인에서 특정 패턴의 이벤트 정보를 추출하기 위한 패턴 매칭을 수행합니다.
- **openpyxl:**
  - **역할:** 결과 데이터를 Excel 형식으로 저장하고, 나노초 정밀도 유지를 위한 셀 포맷팅을 수행합니다.

---

## 3. 핵심 요구사항: 5가지 이벤트 추출

### 3.1. 추출 대상 이벤트
멀티스레드 임계 영역 성능 측정을 위해 다음 5가지 이벤트를 추출한다:

1. **WAITING_START:** 임계 영역 진입 대기 시작
2. **CRITICAL_ENTER:** 임계 영역 진입 성공  
3. **CRITICAL_LEAVE:** 임계 영역 퇴장
4. **INCREMENT_BEFORE:** 카운터 증가 작업 시작
5. **INCREMENT_AFTER:** 카운터 증가 작업 완료

### 3.2. 데이터 처리 과정
1. **로그 라인 파싱:** 각 로그 라인에서 이벤트 정보 추출
2. **사용자별 그룹화:** userId 기준으로 이벤트들을 그룹화
3. **시간순 정렬:** timestamp, nanoTime, epochNano 기준 정렬
4. **방별 진입 순서 계산:** 각 방에서의 사용자 처리 순번 계산
5. **구간 분할:** 각 방의 요청을 10개 구간으로 균등 분할

---

## 4. 출력 데이터 명세

### 4.1. 기본 컬럼 구조
| 컬럼명 | 데이터 타입 | 설명 | 계산식 |
|--------|-------------|------|--------|
| roomNumber | Integer | 방을 식별하는 고유 번호 | 로그에서 추출된 방 식별 번호 |
| bin | Integer | 방별 분석 구간 (1~10) | 각 방의 요청을 10개 구간으로 균등 분할 |
| user_id | String | 사용자 식별자 | 로그 필드: userId |
| room_entry_sequence | Integer | 방별 처리 순번 | 방별 타임스탬프 기준 순번 |
| join_result | String | 입장 결과 구분 | SUCCESS/FAIL_OVER_CAPACITY/UNKNOWN |

### 4.2. 이벤트별 상세 컬럼

각 이벤트마다 다음 4개의 컬럼이 생성됩니다:

#### 4.2.1. WAITING_START 이벤트
- `waiting_start_time`: 이벤트 발생 시간 (timestampIso)
- `waiting_start_nanoTime`: 나노초 정밀도 시간 (System.nanoTime())
- `waiting_start_epochNano`: Epoch 기준 나노초 시간
- `waiting_start_event_type`: 이벤트 상세 타입

#### 4.2.2. CRITICAL_ENTER 이벤트
- `critical_enter_time`: 이벤트 발생 시간
- `critical_enter_nanoTime`: 나노초 정밀도 시간
- `critical_enter_epochNano`: Epoch 기준 나노초 시간
- `critical_enter_event_type`: 이벤트 상세 타입

#### 4.2.3. CRITICAL_LEAVE 이벤트
- `critical_leave_time`: 이벤트 발생 시간
- `critical_leave_nanoTime`: 나노초 정밀도 시간
- `critical_leave_epochNano`: Epoch 기준 나노초 시간
- `critical_leave_event_type`: 이벤트 상세 타입 (SUCCESS/FAIL_OVER_CAPACITY)

#### 4.2.4. INCREMENT_BEFORE 이벤트
- `increment_before_time`: 이벤트 발생 시간
- `increment_before_nanoTime`: 나노초 정밀도 시간
- `increment_before_epochNano`: Epoch 기준 나노초 시간
- *주의: INCREMENT 이벤트는 event_type 컬럼이 없음*

#### 4.2.5. INCREMENT_AFTER 이벤트
- `increment_after_time`: 이벤트 발생 시간
- `increment_after_nanoTime`: 나노초 정밀도 시간
- `increment_after_epochNano`: Epoch 기준 나노초 시간
- *주의: INCREMENT 이벤트는 event_type 컬럼이 없음*

### 4.3. 나노초 정밀도 보존
- **요구사항:** 모든 나노초 값은 문자열 형태로 저장하여 정밀도 손실을 방지한다.
- **이유:** Python의 int는 임의 정밀도를 지원하지만, pandas/numpy는 64비트 제한이 있어 문자열로 보존한다.

---

## 5. 출력 파일 형식

### 5.1. CSV 파일
- **파일명:** `clean_five_events_performance_all_rooms.csv` (전체) 또는 `clean_five_events_performance_room{N}.csv` (특정 방)
- **인코딩:** UTF-8 with BOM (Excel 한글 깨짐 방지)
- **특징:** 나노초 값을 문자열로 저장하여 지수 표기법 방지

### 5.2. Excel 파일 (선택사항)
- **파일명:** 사용자 지정 가능
- **추가 기능:** 
  - 나노초 컬럼을 텍스트 형식으로 설정
  - 컬럼 설명 테이블을 별도 영역에 추가
  - timezone 정보 제거하여 호환성 향상

---

## 6. 스크립트 실행 방법

### 6.1. 기본 실행
```bash
python preprocess_logs.py
```

### 6.2. 명령줄 옵션
- `--room N`: 특정 방 번호(N)만 처리
- `--csv filename.csv`: 추가 CSV 파일명 지정
- `--xlsx filename.xlsx`: Excel 파일명 지정

### 6.3. 실행 예시
```bash
# 특정 방만 처리하고 Excel로 저장
python preprocess_logs.py --room 1 --xlsx room1_analysis.xlsx

# 전체 방을 처리하고 추가 CSV 파일 생성
python preprocess_logs.py --csv backup_data.csv
```

---

## 7. 성능 분석 결과 출력

### 7.1. 전체 통계
- 전체 처리 작업 수
- 입장 결과별 분석 (SUCCESS/FAIL_OVER_CAPACITY/UNKNOWN)
- 이벤트 완성도 분석 (각 이벤트별 완료율)

### 7.2. 세션 완성도 분석
- 완전한 5개 이벤트 세션 수
- 성공/실패별 완전 세션 수

### 7.3. 방별 성능 통계
- 방별 총 작업 수
- 방별 구간 수
- 방별 결과 유형 분포

### 7.4. 나노초 정밀도 확인
- 첫 3개 샘플의 나노초 값 출력하여 정밀도 확인

---

## 8. 데이터 품질 보장

### 8.1. 시간 정렬 정확성
- timestamp, nanoTime, epochNano를 조합한 다중 기준 정렬
- 방별 사용자 처리 순서의 정확성 보장

### 8.2. 구간 분할 알고리즘
- 각 방의 요청 수가 10개 이하인 경우: 각각을 별도 구간으로 설정
- 10개 초과인 경우: pandas.cut()을 사용하여 균등 분할

### 8.3. 결과 검증
- join_result 설정 로직: CRITICAL_LEAVE의 event_type 기반
- 누락된 이벤트에 대한 적절한 처리

---

## 9. 예외 처리 및 오류 관리

### 9.1. 파일 오류
- FileNotFoundError: 로그 파일 부재 시 명확한 오류 메시지
- PermissionError: 파일 접근 권한 문제 시 안내

### 9.2. 데이터 오류
- 잘못된 나노초 값: 안전한 변환 함수 사용
- 누락된 이벤트: 빈 값으로 처리하되 분석 결과에 반영

### 9.3. 시스템 오류
- 예상치 못한 오류 발생 시 traceback 출력으로 디버깅 지원

---

## 10. 성능 최적화

### 10.1. 정규표현식 최적화
- 컴파일된 정규표현식 패턴 사용으로 반복 매칭 성능 향상

### 10.2. 메모리 효율성
- 대용량 로그 파일 처리를 위한 라인별 스트리밍 파싱
- 불필요한 데이터 복사 최소화

### 10.3. 데이터 타입 최적화
- 나노초 값의 문자열 보존과 숫자 변환의 적절한 조합
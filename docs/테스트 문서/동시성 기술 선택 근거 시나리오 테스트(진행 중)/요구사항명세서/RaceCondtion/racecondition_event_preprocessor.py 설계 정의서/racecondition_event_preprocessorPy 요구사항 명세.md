# racecondition_event_preprocessor.py 요구사항 명세서

## 1. 배경 및 목적

ChatService 로그에서 발생하는 Race Condition 현상을 체계적으로 분석하기 위해, 원시 로그 데이터에서 핵심 이벤트만을 추출하고 시간순 매칭을 통해 입장 요청의 완전한 생명주기를 재구성한다. 나노초 정밀도 시간 데이터를 활용하여 정확한 순서 분석이 가능한 구조화된 데이터를 생성한다.

## 2. 핵심 처리 원칙

### 2.1 데이터 처리 원칙
- **로그 파일 교체**: 매 실행 시마다 최신 로그 파일로 교체하여 데이터 일관성 보장
- **핵심 이벤트 추출**: 3개 핵심 이벤트만 파싱하여 노이즈 제거
- **시간순 매칭**: 나노초 정밀도를 활용한 정확한 시간순 페어링
- **방별 독립 처리**: 각 방(roomNumber)별로 독립적인 분석 수행

### 2.2 분석 범위
- **이벤트 범위**: PRE_JOIN_CURRENT_STATE, JOIN_SUCCESS_EXISTING, JOIN_FAIL_OVER_CAPACITY_EXISTING
- **시간 정밀도**: 나노초 단위 정렬 및 순번 부여
- **데이터 완전성**: 모든 입장 요청의 시작-끝 페어링 보장

## 3. 핵심 이벤트 정의

### 3.1 추출 대상 이벤트

#### 3.1.1 PRE_JOIN_CURRENT_STATE
- **의미**: 진짜 임계구역 시작점
- **역할**: 입장 시도 시점의 현재 상태 확인
- **필수 필드**: timestampIso, roomNumber, userId, currentPeople, maxPeople, nanoTime, epochNano

#### 3.1.2 JOIN_SUCCESS_EXISTING
- **의미**: 진짜 임계구역 종료점 (성공)
- **역할**: 입장 성공 후 최종 상태 기록
- **필수 필드**: timestampIso, roomNumber, userId, currentPeople, maxPeople, nanoTime

#### 3.1.3 JOIN_FAIL_OVER_CAPACITY_EXISTING
- **의미**: 진짜 임계구역 종료점 (실패)
- **역할**: 정원 초과로 인한 입장 실패 기록
- **필수 필드**: timestampIso, roomNumber, userId, currentPeople, maxPeople, nanoTime

### 3.2 로그 파싱 규칙

#### 3.2.1 정규 표현식 패턴
```regex
timestampIso=(?P<timestamp>\S+).*?
event=(?P<event>PRE_JOIN_CURRENT_STATE|JOIN_SUCCESS_EXISTING|JOIN_FAIL_OVER_CAPACITY_EXISTING).*?
roomNumber=(?P<roomNumber>\d+).*?
userId=(?P<userId>\S+).*?
currentPeople=(?P<currentPeople>\d+).*?
maxPeople=(?P<maxPeople>\d+)
```

#### 3.2.2 나노초 정밀도 데이터 추출
- **nanoTime**: `nanoTime=(\d+)` 패턴으로 추출
- **용도**: 정확한 시간순 정렬 및 임계구역 분석

## 4. 시간순 매칭 페어링 알고리즘

### 4.1 페어링 로직 개요
```
FOR each room:
  1. 전체 이벤트를 epochNano 기준으로 시간순 정렬
  2. PRE_JOIN_CURRENT_STATE 이벤트 탐지
  3. 동일 사용자의 다음 SUCCESS/FAIL 이벤트 매칭
  4. 페어링 완성된 레코드 생성
```

### 4.2 매칭 조건
- **사용자 일치**: `userId`가 동일한 이벤트만 매칭
- **시간 순서**: PRE_JOIN 이후 첫 번째 SUCCESS/FAIL 이벤트 선택
- **방 범위**: 동일 `roomNumber` 내에서만 매칭 수행

### 4.3 페어링 결과 데이터 구조

#### 4.3.1 기본 정보
| 필드명 | 데이터 타입 | 설명 | 계산식 |
|--------|-------------|------|--------|
| roomNumber | Integer | 방 번호 | 로그 필드 그대로 |
| userId | String | 사용자 ID | 로그 필드 그대로 |
| maxPeople_pre | Integer | 최대 정원 | PRE_JOIN의 maxPeople |

#### 4.3.2 인원수 정보
| 필드명 | 데이터 타입 | 설명 | 계산식 |
|--------|-------------|------|--------|
| currentPeople_pre | Integer | 임계구역 진입 시 인원 | PRE_JOIN의 currentPeople |
| curr_people | Integer | 임계구역 종료 시 인원 | SUCCESS/FAIL의 currentPeople |
| expected_people | Float/None | 기대 인원수 | 성공시: `currentPeople_pre + 1`, 실패시: `None` |

#### 4.3.3 결과 정보
| 필드명 | 데이터 타입 | 설명 | 값 |
|--------|-------------|------|-----|
| join_result | String | 입장 결과 | "SUCCESS" 또는 "FAIL_OVER_CAPACITY" |

#### 4.3.4 시간 정보
| 필드명 | 데이터 타입 | 설명 | 계산식 |
|--------|-------------|------|--------|
| timestamp_pre | DateTime | 임계구역 시작 시간 | PRE_JOIN의 timestamp |
| timestamp_end | DateTime | 임계구역 종료 시간 | SUCCESS/FAIL의 timestamp |
| nanoTime_pre | Float | 시작 나노초 | PRE_JOIN의 nanoTime |
| nanoTime_end | Float | 종료 나노초 | SUCCESS/FAIL의 nanoTime |

## 5. 방별 개별 bin 할당 로직

### 5.1 bin 할당 규칙
- **기준**: 나노초 정밀도 기반 시간순 정렬 후 순번 부여
- **크기**: 각 bin당 20개 레코드
- **상한**: 최대 10개 bin까지 제한
- **방별 독립**: 각 방마다 독립적으로 bin 1부터 시작

### 5.2 bin 할당 계산식
```python
bin = (room_entry_sequence - 1) // 20 + 1
bin = min(bin, 10)  # 최대 10으로 제한
```

### 5.3 순번 부여 기준
- **1차 정렬**: nanoTime (나노초 정밀도 기준)
- **2차 정렬**: timestamp (nanoTime이 없는 경우)
- **범위**: 방별로 1부터 시작하는 연속 번호

## 6. 출력 데이터 구조

### 6.1 CSV 파일 컬럼 구성 (총 13개 컬럼)

#### 6.1.1 식별 정보 (3개)
1. **roomNumber** (Integer) - 방 번호
2. **bin** (Integer) - 분석 구간 번호 (1-10)
3. **user_id** (String) - 사용자 식별자

#### 6.1.2 인원 수 정보 (4개)
4. **prev_people** (Integer) - 임계구역 진입 시 현재 인원
5. **curr_people** (Integer) - 임계구역 종료 시 현재 인원
6. **expected_people** (Float) - 기대 인원수 (성공시만)
7. **max_people** (Integer) - 방 최대 정원

#### 6.1.3 순서 및 결과 정보 (2개)
8. **room_entry_sequence** (Integer) - 방별 입장 순번
9. **join_result** (String) - 입장 결과 ("SUCCESS"/"FAIL_OVER_CAPACITY")

#### 6.1.4 시간 정보 (4개)
10. **prev_entry_time** (DateTime) - 임계구역 시작 시간 (기존 형식)
11. **curr_entry_time** (DateTime) - 임계구역 종료 시간 (기존 형식)
12. **true_critical_section_nanoTime_start** (Float) - 시작 나노초
13. **true_critical_section_nanoTime_end** (Float) - 종료 나노초

### 6.2 시간 형식 표준화

#### 6.2.1 기존 형식으로 통일
- **입력 형식**: `2025-07-09T13:36:41.721432200Z`
- **출력 형식**: `2025-07-09 13:36:41.721432200+00:00`
- **변환 함수**: `normalize_timestamp_format()`

#### 6.2.2 변환 로직
```python
df['prev_entry_time'] = pd.to_datetime(df['prev_entry_time'])
df['prev_entry_time'] = df['prev_entry_time'].dt.strftime('%Y-%m-%d %H:%M:%S.%f+00:00')
```

### 6.2 데이터 정렬 순서
- **1차**: roomNumber (오름차순)
- **2차**: nanoTime_pre (나노초 정밀도 기준)
- **3차**: timestamp_pre (nanoTime이 없는 경우)

## 7. Excel 파일 설명 테이블

### 7.1 설명 테이블 구조
xlsx 파일 저장 시 N+2열(데이터 컬럼 수 + 2)부터 설명 테이블 추가

| 속성명 | 분석 목적 | 도출 방법 |
|--------|-----------|-----------|
| roomNumber | 방 번호 식별 | 로그 필드: roomNumber |
| bin | 분석 구간 구분 | 각 방별로 나노초 순서 기준 20개씩 10구간 |
| user_id | 사용자 식별 | 로그 필드: userId |
| prev_people | 입장 전 인원수 | PRE_JOIN_CURRENT_STATE의 currentPeople |
| curr_people | 입장 후 인원수 | SUCCESS/FAIL 이벤트의 currentPeople |
| expected_people | 기대 인원수 | prev_people + 1 (성공시) |
| max_people | 최대 정원 | 로그 필드: maxPeople |
| room_entry_sequence | 방별 입장 순번 | 나노초 정밀도 기준 방별 순번 |
| join_result | 입장 결과 | SUCCESS 또는 FAIL_OVER_CAPACITY |
| prev_entry_time | 임계구역 시작 시간 | PRE_JOIN_CURRENT_STATE 타임스탬프 |
| curr_entry_time | 임계구역 종료 시간 | SUCCESS/FAIL 타임스탬프 |
| true_critical_section_nanoTime_start | 임계구역 시작 나노초 | PRE_JOIN_CURRENT_STATE의 nanoTime |
| true_critical_section_nanoTime_end | 임계구역 끝 나노초 | SUCCESS/FAIL의 nanoTime |

## 8. 통계 분석 및 출력

### 8.1 기본 통계
- **전체 입장 요청 수**: 페어링된 총 레코드 수
- **성공률**: SUCCESS 비율 계산
- **실패율**: FAIL_OVER_CAPACITY 비율 계산
- **나노초 데이터 커버리지**: 나노초 정밀도 데이터 비율

### 8.2 레이스 컨디션 예비 분석
```python
race_conditions = valid_success[valid_success['curr_people'] != valid_success['expected_people']]
race_rate = len(race_conditions) / len(valid_success) * 100
```

### 8.3 방별 bin 분포 분석
각 방별로 bin 1-10까지의 레코드 분포 현황 출력

### 8.4 방별 통계 요약
- 방별 총 요청 수
- 방별 성공 건수 및 성공률
- 방별 데이터 품질 지표

## 9. 파일 시스템 구성

### 9.1 상수 정의
```python
LOG_FILE = 'ChatService.log'  # 작업 디렉토리의 로그 파일
NEW_LOG_PATH = r'E:\devSpace\ChatServiceTest\log\ChatService.log'  # 원본 로그 경로
```

### 9.2 로그 파일 교체 프로세스
1. **기존 파일 삭제**: `os.remove(LOG_FILE)` (존재하는 경우)
2. **새 파일 복사**: `shutil.copy(NEW_LOG_PATH, LOG_FILE)`
3. **인코딩**: UTF-8로 파일 읽기

## 10. 명령줄 인터페이스

### 10.1 필수 인자
```bash
python racecondition_event_preprocessor.py --csv output.csv
```

### 10.2 선택적 인자
- `--room ROOM_NUMBER`: 특정 방 번호만 처리
- `--xlsx output.xlsx`: Excel 파일 추가 생성
- `--csv output.csv`: CSV 파일 생성

### 10.3 인자 검증
- CSV 또는 XLSX 중 최소 하나는 필수
- 방 번호는 정수형만 허용
- 출력 파일 확장자 검증

## 11. 오류 처리 및 검증

### 11.1 입력 데이터 검증
- **로그 파일 존재 여부**: 파일 경로 유효성 검사
- **필수 필드 존재**: 정규식 매칭 실패 시 건너뛰기
- **데이터 타입 변환**: 문자열→정수 변환 실패 시 예외 처리

### 11.2 페어링 검증
- **고아 이벤트**: PRE_JOIN만 있고 SUCCESS/FAIL이 없는 경우
- **중복 매칭**: 동일 사용자의 여러 SUCCESS/FAIL 이벤트 처리
- **시간 순서**: 역순 이벤트 감지 및 처리

### 11.3 출력 검증
- **빈 결과**: 페어링 실패 시 빈 DataFrame 반환
- **컬럼 누락**: 나노초 데이터 없는 경우 해당 컬럼 제외
- **파일 쓰기**: 디스크 공간 및 권한 오류 처리

## 12. 성능 고려사항

### 12.1 메모리 최적화
- **스트림 처리**: 대용량 로그 파일 한 줄씩 처리
- **필터링**: 불필요한 이벤트 조기 제거
- **인덱싱**: 방별 그룹핑으로 처리 범위 최소화

### 12.2 시간 복잡도
- **로그 파싱**: O(n) - 전체 로그 라인 수에 비례
- **페어링**: O(n²) - 최악의 경우 모든 이벤트 조합 확인
- **정렬**: O(n log n) - 나노초 기준 정렬

### 12.3 확장성 고려
- **방 수 증가**: 방별 독립 처리로 선형 확장
- **이벤트 증가**: 정규식 패턴 추가로 확장 가능
- **출력 형식**: 새로운 출력 형식 추가 지원

## 13. 품질 보증

### 13.1 데이터 무결성
- **원본 보존**: 로그 데이터 원본 값 유지
- **추적 가능성**: 각 레코드를 원본 로그로 역추적 가능
- **일관성**: 동일 입력에 대한 동일 출력 보장

### 13.2 정확성 검증
- **페어링 완전성**: 모든 PRE_JOIN에 대응하는 SUCCESS/FAIL 확인
- **시간 순서**: 나노초 정밀도 기반 정확한 순서 보장
- **계산 정확성**: expected_people 계산 로직 검증

### 13.3 테스트 케이스
- **정상 케이스**: 완전한 페어링이 가능한 로그
- **불완전 케이스**: 고아 이벤트가 포함된 로그  
- **대용량 케이스**: 10,000+ 레코드 처리 성능 검증
- **에지 케이스**: 동시 이벤트, 역순 타임스탬프 등

## 14. 버전 관리 및 호환성

### 14.1 로그 형식 버전
- **현재 지원**: ChatService.log 표준 형식
- **확장성**: 새로운 필드 추가 시 하위 호환성 유지
- **마이그레이션**: 로그 형식 변경 시 변환 도구 제공

### 14.2 출력 형식 호환성
- **CSV**: 표준 CSV 형식 (UTF-8 BOM)
- **Excel**: OpenPyXL 호환 XLSX 형식
- **시간 형식**: ISO 8601 표준 준수

### 14.3 의존성 관리
- **pandas**: 데이터 처리 및 시간 변환
- **openpyxl**: Excel 파일 생성 및 편집
- **re**: 정규 표현식 패턴 매칭
- **os, shutil**: 파일 시스템 조작
- **argparse**: 명령줄 인자 처리
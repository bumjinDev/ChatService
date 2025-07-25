# Race Condition 이벤트 전처리기 사용 명령어

## 기본 사용법

### 1. CSV 파일만 생성
```bash
python racecondition_event_preprocessor_modified.py --csv result.csv
```

### 2. Excel 파일만 생성
```bash
python racecondition_event_preprocessor_modified.py --xlsx result.xlsx
```

### 3. CSV와 Excel 둘 다 생성
```bash
python racecondition_event_preprocessor_modified.py --csv result.csv --xlsx result.xlsx
```

## 출력 디렉토리 지정

### 4. 출력 디렉토리 지정해서 CSV 생성
```bash
python racecondition_event_preprocessor_modified.py --csv result.csv --output-dir C:\output
```

### 5. 출력 디렉토리 지정해서 Excel 생성
```bash
python racecondition_event_preprocessor_modified.py --xlsx result.xlsx --output-dir C:\output
```

### 6. 출력 디렉토리 지정해서 CSV, Excel 둘 다 생성
```bash
python racecondition_event_preprocessor_modified.py --csv result.csv --xlsx result.xlsx --output-dir C:\output
```

## 특정 방 번호만 처리

### 7. 특정 방(예: 방 1번)만 처리해서 CSV 생성
```bash
python racecondition_event_preprocessor_modified.py --room 1 --csv room1_result.csv
```

### 8. 특정 방 + 출력 디렉토리 지정
```bash
python racecondition_event_preprocessor_modified.py --room 1 --csv room1_result.csv --output-dir C:\output
```

## 복합 옵션 사용

### 9. 모든 옵션 조합 (특정 방 + CSV + Excel + 출력 디렉토리)
```bash
python racecondition_event_preprocessor_modified.py --room 1 --csv room1_result.csv --xlsx room1_result.xlsx --output-dir C:\analysis_output
```

### 10. 여러 방 분석 시 (방별로 실행)
```bash
# 방 1번 분석
python racecondition_event_preprocessor_modified.py --room 1 --csv room1.csv --output-dir C:\output

# 방 2번 분석  
python racecondition_event_preprocessor_modified.py --room 2 --csv room2.csv --output-dir C:\output

# 전체 방 분석
python racecondition_event_preprocessor_modified.py --csv all_rooms.csv --output-dir C:\output
```

## 주요 인자 설명

| 인자 | 필수여부 | 설명 | 예시 |
|------|---------|------|------|
| `--csv` | 선택* | CSV 파일명 | `--csv result.csv` |
| `--xlsx` | 선택* | Excel 파일명 | `--xlsx result.xlsx` |
| `--output-dir` | 선택 | 출력 디렉토리 경로 | `--output-dir C:\output` |
| `--room` | 선택 | 특정 방 번호만 처리 | `--room 1` |

*주의: `--csv` 또는 `--xlsx` 중 최소 하나는 반드시 지정해야 함

## 오류 발생 시

### 파일 저장 옵션 미지정 오류
```bash
# 잘못된 사용 (오류 발생)
python racecondition_event_preprocessor_modified.py

# 올바른 사용
python racecondition_event_preprocessor_modified.py --csv result.csv
```

### 출력 디렉토리 생성 실패 시
- 디렉토리 경로가 올바른지 확인
- 쓰기 권한이 있는지 확인
- 드라이브가 존재하는지 확인

## 실행 결과 파일

### CSV 파일 내용
- 페어링된 입장 요청 데이터
- 컬럼: roomNumber, bin, user_id, prev_people, curr_people, expected_people, max_people, room_entry_sequence, join_result, prev_entry_time, curr_entry_time, true_critical_section_nanoTime_start, true_critical_section_nanoTime_end

### Excel 파일 내용  
- 메인 데이터 (CSV와 동일)
- 우측에 컬럼 설명 테이블 포함
import re
import pandas as pd
import os
import shutil

# ---------- [파일 삭제/복사 자동화] ----------
src_log_path = r'E:\devSpace\ChatServiceTest\log\ChatService.log'
dst_log_path = 'ChatService.log'

# 기존 ChatService.log 파일 삭제
if os.path.exists(dst_log_path):
    try:
        os.remove(dst_log_path)
        print(f"[정리] 기존 '{dst_log_path}' 파일 삭제 완료.")
    except Exception as e:
        print(f"[오류] 기존 로그 파일 삭제 실패: {e}")
        exit(1)

# 새 로그 파일 복사
try:
    shutil.copy2(src_log_path, dst_log_path)
    print(f"[복사] '{src_log_path}' → '{dst_log_path}' 복사 완료.")
except Exception as e:
    print(f"[오류] 로그 파일 복사 실패: {e}")
    exit(1)
# --------------------------------------------

def parse_log_line(line):
    if "CRITICAL_SECTION_MARK" in line:
        pattern = (
            r"CRITICAL_SECTION_MARK tag=(?P<tag>\w+)"
            r" timestamp=(?P<timestamp>[\w\-\:\.TZ]+)"
            r" event=(?P<event>[\w\_]+)"
            r" className=(?P<className>\w+)"
            r" methodName=(?P<methodName>\w+)"
            r" roomNumber=(?P<roomNumber>\d+)"
            r" userId=(?P<userId>[\w\-\_]+)"
        )
        m = re.search(pattern, line)
        if m:
            return m.groupdict()
    return None

log_file = 'ChatService.log'
records = []
with open(log_file, encoding='utf-8') as f:
    for line in f:
        rec = parse_log_line(line)
        if rec:
            records.append(rec)

df = pd.DataFrame(records)
if df.empty:
    raise Exception("CRITICAL_SECTION_MARK 로그가 파싱되지 않음. 로그 구조 즉시 점검 필요.")

# 타입 변환 및 타임스탬프 오름차순 정렬
df['roomNumber'] = pd.to_numeric(df['roomNumber'], errors='coerce')
df['timestamp'] = pd.to_datetime(df['timestamp'], errors='coerce')
df = df.sort_values('timestamp')

# 지정 필드만 추출
fields = ['tag', 'timestamp', 'event', 'className', 'methodName', 'roomNumber', 'userId']
df = df[fields]

# CSV 저장
df.to_csv('critical_section_marks.csv', index=False)
print("[성공] critical_section_marks.csv 저장 완료.")

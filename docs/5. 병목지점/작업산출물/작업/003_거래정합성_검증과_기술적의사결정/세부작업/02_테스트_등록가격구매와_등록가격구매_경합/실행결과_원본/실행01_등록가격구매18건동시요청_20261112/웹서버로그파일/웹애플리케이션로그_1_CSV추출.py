"""
서버 로그(chatservice.log)에서 웹 애플리케이션 로그만 골라 CSV 파일로 옮긴다. SQL 기록(p6spy가 남긴 기록)은 넣지 않는다.

서버 로그에서 로그 한 건은 날짜와 시각으로 시작하는 줄에서 시작한다.
웹 애플리케이션 로그는 대부분 한 줄이지만, SQL 기록은 SQL 문장 안의 줄바꿈 때문에 여러 줄로 이어진다.
그래서 날짜와 시각으로 시작하는 줄을 만나면 새 로그로 보고, 다음 날짜·시각 줄이 나오기 전까지의 줄을 그 로그에 붙인 뒤,
로그를 남긴 클래스가 p6spy인 로그를 뺀다. 이렇게 해야 SQL 문장의 뒷줄이 웹 애플리케이션 로그에 섞이지 않는다.

CSV에는 로그 한 건을 한 행으로 두고, 서버 시각·로그 레벨·서버 스레드·로그를 남긴 클래스·메시지를 열로 나눈다.
메시지는 서버 로그의 글자를 바꾸지 않고 옮긴다. 메시지가 여러 줄이면(예외 스택 등) 줄바꿈을 그대로 둔다.
원본 로그는 읽기만 한다. 출력 파일이 이미 있으면 덮어쓰지 않고 중단한다.

실행 방법은 같은 폴더의 웹애플리케이션로그_1_CSV추출_실행방법.txt를 참고한다.
"""
import argparse
import csv
import re
import sys
from pathlib import Path

# 서버 로그 줄의 형식(Spring Boot 기본 형식)
# 2026-11-11T15:38:03.407+09:00  INFO 7683 --- [ChatService] [http-nio-8186-exec-6] c.c.m.testsupport.TradeTestLogFilter     : 메시지
LOG_LINE = re.compile(
    r'^(?P<time>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}[+-]\d{2}:\d{2})\s+'
    r'(?P<level>[A-Z]+)\s+(?P<pid>\d+)\s+---\s+\[(?P<app>[^\]]*)\]\s+\[\s*(?P<thread>[^\]]*?)\s*\]\s+'
    r'(?P<logger>\S+)\s*:\s(?P<message>.*)$'
)

# 이번 테스트에서 추가한 로그(공통 로그 형식)에는 모두 이 글자가 들어 있다. 화면에 건수를 보여 줄 때만 쓴다.
TEST_LOG_MARK = '| 실행ID='

HEADERS = ['원본 줄 번호', '원본 줄 수', '서버 시각', '로그 레벨', '프로세스 번호', '애플리케이션', '서버 스레드', '로그를 남긴 클래스', '메시지']


def read_records(log_path):
    """서버 로그를 읽어 로그 한 건씩 나눈 목록과, 첫 로그보다 앞에 있어 어느 로그에도 붙지 않은 줄 수를 돌려준다."""
    lines = log_path.read_text(encoding='utf-8').splitlines()
    records = []
    current = None
    orphan_lines = 0
    for number, line in enumerate(lines, start=1):
        match = LOG_LINE.match(line)
        if match:
            current = {
                'line': number,
                'line_count': 1,
                'time': match.group('time'),
                'level': match.group('level'),
                'pid': match.group('pid'),
                'app': match.group('app'),
                'thread': match.group('thread'),
                'logger': match.group('logger'),
                'message_lines': [match.group('message')],
            }
            records.append(current)
        elif current is not None:
            # 날짜·시각으로 시작하지 않는 줄은 바로 앞 로그에 딸린 줄이다. 빈 줄도 원본대로 붙인다.
            current['message_lines'].append(line)
            current['line_count'] += 1
        else:
            orphan_lines += 1
    return lines, records, orphan_lines


def main():
    parser = argparse.ArgumentParser(description='서버 로그에서 웹 애플리케이션 로그만 골라 CSV 파일로 옮긴다.')
    parser.add_argument('--log', required=True, help='원본 서버 로그 파일(chatservice.log)')
    parser.add_argument('--output', required=True, help='만들 CSV 파일. 같은 이름의 파일이 있으면 중단한다')
    args = parser.parse_args()

    log_path, output = Path(args.log), Path(args.output)
    if output.exists():
        sys.exit(f'출력 파일이 이미 있습니다. 다른 이름을 지정하세요: {output}')

    lines, records, orphan_lines = read_records(log_path)
    web_records = [record for record in records if record['logger'] != 'p6spy']
    sql_records = len(records) - len(web_records)

    output.parent.mkdir(parents=True, exist_ok=True)
    # 엑셀에서 열어도 한글이 깨지지 않도록 UTF-8 BOM을 붙인다(utf-8-sig).
    with output.open('w', encoding='utf-8-sig', newline='') as file:
        writer = csv.writer(file)
        writer.writerow(HEADERS)
        for record in web_records:
            writer.writerow([
                record['line'], record['line_count'], record['time'], record['level'], record['pid'], record['app'],
                record['thread'], record['logger'], '\n'.join(record['message_lines']),
            ])

    test_logs = sum(1 for record in web_records if TEST_LOG_MARK in record['message_lines'][0])
    print(f'원본 파일: {log_path} ({len(lines)}줄)')
    print(f'로그: 모두 {len(records)}건 (SQL 기록 {sql_records}건은 넣지 않음)')
    if orphan_lines:
        print(f'첫 로그보다 앞에 있어 어느 로그에도 붙지 않은 줄: {orphan_lines}줄 (CSV에 넣지 않음)')
    print(f'CSV에 넣은 웹 애플리케이션 로그: {len(web_records)}건 (그 가운데 이번 테스트에서 추가한 로그 {test_logs}건)')
    if web_records:
        print(f'첫 로그 시각: {web_records[0]["time"]}')
        print(f'마지막 로그 시각: {web_records[-1]["time"]}')
    print(f'저장한 파일: {output}')


if __name__ == '__main__':
    main()

"""
서버 로그(chatservice.log)에서 p6spy 기록(서버가 DB에 보낸 SQL과 커밋·롤백의 기록)만 골라 CSV 파일로 옮긴다.

서버 로그에서 로그 한 건은 날짜와 시각으로 시작하는 줄에서 시작한다. 그래서 날짜와 시각으로 시작하는 줄을 만나면 새 로그로 보고,
다음 날짜·시각 줄이 나오기 전까지의 줄을 그 로그에 붙인 뒤, 로그를 남긴 클래스가 p6spy인 로그만 남긴다.
p6spy 기록 한 건은 다음 줄로 이루어진다.
  SQL 실행 기록: 머리 줄(#시각 | took N ms | statement | connection N| url …), 값 자리가 ?인 SQL, 바인드 값을 넣은 SQL
  커밋·롤백 기록: 머리 줄(… | commit | … 또는 … | rollback | …), 빈 줄, 「;」 줄

CSV에는 p6spy 기록 한 건을 한 행으로 두고, 열 이름은 로깅 설계 문서(작업명세 01_1) 「1.1 애플리케이션 로그와 p6spy 기록」에 적힌
p6spy 기록의 정보 이름을 그대로 쓴다: 스레드 이름, 시각, SQL 실행에 걸린 시간, 구분, 연결 번호, 바인드 값을 넣은 SQL.
값은 서버 로그의 글자 그대로 옮긴다. 커밋·롤백 기록에는 SQL 문장이 없으므로 바인드 값을 넣은 SQL 칸에 「-」를 적는다.
원본 로그는 읽기만 한다. 출력 파일이 이미 있으면 덮어쓰지 않고 중단한다.

실행 방법은 같은 폴더의 DB로그_1_p6spy기록_CSV추출_실행방법.txt를 참고한다.
"""
import argparse
import csv
import re
import sys
from collections import Counter
from pathlib import Path

# 서버 로그 줄의 형식(Spring Boot 기본 형식). 웹애플리케이션로그_1_CSV추출.py와 같다.
# 2026-11-11T15:38:03.426+09:00  INFO 7683 --- [ChatService] [http-nio-8186-exec-6] p6spy                                    : #1794379083426 | took 3ms | ...
LOG_LINE = re.compile(
    r'^(?P<time>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}[+-]\d{2}:\d{2})\s+'
    r'(?P<level>[A-Z]+)\s+(?P<pid>\d+)\s+---\s+\[(?P<app>[^\]]*)\]\s+\[\s*(?P<thread>[^\]]*?)\s*\]\s+'
    r'(?P<logger>\S+)\s*:\s(?P<message>.*)$'
)
# p6spy 기록의 머리 줄. 「took」 뒤가 SQL 실행에 걸린 시간(밀리초), 그 뒤가 구분, 「connection」 뒤가 연결 번호다.
P6SPY_HEAD = re.compile(r'^#(?P<epoch>\d+) \| took (?P<took>\d+)ms \| (?P<category>\w+) \| connection (?P<connection>\d+)\|')

HEADERS = ['원본 줄 번호', '스레드 이름', '시각', 'SQL 실행에 걸린 시간', '구분', '연결 번호', '바인드 값을 넣은 SQL']


def read_records(log_path):
    """서버 로그를 읽어 로그 한 건씩 나눈 목록을 돌려준다."""
    lines = log_path.read_text(encoding='utf-8').splitlines()
    records = []
    current = None
    for number, line in enumerate(lines, start=1):
        match = LOG_LINE.match(line)
        if match:
            current = {'line': number, 'time': match.group('time'), 'thread': match.group('thread'),
                       'logger': match.group('logger'), 'message_lines': [match.group('message')]}
            records.append(current)
        elif current is not None:
            # 날짜·시각으로 시작하지 않는 줄은 바로 앞 로그에 딸린 줄이다. 빈 줄도 원본대로 붙인다.
            current['message_lines'].append(line)
    return lines, records


def main():
    parser = argparse.ArgumentParser(description='서버 로그에서 p6spy 기록만 골라 CSV 파일로 옮긴다.')
    parser.add_argument('--log', required=True, help='원본 서버 로그 파일(chatservice.log)')
    parser.add_argument('--output', required=True, help='만들 CSV 파일. 같은 이름의 파일이 있으면 중단한다')
    args = parser.parse_args()

    log_path, output = Path(args.log), Path(args.output)
    if output.exists():
        sys.exit(f'출력 파일이 이미 있습니다. 다른 이름을 지정하세요: {output}')

    lines, records = read_records(log_path)
    rows, irregular, unreadable = [], [], []
    for record in (r for r in records if r['logger'] == 'p6spy'):
        head = P6SPY_HEAD.match(record['message_lines'][0])
        if not head:
            unreadable.append(record['line'])
            continue
        category = head.group('category')
        rest = record['message_lines'][1:]
        if category == 'statement':
            # SQL 실행 기록은 머리 줄 뒤에 값 자리가 ?인 SQL 한 줄과 바인드 값을 넣은 SQL 한 줄이 온다
            if len(rest) == 2:
                sql = rest[1]
            else:
                irregular.append(record['line'])
                sql = '\n'.join(rest[1:]) if len(rest) > 1 else '\n'.join(rest)
        else:
            sql = '-'
        rows.append([record['line'], record['thread'], record['time'], int(head.group('took')), category,
                     int(head.group('connection')), sql])

    output.parent.mkdir(parents=True, exist_ok=True)
    # 엑셀에서 열어도 한글이 깨지지 않도록 UTF-8 BOM을 붙인다(utf-8-sig).
    with output.open('w', encoding='utf-8-sig', newline='') as file:
        writer = csv.writer(file)
        writer.writerow(HEADERS)
        writer.writerows(rows)

    print(f'원본 파일: {log_path} ({len(lines)}줄)')
    print(f'로그: 모두 {len(records)}건, 그 가운데 p6spy 기록 {len(rows) + len(unreadable)}건')
    print('구분별 p6spy 기록: ' + ', '.join(f'{name} {count}건' for name, count in Counter(row[4] for row in rows).items()))
    if irregular:
        print(f'SQL 줄이 두 줄이 아닌 SQL 실행 기록: {len(irregular)}건 (원본 줄 번호 {irregular[:10]} …). 머리 줄 다음 줄들을 이어 붙여 넣었다')
    if unreadable:
        print(f'머리 줄을 읽지 못해 넣지 않은 p6spy 기록: {len(unreadable)}건 (원본 줄 번호 {unreadable[:10]} …)')
    if rows:
        print(f'첫 기록 시각: {rows[0][2]}')
        print(f'마지막 기록 시각: {rows[-1][2]}')
    print(f'CSV에 넣은 p6spy 기록: {len(rows)}건')
    print(f'저장한 파일: {output}')


if __name__ == '__main__':
    main()

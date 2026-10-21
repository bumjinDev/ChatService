"""
서버 로그(chatservice.log)에서 지정한 시각 범위의 로그 기록만 뽑아 새 파일로 저장한다.

로그 기록 하나는 시각으로 시작하는 줄과, 그 뒤에 이어지는 시각 없는 줄로 이루어진다.
시각 없는 줄은 p6spy가 남기는 SQL 줄, 예외 스택 줄, 빈 줄과 같은 줄이며, 바로 앞 시각 줄에 딸린 줄로 함께 뽑는다.
원본 파일은 읽기만 하고 바꾸지 않는다. 뽑은 줄은 원본의 바이트(한글과 줄 끝 문자 포함)를 그대로 쓴다.

사용법(실행 방법은 같은 폴더의 1_시각범위추출_실행방법.txt 참고):
  py -V:3.13 1_시각범위추출.py --input <원본 로그> --output <저장할 파일> --start 2026-10-15T15:20:00 --end 2026-10-15T15:21:00
"""
import argparse
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

# 로그 기록의 첫 줄은 '2026-10-15T15:20:39.123+09:00'처럼 시각으로 시작한다.
# 원본을 바이트로 읽으므로 바이트 정규식으로 시각과 시간대 부분을 찾는다.
TIMESTAMP = re.compile(rb'^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3})([+-]\d{2}:\d{2})')

# 명령줄에 시간대를 적지 않은 시각은 로그와 같은 한국 시각(+09:00)으로 본다.
KST = timezone(timedelta(hours=9))


def parse_cli_time(text):
    """명령줄에 적은 시각을 datetime으로 바꾼다. 시간대를 적지 않았으면 한국 시각으로 본다."""
    value = datetime.fromisoformat(text.replace(' ', 'T'))
    return value if value.tzinfo else value.replace(tzinfo=KST)


def parse_line_time(line):
    """로그 줄이 시각으로 시작하면 그 시각을 돌려주고, 시각으로 시작하지 않으면 None을 돌려준다."""
    match = TIMESTAMP.match(line)
    if not match:
        return None
    return datetime.fromisoformat((match.group(1) + match.group(2)).decode('ascii'))


def main():
    parser = argparse.ArgumentParser(description='서버 로그에서 지정한 시각 범위의 로그 기록만 뽑는다.')
    parser.add_argument('--input', required=True, help='원본 로그 파일 경로')
    parser.add_argument('--output', required=True, help='뽑은 결과를 저장할 파일 경로. 같은 이름의 파일이 있으면 중단한다')
    parser.add_argument('--start', required=True, help='시작 시각. 이 시각의 기록은 포함한다. 예: 2026-10-15T15:20:00')
    parser.add_argument('--end', help='끝 시각. 이 시각의 기록은 포함하지 않는다. 생략하면 파일 끝까지 뽑는다')
    args = parser.parse_args()

    source = Path(args.input)
    target = Path(args.output)
    start = parse_cli_time(args.start)
    end = parse_cli_time(args.end) if args.end else None

    # 원본 로그나 이전에 만든 결과를 덮어쓰지 않도록, 출력 파일이 이미 있으면 중단한다.
    if target.exists():
        sys.exit(f'출력 파일이 이미 있습니다. 다른 이름을 지정하세요: {target}')

    # 한글 바이트와 줄 끝 문자를 원본 그대로 옮기기 위해 바이너리로 읽고, 줄 끝 문자를 남긴 채 줄로 나눈다.
    lines = source.read_bytes().splitlines(keepends=True)

    selected = []        # 뽑은 줄
    in_range = False     # 지금 읽고 있는 로그 기록이 시각 범위 안에 있는지
    record_count = 0     # 뽑은 로그 기록 수(시각 줄 수)
    first_time = None    # 뽑은 첫 로그 기록의 시각
    last_time = None     # 뽑은 마지막 로그 기록의 시각

    for line in lines:
        line_time = parse_line_time(line)
        if line_time is not None:
            # 시각 줄을 만나면 새 로그 기록이 시작된다. 이 기록을 뽑을지는 이 줄의 시각으로 정한다.
            in_range = line_time >= start and (end is None or line_time < end)
            if in_range:
                record_count += 1
                first_time = first_time or line_time
                last_time = line_time
        # 시각 없는 줄은 새로 판단하지 않고, 바로 앞 시각 줄의 판단(in_range)을 그대로 따른다.
        if in_range:
            selected.append(line)

    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(b''.join(selected))

    print(f'원본 파일: {source} ({len(lines)}줄)')
    print(f'시각 범위: {start.isoformat()} 이상, {end.isoformat() + " 미만" if end else "파일 끝까지"}')
    print(f'뽑은 로그 기록: {record_count}건, {len(selected)}줄')
    if first_time:
        print(f'첫 기록 시각: {first_time.isoformat()}')
        print(f'마지막 기록 시각: {last_time.isoformat()}')
    print(f'저장한 파일: {target}')


if __name__ == '__main__':
    main()

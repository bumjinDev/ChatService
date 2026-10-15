"""
서버 로그에서 p6spy 기록(서버가 DB에 보낸 SQL과 커밋의 기록)만 뽑아 새 파일로 저장한다.

로그 기록 하나는 시각으로 시작하는 줄과, 그 뒤에 이어지는 시각 없는 줄로 이루어진다.
p6spy 기록은 시각 줄의 로그를 남긴 클래스 자리에 'p6spy'가 적혀 있고, 그 뒤에 SQL 두 줄(값 자리가 ?로 적힌 바인딩 전 SQL, ? 자리에 실제 값을 넣은 바인딩 후 SQL)이 이어진다.
커밋 기록은 SQL 문장이 없어 빈 줄과 ';' 줄이 이어진다. 이 줄들은 바로 앞 시각 줄에 딸린 줄로 함께 뽑는다.
원본 파일은 읽기만 하고 바꾸지 않는다. 뽑은 줄은 원본의 바이트(한글과 줄 끝 문자 포함)를 그대로 쓴다.

사용법(실행 방법은 같은 폴더의 3_p6spy기록추출_실행방법.txt 참고):
  py -V:3.13 3_p6spy기록추출.py --input <서버 로그> --output <저장할 파일>
"""
import argparse
import re
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path

# 로그 기록의 첫 줄 형식
# 2026-10-15T15:20:44.865+09:00  INFO 650842 --- [ChatService] [http-nio-8186-exec-5] p6spy    : #1792045244865 | took 1ms | statement | ...
# 원본을 바이트로 읽으므로 바이트 정규식으로 시각과 로그를 남긴 클래스 자리를 찾는다.
RECORD_HEAD = re.compile(
    rb'^(?P<time>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}[+-]\d{2}:\d{2})\s+[A-Z]+\s+\d+\s+---\s+'
    rb'\[[^\]]*\]\s+\[[^\]]*\]\s+(?P<logger>\S+)\s*:'
)

# 시각 줄이지만 위 형식과 맞지 않는 줄(형식이 다른 로그)이 있는지 알아보기 위해, 시각으로 시작하는지만 따로 확인한다.
TIMESTAMP = re.compile(rb'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}[+-]\d{2}:\d{2}')

# p6spy 시각 줄에서 기록의 구분(statement는 SQL을 보낸 기록, commit은 커밋한 기록)을 찾는다.
CATEGORY = re.compile(rb'\| took \d+ms \| (?P<category>\w+) \|')


def main():
    parser = argparse.ArgumentParser(description='서버 로그에서 p6spy 기록만 뽑는다.')
    parser.add_argument('--input', required=True, help='서버 로그 파일 경로. 1번 스크립트로 뽑은 파일이나 원본 chatservice.log')
    parser.add_argument('--output', required=True, help='뽑은 결과를 저장할 파일 경로. 같은 이름의 파일이 있으면 중단한다')
    args = parser.parse_args()

    source = Path(args.input)
    target = Path(args.output)

    # 원본 로그나 이전에 만든 결과를 덮어쓰지 않도록, 출력 파일이 이미 있으면 중단한다.
    if target.exists():
        sys.exit(f'출력 파일이 이미 있습니다. 다른 이름을 지정하세요: {target}')

    # 한글 바이트와 줄 끝 문자를 원본 그대로 옮기기 위해 바이너리로 읽고, 줄 끝 문자를 남긴 채 줄로 나눈다.
    lines = source.read_bytes().splitlines(keepends=True)

    selected = []             # 뽑은 줄
    in_p6spy = False          # 지금 읽고 있는 로그 기록이 p6spy 기록인지
    categories = Counter()    # 구분별 p6spy 기록 수
    unmatched = 0             # 시각으로 시작하지만 로그 형식과 맞지 않아 판단하지 못한 줄 수
    first_time = None         # 뽑은 첫 기록의 시각
    last_time = None          # 뽑은 마지막 기록의 시각

    for line in lines:
        head = RECORD_HEAD.match(line)
        if head:
            # 시각 줄을 만나면 새 로그 기록이 시작된다. 이 기록을 뽑을지는 로그를 남긴 클래스 자리로 정한다.
            in_p6spy = head.group('logger') == b'p6spy'
            if in_p6spy:
                category = CATEGORY.search(line)
                categories[category.group('category').decode('ascii') if category else '구분을 찾지 못함'] += 1
                record_time = datetime.fromisoformat(head.group('time').decode('ascii'))
                first_time = first_time or record_time
                last_time = record_time
        elif TIMESTAMP.match(line):
            # 시각으로 시작하지만 형식이 다른 줄은 p6spy 기록인지 판단할 수 없으므로 뽑지 않고, 그 뒤 줄도 뽑지 않는다.
            in_p6spy = False
            unmatched += 1
        # 시각 없는 줄은 새로 판단하지 않고, 바로 앞 시각 줄의 판단(in_p6spy)을 그대로 따른다.
        if in_p6spy:
            selected.append(line)

    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(b''.join(selected))

    print(f'원본 파일: {source} ({len(lines)}줄)')
    print(f'뽑은 p6spy 기록: {sum(categories.values())}건, {len(selected)}줄')
    for category, count in categories.items():
        print(f'  {category}: {count}건')
    if first_time:
        print(f'첫 기록 시각: {first_time.isoformat()}')
        print(f'마지막 기록 시각: {last_time.isoformat()}')
    if unmatched:
        print(f'형식이 맞지 않아 판단하지 못한 시각 줄: {unmatched}줄 (뽑지 않음)')
    print(f'저장한 파일: {target}')


if __name__ == '__main__':
    main()

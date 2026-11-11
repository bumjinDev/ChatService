"""
웹애플리케이션로그_1_CSV추출.py가 만든 웹 애플리케이션 로그 CSV와 JMeter가 실행 폴더에 남긴 사후 확인 파일(사후확인.log)을 읽어,
수락 경합 요청 18건을 확인할 수 있게 엑셀 파일 하나로 정리한다.

대상은 실행 ID가 --run-id와 같은 수락 요청이다. 실행 ID가 다른 요청(예: 15:35에 가격 제안 ID 없이 보낸 요청)은 넣지 않는다.
구매 희망자는 테스트 명세의 흥정 구매자다.

시트 구성
  1_요청별로그              : 구매 희망자 01~18의 요청마다, 로깅 설계 문서(작업명세 01_1) 「2. 로깅 추가 위치」의 로그를
                              로그 종류마다 표 하나로 둔다. 표의 열은 그 로그 종류의 항목이다.
                              그 아래에 같은 요청 스레드에 남은 기존 로그를 표 하나로 둔다.
                              시트 맨 끝 「실행 전체」에는 사후 확인 파일의 전체사후확인 줄을 표 하나로 둔다.
  2_단계별기댓값과실제값    : 맨 위에 기대 값 계산 표와 합계 표를 둔다. 기대 값은 수락 요청을 커밋 순서대로 하나씩 차례로
                              처리(순차 처리)했다면 나와야 하는 값이다. 그 아래에 요청마다 로그 종류별 표를 두고,
                              행에 기대 값·실제 값·문제 여부를 둔다. 수량판단 표에는 판단 시점 정상 기대 값 행을 더한다.
  3_구간별다른요청로그      : 기준 요청의 로그 하나와, 같은 요청에서 그 바로 앞에 남은 로그 사이를 구간으로 본다.
                              구간마다 그 사이에 남은 다른 요청의 로그(설계 문서의 서버 로그)를 시각 순서대로 표 하나에 둔다.
각 시트 아래에는 열마다 무슨 데이터인지 적은 열 설명 표를 둔다.
요청마다 엑셀의 행 그룹으로 묶어, 요청 제목 줄 왼쪽의 단추로 접었다 펼 수 있다.

시각은 서버(미니PC)가 기록한 시각만 쓴다. 서버 로그 가운데 이번 테스트에서 추가한 로그는 메시지 맨 앞에 적힌 시각을,
기존 로그는 서버 로그 줄 맨 앞의 시각을 쓴다. 사후 확인 파일의 시각은 노트북 시계로 적힌 값이라 서버 시각 칸에 넣지 않는다.
입력 파일은 읽기만 한다. 출력 파일이 이미 있으면 덮어쓰지 않고 중단한다.
실행 방법은 같은 폴더의 웹애플리케이션로그_2_엑셀정리_실행방법.txt를 참고한다.
"""
import argparse
import csv
import math
import re
import sys
from datetime import datetime
from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.cell.rich_text import CellRichText, TextBlock
from openpyxl.cell.text import InlineFont
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.hyperlink import Hyperlink

# 이번 테스트에서 추가한 로그(공통 로그 형식)의 메시지. 사후 확인 파일의 줄도 같은 형식이다.
# 2026-11-11T15:38:03.509746+09:00 - 예약–예약 - 가격 제안 수락 - ReservationService.java - reserve
#   | 실행ID=run01-... 요청ID=accept-01 단계=수량판단 | 상품ID=163 제안ID=137 ...
# 사후 확인 파일의 메소드명 칸(수락 경합 뒤 저장 상태 확인)에는 공백이 있으므로 메소드명은 「 | 실행ID=」 앞까지로 읽는다.
TEST_LOG = re.compile(
    r'^(?P<time>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+[+-]\d{2}:\d{2}) - (?P<test>.*?) - (?P<feature>.*?) - '
    r'(?P<file>\S+) - (?P<method>.*?) \| 실행ID=(?P<run>\S+) 요청ID=(?P<request>\S+) 단계=(?P<stage>\S+)'
    r'(?: \| (?P<items>.*))?$'
)
# 항목은 「이름=값」이고, 값에는 공백이 들어갈 수 있다(실패사유 등). 그래서 공백으로 나누지 않고,
# 다음 「이름=」이 시작하는 곳에서 나눈다.
ITEM_START = re.compile(r'(?:^|(?<=\s))([^\s=|]+)=')

# 기존 로그의 기능 구분. 메시지의 앞부분으로 정하며, 위에서부터 차례로 확인한다.
EXISTING_KINDS = [
    ('JWTAuthenticationFilter - 요청 처리 시작', '인증 필터 처리 시작'),
    ('HTTP Method:', '요청 메서드와 URL'),
    ('JWT 인증 성공: 사용자 ID', '인증 성공(사용자 ID)'),
    ('JWT 인증 성공: 사용자 이름', '인증 성공(사용자 이름)'),
    ('[가격 제안 응답]', '가격 제안 응답'),
    ('[예약 걸기]', '예약 걸기'),
    ('[실시간 전달]', '실시간 전달'),
    ('[업무 오류]', '업무 오류'),
]

# 수락 요청 하나에서 서버 로그에 설계 로그가 남는 순서(작업명세 01_1 「2.1 요청 하나에서 로그가 남는 순서」)
STAGE_ORDER = ['요청수신', '수락시작', '수량판단', '예약저장처리완료', '커밋전', '커밋완료', '롤백완료', '확정여부미확인', '요청응답']
END_STAGES = ['커밋완료', '롤백완료', '확정여부미확인']
SHORTAGE_CODE = 'RESERVATION_QUANTITY_EXCEEDED'

# 로그 종류. 커밋완료·롤백완료·확정여부미확인은 한 종류로 본다(작업명세 01_1 「2. 로깅 추가 위치」의 6번, 단계 이름 세 가지).
END_KIND = '커밋완료·롤백완료·확정여부미확인'
KIND_ORDER = ['요청수신', '수락시작', '수량판단', '예약저장처리완료', '커밋전', END_KIND, '요청응답', '요청별사후확인']

# 로그 종류마다 표에 둘 항목: (로그의 항목 이름, 엑셀의 열 이름). 열 이름은 작업명세 01_1 「2. 로깅 추가 위치」의 항목 이름 그대로 쓴다.
# 「단계」는 항목이 아니라 그 로그의 단계 이름(공통 항목)이다.
LOG_ITEMS = {
    '요청수신': [('HTTP메소드', 'HTTP메소드'), ('경로', '경로'), ('스레드', '스레드')],
    '수락시작': [('상품ID', '상품ID'), ('제안ID', '제안ID'), ('대화ID', '대화ID'), ('판매자ID', '판매자ID'),
             ('구매자ID', '구매자ID'), ('요청수량', '요청수량'), ('제안상태', '제안상태')],
    '수량판단': [('상품ID', '상품ID'), ('제안ID', '제안ID'), ('구매자ID', '구매자ID'), ('요청수량', '요청수량'),
             ('남은수량', '남은수량'), ('유효예약합계', '유효예약합계'), ('예약가능수량', '예약가능수량'),
             ('판단기준시각', '판단기준시각'), ('판단결과', '판단결과')],
    '예약저장처리완료': [('예약ID', '예약ID'), ('제안ID', '제안ID'), ('상품ID', '상품ID'), ('구매자ID', '구매자ID'),
                 ('예약수량', '예약수량'), ('예약시각', '예약시각'), ('만료시각', '만료시각')],
    '커밋전': [('제안ID', '제안ID'), ('제안상태', '제안상태'), ('예약ID', '예약ID'), ('예약수량', '예약수량')],
    END_KIND: [('단계', '단계'), ('제안ID', '제안ID'), ('실패사유', '실패사유')],
    '요청응답': [('응답상태', '응답상태'), ('오류코드', '오류코드'), ('소요시간ms', '소요시간ms')],
    '요청별사후확인': [('제안ID', '제안ID'), ('구매자ID', '구매자ID'), ('요청수량', '요청수량'),
                ('응답상태', '응답상태'), ('오류코드', '오류코드'), ('응답구분', '응답구분'), ('제안상태', '제안상태'),
                ('예약ID', '예약ID'), ('예약수량', '예약수량'), ('예약상태', '예약상태'), ('수락예약일치', '수락예약일치')],
    '전체사후확인': [('유효예약합계', '유효예약합계'), ('남은수량', '남은수량'), ('주문수량', '주문수량'),
               ('성공수', '성공수'), ('수량부족수', '수량부족수'), ('기타오류수', '기타오류수'),
               ('미확인수', '미확인수'), ('결과확인기준위반', '결과확인기준위반')],
}
NO_RECORD = '기록 없음'

TITLE_FONT = Font(bold=True, size=12)
REQUEST_FONT = Font(bold=True, size=12, color='FFFFFF')
REQUEST_FILL = PatternFill('solid', fgColor='305496')
TABLE_FONT = Font(bold=True)
# 표 제목 줄(로그 종류, 구간 이름 등): 요청 제목 줄(남색)과 머리글(옅은 파랑)과 구별되게 진한 초록 바탕에 흰 굵은 글씨로 쓴다.
TABLE_TITLE_FONT = Font(bold=True, size=11, color='FFFFFF')
TABLE_TITLE_FILL = PatternFill('solid', fgColor='548235')
HEADER_FONT = Font(bold=True)
HEADER_FILL = PatternFill('solid', fgColor='DDEBF7')
PROBLEM_FILL = PatternFill('solid', fgColor='FCE4D6')
LINK_FONT = Font(color='0563C1', underline='single')
WRAP = Alignment(wrap_text=True, vertical='top')
NO_WRAP = Alignment(wrap_text=False, vertical='top')
# 표의 머리글과 값 칸에 두르는 가는 테두리. 행과 열의 경계가 보이게 한다.
THIN = Side(style='thin', color='A6A6A6')
CELL_BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)


# ---------------------------------------------------------------------
# 입력 파일 읽기
# ---------------------------------------------------------------------
def parse_items(text):
    """「이름=값 이름=값 …」 문자열을 이름 → 값 사전으로 바꾼다. 값의 앞뒤 공백은 지운다."""
    items = {}
    if not text:
        return items
    starts = list(ITEM_START.finditer(text))
    for index, match in enumerate(starts):
        end = starts[index + 1].start() if index + 1 < len(starts) else len(text)
        items[match.group(1)] = text[match.end():end].strip()
    return items


def parse_test_log(text):
    """공통 로그 형식의 한 줄을 읽어 시각, 실행 ID, 요청 ID, 단계, 항목을 돌려준다. 형식이 아니면 None."""
    match = TEST_LOG.match(text)
    if not match:
        return None
    return {
        'time': datetime.fromisoformat(match.group('time')),
        'run': match.group('run'),
        'request': match.group('request'),
        'stage': match.group('stage'),
        'items': parse_items(match.group('items') or ''),
    }


def read_csv_records(csv_path):
    """웹애플리케이션로그_1_CSV추출.py의 CSV를 읽는다. 이번 테스트에서 추가한 로그는 실행 ID, 요청 ID, 단계, 항목을 꺼내 둔다."""
    records = []
    with csv_path.open(encoding='utf-8-sig', newline='') as file:
        for row in csv.DictReader(file):
            message = row['메시지']
            records.append({
                'line': int(row['원본 줄 번호']),
                'server_time': datetime.fromisoformat(row['서버 시각']),
                'thread': row['서버 스레드'],
                'message': message,
                'test': parse_test_log(message.split('\n', 1)[0]),
            })
    return records


def read_after_check(after_path, run_id):
    """사후 확인 파일을 읽어, 실행 ID가 run_id인 요청별사후확인(요청 ID → 줄)과 전체사후확인 줄을 돌려준다."""
    per_request, overall = {}, None
    for number, line in enumerate(after_path.read_text(encoding='utf-8').splitlines(), start=1):
        test = parse_test_log(line)
        if not test or test['run'] != run_id:
            continue
        entry = {'line_text': f'{after_path.name} {number}번째 줄', 'message': line, 'test': test}
        if test['stage'] == '요청별사후확인':
            per_request[test['request']] = entry
        elif test['stage'] == '전체사후확인':
            overall = entry
    return per_request, overall


def read_prepare(ids_path, summary_path):
    """준비 그룹이 남긴 ID 대조 CSV와 요청 전 상태 요약 CSV를 읽는다."""
    with ids_path.open(encoding='utf-8-sig', newline='') as file:
        by_request = {row['request_log_id']: row for row in csv.DictReader(file)}
    with summary_path.open(encoding='utf-8-sig', newline='') as file:
        summary = next(csv.DictReader(file))
    return by_request, summary


# ---------------------------------------------------------------------
# 요청별로 묶기와 계산
# ---------------------------------------------------------------------
def existing_kind(message):
    """기존 로그의 기능 구분을 메시지 앞부분으로 정한다. 목록에 없으면 「그 밖의 로그」다."""
    for prefix, kind in EXISTING_KINDS:
        if message.startswith(prefix):
            return kind
    return '그 밖의 로그'


def kind_of(stage):
    """단계 이름을 로그 종류로 바꾼다. 커밋완료·롤백완료·확정여부미확인은 한 종류(END_KIND)다."""
    return END_KIND if stage in END_STAGES else stage


def group_requests(records, run_id, prepare, after_per_request):
    """실행 ID가 run_id인 요청을 요청 ID별로 묶는다.

    요청 하나의 로그는 그 요청의 요청수신 로그를 남긴 서버 스레드에서, 요청수신 로그부터 요청응답 로그까지 남은 로그다.
    서버 스레드는 요청 하나를 끝까지 처리한 뒤 다음 요청을 받으므로, 이 범위의 로그는 모두 그 요청의 로그다.
    """
    test_records = [r for r in records if r['test'] and r['test']['run'] == run_id]
    request_ids = sorted({r['test']['request'] for r in test_records})
    requests = []
    for request_id in request_ids:
        own = [r for r in test_records if r['test']['request'] == request_id]
        stages = {r['test']['stage']: r for r in own}
        received = stages.get('요청수신')
        thread = received['thread'] if received else own[0]['thread']
        first_line = received['line'] if received else own[0]['line']
        responded = stages.get('요청응답')
        last_line = responded['line'] if responded else own[-1]['line']
        window = [r for r in records if r['thread'] == thread and first_line <= r['line'] <= last_line]
        prep = prepare.get(request_id, {})
        requests.append({
            'id': request_id,
            'buyer_no': prep.get('buyer_no', ''),
            'buyer_id': prep.get('buyer_id', ''),
            'offer_id': prep.get('offer_id', ''),
            'conversation_id': prep.get('conversation_id', ''),
            'offer_qty': int(prep['offer_quantity']) if prep.get('offer_quantity', '').isdigit() else None,
            'offer_status': prep.get('offer_status', ''),
            'thread': thread,
            'records': window,
            'designed': [r for r in window if r['test'] and r['test']['request'] == request_id],
            'stages': stages,
            'after': after_per_request.get(request_id),
        })
    return requests


def stage_time(request, stage):
    """그 요청의 단계 로그가 남은 서버 시각. 그 단계의 로그가 없으면 None."""
    record = request['stages'].get(stage)
    return record['test']['time'] if record else None


def end_stage(request):
    """트랜잭션이 끝난 뒤 남은 단계(커밋완료, 롤백완료, 확정여부미확인) 가운데 그 요청에 남은 것. 없으면 None."""
    return next((stage for stage in END_STAGES if stage in request['stages']), None)


def item(request, stage, name):
    """그 요청의 단계 로그에서 항목 값을 꺼낸다. 로그나 항목이 없으면 None."""
    record = request['stages'].get(stage)
    if not record:
        return None
    return record['test']['items'].get(name)


def calculate(requests, sale_qty):
    """수량판단 단계의 값을 두 가지로 계산해 요청마다 넣는다.

    기대 값(request['serial']): 커밋 순서(커밋완료·롤백완료 로그 시각의 순서)대로 요청을 하나씩 차례로 처리했다고 놓는다.
      앞서 수락된 예약 수량을 판매 수량에서 빼서 예약 가능 수량을 구하고, 제안 수량이 그 이하이면 통과, 넘으면 거절로 본다.
      통과한 요청의 수량만 다음 요청의 「앞서 수락된 예약」에 더한다.
    판단 시점 정상 기대 값(request['at_judge']):
      그 요청의 수량판단 로그 시각보다 먼저 커밋완료 로그가 남은 다른 요청의 예약 수량을 더한다.
    """
    def finish_time(request):
        stage = end_stage(request)
        return stage_time(request, stage) if stage else stage_time(request, '요청응답')

    judged = [r for r in requests if '수량판단' in r['stages']]
    order = sorted(judged, key=lambda r: (finish_time(r) is None, finish_time(r) or datetime.max))
    accepted_sum = 0
    accepted_count = 0
    serial = []
    for position, request in enumerate(order, start=1):
        qty = int(item(request, '수량판단', '요청수량'))
        before = accepted_sum
        passes = qty <= sale_qty - before
        request['serial'] = {'position': position, 'before': before, 'before_count': accepted_count,
                             'available': sale_qty - before, 'passes': passes, 'finish_time': finish_time(request)}
        if passes:
            accepted_sum += qty
            accepted_count += 1
        request['serial']['after'] = accepted_sum
        serial.append(request)

    for request in judged:
        judge_time = stage_time(request, '수량판단')
        committed_before = [o for o in judged if o is not request and stage_time(o, '커밋완료')
                            and stage_time(o, '커밋완료') < judge_time]
        committed_sum = sum(int(item(o, '수량판단', '요청수량')) for o in committed_before)
        qty = int(item(request, '수량판단', '요청수량'))
        request['at_judge'] = {'sum': committed_sum, 'available': sale_qty - committed_sum,
                               'passes': qty <= sale_qty - committed_sum, 'count': len(committed_before)}
    return serial


def log_values(kind, test):
    """로그 한 줄에서 그 로그 종류의 항목 값을 표의 열 순서대로 꺼낸다. 로그에 없는 항목은 「-」다."""
    values = []
    for name, _ in LOG_ITEMS[kind]:
        values.append(test['stage'] if name == '단계' else test['items'].get(name, '-'))
    return values


def extra_items(kind, test):
    """로그에 남았지만 LOG_ITEMS에 정하지 않은 항목 이름. 있으면 표 오른쪽에 열로 더한다."""
    known = {name for name, _ in LOG_ITEMS[kind]}
    return [name for name in test['items'] if name not in known]


# ---------------------------------------------------------------------
# 엑셀 쓰기 도구
# ---------------------------------------------------------------------
def time_us(moment):
    """마이크로초까지의 시:분:초. 예) 15:38:03.509746"""
    return moment.strftime('%H:%M:%S.%f') if moment else '-'


def time_ms(moment):
    """밀리초까지의 시:분:초. 예) 15:38:03.407"""
    return moment.strftime('%H:%M:%S.') + f'{moment.microsecond // 1000:03d}' if moment else '-'


def put(sheet, row, col, value, font=None, fill=None, alignment=WRAP, border=None):
    cell = sheet.cell(row=row, column=col, value=value)
    if font:
        cell.font = font
    if fill:
        cell.fill = fill
    if border:
        cell.border = border
    cell.alignment = alignment
    return cell


def put_request_title(sheet, row, text, width):
    """요청 제목 줄. width 열까지 배경색을 칠한다."""
    put(sheet, row, 1, text, font=REQUEST_FONT, fill=REQUEST_FILL, alignment=NO_WRAP)
    for col in range(2, width + 1):
        sheet.cell(row=row, column=col).fill = REQUEST_FILL


def put_table_title(sheet, row, start_col, text, width):
    """표 제목 줄. 그 표의 열 수(width)만큼 배경색을 칠한다."""
    put(sheet, row, start_col, text, font=TABLE_TITLE_FONT, fill=TABLE_TITLE_FILL, alignment=NO_WRAP)
    for col in range(start_col + 1, start_col + width):
        sheet.cell(row=row, column=col).fill = TABLE_TITLE_FILL


def put_header(sheet, row, headers, start_col=1):
    for offset, header in enumerate(headers):
        put(sheet, row, start_col + offset, header, font=HEADER_FONT, fill=HEADER_FILL, border=CELL_BORDER)


WHOLE_NUMBER = re.compile(r'-?(?:0|[1-9]\d*)')
ZERO_PADDED = re.compile(r'0\d+')


def put_values(sheet, row, values, start_col=1, fill=None):
    """값 행 하나를 테두리를 둘러 쓴다.

    로그에서 읽은 숫자 문자열(예: 「163」)은 숫자로 바꿔 넣는다. 엑셀이 「텍스트로 저장된 숫자」 표시를 붙이지 않게 하려는 것이다.
    앞에 0이 붙은 값(구매 희망자 번호 「01」 등)은 숫자로 넣고 표시 형식으로 자릿수를 맞춰, 화면에는 「01」로 보이게 한다.
    """
    for offset, value in enumerate(values):
        number_format = None
        if isinstance(value, str) and WHOLE_NUMBER.fullmatch(value):
            value = int(value)
        elif isinstance(value, str) and ZERO_PADDED.fullmatch(value):
            number_format = '0' * len(value)
            value = int(value)
        cell = put(sheet, row, start_col + offset, value, fill=fill, border=CELL_BORDER)
        if number_format:
            cell.number_format = number_format


def write_simple_table(sheet, row, title, headers, rows, start_col=1, long_text_col=None):
    """제목·머리글·행으로 된 표를 쓰고, 표 다음 빈 줄을 하나 둔 행 번호를 돌려준다.

    long_text_col(표 안의 열 순서, 0부터)의 칸은 줄을 바꾸지 않아 오른쪽 빈 칸으로 이어 보이게 한다.
    """
    if title:
        put_table_title(sheet, row, start_col, title, len(headers))
        row += 1
    put_header(sheet, row, headers, start_col)
    for values in rows:
        row += 1
        put_values(sheet, row, values, start_col)
        if long_text_col is not None:
            sheet.cell(row=row, column=start_col + long_text_col).alignment = NO_WRAP
    return row + 2


def write_notes(sheet, row, notes):
    """열 설명 표. 내용 칸은 줄을 바꾸지 않아 오른쪽 빈 칸으로 이어 보이게 한다."""
    put(sheet, row, 1, '열 설명', font=TITLE_FONT, alignment=NO_WRAP)
    row += 1
    put_header(sheet, row, ['열', '내용'])
    for name, text in notes:
        row += 1
        put(sheet, row, 1, name, border=CELL_BORDER)
        put(sheet, row, 2, text, alignment=NO_WRAP, border=CELL_BORDER)
    return row + 2


def set_widths(sheet, widths, start_col=1):
    for offset, width in enumerate(widths):
        sheet.column_dimensions[get_column_letter(start_col + offset)].width = width


def group_rows(sheet, start, end):
    """start~end 행을 엑셀의 행 그룹(수준 1)으로 묶는다. 접는 단추는 그룹 위쪽(요청 제목 줄)에 둔다."""
    if end >= start:
        sheet.row_dimensions.group(start, end, outline_level=1, hidden=False)


def request_title(request, thread=True):
    text = (f"구매 희망자 {request['buyer_no']}의 요청 · 요청 ID {request['id']} · 구매 희망자 ID {request['buyer_id']}"
            f" · 가격 제안 ID {request['offer_id']}")
    return text + (f" · 서버 스레드 {request['thread']}" if thread else '')


# ---------------------------------------------------------------------
# 시트 1: 요청별 로그
# ---------------------------------------------------------------------
def build_sheet_requests(workbook, requests, overall, after_name):
    """1_요청별로그 시트를 쓰고, 원본 줄 번호 → 그 로그의 값이 있는 시트 1 행 번호 사전을 돌려준다(시트 3의 링크에 쓴다)."""
    sheet = workbook.active
    sheet.title = '1_요청별로그'
    sheet.sheet_properties.outlinePr.summaryBelow = False
    row = 1
    row_of_line = {}

    for request in requests:
        title_row = row
        put_request_title(sheet, row, request_title(request), 14)
        row += 1
        sequence_of = {record['line']: index for index, record in enumerate(request['records'], start=1)}
        designed_lines = {r['line'] for r in request['designed']}

        # 로깅 설계 문서의 로그: 로그 종류마다 표 하나
        for record in request['designed']:
            test = record['test']
            kind = kind_of(test['stage'])
            extras = extra_items(kind, test)
            headers = ['원본 줄 번호', '로그 순번', '서버 시각'] + [column for _, column in LOG_ITEMS[kind]] + extras
            values = ([record['line'], sequence_of[record['line']], time_us(test['time'])]
                      + log_values(kind, test) + [test['items'][name] for name in extras])
            row_of_line[record['line']] = row + 2
            row = write_simple_table(sheet, row, test['stage'], headers, [values])
        if request['after']:
            after = request['after']
            test = after['test']
            extras = extra_items('요청별사후확인', test)
            headers = ['원본 줄 번호', '로그 순번', '서버 시각'] + [column for _, column in LOG_ITEMS['요청별사후확인']] + extras
            values = [after['line_text'], '-', '-'] + log_values('요청별사후확인', test) + [test['items'][name] for name in extras]
            row = write_simple_table(sheet, row, '요청별사후확인', headers, [values])

        # 기존 로그: 표 하나
        existing_rows = []
        for record in request['records']:
            if record['line'] in designed_lines:
                continue
            previous = [r for r in request['designed'] if r['line'] < record['line']]
            existing_rows.append([sequence_of[record['line']], record['line'], time_ms(record['server_time']),
                                  existing_kind(record['message']), previous[-1]['test']['stage'] if previous else '-',
                                  record['message']])
        row = write_simple_table(sheet, row, '기존 로그', ['로그 순번', '원본 줄 번호', '서버 시각', '기능 구분', '직전 설계 로그', '메시지 원문'],
                                 existing_rows, long_text_col=5)
        group_rows(sheet, title_row + 1, row - 1)

    # 시트 맨 끝: 실행 전체
    if overall:
        title_row = row
        put_request_title(sheet, row, '실행 전체 · 수락 요청 18건이 모두 끝난 뒤 JMeter가 쓴 전체사후확인 줄', 14)
        row += 1
        test = overall['test']
        extras = extra_items('전체사후확인', test)
        headers = ['원본 줄 번호', '로그 순번', '서버 시각'] + [column for _, column in LOG_ITEMS['전체사후확인']] + extras
        values = [overall['line_text'], '-', '-'] + log_values('전체사후확인', test) + [test['items'][name] for name in extras]
        row = write_simple_table(sheet, row, '전체사후확인', headers, [values])
        group_rows(sheet, title_row + 1, row - 1)

    set_widths(sheet, [22, 12, 16] + [17] * 12)
    write_notes(sheet, row, [
        ('요청 제목 줄', '구매 희망자 번호, 요청 ID, 구매 희망자 ID, 가격 제안 ID, 그 요청을 처리한 서버 스레드. 구매 희망자는 테스트 명세의 흥정 구매자다. '
                     '요청 ID는 JMeter가 수락 요청 헤더에 담은 ID이고, 구매 희망자 ID와 가격 제안 ID는 준비 그룹이 남긴 ID 대조 CSV에서 찾은 값이다'),
        ('로그 종류별 표', '로깅 설계 문서(작업명세 01_1) 「2. 로깅 추가 위치」에 정한 로그. 표 하나가 로그 한 줄이고, 열은 그 로그 종류가 기록하는 항목이며 열 이름은 설계 문서의 항목 이름 그대로다. '
                     '표는 서버 로그에 남은 순서대로 두었고, 요청별사후확인 표를 맨 뒤에 두었다'),
        ('원본 줄 번호', f'서버 로그 파일(chatservice.log)에서 그 로그가 시작되는 줄 번호. 요청별사후확인과 전체사후확인은 {after_name}의 몇 번째 줄인지 적었다'),
        ('로그 순번', '그 요청의 서버 로그(로깅 설계 문서의 로그와 기존 로그) 가운데 몇 번째로 남은 로그인지. '
                   f'{after_name}의 줄은 서버 로그가 아니므로 「-」'),
        ('서버 시각', '서버가 그 로그를 남긴 시각. 로깅 설계 문서의 로그는 메시지 맨 앞에 적힌 시각(1/1,000,000초까지)이고, '
                   '기존 로그는 서버 로그 줄 맨 앞의 시각(1/1,000초까지)이다. '
                   f'{after_name}의 줄은 JMeter가 노트북에서 쓴 줄이라 「-」'),
        ('단계(커밋완료·롤백완료·확정여부미확인 표)', '커밋완료, 롤백완료, 확정여부미확인 가운데 실제로 남은 단계. 표 제목에도 그 단계 이름을 적었다'),
        ('판단기준시각·예약시각·만료시각', '로그에 적힌 값 그대로'),
        ('기존 로그 표', '테스트 전부터 코드에 있던 로그(로그인 확인, 가격 제안 응답, 예약 걸기, 실시간 전달 등). '
                    '같은 요청 스레드에서 요청수신 로그부터 요청응답 로그 사이에 남은 것이다'),
        ('기능 구분', '기존 로그의 메시지 앞부분으로 정한 이름'),
        ('직전 설계 로그', '그 기존 로그 바로 앞에 같은 요청 스레드에서 남은 로깅 설계 문서 로그의 단계. 기존 로그가 요청 처리의 어디에서 남았는지 알려 준다'),
        ('실행 전체', '수락 요청 18건이 모두 끝난 뒤 JMeter가 쓴 전체사후확인 줄. 요청 하나가 아니라 실행 전체의 값이다'),
        ('「-」', '그 로그에 해당하지 않는 칸'),
    ])
    return row_of_line


# ---------------------------------------------------------------------
# 시트 2: 단계별 기대 값과 실제 값
# ---------------------------------------------------------------------
def expected_rows(request, kind, product_id, seller_id, sale_qty):
    """로그 종류 하나의 기대 값을 표의 열 순서대로 돌려준다.

    None은 기대 값을 정하지 않은 칸(스레드, 시각, 예약 ID 등)이다. 화면에는 「-」로 적고 문제 여부는 따지지 않는다.
    문자열 「-」는 값이 없어야 하는 칸(오류코드, 실패사유)이다. 실제 값과 비교한다.
    순차 처리에서 그 로그가 남지 않아야 하면 모든 칸을 「기록 없음」으로 돌려준다.
    """
    serial = request.get('serial')
    passes = serial['passes'] if serial else None
    qty = request['offer_qty']
    offer_id = request['offer_id']
    saved_id = item(request, '예약저장처리완료', '예약ID')
    columns = len(LOG_ITEMS[kind])
    if kind == '요청수신':
        return ['POST', f'/ChatService/api/offers/{offer_id}/accept', None]
    if kind == '수락시작':
        return [product_id, offer_id, request['conversation_id'], seller_id, request['buyer_id'], qty, request['offer_status']]
    if kind == '수량판단':
        if not serial:
            return [product_id, offer_id, request['buyer_id'], qty, sale_qty, None, None, None, None]
        return [product_id, offer_id, request['buyer_id'], qty, sale_qty, serial['before'], serial['available'], None,
                '통과' if passes else '거절']
    if passes is None:
        return [None] * columns
    if kind == '예약저장처리완료':
        return [None, offer_id, product_id, request['buyer_id'], qty, None, None] if passes else [NO_RECORD] * columns
    if kind == '커밋전':
        return [offer_id, 'ACCEPTED', saved_id or None, qty] if passes else [NO_RECORD] * columns
    if kind == END_KIND:
        return ['커밋완료', offer_id, '-'] if passes else ['롤백완료', offer_id, SHORTAGE_CODE]
    if kind == '요청응답':
        return ['200', '-', None] if passes else ['409', SHORTAGE_CODE, None]
    if kind == '요청별사후확인':
        if passes:
            return [offer_id, request['buyer_id'], qty, '200', '-', '성공', 'ACCEPTED', saved_id or None, qty, 'ACTIVE', '일치']
        return [offer_id, request['buyer_id'], qty, '409', SHORTAGE_CODE, '수량부족', 'PENDING', '-', '-', '-', '일치']
    return [None] * columns


def actual_values(request, kind):
    """로그 종류 하나의 실제 값과, 그 로그의 제목 정보(서버 시각, 원본 줄 번호)를 돌려준다. 로그가 없으면 모든 칸이 「기록 없음」이다."""
    if kind == '요청별사후확인':
        after = request['after']
        if not after:
            return [NO_RECORD] * len(LOG_ITEMS[kind]), f'{kind} · 로그 없음'
        return log_values(kind, after['test']), f"{kind} · {after['line_text']}"
    stage = end_stage(request) if kind == END_KIND else kind
    record = request['stages'].get(stage) if stage else None
    if not record:
        return [NO_RECORD] * len(LOG_ITEMS[kind]), f'{kind} · 로그 없음'
    return (log_values(kind, record['test']),
            f"{stage} · 서버 시각 {time_us(record['test']['time'])} · 원본 줄 번호 {record['line']}")


def explain(request, kind, columns, expected, actual, problems, sale_qty):
    """문제 여부가 「있음」인 칸마다 설명 문장을 만든다."""
    serial = request.get('serial')
    passes = serial['passes'] if serial else None
    qty = request['offer_qty']
    # 로그가 남았는지부터 다른 경우: 표 전체에 문장 하나
    if all(value == NO_RECORD for value in expected) and any(value != NO_RECORD for value in actual):
        reason = '예약을 저장하지 않으므로' if kind == '예약저장처리완료' else '수락 처리가 예외로 끝나므로'
        return [f'순차 처리에서 거절되는 요청은 {reason} 이 로그가 없어야 하지만, 로그가 남았다.']
    if all(value == NO_RECORD for value in actual) and any(value not in (NO_RECORD, None) for value in expected):
        return [f"순차 처리였다면 {expected[0] if kind == END_KIND else kind} 로그가 남아야 하지만, 로그가 없다."]

    sentences = []
    for index, column in enumerate(columns):
        if not problems[index]:
            continue
        e, a = expected[index], actual[index]
        sentence = f'{column}: 기대 값 「{e}」, 실제 값 「{a}」.'
        if kind == '수량판단' and serial:
            committed = request['at_judge']
            if column == '유효예약합계':
                sentence = (f"{column}: 커밋 순서로 이 요청보다 앞서 수락된 요청이 {serial['before_count']}건(예약 {serial['before']}개) 있다. "
                            f"순차 처리였다면 {serial['before']}개여야 하지만 {a}개로 남았다. "
                            f"이 요청의 수량판단 로그보다 먼저 커밋완료 로그가 남은 다른 요청은 {committed['count']}건(예약 {committed['sum']}개)이다.")
            elif column == '예약가능수량' and str(a).lstrip('-').isdigit():
                diff = int(a) - serial['available']
                sentence = (f"{column}: 판매 수량 {sale_qty}개에서 앞서 수락된 예약 {serial['before']}개를 빼면 {serial['available']}개여야 하지만, "
                            f"{a}개로 남아 {abs(diff)}개 {'많다' if diff > 0 else '적다'}.")
            elif column == '판단결과':
                if passes:
                    sentence = f"{column}: 예약가능수량 {serial['available']}개 안에 요청수량 {qty}개가 들어가므로 통과여야 하지만, {a}로 남았다."
                else:
                    sentence = f"{column}: 예약가능수량 {serial['available']}개보다 요청수량 {qty}개가 많으므로 거절이어야 하지만, {a}로 남았다."
            elif column == '남은수량':
                sentence = f'{column}: 예약은 상품의 남은 수량을 줄이지 않으므로 판매 수량 {sale_qty}개여야 하지만, {a}개로 남았다.'
        elif kind == END_KIND and column == '단계':
            if passes:
                sentence = f'{column}: 순차 처리에서 통과하는 요청은 커밋되어야 하지만, 실제로 남은 단계는 {a}이다.'
            else:
                sentence = f'{column}: 순차 처리에서 거절되는 요청은 롤백되어 수락과 예약이 함께 취소되어야 하지만, 실제로 남은 단계는 {a}이다.'
        elif kind == '요청응답' and column == '응답상태':
            sentence = (f"{column}: 순차 처리에서 {'통과하는' if passes else '거절되는'} 요청이다. "
                        f"기대 값 「{e}」, 실제 값 「{a}」.")
        elif kind == '요청응답' and column == '오류코드' and not passes and a == '-':
            sentence = f'{column}: 순차 처리에서 거절되는 요청은 오류코드 「{e}」로 응답해야 하지만, 오류코드가 없다.'
        sentences.append(sentence)
    return sentences


def explain_overall(columns, expected, actual, problems, sale_qty):
    sentences = []
    for index, column in enumerate(columns):
        if not problems[index]:
            continue
        e, a = expected[index], actual[index]
        if column == '유효예약합계' and str(a).isdigit():
            sentence = (f'{column}: 순차 처리였다면 수락되는 예약 합계는 {e}개여야 하지만, {a}개로 남아 '
                        f'판매 수량 {sale_qty}개를 {int(a) - sale_qty}개 넘었다.' if int(a) > sale_qty
                        else f'{column}: 순차 처리였다면 {e}개여야 하지만, {a}개로 남았다.')
        elif column == '성공수':
            sentence = f'{column}: 순차 처리였다면 {e}건이 성공해야 하지만, {a}건이 성공했다.'
        elif column == '수량부족수':
            sentence = f'{column}: 순차 처리였다면 {e}건이 수량 부족으로 거절되어야 하지만, {a}건이다.'
        elif column == '결과확인기준위반':
            sentence = f'{column}: 위반이 없어야 하지만, 실제로 기록된 위반은 「{a}」이다.'
        else:
            sentence = f'{column}: 기대 값 「{e}」, 실제 값 「{a}」.'
        sentences.append(sentence)
    return sentences


def write_check_table(sheet, row, title, columns, expected, normal, actual, sentences_of):
    """기대 값·실제 값 표 하나를 쓴다. normal은 판단 시점 정상 기대 값 행(없으면 None)이다. 다음 빈 줄 행 번호와 문제 여부를 돌려준다."""
    problems = []
    for e, a in zip(expected, actual):
        problems.append(False if e is None else str(e) != str(a))
    put_table_title(sheet, row, 1, title, 1 + len(columns))
    row += 1
    put_header(sheet, row, ['구분'] + columns)
    row += 1
    put_values(sheet, row, ['기대 값'] + ['-' if e is None else e for e in expected])
    row += 1
    put_values(sheet, row, ['실제 값'] + actual)
    if normal is not None:
        row += 1
        put_values(sheet, row, ['판단 시점 정상 기대 값'] + ['-' if v is None else v for v in normal])
    row += 1
    put(sheet, row, 1, '문제 여부')
    for index, (e, problem) in enumerate(zip(expected, problems)):
        cell = put(sheet, row, 2 + index, '-' if e is None else ('있음' if problem else '없음'))
        if problem:
            cell.fill = PROBLEM_FILL
    sentences = sentences_of(problems) if any(problems) else []
    if sentences:
        row = write_explanations(sheet, row + 1, columns, sentences)
    return row + 2, any(problems)


# 시트 2의 열 너비. 설명 칸의 줄 수를 어림해 행 높이를 정할 때도 쓴다.
SHEET2_WIDTHS = [24, 15, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16]
EXPLAIN_LAST_COL = 11          # 설명 문장 칸은 적어도 K열까지 합친다(열이 적은 표에서도 문장이 너무 여러 줄로 꺾이지 않게)
EXPLAIN_LABEL_FILL = PatternFill('solid', fgColor='FCE4D6')
EXPLAIN_NAME_FILL = PatternFill('solid', fgColor='F2F2F2')


def text_width(text):
    """엑셀 열 너비 단위로 어림한 글자 폭. 한글과 전각 문자는 영문·숫자의 약 1.8배로 본다."""
    return sum(1.8 if ord(ch) >= 0x1100 else 1.0 for ch in str(text))


def write_explanations(sheet, row, columns, sentences):
    """표 아래 설명 묶음을 쓰고, 마지막 설명 행 번호를 돌려준다.

    A열은 「설명」 칸 하나(설명 행 전체를 세로로 합친다), B열은 설명하는 항목 이름, C열부터는 설명 문장(가로로 합치고 줄을 바꾼다)이다.
    합친 칸은 엑셀이 행 높이를 맞추지 않으므로 문장 길이로 줄 수를 어림해 행 높이를 정한다.
    """
    last_col = max(1 + len(columns), EXPLAIN_LAST_COL)
    text_span = sum(SHEET2_WIDTHS[col - 1] for col in range(3, last_col + 1))
    first_row = row
    for index, sentence in enumerate(sentences):
        current = first_row + index
        name, separator, text = sentence.partition(': ')
        if not separator or name not in columns + [column for _, column in LOG_ITEMS['전체사후확인']]:
            name, text = '로그 전체', sentence
        put(sheet, current, 2, name, font=TABLE_FONT, fill=EXPLAIN_NAME_FILL, border=CELL_BORDER)
        put(sheet, current, 3, text, border=CELL_BORDER)
        for col in range(4, last_col + 1):
            sheet.cell(row=current, column=col).border = CELL_BORDER
        sheet.merge_cells(start_row=current, start_column=3, end_row=current, end_column=last_col)
        lines = max(math.ceil(text_width(text) / (text_span * 0.9)), math.ceil(text_width(name) / (SHEET2_WIDTHS[1] * 0.9)), 1)
        sheet.row_dimensions[current].height = 15 * lines + 3
    last_row = first_row + len(sentences) - 1
    put(sheet, first_row, 1, '설명', font=TABLE_FONT, fill=EXPLAIN_LABEL_FILL, border=CELL_BORDER)
    for current in range(first_row + 1, last_row + 1):
        sheet.cell(row=current, column=1).border = CELL_BORDER
    if last_row > first_row:
        sheet.merge_cells(start_row=first_row, start_column=1, end_row=last_row, end_column=1)
    return last_row


def build_sheet_checks(workbook, requests, serial, overall, summary, seller_id):
    """2_단계별기댓값과실제값 시트를 쓰고, 요청 ID → 문제 여부 사전을 돌려준다."""
    sheet = workbook.create_sheet('2_단계별기댓값과실제값')
    sheet.sheet_properties.outlinePr.summaryBelow = False
    sale_qty = int(summary['remaining_quantity'])
    product_id = summary['product_id']
    row = 1

    # 맨 위: 기대 값 계산 표와 합계 표
    serial_rows = []
    for request in serial:
        s = request['serial']
        serial_rows.append([s['position'], request['id'], request['buyer_no'], int(item(request, '수량판단', '요청수량')),
                            time_us(s['finish_time']), s['before'], s['available'], '통과' if s['passes'] else '거절', s['after']])
    row = write_simple_table(sheet, row, f'기대 값 계산 표(수락 요청 {len(serial)}건을 커밋 순서대로 순차 처리)',
                             ['커밋 순서', '요청 ID', '구매 희망자 번호', '요청수량', '커밋완료 시각', '앞서 수락된 예약(개)',
                              '예약가능수량', '판단결과', '이 요청까지 수락된 예약(개)'], serial_rows)
    actual_total = sum(int(item(r, '예약저장처리완료', '예약수량')) for r in requests
                       if '예약저장처리완료' in r['stages'] and end_stage(r) == '커밋완료')
    serial_total = serial[-1]['serial']['after'] if serial else 0
    passed = [r for r in serial if r['serial']['passes']]
    rejected = [r for r in serial if not r['serial']['passes']]
    rejected_qty = sum(int(item(r, '수량판단', '요청수량')) for r in rejected)
    row = write_simple_table(sheet, row, '합계 표', ['구분', '값', '계산 방법'], [
        ['판매 수량(개)', sale_qty, '준비 그룹이 확인한 상품의 남은 수량(준비_요청전상태_요약.csv)'],
        ['실제 커밋된 예약 합계(개)', actual_total, '커밋완료 로그가 남은 요청의 예약저장처리완료 로그의 예약수량을 더한 값'],
        ['순차 처리 시 수락되는 예약 합계(개)', serial_total, '기대 값 계산 표의 마지막 행 값'],
        ['순차 처리 시 거절되는 요청', f'{len(rejected)}건, {rejected_qty}개', '기대 값 계산 표에서 판단결과가 「거절」인 요청의 건수와 수량 합계'],
        ['판매 수량을 넘은 예약(개)', actual_total - sale_qty, '실제 커밋된 예약 합계 − 판매 수량'],
    ], long_text_col=2)

    # 요청마다 로그 종류별 표
    problem_of = {}
    for request in requests:
        title_row = row
        put_request_title(sheet, row, request_title(request, thread=False), 12)
        row += 1
        found = False
        for kind in KIND_ORDER:
            columns = [column for _, column in LOG_ITEMS[kind]]
            expected = expected_rows(request, kind, product_id, seller_id, sale_qty)
            actual, title = actual_values(request, kind)
            normal = None
            if kind == '수량판단' and request.get('at_judge'):
                committed = request['at_judge']
                normal = [None] * 5 + [committed['sum'], committed['available'], None, '통과' if committed['passes'] else '거절']
            row, problem = write_check_table(
                sheet, row, title, columns, expected, normal, actual,
                lambda problems, k=kind, c=columns, e=expected, a=actual: explain(request, k, c, e, a, problems, sale_qty))
            found = found or problem
        problem_of[request['id']] = found
        group_rows(sheet, title_row + 1, row - 1)

    # 시트 맨 끝: 실행 전체
    if overall:
        title_row = row
        put_request_title(sheet, row, '실행 전체 · 수락 요청 18건이 모두 끝난 뒤 JMeter가 쓴 전체사후확인 줄', 12)
        row += 1
        columns = [column for _, column in LOG_ITEMS['전체사후확인']]
        expected = [serial_total, sale_qty, '0', len(passed), len(rejected), '0', '0', '없음']
        actual = log_values('전체사후확인', overall['test'])
        row, _ = write_check_table(sheet, row, f"전체사후확인 · {overall['line_text']}", columns, expected, None, actual,
                                   lambda problems: explain_overall(columns, expected, actual, problems, sale_qty))
        group_rows(sheet, title_row + 1, row - 1)

    set_widths(sheet, SHEET2_WIDTHS)
    write_notes(sheet, row, [
        ('기대 값 계산 표', '커밋완료 시각의 순서대로 요청을 하나씩 처리(순차 처리)했다고 놓고 계산했다. 판매 수량에서 「앞서 수락된 예약(개)」를 빼서 '
                      '예약가능수량을 구하고, 요청수량이 예약가능수량 이하이면 통과, 넘으면 거절로 보았다. '
                      '통과한 요청의 수량만 다음 요청의 「앞서 수락된 예약(개)」에 더했다'),
        ('합계 표', '판매 수량, 실제로 커밋된 예약 합계, 순차 처리였다면 수락되는 예약 합계와 거절되는 요청'),
        ('요청 제목 줄', '구매 희망자 번호, 요청 ID, 구매 희망자 ID, 가격 제안 ID. 구매 희망자는 테스트 명세의 흥정 구매자다'),
        ('로그 종류별 표의 제목', '로그 종류, 그 로그가 남은 서버 시각과 원본 줄 번호(시트 1과 같다). 요청별사후확인과 전체사후확인은 사후확인.log의 몇 번째 줄인지 적었다'),
        ('기대 값', '순차 처리였다면 나와야 하는 값. 요청수신의 경로와 수락시작의 값은 준비 그룹이 등록한 가격 제안의 값이고, '
                 '수량판단 이후의 값은 기대 값 계산 표에서 정한 통과·거절에 따른 값이다'),
        ('기대 값의 「-」', '문제 여부도 「-」인 칸은 기대 값을 정하지 않은 항목(스레드, 판단기준시각·예약시각·만료시각, 서버가 정하는 예약ID, 소요시간ms)이다. '
                       '문제 여부가 「없음」이나 「있음」인 칸은 값이 없어야 하는 항목(오류코드, 실패사유 등)이다'),
        ('기록 없음', '기대 값에서는 순차 처리였다면 그 로그가 남지 않아야 한다는 뜻이고, 실제 값에서는 그 로그가 남지 않았다는 뜻이다'),
        ('판단 시점 정상 기대 값', '그 요청이 수량을 판단할 때 이미 커밋된 예약을 제대로 조회했다면 나와야 하는 값. '
                          '그 요청의 수량판단 로그보다 먼저 커밋완료 로그가 남은 다른 요청의 예약만 더해 계산했다. '
                          '수량판단 표에만 있고, 문제 여부를 정하는 데는 쓰지 않는다. 그 요청이 예약을 조회한 SQL의 실제 시각은 DB 로그를 정리할 때 확인한다'),
        ('실제 값', '그 로그에 남은 값'),
        ('문제 여부', '기대 값과 실제 값이 같으면 「없음」, 다르면 「있음」이고 「있음」 칸에 배경색을 칠했다. 기대 값을 정하지 않은 칸은 「-」'),
        ('설명', '표 아래의 설명 묶음. 문제 여부가 「있음」인 항목마다 항목 이름과, 기대 값과 실제 값이 어떻게 다른지 적었다. '
               '로그가 남았는지부터 기대와 다르면 항목 이름 칸에 「로그 전체」로 적었다'),
    ])
    return problem_of


# ---------------------------------------------------------------------
# 시트 3: 구간별 다른 요청 로그
# ---------------------------------------------------------------------
def interval_label(stage):
    return f'{stage} 구간'


def item_pairs(kind, test):
    """시트 3의 「로그 항목」 칸들. 그 로그에 남은 항목을 LOG_ITEMS의 순서대로 (열 이름, 값)으로 돌려준다.
    순서와 이름이 시트 1의 그 로그 종류 표와 같으므로, 같은 종류의 로그는 같은 항목이 같은 칸에 놓인다."""
    pairs = [(column, test['items'][name]) for name, column in LOG_ITEMS[kind]
             if name != '단계' and name in test['items']]
    return pairs + [(name, test['items'][name]) for name in extra_items(kind, test)]


ITEM_NAME_FONT = InlineFont(sz=9, color='808080')
ITEM_VALUE_FONT = InlineFont(b=True, sz=11)


def item_cell_value(name, value):
    """항목 칸 하나의 값. 항목 이름은 작은 회색 글씨, 값은 굵은 글씨로 한 칸에 적는다."""
    return CellRichText(TextBlock(ITEM_NAME_FONT, f'{name} '), TextBlock(ITEM_VALUE_FONT, str(value)))


def build_sheet_intervals(workbook, requests, row_of_line):
    """3_구간별다른요청로그 시트를 쓴다.

    기준 요청의 로그 하나와, 같은 요청에서 그 바로 앞에 남은 로그 사이를 구간으로 본다(요청수신은 구간이 없다).
    구간마다 그 사이(두 로그의 시각 포함)에 남은 다른 요청의 로깅 설계 문서 로그를 시각 순서대로 표 하나에 둔다.
    로그 항목은 항목마다 칸 하나에 적는다. 머리글 「로그 항목」은 그 칸들 위에 하나로 합친다.
    """
    sheet = workbook.create_sheet('3_구간별다른요청로그')
    sheet.sheet_properties.outlinePr.summaryBelow = False
    row = 1
    buyer_no_of = {r['id']: r['buyer_no'] for r in requests}
    all_logs = sorted((record for request in requests for record in request['designed']),
                      key=lambda record: (record['test']['time'], record['line']))
    fixed_headers = ['구간 안 순번', '다른 요청 ID', '구매 희망자 번호', '로그 시각', '로그 종류', '원본 줄 번호']
    first_item_col = len(fixed_headers) + 1
    item_count = max(len(item_pairs(kind_of(r['test']['stage']), r['test'])) for r in all_logs)
    width = len(fixed_headers) + item_count
    item_widths = [10] * item_count     # 항목 칸마다 가장 긴 「이름 값」에 맞춰 넓힌다
    interval_count = {}

    def put_item_header(row):
        put_header(sheet, row, fixed_headers)
        for col in range(first_item_col, width + 1):
            put(sheet, row, col, '로그 항목' if col == first_item_col else None, font=HEADER_FONT, fill=HEADER_FILL, border=CELL_BORDER)
        sheet.merge_cells(start_row=row, start_column=first_item_col, end_row=row, end_column=width)

    for base in requests:
        title_row = row
        put_request_title(sheet, row, '기준 요청: ' + request_title(base), width)
        row += 1
        own = base['designed']
        interval_count[base['id']] = max(len(own) - 1, 0)
        for previous, current in zip(own, own[1:]):
            start, end = previous['test']['time'], current['test']['time']
            put_table_title(sheet, row, 1, f"{interval_label(current['test']['stage'])}: {previous['test']['stage']} {time_us(start)}"
                                           f" → {current['test']['stage']} {time_us(end)}", width)
            row += 1
            put_item_header(row)
            inside = [record for record in all_logs
                      if record['test']['request'] != base['id'] and start <= record['test']['time'] <= end]
            if not inside:
                row += 1
                put_values(sheet, row, ['-'] * len(fixed_headers) + ['이 구간에 남은 다른 요청의 로그 없음'] + [None] * (item_count - 1))
            for number, record in enumerate(inside, start=1):
                row += 1
                test = record['test']
                kind = kind_of(test['stage'])
                put_values(sheet, row, [number, test['request'], buyer_no_of.get(test['request'], ''), time_us(test['time']),
                                        test['stage'], record['line']])
                pairs = item_pairs(kind, test)
                for index in range(item_count):
                    col = first_item_col + index
                    if index < len(pairs):
                        name, value = pairs[index]
                        put(sheet, row, col, item_cell_value(name, value), alignment=NO_WRAP, border=CELL_BORDER)
                        item_widths[index] = max(item_widths[index], text_width(name) * 0.85 + text_width(value) + 3)
                    else:
                        put(sheet, row, col, None, border=CELL_BORDER)
                target = row_of_line.get(record['line'])
                if target:
                    link = sheet.cell(row=row, column=len(fixed_headers))
                    # 같은 파일 안의 위치로 가는 링크는 location에 「'시트 이름'!셀」을 적는다.
                    link.hyperlink = Hyperlink(ref=link.coordinate, location=f"'1_요청별로그'!A{target}")
                    link.font = LINK_FONT
            row += 2
        group_rows(sheet, title_row + 1, row - 1)

    set_widths(sheet, [12, 12, 11, 16, 17, 12] + [min(round(w), 48) for w in item_widths])
    write_notes(sheet, row, [
        ('기준 요청 제목 줄', '구간을 나누는 기준이 되는 요청. 구매 희망자 번호, 요청 ID, 구매 희망자 ID, 가격 제안 ID, 서버 스레드. '
                       '구매 희망자는 테스트 명세의 흥정 구매자다'),
        ('구간', '기준 요청의 로그 하나와, 같은 요청에서 그 바로 앞에 남은 로그 사이. 요청수신은 바로 앞 로그가 없어 구간이 없다'),
        ('구간 제목', '구간 이름과 시작 로그·끝 로그의 단계와 서버 시각. 구간 이름은 끝 로그의 단계 이름으로 붙였다'),
        ('구간에 넣은 로그', '다른 요청이 남긴 로깅 설계 문서의 서버 로그(요청수신~요청응답) 가운데 구간의 시작 시각부터 끝 시각 사이에 남은 것. '
                  '두 시각에 남은 로그도 넣었다. 기존 로그, p6spy 기록, 사후확인.log의 줄은 넣지 않았다'),
        ('구간 안 순번', '구간 안의 다른 요청 로그를 시각 순서(같은 시각이면 원본 줄 번호 순서)로 매긴 번호'),
        ('다른 요청 ID·구매 희망자 번호', '그 로그를 남긴 요청과 그 요청의 구매 희망자 번호'),
        ('로그 시각', '그 로그의 서버 시각'),
        ('로그 종류', '그 로그의 단계 이름'),
        ('원본 줄 번호', '서버 로그 파일(chatservice.log)에서 그 로그가 시작되는 줄 번호. 누르면 시트 1에서 그 로그가 있는 표로 이동한다'),
        ('로그 항목', '그 로그에 남은 항목을 칸마다 하나씩 적었다. 칸 안의 작은 회색 글씨가 항목 이름이고 굵은 글씨가 값이다. '
                   '항목의 순서와 이름은 로깅 설계 문서와 시트 1의 그 로그 종류 표와 같아서, 같은 종류의 로그는 같은 항목이 같은 열에 놓인다'),
    ])
    return interval_count


def main():
    parser = argparse.ArgumentParser(description='웹 애플리케이션 로그 CSV와 사후 확인 파일을 수락 경합 확인용 엑셀 파일로 정리한다.')
    parser.add_argument('--csv', required=True, help='웹애플리케이션로그_1_CSV추출.py가 만든 웹 애플리케이션 로그 CSV')
    parser.add_argument('--after-check', required=True, help='JMeter가 실행 폴더에 남긴 사후 확인 파일(사후확인.log)')
    parser.add_argument('--run-id', required=True, help='정리할 수락 경합의 실행 ID. 예) run01-20261111-153709')
    parser.add_argument('--prepare-ids', required=True, help='준비 그룹이 남긴 ID 대조 CSV(준비_ID와_요청전상태.csv)')
    parser.add_argument('--prepare-summary', required=True, help='준비 그룹이 남긴 요청 전 상태 요약 CSV(준비_요청전상태_요약.csv)')
    parser.add_argument('--seller-id', required=True, help='준비 그룹이 상품을 등록한 판매자 ID. 수락시작 로그의 판매자 ID 기대 값이다')
    parser.add_argument('--output', required=True, help='만들 엑셀 파일. 같은 이름의 파일이 있으면 중단한다')
    args = parser.parse_args()

    csv_path, after_path, output = Path(args.csv), Path(args.after_check), Path(args.output)
    if output.exists():
        sys.exit(f'출력 파일이 이미 있습니다. 다른 이름을 지정하세요: {output}')

    records = read_csv_records(csv_path)
    after_per_request, overall = read_after_check(after_path, args.run_id)
    prepare, summary = read_prepare(Path(args.prepare_ids), Path(args.prepare_summary))
    requests = group_requests(records, args.run_id, prepare, after_per_request)
    if not requests:
        sys.exit(f'실행 ID {args.run_id}의 로그가 없습니다.')
    requests.sort(key=lambda r: (r['buyer_no'] or '99', r['id']))
    serial = calculate(requests, int(summary['remaining_quantity']))

    unknown = sorted({f"{kind_of(r['test']['stage'])}: {name}" for request in requests for r in request['designed']
                      for name in extra_items(kind_of(r['test']['stage']), r['test'])})

    workbook = Workbook()
    row_of_line = build_sheet_requests(workbook, requests, overall, after_path.name)
    problem_of = build_sheet_checks(workbook, requests, serial, overall, summary, args.seller_id)
    interval_count = build_sheet_intervals(workbook, requests, row_of_line)
    output.parent.mkdir(parents=True, exist_ok=True)
    workbook.save(output)

    print(f'실행 ID {args.run_id}의 요청: {len(requests)}건')
    for request in requests:
        stages = [r['test']['stage'] for r in request['designed']]
        print(f"  {request['id']} (구매 희망자 {request['buyer_no']}, {request['thread']}): "
              f"설계 문서의 로그 {len(request['designed'])}건{' + 요청별사후확인 1줄' if request['after'] else ' (요청별사후확인 없음)'}, "
              f"기존 로그 {len(request['records']) - len(request['designed'])}건, 단계 {'→'.join(stages)}, "
              f"시트 2 {'문제 있음' if problem_of[request['id']] else '문제 없음'}, 시트 3 구간 {interval_count[request['id']]}개")
    print(f"전체사후확인: {'있음' if overall else '없음'}")
    rejected = [r for r in serial if not r['serial']['passes']]
    print(f"순차 처리 시 거절되는 요청: {len(rejected)}건 ({', '.join(r['id'] for r in rejected)})")
    if unknown:
        print('로그 종류별 항목 목록에 없는 항목(표 오른쪽 열로 더함): ' + ', '.join(unknown))
    print(f'저장한 파일: {output}')


if __name__ == '__main__':
    main()

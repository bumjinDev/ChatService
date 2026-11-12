"""
DB로그_1_p6spy기록_CSV추출.py가 만든 p6spy 기록 CSV와 웹애플리케이션로그_1_CSV추출.py가 만든 웹 애플리케이션 로그 CSV를 읽어,
구매 경합 요청 18건이 DB에 보낸 SQL과 커밋·롤백을 확인할 수 있게 엑셀 파일 하나로 정리한다.

요청과 p6spy 기록은 로깅 설계 문서(작업명세 05) 「1.1 애플리케이션 로그와 p6spy 기록」에 적힌 방법으로 잇는다.
같은 서버 스레드에서 그 요청의 요청수신 로그부터 요청응답 로그 사이에 남은 p6spy 기록이 그 요청의 것이다.
대상은 실행 ID가 --run-id와 같은 등록 가격 구매 요청이다.

구간은 웹 애플리케이션 로그 엑셀의 시트 3과 같다. 요청의 로깅 설계 문서 로그 하나와, 같은 요청에서 그 바로 앞에 남은 로그 사이다.
p6spy 기록이 어느 구간에 드는지는 서버 로그 파일의 줄 순서로 정한다. p6spy 기록의 시각은 1/1,000초까지라,
1/1,000,000초까지 있는 설계 로그 시각과 비교하면 같은 1/1,000초 안의 앞뒤를 가를 수 없기 때문이다.

시트 구성
  1_요청별SQL         : 요청마다, 구간마다 그 요청 자신의 p6spy 기록을 표 하나에 둔다.
                        「직전에 실행되는 SQL」 칸에는 로깅 설계 문서 「2.1」의 「앞 로그부터 이 로그까지 p6spy에 남는 SQL」 열의 이름
                        (상품 조회, 상품의 예약 조회, 주문 INSERT 등)을 적는다.
  2_요청별시간        : 요청 18건을 한 표에 한 행씩 두고, 트랜잭션 경과 시간, DB 연결 대기(추정), 반복하는 예약 조회의 건수와 시간을 계산해 둔다.
  3_구간별다른요청SQL : 기준 요청마다, 구간마다 그 사이에 남은 다른 요청의 p6spy 기록을 줄 순서대로 표 하나에 둔다.
각 시트 아래에는 열마다 무슨 데이터인지 적은 열 설명 표를 둔다.
입력 파일은 읽기만 한다. 출력 파일이 이미 있으면 덮어쓰지 않고 중단한다.
실행 방법은 같은 폴더의 DB로그_2_엑셀정리_실행방법.txt를 참고한다.
"""
import argparse
import csv
import re
import sys
from datetime import datetime, timedelta
from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.hyperlink import Hyperlink

# 이번 테스트에서 추가한 로그(공통 로그 형식)의 메시지. 웹애플리케이션로그_2_엑셀정리.py와 같다.
TEST_LOG = re.compile(
    r'^(?P<time>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+[+-]\d{2}:\d{2}) - (?P<test>.*?) - (?P<feature>.*?) - '
    r'(?P<file>\S+) - (?P<method>.*?) \| 실행ID=(?P<run>\S+) 요청ID=(?P<request>\S+) 단계=(?P<stage>\S+)'
    r'(?: \| (?P<items>.*))?$'
)
AUTH_SUCCESS = 'JWT 인증 성공'          # 기존 로그 가운데 인증이 성공한 로그의 메시지 앞부분

# 커밋·롤백이 끝난 뒤 남는 단계. 이 단계로 끝나는 구간에 결제 트랜잭션의 커밋·롤백 기록이 든다.
END_STAGES = ['커밋완료', '롤백완료', '확정여부미확인']
REPEATED_QUERY = '대화마다 예약 조회'     # 대화 상태 이벤트를 만들려고 대화마다 반복하는 예약 조회

TITLE_FONT = Font(bold=True, size=12)
REQUEST_FONT = Font(bold=True, size=12, color='FFFFFF')
REQUEST_FILL = PatternFill('solid', fgColor='305496')
TABLE_TITLE_FONT = Font(bold=True, size=11, color='FFFFFF')
TABLE_TITLE_FILL = PatternFill('solid', fgColor='548235')
HEADER_FONT = Font(bold=True)
HEADER_FILL = PatternFill('solid', fgColor='DDEBF7')
LINK_FONT = Font(color='0563C1', underline='single')
WRAP = Alignment(wrap_text=True, vertical='top')
NO_WRAP = Alignment(wrap_text=False, vertical='top')
THIN = Side(style='thin', color='A6A6A6')
CELL_BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
WHOLE_NUMBER = re.compile(r'-?(?:0|[1-9]\d*)')
ZERO_PADDED = re.compile(r'0\d+')

SQL_HEADERS = ['원본 줄 번호', '시각', 'SQL 실행에 걸린 시간', '구분', '연결 번호', '직전에 실행되는 SQL', '바인드 값을 넣은 SQL']


# ---------------------------------------------------------------------
# 입력 파일 읽기
# ---------------------------------------------------------------------
def read_p6spy(path):
    rows = []
    with path.open(encoding='utf-8-sig', newline='') as file:
        for row in csv.DictReader(file):
            rows.append({'line': int(row['원본 줄 번호']), 'thread': row['스레드 이름'],
                         'time': datetime.fromisoformat(row['시각']), 'took': int(row['SQL 실행에 걸린 시간']),
                         'category': row['구분'], 'connection': int(row['연결 번호']), 'sql': row['바인드 값을 넣은 SQL']})
    return rows


def read_web(path, run_id):
    """웹 애플리케이션 로그 CSV를 읽어, 서버 로그 전체 기록과 실행 ID가 run_id인 설계 로그를 돌려준다."""
    records = []
    with path.open(encoding='utf-8-sig', newline='') as file:
        for row in csv.DictReader(file):
            message = row['메시지']
            match = TEST_LOG.match(message.split('\n', 1)[0])
            test = None
            if match and match.group('run') == run_id:
                test = {'time': datetime.fromisoformat(match.group('time')), 'request': match.group('request'),
                        'stage': match.group('stage')}
            records.append({'line': int(row['원본 줄 번호']), 'server_time': datetime.fromisoformat(row['서버 시각']),
                            'thread': row['서버 스레드'], 'message': message, 'test': test})
    return records


def read_prepare(path):
    with path.open(encoding='utf-8-sig', newline='') as file:
        return {row['request_log_id']: row for row in csv.DictReader(file)}


# ---------------------------------------------------------------------
# 요청별로 묶기
# ---------------------------------------------------------------------
def sql_name(end_stage, record):
    """p6spy 기록 하나에 붙일 이름. 로깅 설계 문서 「2.1」의 「앞 로그부터 이 로그까지 p6spy에 남는 SQL」 열의 이름을 쓴다.

    같은 SQL 문장이 여러 구간에 나오는 것이 있다. 상품의 예약 조회(상품 ID와 예약 상태로 조회)는 구매가능수량판단 구간에서는
    구매 가능 수량 계산의 조회이고, 커밋전 구간에서는 대화 상태 이벤트를 만들려고 대화마다 반복하는 조회다. 상품 조회도 남은수량판단 구간과
    요청응답 구간에 나온다. 그래서 이름은 SQL 문장과 그 기록이 든 구간(끝 로그의 단계)으로 함께 정한다. 정하지 못하면 「-」다.
    """
    if record['category'] == 'commit':
        return 'COMMIT'
    if record['category'] == 'rollback':
        return 'ROLLBACK'
    sql = record['sql'].lower()
    where = sql.split(' where ', 1)[1] if ' where ' in sql else ''
    if sql.startswith('insert into purchase_order'):
        return '주문 INSERT'
    if sql.startswith('insert into balance_transaction'):
        return '잔액 변동 내역 INSERT'
    if sql.startswith('insert into conversation'):
        return '대화 INSERT'
    if sql.startswith('update product'):
        return '상품 UPDATE'
    if sql.startswith('update wallet'):
        return '지갑 UPDATE'
    if end_stage == '요청응답':
        for table, name in [(' from purchase_order ', '주문 조회'), (' from product ', '상품 조회'), (' from membertbl ', '회원 닉네임 조회'),
                            (' from shipment ', '발송 정보 조회'), (' from refund_request ', '환불 요청 조회'),
                            (' from balance_transaction ', '잔액 변동 내역 조회')]:
            if table in sql:
                return name
        return '-'
    if ' from product ' in sql:
        return '상품 조회'
    if ' from wallet ' in sql:
        return '지갑 조회'
    if ' from reservation ' in sql and 'buyer_id=' in where:
        return '같은 구매자의 예약 조회'
    if ' from reservation ' in sql:
        return REPEATED_QUERY if end_stage == '커밋전' else '상품의 예약 조회'
    if ' from conversation ' in sql and 'buyer_id=' in where:
        return '대화 조회'
    if ' from conversation ' in sql:
        return '상품의 대화 조회'
    if ' from purchase_order ' in sql and end_stage == '커밋전':
        return '대화마다 주문 조회'
    return '-'


def group_requests(p6spy, web, run_id, prepare):
    """요청마다 설계 로그, 그 요청의 p6spy 기록, 구간, 인증 성공 시각을 묶는다."""
    designed = {}
    for record in web:
        if record['test']:
            designed.setdefault(record['test']['request'], []).append(record)
    requests = []
    for request_id, logs in designed.items():
        logs.sort(key=lambda r: r['line'])
        stages = {r['test']['stage']: r for r in logs}
        thread = stages['요청수신']['thread']
        low, high = stages['요청수신']['line'], stages['요청응답']['line']
        own = [r for r in p6spy if r['thread'] == thread and low < r['line'] < high]
        intervals = []
        for previous, current in zip(logs, logs[1:]):
            inside = [r for r in own if previous['line'] < r['line'] < current['line']]
            for r in inside:
                r['name'] = sql_name(current['test']['stage'], r)
                r['request'] = request_id
            intervals.append({'start': previous, 'end': current, 'records': inside})
        auth = next((r for r in web if r['thread'] == thread and low < r['line'] < high
                     and not r['test'] and r['message'].startswith(AUTH_SUCCESS)), None)
        prep = prepare.get(request_id, {})
        requests.append({'id': request_id, 'buyer_no': prep.get('buyer_no', ''), 'buyer_id': prep.get('buyer_id', ''),
                         'conversation_id': prep.get('conversation_id', ''), 'thread': thread, 'logs': logs, 'own': own,
                         'intervals': intervals, 'auth': auth})
    requests.sort(key=lambda r: (r['buyer_no'] or '99', r['id']))
    return requests


# ---------------------------------------------------------------------
# 엑셀 쓰기 도구(웹애플리케이션로그_2_엑셀정리.py와 같은 모양)
# ---------------------------------------------------------------------
def time_us(moment):
    return moment.strftime('%H:%M:%S.%f') if moment else '-'


def time_ms(moment):
    return moment.strftime('%H:%M:%S.') + f'{moment.microsecond // 1000:03d}' if moment else '-'


def ms_between(start, end):
    return round((end - start).total_seconds() * 1000)


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
    put(sheet, row, 1, text, font=REQUEST_FONT, fill=REQUEST_FILL, alignment=NO_WRAP)
    for col in range(2, width + 1):
        sheet.cell(row=row, column=col).fill = REQUEST_FILL


def put_table_title(sheet, row, text, width):
    put(sheet, row, 1, text, font=TABLE_TITLE_FONT, fill=TABLE_TITLE_FILL, alignment=NO_WRAP)
    for col in range(2, width + 1):
        sheet.cell(row=row, column=col).fill = TABLE_TITLE_FILL


def put_header(sheet, row, headers):
    for offset, header in enumerate(headers):
        put(sheet, row, 1 + offset, header, font=HEADER_FONT, fill=HEADER_FILL, border=CELL_BORDER)


def put_values(sheet, row, values, no_wrap_last=False):
    """값 행 하나를 테두리를 둘러 쓴다. 숫자 문자열은 숫자로 넣고, 앞에 0이 붙은 값은 표시 형식으로 자릿수를 맞춘다."""
    for offset, value in enumerate(values):
        number_format = None
        if isinstance(value, str) and WHOLE_NUMBER.fullmatch(value):
            value = int(value)
        elif isinstance(value, str) and ZERO_PADDED.fullmatch(value):
            number_format = '0' * len(value)
            value = int(value)
        last = offset == len(values) - 1
        cell = put(sheet, row, 1 + offset, value, border=CELL_BORDER,
                   alignment=NO_WRAP if (no_wrap_last and last) else WRAP)
        if number_format:
            cell.number_format = number_format


def write_notes(sheet, row, notes):
    put(sheet, row, 1, '열 설명', font=TITLE_FONT, alignment=NO_WRAP)
    row += 1
    put_header(sheet, row, ['열', '내용'])
    for name, text in notes:
        row += 1
        put(sheet, row, 1, name, border=CELL_BORDER)
        put(sheet, row, 2, text, alignment=NO_WRAP, border=CELL_BORDER)
    return row + 2


def set_widths(sheet, widths):
    for offset, width in enumerate(widths):
        sheet.column_dimensions[get_column_letter(1 + offset)].width = width


def group_rows(sheet, start, end):
    if end >= start:
        sheet.row_dimensions.group(start, end, outline_level=1, hidden=False)


def request_title(request):
    return (f"등록 가격 구매자 {request['buyer_no']}의 요청 · 요청 ID {request['id']} · 등록 가격 구매자 ID {request['buyer_id']}"
            f" · 대화 ID {request['conversation_id']} · 서버 스레드 {request['thread']}")


def interval_title(interval):
    start, end = interval['start']['test'], interval['end']['test']
    return f"{end['stage']} 구간: {start['stage']} {time_us(start['time'])} → {end['stage']} {time_us(end['time'])}"


def sql_values(record):
    return [record['line'], time_ms(record['time']), record['took'], record['category'], record['connection'],
            record.get('name', '-'), record['sql']]


# ---------------------------------------------------------------------
# 시트 1: 요청별 SQL
# ---------------------------------------------------------------------
def build_sheet_sql(workbook, requests):
    """1_요청별SQL 시트를 쓰고, 원본 줄 번호 → 그 기록이 있는 시트 1 행 번호 사전을 돌려준다(시트 3의 링크에 쓴다)."""
    sheet = workbook.active
    sheet.title = '1_요청별SQL'
    sheet.sheet_properties.outlinePr.summaryBelow = False
    row, row_of_line = 1, {}
    width = len(SQL_HEADERS)
    for request in requests:
        title_row = row
        put_request_title(sheet, row, request_title(request), width)
        row += 1
        for interval in request['intervals']:
            put_table_title(sheet, row, interval_title(interval), width)
            row += 1
            put_header(sheet, row, SQL_HEADERS)
            if not interval['records']:
                row += 1
                put_values(sheet, row, ['-'] * (width - 1) + ['이 구간에 남은 p6spy 기록 없음'], no_wrap_last=True)
            for record in interval['records']:
                row += 1
                put_values(sheet, row, sql_values(record), no_wrap_last=True)
                row_of_line[record['line']] = row
            row += 2
        group_rows(sheet, title_row + 1, row - 1)
    set_widths(sheet, [12, 14, 12, 11, 10, 34, 120])
    write_notes(sheet, row, [
        ('요청 제목 줄', '등록 가격 구매자 번호, 요청 ID, 등록 가격 구매자 ID, 대화 ID, 그 요청을 처리한 서버 스레드. 웹 애플리케이션 로그 엑셀 시트 1과 같다'),
        ('구간', '그 요청의 로깅 설계 문서 로그 하나와, 같은 요청에서 그 바로 앞에 남은 로그 사이. 구간 이름은 끝 로그의 단계 이름으로 붙였다. '
               '웹 애플리케이션 로그 엑셀 시트 3의 구간과 같다'),
        ('구간 안의 기록', '그 요청을 처리한 서버 스레드의 p6spy 기록 가운데, 서버 로그 파일에서 구간의 시작 로그 줄과 끝 로그 줄 사이에 있는 기록. '
                     '로깅 설계 문서 「1.1 애플리케이션 로그와 p6spy 기록」에 적힌 방법이다'),
        ('원본 줄 번호', '서버 로그 파일(chatservice.log)에서 그 p6spy 기록이 시작되는 줄 번호'),
        ('시각', '서버 로그 줄 맨 앞의 시각(1/1,000초까지). p6spy가 그 기록을 남긴 시각이다'),
        ('SQL 실행에 걸린 시간', 'p6spy 기록의 「took」 값. 단위는 밀리초(ms)다'),
        ('구분', 'p6spy 기록의 구분을 로그에 적힌 그대로 적었다. statement는 SQL 실행, commit은 커밋, rollback은 롤백이다'),
        ('연결 번호', 'p6spy 기록의 「connection」 값'),
        ('직전에 실행되는 SQL', '로깅 설계 문서 「2.1 요청 하나에서 로그가 남는 순서」의 「앞 로그부터 이 로그까지 p6spy에 남는 SQL」 열의 이름. '
                         '커밋전 구간의 대화마다 예약 조회는 SQL 문장이 구매가능수량판단 구간의 상품의 예약 조회와 같고, 요청응답 구간의 상품 조회는 남은수량판단 구간의 상품 조회와 같아, '
                         'SQL 문장과 그 기록이 든 구간으로 함께 이름을 붙였다'),
        ('바인드 값을 넣은 SQL', 'p6spy 기록에서 값 자리에 실제 값을 넣은 SQL. 커밋·롤백 기록에는 SQL 문장이 없어 「-」'),
    ])
    return row_of_line


# ---------------------------------------------------------------------
# 시트 2: 요청별 시간
# ---------------------------------------------------------------------
def build_sheet_times(workbook, requests):
    sheet = workbook.create_sheet('2_요청별시간')
    headers = ['요청 ID', '등록 가격 구매자 번호', '첫 SQL 기록 시각', '커밋·롤백 기록 시각', '트랜잭션 경과 시간',
               '인증 성공 시각', '첫 SQL 시작 시각', 'DB 연결 대기',
               '반복하는 예약 조회 건수', '반복하는 예약 조회 시간(첫 기록부터 마지막 기록까지)',
               '반복하는 예약 조회 시간(SQL 실행에 걸린 시간의 합)']
    put_table_title(sheet, 1, '요청별 시간(트랜잭션 경과 시간, DB 연결 대기(추정), 반복하는 예약 조회의 건수와 시간)', len(headers))
    put_header(sheet, 2, headers)
    row = 2
    for request in requests:
        own = request['own']
        first = own[0] if own else None
        # 결제 트랜잭션의 커밋·롤백 기록: 커밋완료·롤백완료·확정여부미확인으로 끝나는 구간의 기록이다.
        # 요청응답 구간에도 응답 본문을 조회한 읽기 전용 트랜잭션의 commit 기록이 남으므로, 그 기록은 쓰지 않는다.
        end = next((r for i in request['intervals'] if i['end']['test']['stage'] in END_STAGES
                    for r in i['records'] if r['category'] in ('commit', 'rollback')), None)
        first_start = first['time'] - timedelta(milliseconds=first['took']) if first else None
        auth = request['auth']['server_time'] if request['auth'] else None
        repeated = [r for r in own if r.get('name') == REPEATED_QUERY]
        row += 1
        put_values(sheet, row, [
            request['id'], request['buyer_no'], time_ms(first['time']) if first else '-', time_ms(end['time']) if end else '-',
            ms_between(first['time'], end['time']) if first and end else '-',
            time_ms(auth), time_ms(first_start), ms_between(auth, first_start) if auth and first_start else '-',
            len(repeated), ms_between(repeated[0]['time'], repeated[-1]['time']) if repeated else '-',
            sum(r['took'] for r in repeated) if repeated else '-',
        ])
    set_widths(sheet, [11, 10, 14, 14, 12, 14, 14, 11, 12, 22, 22])
    write_notes(sheet, row + 3, [
        ('요청 ID·등록 가격 구매자 번호', '시트 1과 같다'),
        ('첫 SQL 기록 시각', '그 요청의 p6spy 기록 가운데 처음 남은 기록의 시각'),
        ('커밋·롤백 기록 시각', '그 요청의 p6spy 기록 가운데 커밋완료(또는 롤백완료, 확정여부미확인) 구간에 남은, 구분이 commit이나 rollback인 기록의 시각. '
                        '요청응답 구간에 남은 응답 조회 트랜잭션의 commit 기록은 쓰지 않았다'),
        ('트랜잭션 경과 시간', '커밋·롤백 기록 시각 − 첫 SQL 기록 시각. 단위는 밀리초(ms)다'),
        ('인증 성공 시각', '같은 서버 스레드에서 요청수신 로그 뒤에 처음 남은 「JWT 인증 성공」 로그의 서버 시각'),
        ('첫 SQL 시작 시각', '첫 SQL 기록 시각 − 그 기록의 SQL 실행에 걸린 시간'),
        ('DB 연결 대기', '첫 SQL 시작 시각 − 인증 성공 시각. 단위는 밀리초(ms)다. 추정한 값이며, 대기 시간을 직접 기록한 값이 아니다'),
        ('반복하는 예약 조회 건수', '커밋전 구간(차감처리완료 로그부터 커밋전 로그 사이)에 남은 대화마다 예약 조회 기록 수. '
                         '로깅 설계 문서 「2.1」에 적힌 「대화마다 실행되는 예약 조회」이다'),
        ('반복하는 예약 조회 시간(첫 기록부터 마지막 기록까지)', '위 예약 조회 기록 가운데 마지막 기록의 시각 − 첫 기록의 시각. 단위는 밀리초(ms)다'),
        ('반복하는 예약 조회 시간(SQL 실행에 걸린 시간의 합)', '위 예약 조회 기록마다의 SQL 실행에 걸린 시간을 더한 값. 단위는 밀리초(ms)다'),
    ])


# ---------------------------------------------------------------------
# 시트 3: 구간별 다른 요청 SQL
# ---------------------------------------------------------------------
def build_sheet_others(workbook, requests, row_of_line):
    sheet = workbook.create_sheet('3_구간별다른요청SQL')
    sheet.sheet_properties.outlinePr.summaryBelow = False
    headers = ['구간 안 순번', '다른 요청 ID', '등록 가격 구매자 번호'] + SQL_HEADERS
    width = len(headers)
    buyer_no_of = {r['id']: r['buyer_no'] for r in requests}
    all_records = sorted((record for request in requests for record in request['own']), key=lambda r: r['line'])
    row, total = 1, 0
    for base in requests:
        title_row = row
        put_request_title(sheet, row, '기준 요청: ' + request_title(base), width)
        row += 1
        for interval in base['intervals']:
            low, high = interval['start']['line'], interval['end']['line']
            put_table_title(sheet, row, interval_title(interval), width)
            row += 1
            put_header(sheet, row, headers)
            inside = [r for r in all_records if r['request'] != base['id'] and low < r['line'] < high]
            if not inside:
                row += 1
                put_values(sheet, row, ['-'] * (width - 1) + ['이 구간에 남은 다른 요청의 p6spy 기록 없음'], no_wrap_last=True)
            for number, record in enumerate(inside, start=1):
                row += 1
                put_values(sheet, row, [number, record['request'], buyer_no_of.get(record['request'], '')] + sql_values(record),
                           no_wrap_last=True)
                link = sheet.cell(row=row, column=4)
                link.hyperlink = Hyperlink(ref=link.coordinate, location=f"'1_요청별SQL'!A{row_of_line[record['line']]}")
                link.font = LINK_FONT
            total += len(inside)
            row += 2
        group_rows(sheet, title_row + 1, row - 1)
    set_widths(sheet, [10, 12, 10, 12, 14, 12, 11, 10, 34, 120])
    write_notes(sheet, row, [
        ('기준 요청 제목 줄', '구간을 나누는 기준이 되는 요청. 시트 1의 요청 제목 줄과 같다'),
        ('구간', '기준 요청의 로깅 설계 문서 로그 하나와, 같은 요청에서 그 바로 앞에 남은 로그 사이. 시트 1의 구간과 같다'),
        ('구간에 넣은 기록', '다른 요청(등록 가격 구매 요청 18건 가운데 기준 요청이 아닌 요청)의 p6spy 기록 가운데, 서버 로그 파일에서 '
                    '기준 요청의 구간 시작 로그 줄과 끝 로그 줄 사이에 있는 기록. 시각이 아니라 서버 로그 파일의 줄 순서로 정했다'),
        ('구간 안 순번', '구간 안의 다른 요청 기록을 서버 로그 파일의 줄 순서로 매긴 번호'),
        ('다른 요청 ID·등록 가격 구매자 번호', '그 기록을 남긴 요청과 그 요청의 등록 가격 구매자 번호'),
        ('원본 줄 번호', '서버 로그 파일(chatservice.log)에서 그 기록이 시작되는 줄 번호. 누르면 시트 1에서 그 기록이 있는 행으로 이동한다'),
        ('시각·SQL 실행에 걸린 시간·구분·연결 번호·바인드 값을 넣은 SQL', '시트 1과 같다'),
        ('직전에 실행되는 SQL', '그 기록을 남긴 요청에서 그 기록이 든 구간으로 붙인 이름. 시트 1과 같다'),
    ])
    return total


def main():
    parser = argparse.ArgumentParser(description='p6spy 기록 CSV와 웹 애플리케이션 로그 CSV를 구매 경합 확인용 엑셀 파일로 정리한다.')
    parser.add_argument('--p6spy', required=True, help='DB로그_1_p6spy기록_CSV추출.py가 만든 p6spy 기록 CSV')
    parser.add_argument('--web-csv', required=True, help='웹애플리케이션로그_1_CSV추출.py가 만든 웹 애플리케이션 로그 CSV')
    parser.add_argument('--run-id', required=True, help='정리할 구매 경합의 실행 ID. 예) run01-20261112-135853')
    parser.add_argument('--prepare-ids', required=True, help='준비 그룹이 남긴 ID 대조 CSV(준비_ID와_요청전상태.csv)')
    parser.add_argument('--output', required=True, help='만들 엑셀 파일. 같은 이름의 파일이 있으면 중단한다')
    args = parser.parse_args()

    output = Path(args.output)
    if output.exists():
        sys.exit(f'출력 파일이 이미 있습니다. 다른 이름을 지정하세요: {output}')

    p6spy = read_p6spy(Path(args.p6spy))
    web = read_web(Path(args.web_csv), args.run_id)
    requests = group_requests(p6spy, web, args.run_id, read_prepare(Path(args.prepare_ids)))
    if not requests:
        sys.exit(f'실행 ID {args.run_id}의 로그가 없습니다.')

    workbook = Workbook()
    row_of_line = build_sheet_sql(workbook, requests)
    build_sheet_times(workbook, requests)
    total = build_sheet_others(workbook, requests, row_of_line)
    output.parent.mkdir(parents=True, exist_ok=True)
    workbook.save(output)

    unnamed = sum(1 for request in requests for r in request['own'] if r.get('name', '-') == '-')
    print(f'실행 ID {args.run_id}의 요청: {len(requests)}건')
    for request in requests:
        parts = [f"{i['end']['test']['stage']} {len(i['records'])}" for i in request['intervals']]
        print(f"  {request['id']} (등록 가격 구매자 {request['buyer_no']}, {request['thread']}): p6spy 기록 {len(request['own'])}건 "
              f"[구간별 {', '.join(parts)}]")
    print(f'요청 {len(requests)}건의 p6spy 기록 합계: {sum(len(r["own"]) for r in requests)}건')
    if unnamed:
        print(f'직전에 실행되는 SQL 이름을 정하지 못한 기록: {unnamed}건(「-」로 적음)')
    print(f'시트 3에 넣은 다른 요청의 p6spy 기록: {total}행')
    print(f'저장한 파일: {output}')


if __name__ == '__main__':
    main()

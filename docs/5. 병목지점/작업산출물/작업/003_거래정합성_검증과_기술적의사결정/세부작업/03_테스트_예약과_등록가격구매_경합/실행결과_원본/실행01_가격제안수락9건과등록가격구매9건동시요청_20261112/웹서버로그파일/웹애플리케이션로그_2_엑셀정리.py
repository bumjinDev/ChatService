"""
웹애플리케이션로그_1_CSV추출.py가 만든 웹 애플리케이션 로그 CSV와 JMeter가 실행 폴더에 남긴 종료 후 확인 파일(종료후확인.log)을 읽어,
수락·결제 경합 요청 18건을 확인할 수 있게 엑셀 파일 하나로 정리한다. 18건은 판매자가 흥정 구매자 01~09의 가격 제안을 수락하는 요청 9건과
등록 가격 구매자 01~09가 등록 가격으로 결제하는 요청 9건이다.

대상은 실행 ID가 --run-id와 같은 가격 제안 수락 요청과 등록 가격 결제 요청이다.
요청은 흥정 구매자 01~09의 수락 요청, 그다음 등록 가격 구매자 01~09의 결제 요청 순서로 둔다.

시트 구성
  1_요청별로그              : 요청마다, 로깅 설계 문서 「2. 로깅 추가 위치」의 로그(수락 요청은 작업명세 04, 결제 요청은 작업명세 05)를
                              로그 종류마다 표 하나로 둔다. 표의 열은 작업명세 03 「1. 공통 로그 포맷」 순서대로 시각, 테스트명, 기능명,
                              파일명, 메소드명, 실행ID, 요청ID, 단계와 그 로그 종류의 항목이고, 값은 로그에 적힌 글자 그대로다.
                              그 아래에 같은 요청 스레드에 남은 기존 로그를 표 하나로 둔다.
                              시트 맨 끝 「실행 전체」에는 종료 후 확인 파일의 전체종료후확인 줄을 표 하나로 둔다.
  2_단계별기댓값과실제값    : 맨 위에 기대 값 계산 표와 합계 표를 둔다. 기대 값은 수락 요청과 결제 요청 18건을 커밋 순서대로 하나씩 차례로
                              처리(순차 처리)했다면 나와야 하는 값이다. 수락은 예약을, 결제는 주문과 남은 수량 차감을 앞선 요청의 결과로 쌓아 간다.
                              그 아래에 요청마다 로그 종류별 표를 두고, 행에 기대 값·실제 값·문제 여부를 둔다.
                              수량을 판단하는 로그의 표(수락 요청의 수량판단 표, 결제 요청의 남은수량판단 표와 구매가능수량판단 표)에는
                              판단 시점 정상 기대 값 행을 더한다.
  3_구간별다른요청로그      : 기준 요청의 로그 하나와, 같은 요청에서 그 바로 앞에 남은 로그 사이를 구간으로 본다.
                              구간마다 그 사이에 남은 다른 요청(수락 요청과 결제 요청 모두)의 로그(설계 문서의 서버 로그)를 시각 순서대로 표 하나에 둔다.
각 시트 아래에는 열마다 무슨 데이터인지 적은 열 설명 표를 둔다.
요청마다 엑셀의 행 그룹으로 묶어, 요청 제목 줄 왼쪽의 단추로 접었다 펼 수 있다.

시트 1의 로그 표의 시각은 로그에 적힌 글자 그대로다. 서버 로그 가운데 이번 테스트에서 추가한 로그는 메시지 맨 앞에 적힌 서버 시각이고,
종료 후 확인 파일의 줄은 JMeter가 노트북에서 그 줄을 쓴 시각이다. 기존 로그 표와 시트 2·3의 시각은 서버(미니PC)가 기록한 시각만 쓴다.
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

# 이번 테스트에서 추가한 로그(공통 로그 형식)의 메시지. 종료 후 확인 파일의 줄도 같은 형식이다.
# 2026-11-12T17:43:19.420301+09:00 - 예약–등록 가격 구매 - 가격 제안 수락 - ReservationService.java - reserve
#   | 실행ID=run01-... 요청ID=accept-01 단계=수량판단 | 상품ID=10171 제안ID=157 ...
# 종료 후 확인 파일의 메소드명 칸(수락·결제 경합 뒤 저장 상태 확인)에는 공백이 있으므로 메소드명은 「 | 실행ID=」 앞까지로 읽는다.
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
    ('[구매 차감]', '구매 차감'),
    ('[재고 차감]', '재고 차감'),
    ('[결제 성공]', '결제 성공'),
    ('[실시간 전달]', '실시간 전달'),
    ('[업무 오류]', '업무 오류'),
]

# 요청의 종류. 준비 그룹이 남긴 ID 대조 CSV의 role 열로 정한다.
ACCEPT = 'accept'      # 판매자가 흥정 구매자의 가격 제안을 수락하는 요청(작업명세 04)
BUY = 'buy'            # 등록 가격 구매자가 등록 가격으로 결제하는 요청(작업명세 05)
ROLE_OF = {ACCEPT: '흥정 구매자', BUY: '등록 가격 구매자'}

# 요청 하나에서 서버 로그에 설계 로그가 남는 순서(작업명세 04·05 「2.1 요청 하나에서 로그가 남는 순서」)
STAGE_ORDER = {
    ACCEPT: ['요청수신', '수락시작', '수량판단', '예약저장처리완료', '커밋전', '커밋완료', '롤백완료', '확정여부미확인', '요청응답'],
    BUY: ['요청수신', '결제시작', '남은수량판단', '구매가능수량판단', '잔액판단', '주문저장처리완료', '차감처리완료', '커밋전',
          '커밋완료', '롤백완료', '확정여부미확인', '요청응답'],
}
START_STAGE = {ACCEPT: '수락시작', BUY: '결제시작'}
END_STAGES = ['커밋완료', '롤백완료', '확정여부미확인']
# 수량부족으로 보는 오류 코드(세부작업기록 「2. 계획」의 응답 구분, 작업명세 05 「3.10 요청별종료후확인, 전체종료후확인」의 응답구분)
RESERVATION_SHORTAGE_CODE = 'RESERVATION_QUANTITY_EXCEEDED'   # 수락: 예약 가능 수량 부족
STOCK_SHORTAGE_CODE = 'INSUFFICIENT_STOCK'                    # 결제: 남은 수량 부족
RESERVED_CODE = 'PRODUCT_RESERVED'                            # 결제: 구매 가능 수량 부족
NOT_ON_SALE_CODE = 'PRODUCT_NOT_ON_SALE'                      # 수락·결제: 판매 종료
# 수량을 판단하는 로그(판단 시점 정상 기대 값 행을 두는 표)
JUDGE_KINDS = {ACCEPT: ['수량판단'], BUY: ['남은수량판단', '구매가능수량판단']}

# 로그 종류. 커밋완료·롤백완료·확정여부미확인은 한 종류로 본다(작업명세 04 「2. 로깅 추가 위치」의 6번, 작업명세 05의 9번, 단계 이름 세 가지).
END_KIND = '커밋완료·롤백완료·확정여부미확인'
KIND_ORDER = {
    ACCEPT: ['요청수신', '수락시작', '수량판단', '예약저장처리완료', '커밋전', END_KIND, '요청응답', '요청별종료후확인'],
    BUY: ['요청수신', '결제시작', '남은수량판단', '구매가능수량판단', '잔액판단', '주문저장처리완료', '차감처리완료', '커밋전',
          END_KIND, '요청응답', '요청별종료후확인'],
}

# 로그 종류마다 표에 둘 항목: (로그의 항목 이름, 엑셀의 열 이름). 열 이름은 「2. 로깅 추가 위치」의 항목 이름 그대로 쓴다.
# 수락 요청은 작업명세 04, 결제 요청은 작업명세 05의 항목이다. 「단계」는 항목이 아니라 그 로그의 단계 이름(공통 항목)이다.
LOG_ITEMS = {
    ACCEPT: {
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
        '요청별종료후확인': [('제안ID', '제안ID'), ('구매자ID', '구매자ID'), ('요청수량', '요청수량'), ('응답상태', '응답상태'),
                     ('오류코드', '오류코드'), ('응답구분', '응답구분'), ('제안상태', '제안상태'), ('예약ID', '예약ID'),
                     ('예약수량', '예약수량'), ('예약상태', '예약상태'), ('수락예약일치', '수락예약일치')],
    },
    BUY: {
        '요청수신': [('HTTP메소드', 'HTTP메소드'), ('경로', '경로'), ('스레드', '스레드')],
        '결제시작': [('상품ID', '상품ID'), ('구매자ID', '구매자ID'), ('요청수량', '요청수량'), ('제안ID', '제안ID')],
        '남은수량판단': [('상품ID', '상품ID'), ('구매자ID', '구매자ID'), ('요청수량', '요청수량'), ('남은수량', '남은수량'),
                   ('판단결과', '판단결과')],
        '구매가능수량판단': [('상품ID', '상품ID'), ('구매자ID', '구매자ID'), ('요청수량', '요청수량'), ('남은수량', '남은수량'),
                     ('유효예약합계', '유효예약합계'), ('구매가능수량', '구매가능수량'), ('판단기준시각', '판단기준시각'),
                     ('판단결과', '판단결과')],
        '잔액판단': [('구매자ID', '구매자ID'), ('결제금액', '결제금액'), ('잔액', '잔액'), ('판단결과', '판단결과')],
        '주문저장처리완료': [('주문ID', '주문ID'), ('상품ID', '상품ID'), ('구매자ID', '구매자ID'), ('주문수량', '주문수량'),
                     ('결제금액', '결제금액')],
        '차감처리완료': [('주문ID', '주문ID'), ('주문수량', '주문수량'), ('차감후남은수량', '차감후남은수량'), ('상품상태', '상품상태')],
        '커밋전': [('주문ID', '주문ID')],
        END_KIND: [('단계', '단계'), ('상품ID', '상품ID'), ('구매자ID', '구매자ID'), ('실패사유', '실패사유')],
        '요청응답': [('응답상태', '응답상태'), ('오류코드', '오류코드'), ('소요시간ms', '소요시간ms')],
        '요청별종료후확인': [('구매자ID', '구매자ID'), ('요청수량', '요청수량'), ('응답상태', '응답상태'), ('오류코드', '오류코드'),
                     ('응답구분', '응답구분'), ('주문ID', '주문ID'), ('주문수량', '주문수량'), ('결제금액', '결제금액'),
                     ('거래상태', '거래상태'), ('잔액', '잔액'), ('주문잔액일치', '주문잔액일치')],
    },
}
# 전체종료후확인의 항목. 세부작업기록 「2. 계획」에서 정한 항목이고, 종료 후 확인 파일에 적힌 이름 그대로 쓴다.
OVERALL_KIND = '전체종료후확인'
OVERALL_ITEMS = [('유효예약합계', '유효예약합계'), ('주문수량합계', '주문수량합계'), ('주문건수', '주문건수'), ('남은수량', '남은수량'),
                 ('상품상태', '상품상태'), ('수락성공수', '수락성공수'), ('수락수량부족수', '수락수량부족수'),
                 ('수락기타오류수', '수락기타오류수'), ('수락미확인수', '수락미확인수'), ('결제성공수', '결제성공수'),
                 ('결제수량부족수', '결제수량부족수'), ('결제기타오류수', '결제기타오류수'), ('결제미확인수', '결제미확인수'),
                 ('결과확인기준위반', '결과확인기준위반')]
REQUEST_MAX_ITEMS = max(len(items) for kinds in LOG_ITEMS.values() for items in kinds.values())   # 요청 하나의 표에서 가장 많은 항목 수(11)
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


def items_of(request_type, kind):
    """요청 종류와 로그 종류에 맞는 항목 목록. 전체종료후확인은 요청 종류와 관계없이 OVERALL_ITEMS다."""
    return OVERALL_ITEMS if kind == OVERALL_KIND else LOG_ITEMS[request_type][kind]


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
        'time_text': match.group('time'),
        'test_name': match.group('test'),
        'feature': match.group('feature'),
        'file': match.group('file'),
        'method': match.group('method'),
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
    """종료 후 확인 파일을 읽어, 실행 ID가 run_id인 요청별종료후확인(요청 ID → 줄)과 전체종료후확인 줄을 돌려준다."""
    per_request, overall = {}, None
    for number, line in enumerate(after_path.read_text(encoding='utf-8').splitlines(), start=1):
        test = parse_test_log(line)
        if not test or test['run'] != run_id:
            continue
        entry = {'line_text': f'{after_path.name} {number}번째 줄', 'message': line, 'test': test}
        if test['stage'] == '요청별종료후확인':
            per_request[test['request']] = entry
        elif test['stage'] == OVERALL_KIND:
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


def number_or_none(text):
    return int(text) if text and text.lstrip('-').isdigit() else None


def request_type_of(prep, stages):
    """요청의 종류. ID 대조 CSV의 role이 흥정 구매자이면 수락 요청, 등록 가격 구매자이면 결제 요청이다.
    role이 없으면 남은 로그의 단계(수락시작이 있으면 수락 요청)로 정한다."""
    role = prep.get('role', '')
    if role == ROLE_OF[ACCEPT]:
        return ACCEPT
    if role == ROLE_OF[BUY]:
        return BUY
    return ACCEPT if '수락시작' in stages else BUY


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
        request_type = request_type_of(prep, stages)
        requests.append({
            'id': request_id,
            'type': request_type,
            'buyer_no': prep.get('buyer_no', ''),
            'buyer_id': prep.get('buyer_id', ''),
            'conversation_id': prep.get('conversation_id', ''),
            'offer_id': prep.get('offer_id', ''),
            'offer_status': prep.get('offer_status', ''),
            'qty': number_or_none(prep.get('offer_quantity' if request_type == ACCEPT else 'purchase_quantity', '')),
            'payment': number_or_none(prep.get('payment_amount', '')),
            'balance': number_or_none(prep.get('balance', '')),
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


def buyer_label(request):
    """구매자 칸의 값. 예) 흥정 구매자 01, 등록 가격 구매자 01"""
    return f"{ROLE_OF[request['type']]} {request['buyer_no']}"


def calculate(requests, sale_qty):
    """수량을 두 가지로 계산해 요청마다 넣는다. 규칙은 코드(가격 제안 수락 처리, 예약 생성 처리, 구매 가능 수량 계산, 결제 처리,
    재고 차감)와 작업명세 04·05 「2.1 요청 하나에서 로그가 남는 순서」에서 확인한 것이다.

    기대 값(request['serial']): 커밋 순서(커밋완료·롤백완료 로그 시각의 순서, 같은 시각이면 서버 로그의 줄 순서)대로
      수락 요청과 결제 요청을 하나씩 차례로 처리했다고 놓는다.
      - 남은 수량 = 판매 수량 − 앞서 주문된 수량. 예약은 상품의 남은 수량을 줄이지 않는다.
      - 앞서 수락된 예약은 모두 만료 전인 예약(유효 예약)으로 본다.
      - 수락: 남은 수량이 0개이면 상품이 판매 종료이므로 판매 중 확인에서 거절(판매 종료)한다.
              예약 가능 수량 = 남은 수량 − 앞서 수락된 예약. 수락은 판매자 ID로 계산하고 판매자에게 걸린 예약은 없으므로 만료 전인 예약을 모두 뺀다.
              요청수량이 예약 가능 수량보다 많으면 수량판단에서 거절(예약 가능 수량 부족), 그 밖에는 통과다.
      - 결제: 남은 수량이 0개이면 판매 중 확인에서 거절(판매 종료), 요청수량이 남은 수량보다 많으면 남은수량판단에서 거절(남은 수량 부족)한다.
              구매 가능 수량 = 남은 수량 − 결제하는 회원 본인에게 걸리지 않은 유효 예약. 등록 가격 구매자에게 걸린 예약은 없으므로 앞서 수락된 예약을 모두 뺀다.
              요청수량이 구매 가능 수량보다 많으면 구매가능수량판단에서 거절(구매 가능 수량 부족)한다.
              잔액은 결제 금액과 같아 잔액판단은 통과한다.
      - 통과한 수락은 예약 수량을 다음 요청의 「앞서 수락된 예약」에, 통과한 결제는 주문 수량을 다음 요청의 「앞서 주문된 수량」에 더한다.
    판단 시점 정상 기대 값(request['at_judge'][로그 종류]): 그 요청이 판단할 때 이미 커밋된 예약과 주문을 반영한 값을 읽었다면 나와야 하는 값.
      - 수락의 수량판단: 그 수량판단 로그 시각보다 먼저 커밋완료 로그가 남은 다른 요청의 주문 수량과 예약 수량으로 계산한다.
      - 결제의 남은수량판단: 그 남은수량판단 로그 시각보다 먼저 커밋완료 로그가 남은 결제 요청의 주문 수량으로 계산한다.
      - 결제의 구매가능수량판단: 남은수량은 남은수량판단과 같은 값이다(결제 처리는 상품을 한 번 조회하고, 구매 가능 수량 계산도 그 상품의 값을 쓴다).
        유효예약합계는 그 구매가능수량판단 로그 시각보다 먼저 커밋완료 로그가 남은 수락 요청의 예약 수량으로 계산한다.
    """
    def finish(request):
        stage = end_stage(request) or '요청응답'
        record = request['stages'].get(stage)
        return (record['test']['time'], record['line']) if record else None

    def qty_of(request):
        return int(item(request, START_STAGE[request['type']], '요청수량'))

    started = [r for r in requests if START_STAGE[r['type']] in r['stages']]
    order = sorted(started, key=lambda r: (finish(r) is None, finish(r) or (datetime.max, 0)))
    ordered_sum = ordered_count = reserved_sum = reserved_count = 0
    serial = []
    for position, request in enumerate(order, start=1):
        qty = qty_of(request)
        remaining = sale_qty - ordered_sum
        available = remaining - reserved_sum
        if remaining <= 0:
            passes, reject_code = False, NOT_ON_SALE_CODE
        elif request['type'] == ACCEPT:
            passes, reject_code = (True, None) if qty <= available else (False, RESERVATION_SHORTAGE_CODE)
        elif qty > remaining:
            passes, reject_code = False, STOCK_SHORTAGE_CODE
        elif qty > available:
            passes, reject_code = False, RESERVED_CODE
        else:
            passes, reject_code = True, None
        request['serial'] = {'position': position, 'before': ordered_sum, 'before_count': ordered_count,
                             'reserved': reserved_sum, 'reserved_count': reserved_count,
                             'remaining': remaining, 'available': available, 'passes': passes, 'reject_code': reject_code,
                             'finish_time': finish(request)[0] if finish(request) else None}
        if passes and request['type'] == ACCEPT:
            reserved_sum += qty
            reserved_count += 1
        elif passes:
            ordered_sum += qty
            ordered_count += 1
        request['serial']['after'] = ordered_sum
        request['serial']['after_reserved'] = reserved_sum
        serial.append(request)

    def committed_before(moment, request_type, exclude):
        others = [o for o in started if o is not exclude and o['type'] == request_type and stage_time(o, '커밋완료')
                  and stage_time(o, '커밋완료') < moment]
        return len(others), sum(qty_of(o) for o in others)

    for request in started:
        qty = qty_of(request)
        request['at_judge'] = {}
        if request['type'] == ACCEPT:
            moment = stage_time(request, '수량판단')
            if moment is None:
                continue
            order_count, order_sum = committed_before(moment, BUY, request)
            res_count, res_sum = committed_before(moment, ACCEPT, request)
            remaining = sale_qty - order_sum
            request['at_judge']['수량판단'] = {'count': order_count, 'sum': order_sum, 'remaining': remaining,
                                             'reserved_count': res_count, 'reserved': res_sum,
                                             'available': remaining - res_sum, 'passes': qty <= remaining - res_sum}
            continue
        moment = stage_time(request, '남은수량판단')
        if moment is None:
            continue
        order_count, order_sum = committed_before(moment, BUY, request)
        remaining = sale_qty - order_sum
        request['at_judge']['남은수량판단'] = {'count': order_count, 'sum': order_sum, 'remaining': remaining,
                                           'passes': qty <= remaining}
        moment = stage_time(request, '구매가능수량판단')
        if moment is None:
            continue
        res_count, res_sum = committed_before(moment, ACCEPT, request)
        request['at_judge']['구매가능수량판단'] = {'count': order_count, 'sum': order_sum, 'remaining': remaining,
                                             'reserved_count': res_count, 'reserved': res_sum,
                                             'available': remaining - res_sum, 'passes': qty <= remaining - res_sum}
    return serial


def log_values(items, test):
    """로그 한 줄에서 그 로그 종류의 항목 값을 표의 열 순서대로 꺼낸다. 로그에 없는 항목은 「-」다."""
    values = []
    for name, _ in items:
        values.append(test['stage'] if name == '단계' else test['items'].get(name, '-'))
    return values


def extra_items(items, test):
    """로그에 남았지만 항목 목록(items)에 정하지 않은 항목 이름. 있으면 표 오른쪽에 열로 더한다."""
    known = {name for name, _ in items}
    return [name for name in test['items'] if name not in known]


# 시트 1의 로그 표 앞부분. 작업명세 03 「1. 공통 로그 포맷」의 「시각 - 테스트명 - 기능명 - 파일명 - 메소드명 | 실행ID=… 요청ID=… 단계=…」 순서와
# 이름(작업명세 04·05 「2. 로깅 추가 위치」의 공통 항목 이름) 그대로 둔다. 그 뒤에 「| 테스트별 확인 항목」을 로그 종류별 항목 열로 둔다.
FORMAT_COLUMNS = ['시각', '테스트명', '기능명', '파일명', '메소드명', '실행ID', '요청ID', '단계']
SHEET1_TITLE_WIDTH = len(FORMAT_COLUMNS) + REQUEST_MAX_ITEMS   # 요청 제목 줄을 칠할 열 수. 항목이 가장 많은 요청별종료후확인(11개)까지 덮는다
SHEET1_OVERALL_WIDTH = len(FORMAT_COLUMNS) + len(OVERALL_ITEMS)  # 실행 전체 제목 줄을 칠할 열 수. 전체종료후확인(14개)까지 덮는다


def format_values(test):
    """로그 한 줄의 공통 로그 포맷 부분을 FORMAT_COLUMNS 순서대로, 로그에 적힌 글자 그대로 꺼낸다."""
    return [test['time_text'], test['test_name'], test['feature'], test['file'], test['method'],
            test['run'], test['request'], test['stage']]


def sheet1_items(items):
    """시트 1의 항목 열. 「단계」는 공통 로그 포맷 부분에 있으므로 항목 열에서 뺀다."""
    return [(name, column) for name, column in items if name != '단계']


# ---------------------------------------------------------------------
# 엑셀 쓰기 도구
# ---------------------------------------------------------------------
def time_us(moment):
    """마이크로초까지의 시:분:초. 예) 17:43:19.420301"""
    return moment.strftime('%H:%M:%S.%f') if moment else '-'


def time_ms(moment):
    """밀리초까지의 시:분:초. 예) 17:43:19.420"""
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

    로그에서 읽은 숫자 문자열(예: 「10171」)은 숫자로 바꿔 넣는다. 엑셀이 「텍스트로 저장된 숫자」 표시를 붙이지 않게 하려는 것이다.
    앞에 0이 붙은 값(구매자 번호 「01」 등)은 숫자로 넣고 표시 형식으로 자릿수를 맞춰, 화면에는 「01」로 보이게 한다.
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
    if request['type'] == ACCEPT:
        text = (f"판매자가 흥정 구매자 {request['buyer_no']}의 가격 제안을 수락하는 요청 · 요청 ID {request['id']}"
                f" · 흥정 구매자 ID {request['buyer_id']} · 제안 ID {request['offer_id']} · 대화 ID {request['conversation_id']}")
    else:
        text = (f"등록 가격 구매자 {request['buyer_no']}의 결제 요청 · 요청 ID {request['id']} · 등록 가격 구매자 ID {request['buyer_id']}"
                f" · 대화 ID {request['conversation_id']}")
    return text + (f" · 서버 스레드 {request['thread']}" if thread else '')


OVERALL_TITLE = '실행 전체 · 가격 제안 수락 요청 9건과 등록 가격 결제 요청 9건이 모두 끝난 뒤 JMeter가 쓴 전체종료후확인 줄'


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

    def log_table(row, test, request_type):
        """로그 한 줄을 공통 로그 포맷 순서의 표 하나로 쓴다. 열은 공통 로그 포맷 부분과 그 로그 종류의 항목이다."""
        kind = kind_of(test['stage'])
        all_items = items_of(request_type, kind)
        extras = extra_items(all_items, test)
        items = sheet1_items(all_items)
        headers = FORMAT_COLUMNS + [column for _, column in items] + extras
        values = format_values(test) + [test['items'].get(name, '-') for name, _ in items] + [test['items'][name] for name in extras]
        return write_simple_table(sheet, row, test['stage'], headers, [values])

    for request in requests:
        title_row = row
        put_request_title(sheet, row, request_title(request), SHEET1_TITLE_WIDTH)
        row += 1
        sequence_of = {record['line']: index for index, record in enumerate(request['records'], start=1)}
        designed_lines = {r['line'] for r in request['designed']}

        # 로깅 설계 문서의 로그: 로그 종류마다 표 하나
        for record in request['designed']:
            row_of_line[record['line']] = row + 2
            row = log_table(row, record['test'], request['type'])
        if request['after']:
            row = log_table(row, request['after']['test'], request['type'])

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
        put_request_title(sheet, row, OVERALL_TITLE, SHEET1_OVERALL_WIDTH)
        row += 1
        row = log_table(row, overall['test'], None)
        group_rows(sheet, title_row + 1, row - 1)

    set_widths(sheet, [34, 26, 14, 22, 28, 22, 10, 16] + [17] * len(OVERALL_ITEMS))
    write_notes(sheet, row, [
        ('요청 제목 줄', '가격 제안 수락 요청은 흥정 구매자 번호, 요청 ID, 흥정 구매자 ID, 제안 ID, 대화 ID, 그 요청을 처리한 서버 스레드. '
                     '등록 가격 결제 요청은 등록 가격 구매자 번호, 요청 ID, 등록 가격 구매자 ID, 대화 ID, 그 요청을 처리한 서버 스레드. '
                     '요청 ID는 JMeter가 수락 요청과 결제 요청 헤더에 담은 ID이고, 구매자 ID, 제안 ID, 대화 ID는 준비 그룹이 남긴 ID 대조 CSV에서 찾은 값이다. '
                     '흥정 구매자 01~09의 수락 요청을 먼저, 등록 가격 구매자 01~09의 결제 요청을 그다음에 두었다'),
        ('로그 종류별 표', '로깅 설계 문서 「2. 로깅 추가 위치」에 정한 로그. 수락 요청은 작업명세 04, 결제 요청은 작업명세 05의 로그다. 표 하나가 로그 한 줄이다. '
                     '열은 작업명세 03 「1. 공통 로그 포맷」의 순서대로 시각, 테스트명, 기능명, 파일명, 메소드명, 실행ID, 요청ID, 단계를 두고, 그 뒤에 그 로그 종류가 기록하는 항목을 둔다. '
                     '값은 로그에 적힌 글자 그대로이고, 열 이름은 설계 문서의 이름 그대로다. 표는 서버 로그에 남은 순서대로 두었고, 요청별종료후확인 표를 맨 뒤에 두었다'),
        ('시각', f'로그 메시지 맨 앞에 적힌 시각. 요청별종료후확인과 전체종료후확인은 JMeter가 노트북에서 {after_name}에 그 줄을 쓴 시각이다'),
        ('기존 로그 표의 원본 줄 번호', '서버 로그 파일(chatservice.log)에서 그 로그가 시작되는 줄 번호'),
        ('기존 로그 표의 로그 순번', '그 요청의 서버 로그(로깅 설계 문서의 로그와 기존 로그) 가운데 몇 번째로 남은 로그인지'),
        ('기존 로그 표의 서버 시각', '서버 로그 줄 맨 앞의 시각(1/1,000초까지)'),
        ('판단기준시각·예약시각·만료시각', '로그에 적힌 값 그대로'),
        ('기존 로그 표', '테스트 전부터 코드에 있던 로그(로그인 확인, 가격 제안 응답, 예약 걸기, 구매 차감, 재고 차감, 결제 성공, 실시간 전달 등). '
                    '같은 요청 스레드에서 요청수신 로그부터 요청응답 로그 사이에 남은 것이다'),
        ('기능 구분', '기존 로그의 메시지 앞부분으로 정한 이름'),
        ('직전 설계 로그', '그 기존 로그 바로 앞에 같은 요청 스레드에서 남은 로깅 설계 문서 로그의 단계. 기존 로그가 요청 처리의 어디에서 남았는지 알려 준다'),
        ('실행 전체', '가격 제안 수락 요청 9건과 등록 가격 결제 요청 9건이 모두 끝난 뒤 JMeter가 쓴 전체종료후확인 줄. 요청 하나가 아니라 실행 전체의 값이다. '
                  '항목은 세부작업기록 「2. 계획」에서 정한 전체종료후확인의 항목이다'),
        ('「-」', '그 로그에 해당하지 않는 칸'),
    ])
    return row_of_line


# ---------------------------------------------------------------------
# 시트 2: 단계별 기대 값과 실제 값
# ---------------------------------------------------------------------
def expected_rows(request, kind, product_id, seller_id):
    """로그 종류 하나의 기대 값을 표의 열 순서대로 돌려준다.

    None은 기대 값을 정하지 않은 칸(스레드, 시각, 예약 ID, 주문 ID 등)이다. 화면에는 「-」로 적고 문제 여부는 따지지 않는다.
    문자열 「-」는 값이 없어야 하는 칸(제안ID, 오류코드, 실패사유)이다. 실제 값과 비교한다.
    순차 처리에서 그 로그가 남지 않아야 하면 모든 칸을 「기록 없음」으로 돌려준다.
    """
    if request['type'] == ACCEPT:
        return expected_accept(request, kind, product_id, seller_id)
    return expected_buy(request, kind, product_id)


def expected_accept(request, kind, product_id, seller_id):
    """가격 제안 수락 요청의 기대 값(작업명세 04 「2. 로깅 추가 위치」의 항목 순서)."""
    serial = request.get('serial')
    passes = serial['passes'] if serial else None
    qty = request['qty']
    offer_id = request['offer_id']
    buyer_id = request['buyer_id']
    saved_id = item(request, '예약저장처리완료', '예약ID')
    columns = len(LOG_ITEMS[ACCEPT][kind])
    if kind == '요청수신':
        return ['POST', f'/ChatService/api/offers/{offer_id}/accept', None]
    if kind == '수락시작':
        return [product_id, offer_id, request['conversation_id'], seller_id, buyer_id, qty, request['offer_status']]
    if not serial:
        return [None] * columns
    code = serial['reject_code']
    if kind == '수량판단':
        if code == NOT_ON_SALE_CODE:
            return [NO_RECORD] * columns
        return [product_id, offer_id, buyer_id, qty, serial['remaining'], serial['reserved'], serial['available'], None,
                '통과' if passes else '거절']
    if kind == '예약저장처리완료':
        return [None, offer_id, product_id, buyer_id, qty, None, None] if passes else [NO_RECORD] * columns
    if kind == '커밋전':
        return [offer_id, 'ACCEPTED', saved_id or None, qty] if passes else [NO_RECORD] * columns
    if kind == END_KIND:
        return ['커밋완료', offer_id, '-'] if passes else ['롤백완료', offer_id, code]
    if kind == '요청응답':
        return ['200', '-', None] if passes else ['409', code, None]
    if kind == '요청별종료후확인':
        if passes:
            return [offer_id, buyer_id, qty, '200', '-', '성공', 'ACCEPTED', saved_id or None, qty, 'ACTIVE', '일치']
        return [offer_id, buyer_id, qty, '409', code, '수량부족', request['offer_status'], '-', '-', '-', '일치']
    return [None] * columns


def expected_buy(request, kind, product_id):
    """등록 가격 결제 요청의 기대 값(작업명세 05 「2. 로깅 추가 위치」의 항목 순서)."""
    serial = request.get('serial')
    passes = serial['passes'] if serial else None
    qty = request['qty']
    pay = request['payment']
    balance = request['balance']
    buyer_id = request['buyer_id']
    saved_id = item(request, '주문저장처리완료', '주문ID')
    columns = len(LOG_ITEMS[BUY][kind])
    if kind == '요청수신':
        return ['POST', '/ChatService/api/orders', None]
    if kind == '결제시작':
        return [product_id, buyer_id, qty, '-']
    if not serial:
        return [None] * columns
    remaining = serial['remaining']
    code = serial['reject_code']
    if kind == '남은수량판단':
        if code == NOT_ON_SALE_CODE:
            return [NO_RECORD] * columns
        return [product_id, buyer_id, qty, remaining, '거절' if code == STOCK_SHORTAGE_CODE else '통과']
    if kind == '구매가능수량판단':
        if code in (NOT_ON_SALE_CODE, STOCK_SHORTAGE_CODE):
            return [NO_RECORD] * columns
        return [product_id, buyer_id, qty, remaining, serial['reserved'], serial['available'], None,
                '거절' if code == RESERVED_CODE else '통과']
    if kind == '잔액판단':
        return [buyer_id, pay, balance, '통과'] if passes else [NO_RECORD] * columns
    if kind == '주문저장처리완료':
        return [None, product_id, buyer_id, qty, pay] if passes else [NO_RECORD] * columns
    if kind == '차감처리완료':
        after = remaining - qty
        return [saved_id or None, qty, after, 'SOLD' if after == 0 else 'ON_SALE'] if passes else [NO_RECORD] * columns
    if kind == '커밋전':
        return [saved_id or None] if passes else [NO_RECORD] * columns
    if kind == END_KIND:
        return ['커밋완료', product_id, buyer_id, '-'] if passes else ['롤백완료', product_id, buyer_id, code]
    if kind == '요청응답':
        return ['201', '-', None] if passes else ['409', code, None]
    if kind == '요청별종료후확인':
        if passes:
            return [buyer_id, qty, '201', '-', '성공', saved_id or None, qty, pay, 'IN_PROGRESS', balance - pay, '일치']
        return [buyer_id, qty, '409', code, '수량부족', '-', '-', '-', '-', balance, '일치']
    return [None] * columns


def actual_values(request, kind):
    """로그 종류 하나의 실제 값과, 그 로그의 제목 정보(서버 시각, 원본 줄 번호)를 돌려준다. 로그가 없으면 모든 칸이 「기록 없음」이다."""
    items = items_of(request['type'], kind)
    if kind == '요청별종료후확인':
        after = request['after']
        if not after:
            return [NO_RECORD] * len(items), f'{kind} · 로그 없음'
        return log_values(items, after['test']), f"{kind} · {after['line_text']}"
    stage = end_stage(request) if kind == END_KIND else kind
    record = request['stages'].get(stage) if stage else None
    if not record:
        return [NO_RECORD] * len(items), f'{kind} · 로그 없음'
    return (log_values(items, record['test']),
            f"{stage} · 서버 시각 {time_us(record['test']['time'])} · 원본 줄 번호 {record['line']}")


# 순차 처리에서 요청이 거절되는 곳. 거절된 요청은 이 확인 뒤의 로그를 남기지 않는다.
REJECT_POINT = {NOT_ON_SALE_CODE: '판매 중 확인', RESERVATION_SHORTAGE_CODE: '수량판단', STOCK_SHORTAGE_CODE: '남은수량판단',
                RESERVED_CODE: '구매가능수량판단'}


def explain(request, kind, columns, expected, actual, problems):
    """문제 여부가 「있음」인 칸마다 설명 문장을 만든다."""
    serial = request.get('serial')
    passes = serial['passes'] if serial else None
    qty = request['qty']
    accept = request['type'] == ACCEPT
    # 로그가 남았는지부터 다른 경우: 표 전체에 문장 하나
    if all(value == NO_RECORD for value in expected) and any(value != NO_RECORD for value in actual):
        where = REJECT_POINT.get(serial['reject_code'], '판단') if serial else '판단'
        return [f'순차 처리에서 이 요청은 {where}에서 거절되므로 이 로그가 없어야 하지만, 로그가 남았다.']
    if all(value == NO_RECORD for value in actual) and any(value not in (NO_RECORD, None) for value in expected):
        return [f"순차 처리였다면 {expected[0] if kind == END_KIND else kind} 로그가 남아야 하지만, 로그가 없다."]

    sentences = []
    for index, column in enumerate(columns):
        if not problems[index]:
            continue
        e, a = expected[index], actual[index]
        sentence = f'{column}: 기대 값 「{e}」, 실제 값 「{a}」.'
        if kind in JUDGE_KINDS[request['type']] and serial:
            committed = request.get('at_judge', {}).get(kind) or {'count': 0, 'sum': 0, 'reserved_count': 0, 'reserved': 0}
            available_name = '예약가능수량' if accept else '구매가능수량'
            if column == '남은수량':
                when = '수량판단' if accept else '남은수량판단'
                sentence = (f"{column}: 커밋 순서로 이 요청보다 앞서 주문된 결제 요청이 {serial['before_count']}건(주문 {serial['before']}개) 있다. "
                            f"순차 처리였다면 {serial['remaining']}개여야 하지만 {a}개로 남았다. "
                            f"이 요청의 {when} 로그보다 먼저 커밋완료 로그가 남은 결제 요청은 {committed['count']}건(주문 {committed['sum']}개)이다.")
            elif column == '유효예약합계':
                sentence = (f"{column}: 커밋 순서로 이 요청보다 앞서 수락된 요청이 {serial['reserved_count']}건(예약 {serial['reserved']}개) 있다. "
                            f"순차 처리였다면 {serial['reserved']}개여야 하지만 {a}개로 남았다. "
                            f"이 요청의 {kind} 로그보다 먼저 커밋완료 로그가 남은 수락 요청은 "
                            f"{committed.get('reserved_count', 0)}건(예약 {committed.get('reserved', 0)}개)이다.")
            elif column == available_name and str(a).lstrip('-').isdigit():
                diff = int(a) - serial['available']
                sentence = (f"{column}: 남은수량 {serial['remaining']}개에서 앞서 수락된 예약 {serial['reserved']}개를 빼면 "
                            f"{serial['available']}개여야 하지만, {a}개로 남아 {abs(diff)}개 {'많다' if diff > 0 else '적다'}.")
            elif column == '판단결과':
                limit_name, limit = (('남은수량', serial['remaining']) if kind == '남은수량판단'
                                     else (available_name, serial['available']))
                if e == '통과':
                    sentence = f"{column}: {limit_name} {limit}개 안에 요청수량 {qty}개가 들어가므로 통과여야 하지만, {a}로 남았다."
                else:
                    sentence = f"{column}: {limit_name} {limit}개보다 요청수량 {qty}개가 많으므로 거절이어야 하지만, {a}로 남았다."
        elif kind == '차감처리완료' and serial and column == '차감후남은수량':
            sentence = (f"{column}: 순차 처리였다면 남은수량 {serial['remaining']}개에서 주문수량 {qty}개를 빼 {e}개여야 하지만, "
                        f"{a}개로 남았다.")
        elif kind == '차감처리완료' and serial and column == '상품상태':
            sentence = f"{column}: 순차 처리였다면 차감 뒤 남은 수량이 {serial['remaining'] - qty}개이므로 {e}여야 하지만, {a}로 남았다."
        elif kind == END_KIND and column == '단계':
            if passes:
                sentence = f'{column}: 순차 처리에서 통과하는 요청은 커밋되어야 하지만, 실제로 남은 단계는 {a}이다.'
            else:
                undone = '수락과 예약이' if accept else '주문 저장과 차감이'
                sentence = f'{column}: 순차 처리에서 거절되는 요청은 롤백되어 {undone} 함께 취소되어야 하지만, 실제로 남은 단계는 {a}이다.'
        elif kind == '요청응답' and column == '응답상태':
            sentence = (f"{column}: 순차 처리에서 {'통과하는' if passes else '거절되는'} 요청이다. "
                        f"기대 값 「{e}」, 실제 값 「{a}」.")
        elif kind == '요청응답' and column == '오류코드' and not passes and a == '-':
            sentence = f'{column}: 순차 처리에서 거절되는 요청은 오류코드 「{e}」로 응답해야 하지만, 오류코드가 없다.'
        sentences.append(sentence)
    return sentences


def explain_overall(columns, expected, actual, problems, sale_qty, serial_reserved, serial_ordered):
    sentences = []
    actual_of = dict(zip(columns, actual))
    for index, column in enumerate(columns):
        if not problems[index]:
            continue
        e, a = expected[index], actual[index]
        if column == '유효예약합계':
            sentence = f'{column}: 순차 처리였다면 유효 예약 합계는 {e}개여야 하지만, {a}개로 남았다.'
        elif column == '주문수량합계':
            sentence = f'{column}: 순차 처리였다면 주문 수량 합계는 {e}개여야 하지만, {a}개로 남았다.'
            reserved = actual_of.get('유효예약합계', '')
            if str(a).isdigit() and str(reserved).isdigit() and int(a) + int(reserved) > sale_qty:
                sentence += (f' 유효예약합계 {reserved}개와 더하면 {int(a) + int(reserved)}개로, '
                             f'판매 수량 {sale_qty}개를 {int(a) + int(reserved) - sale_qty}개 넘었다.')
        elif column == '주문건수':
            sentence = f'{column}: 순차 처리였다면 {e}건이 주문되어야 하지만, {a}건이다.'
        elif column == '남은수량':
            sentence = (f'{column}: 순차 처리였다면 판매 수량 {sale_qty}개에서 주문 수량 합계 {serial_ordered}개를 뺀 {e}개여야 하지만, '
                        f'{a}개로 남았다.')
        elif column == '상품상태':
            sentence = f'{column}: 순차 처리였다면 남은 수량이 {sale_qty - serial_ordered}개이므로 {e}여야 하지만, {a}로 남았다.'
        elif column in ('수락성공수', '결제성공수'):
            sentence = f'{column}: 순차 처리였다면 {e}건이 성공해야 하지만, {a}건이 성공했다.'
        elif column in ('수락수량부족수', '결제수량부족수'):
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


# 시트 2의 열 너비. 설명 칸의 줄 수를 어림해 행 높이를 정할 때도 쓴다. 구분 열과 전체종료후확인의 항목 14개까지 둔다.
SHEET2_WIDTHS = [24] + [16] * len(OVERALL_ITEMS)
SHEET2_TITLE_WIDTH = 1 + REQUEST_MAX_ITEMS           # 요청 제목 줄을 칠할 열 수
SHEET2_OVERALL_WIDTH = 1 + len(OVERALL_ITEMS)        # 실행 전체 제목 줄을 칠할 열 수
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
        if not separator or name not in columns + [column for _, column in OVERALL_ITEMS]:
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


def normal_row(request, kind):
    """판단 시점 정상 기대 값 행. 수량을 판단하는 로그의 표에만 두고, 그 밖의 표는 None이다."""
    committed = request.get('at_judge', {}).get(kind)
    if kind not in JUDGE_KINDS[request['type']] or not committed:
        return None
    result = '통과' if committed['passes'] else '거절'
    if kind == '수량판단':
        return [None] * 4 + [committed['remaining'], committed['reserved'], committed['available'], None, result]
    if kind == '남은수량판단':
        return [None] * 3 + [committed['remaining'], result]
    return [None] * 3 + [committed['remaining'], committed['reserved'], committed['available'], None, result]


def build_sheet_checks(workbook, requests, serial, overall, summary, seller_id, after_name):
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
        accept = request['type'] == ACCEPT
        serial_rows.append([s['position'], request['id'], buyer_label(request), int(item(request, START_STAGE[request['type']], '요청수량')),
                            time_us(s['finish_time']), s['before'], s['reserved'], s['remaining'],
                            s['available'] if accept else '-', '-' if accept else s['available'],
                            '통과' if s['passes'] else '거절', s['after'], s['after_reserved']])
    accept_count = sum(1 for r in serial if r['type'] == ACCEPT)
    row = write_simple_table(sheet, row, f'기대 값 계산 표(가격 제안 수락 요청 {accept_count}건과 등록 가격 결제 요청 {len(serial) - accept_count}건을 '
                                         f'커밋 순서대로 순차 처리)',
                             ['커밋 순서', '요청 ID', '구매자', '요청수량', '커밋완료 시각', '앞서 주문된 수량(개)', '앞서 수락된 예약(개)',
                              '남은수량', '예약가능수량', '구매가능수량', '판단결과', '이 요청까지 주문된 수량(개)', '이 요청까지 수락된 예약(개)'],
                             serial_rows)
    committed = [r for r in requests if end_stage(r) == '커밋완료']
    actual_reserved = sum(int(item(r, '예약저장처리완료', '예약수량')) for r in committed
                          if r['type'] == ACCEPT and '예약저장처리완료' in r['stages'])
    actual_ordered = sum(int(item(r, '주문저장처리완료', '주문수량')) for r in committed
                         if r['type'] == BUY and '주문저장처리완료' in r['stages'])
    serial_reserved = serial[-1]['serial']['after_reserved'] if serial else 0
    serial_ordered = serial[-1]['serial']['after'] if serial else 0
    rejected = [r for r in serial if not r['serial']['passes']]
    rejected_qty = sum(int(item(r, START_STAGE[r['type']], '요청수량')) for r in rejected)
    row = write_simple_table(sheet, row, '합계 표', ['구분', '값', '계산 방법'], [
        ['판매 수량(개)', sale_qty, '준비 그룹이 확인한 상품의 남은 수량(준비_요청전상태_요약.csv)'],
        ['실제 커밋된 예약 합계(개)', actual_reserved, '커밋완료 로그가 남은 수락 요청의 예약저장처리완료 로그의 예약수량을 더한 값'],
        ['실제 커밋된 주문 수량 합계(개)', actual_ordered, '커밋완료 로그가 남은 결제 요청의 주문저장처리완료 로그의 주문수량을 더한 값'],
        ['순차 처리 시 수락되는 예약 합계(개)', serial_reserved, '기대 값 계산 표의 마지막 행의 「이 요청까지 수락된 예약(개)」'],
        ['순차 처리 시 주문되는 수량 합계(개)', serial_ordered, '기대 값 계산 표의 마지막 행의 「이 요청까지 주문된 수량(개)」'],
        ['순차 처리 시 거절되는 요청', f'{len(rejected)}건, {rejected_qty}개', '기대 값 계산 표에서 판단결과가 「거절」인 요청의 건수와 수량 합계'],
        ['판매 수량을 넘은 예약과 주문(개)', actual_reserved + actual_ordered - sale_qty,
         '실제 커밋된 예약 합계 + 실제 커밋된 주문 수량 합계 − 판매 수량'],
    ], long_text_col=2)

    # 요청마다 로그 종류별 표
    problem_of = {}
    for request in requests:
        title_row = row
        put_request_title(sheet, row, request_title(request, thread=False), SHEET2_TITLE_WIDTH)
        row += 1
        found = False
        for kind in KIND_ORDER[request['type']]:
            columns = [column for _, column in items_of(request['type'], kind)]
            expected = expected_rows(request, kind, product_id, seller_id)
            actual, title = actual_values(request, kind)
            row, problem = write_check_table(
                sheet, row, title, columns, expected, normal_row(request, kind), actual,
                lambda problems, k=kind, c=columns, e=expected, a=actual: explain(request, k, c, e, a, problems))
            found = found or problem
        problem_of[request['id']] = found
        group_rows(sheet, title_row + 1, row - 1)

    # 시트 맨 끝: 실행 전체
    if overall:
        title_row = row
        put_request_title(sheet, row, OVERALL_TITLE, SHEET2_OVERALL_WIDTH)
        row += 1
        columns = [column for _, column in OVERALL_ITEMS]
        left = sale_qty - serial_ordered
        accepts = [r for r in serial if r['type'] == ACCEPT]
        buys = [r for r in serial if r['type'] == BUY]
        accept_pass = sum(1 for r in accepts if r['serial']['passes'])
        buy_pass = sum(1 for r in buys if r['serial']['passes'])
        expected = [serial_reserved, serial_ordered, buy_pass, left, 'SOLD' if left == 0 else 'ON_SALE',
                    accept_pass, len(accepts) - accept_pass, '0', '0', buy_pass, len(buys) - buy_pass, '0', '0', '없음']
        actual = log_values(OVERALL_ITEMS, overall['test'])
        row, _ = write_check_table(sheet, row, f"{OVERALL_KIND} · {overall['line_text']}", columns, expected, None, actual,
                                   lambda problems: explain_overall(columns, expected, actual, problems, sale_qty,
                                                                    serial_reserved, serial_ordered))
        group_rows(sheet, title_row + 1, row - 1)

    set_widths(sheet, SHEET2_WIDTHS)
    write_notes(sheet, row, [
        ('기대 값 계산 표', '커밋완료 시각의 순서대로 가격 제안 수락 요청과 등록 가격 결제 요청을 하나씩 처리(순차 처리)했다고 놓고 계산했다. '
                      '커밋완료 시각이 같으면 서버 로그 파일(chatservice.log)의 줄 순서를 따랐다. '
                      '통과한 수락 요청의 요청수량만 다음 요청의 「앞서 수락된 예약(개)」에, 통과한 결제 요청의 요청수량만 다음 요청의 「앞서 주문된 수량(개)」에 더했다'),
        ('구매자', '수락 요청은 가격 제안을 수락받는 흥정 구매자, 결제 요청은 결제하는 등록 가격 구매자'),
        ('남은수량', '판매 수량 − 앞서 주문된 수량(개). 예약은 상품의 남은 수량을 줄이지 않으므로 앞서 수락된 예약은 빼지 않았다'),
        ('앞서 주문된 수량(개)·앞서 수락된 예약(개)', '커밋 순서로 이 요청보다 앞서 통과한 결제 요청의 주문 수량 합계와, 앞서 통과한 수락 요청의 예약 수량 합계. '
                                          '앞서 수락된 예약은 모두 만료 전인 유효 예약으로 보았다(예약 기간 20분)'),
        ('예약가능수량', '수락 요청의 행에만 적었다. 남은수량 − 앞서 수락된 예약(개). 수락의 예약 생성은 판매자 ID로 계산하고 판매자에게 걸린 예약은 없으므로, 만료 전인 예약을 모두 뺀다'),
        ('구매가능수량', '결제 요청의 행에만 적었다. 남은수량 − 앞서 수락된 예약(개). 결제는 결제하는 회원 본인에게 걸린 예약만 빼지 않는데, 등록 가격 구매자에게 걸린 예약은 없으므로 앞서 수락된 예약을 모두 뺀다'),
        ('판단결과(수락 요청)', '남은수량이 0개이면 상품이 판매 종료이므로 거절, 요청수량이 예약가능수량보다 많으면 거절, 그 밖에는 통과로 보았다'),
        ('판단결과(결제 요청)', '남은수량이 0개이면 상품이 판매 종료이므로 거절, 요청수량이 남은수량보다 많으면 거절, 요청수량이 구매가능수량보다 많으면 거절, '
                          '그 밖에는 통과로 보았다. 등록 가격 구매자의 잔액은 결제 금액과 같으므로 잔액판단은 통과로 보았다'),
        ('합계 표', '판매 수량, 실제로 커밋된 예약 합계와 주문 수량 합계, 순차 처리였다면 수락되는 예약 합계와 주문되는 수량 합계, '
                 '순차 처리였다면 거절되는 요청, 실제로 커밋된 예약과 주문이 판매 수량을 넘은 수량'),
        ('요청 제목 줄', '수락 요청은 흥정 구매자 번호, 요청 ID, 흥정 구매자 ID, 제안 ID, 대화 ID. 결제 요청은 등록 가격 구매자 번호, 요청 ID, 등록 가격 구매자 ID, 대화 ID'),
        ('로그 종류별 표의 제목', f'로그 종류, 그 로그가 남은 서버 시각과 서버 로그 파일(chatservice.log)에서 그 로그가 시작되는 줄 번호. 요청별종료후확인과 전체종료후확인은 {after_name}의 몇 번째 줄인지 적었다'),
        ('기대 값', '순차 처리였다면 나와야 하는 값. 요청수신의 경로와 수락시작·결제시작의 값은 준비 그룹이 남긴 ID 대조 CSV(준비_ID와_요청전상태.csv)의 상품·제안·대화·구매자·수량 값이고, '
                 '수락시작의 판매자ID는 실행할 때 받은 판매자 ID다. 수량을 판단하는 로그(수락 요청의 수량판단, 결제 요청의 남은수량판단과 구매가능수량판단) 이후의 값은 '
                 '기대 값 계산 표에서 정한 통과·거절에 따른 값이다. 잔액은 준비 그룹이 요청 전에 조회한 잔액(결제 금액과 같다)이다'),
        ('기대 값의 「-」', '문제 여부도 「-」인 칸은 기대 값을 정하지 않은 항목(스레드, 판단기준시각·예약시각·만료시각, 서버가 정하는 예약ID·주문ID, 소요시간ms)이다. '
                       '문제 여부가 「없음」이나 「있음」인 칸은 값이 없어야 하는 항목(제안ID, 오류코드, 실패사유 등)이다'),
        ('기록 없음', '기대 값에서는 순차 처리였다면 그 로그가 남지 않아야 한다는 뜻이고, 실제 값에서는 그 로그가 남지 않았다는 뜻이다'),
        ('판단 시점 정상 기대 값', '그 요청이 수량을 판단할 때 이미 커밋된 예약과 주문을 반영한 값을 읽었다면 나와야 하는 값. '
                          '수락 요청의 수량판단 표와 결제 요청의 남은수량판단 표, 구매가능수량판단 표에만 있고, 문제 여부를 정하는 데는 쓰지 않는다. '
                          '그 요청이 상품과 예약을 조회한 SQL의 실제 시각은 DB 로그를 정리할 때 확인한다'),
        ('판단 시점 정상 기대 값(수락 요청)', '그 요청의 수량판단 로그보다 먼저 커밋완료 로그가 남은 결제 요청의 주문 수량을 판매 수량에서 빼 남은수량을, '
                                    '먼저 커밋완료 로그가 남은 다른 수락 요청의 예약 수량을 더해 유효예약합계를 계산했다'),
        ('판단 시점 정상 기대 값(결제 요청)', '남은수량은 그 요청의 남은수량판단 로그보다 먼저 커밋완료 로그가 남은 다른 결제 요청의 주문 수량을 판매 수량에서 빼 계산했다. '
                                    '구매가능수량판단 표의 남은수량도 같은 값이다(결제 처리는 상품을 한 번 조회하고, 구매 가능 수량 계산도 그 상품의 값을 쓴다). '
                                    '유효예약합계는 그 요청의 구매가능수량판단 로그보다 먼저 커밋완료 로그가 남은 수락 요청의 예약 수량을 더해 계산했다'),
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


def item_pairs(items, test):
    """시트 3의 「로그 항목」 칸들. 그 로그에 남은 항목을 항목 목록(items)의 순서대로 (열 이름, 값)으로 돌려준다.
    순서와 이름이 시트 1의 그 로그 종류 표와 같으므로, 같은 종류의 로그는 같은 항목이 같은 칸에 놓인다."""
    pairs = [(column, test['items'][name]) for name, column in items
             if name != '단계' and name in test['items']]
    return pairs + [(name, test['items'][name]) for name in extra_items(items, test)]


ITEM_NAME_FONT = InlineFont(sz=9, color='808080')
ITEM_VALUE_FONT = InlineFont(b=True, sz=11)


def item_cell_value(name, value):
    """항목 칸 하나의 값. 항목 이름은 작은 회색 글씨, 값은 굵은 글씨로 한 칸에 적는다."""
    return CellRichText(TextBlock(ITEM_NAME_FONT, f'{name} '), TextBlock(ITEM_VALUE_FONT, str(value)))


def build_sheet_intervals(workbook, requests, row_of_line, after_name):
    """3_구간별다른요청로그 시트를 쓴다.

    기준 요청의 로그 하나와, 같은 요청에서 그 바로 앞에 남은 로그 사이를 구간으로 본다(요청수신은 구간이 없다).
    구간마다 그 사이(두 로그의 시각 포함)에 남은 다른 요청(수락 요청과 결제 요청 모두)의 로깅 설계 문서 로그를 시각 순서대로 표 하나에 둔다.
    로그 항목은 항목마다 칸 하나에 적는다. 머리글 「로그 항목」은 그 칸들 위에 하나로 합친다.
    """
    sheet = workbook.create_sheet('3_구간별다른요청로그')
    sheet.sheet_properties.outlinePr.summaryBelow = False
    row = 1
    request_of = {r['id']: r for r in requests}
    all_logs = sorted((record for request in requests for record in request['designed']),
                      key=lambda record: (record['test']['time'], record['line']))

    def pairs_of(record):
        request_type = request_of[record['test']['request']]['type']
        return item_pairs(items_of(request_type, kind_of(record['test']['stage'])), record['test'])

    fixed_headers = ['구간 안 순번', '다른 요청 ID', '구매자', '로그 시각', '로그 종류', '원본 줄 번호']
    first_item_col = len(fixed_headers) + 1
    item_count = max(len(pairs_of(r)) for r in all_logs)
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
                other = request_of[test['request']]
                put_values(sheet, row, [number, test['request'], buyer_label(other), time_us(test['time']),
                                        test['stage'], record['line']])
                pairs = pairs_of(record)
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

    set_widths(sheet, [12, 12, 17, 16, 17, 12] + [min(round(w), 48) for w in item_widths])
    write_notes(sheet, row, [
        ('기준 요청 제목 줄', '구간을 나누는 기준이 되는 요청. 시트 1의 요청 제목 줄과 같다'),
        ('구간', '기준 요청의 로그 하나와, 같은 요청에서 그 바로 앞에 남은 로그 사이. 요청수신은 바로 앞 로그가 없어 구간이 없다'),
        ('구간 제목', '구간 이름과 시작 로그·끝 로그의 단계와 서버 시각. 구간 이름은 끝 로그의 단계 이름으로 붙였다'),
        ('구간에 넣은 로그', '다른 요청(가격 제안 수락 요청 9건과 등록 가격 결제 요청 9건 가운데 기준 요청이 아닌 요청)이 남긴 로깅 설계 문서의 서버 로그(요청수신~요청응답) 가운데 '
                  f'구간의 시작 시각부터 끝 시각 사이에 남은 것. 두 시각에 남은 로그도 넣었다. 기존 로그, p6spy 기록, {after_name}의 줄은 넣지 않았다'),
        ('구간 안 순번', '구간 안의 다른 요청 로그를 시각 순서(같은 시각이면 원본 줄 번호 순서)로 매긴 번호'),
        ('다른 요청 ID·구매자', '그 로그를 남긴 요청과 그 요청의 구매자. 수락 요청은 가격 제안을 수락받는 흥정 구매자, 결제 요청은 결제하는 등록 가격 구매자다'),
        ('로그 시각', '그 로그의 서버 시각'),
        ('로그 종류', '그 로그의 단계 이름'),
        ('원본 줄 번호', '서버 로그 파일(chatservice.log)에서 그 로그가 시작되는 줄 번호. 누르면 시트 1에서 그 로그가 있는 표로 이동한다'),
        ('로그 항목', '그 로그에 남은 항목을 칸마다 하나씩 적었다. 칸 안의 작은 회색 글씨가 항목 이름이고 굵은 글씨가 값이다. '
                   '항목의 순서와 이름은 로깅 설계 문서와 시트 1의 그 로그 종류 표와 같아서, 같은 요청 종류의 같은 로그 종류는 같은 항목이 같은 열에 놓인다'),
    ])
    return interval_count


def main():
    parser = argparse.ArgumentParser(description='웹 애플리케이션 로그 CSV와 종료 후 확인 파일을 수락·결제 경합 확인용 엑셀 파일로 정리한다.')
    parser.add_argument('--csv', required=True, help='웹애플리케이션로그_1_CSV추출.py가 만든 웹 애플리케이션 로그 CSV')
    parser.add_argument('--after-check', required=True, help='JMeter가 실행 폴더에 남긴 종료 후 확인 파일(종료후확인.log)')
    parser.add_argument('--run-id', required=True, help='정리할 수락·결제 경합의 실행 ID. 예) run01-20261112-173649')
    parser.add_argument('--prepare-ids', required=True, help='준비 그룹이 남긴 ID 대조 CSV(준비_ID와_요청전상태.csv)')
    parser.add_argument('--prepare-summary', required=True, help='준비 그룹이 남긴 요청 전 상태 요약 CSV(준비_요청전상태_요약.csv)')
    parser.add_argument('--seller-id', required=True, help='준비 그룹이 상품을 등록한 판매자 ID. 수락시작 로그의 판매자ID 기대 값이다')
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
    requests.sort(key=lambda r: (0 if r['type'] == ACCEPT else 1, r['buyer_no'] or '99', r['id']))
    serial = calculate(requests, int(summary['remaining_quantity']))

    unknown = sorted({f"{ROLE_OF[request['type']]} {kind_of(r['test']['stage'])}: {name}" for request in requests
                      for r in request['designed']
                      for name in extra_items(items_of(request['type'], kind_of(r['test']['stage'])), r['test'])})

    workbook = Workbook()
    row_of_line = build_sheet_requests(workbook, requests, overall, after_path.name)
    problem_of = build_sheet_checks(workbook, requests, serial, overall, summary, args.seller_id, after_path.name)
    interval_count = build_sheet_intervals(workbook, requests, row_of_line, after_path.name)
    output.parent.mkdir(parents=True, exist_ok=True)
    workbook.save(output)

    accept_count = sum(1 for r in requests if r['type'] == ACCEPT)
    print(f'실행 ID {args.run_id}의 요청: {len(requests)}건(가격 제안 수락 요청 {accept_count}건, 등록 가격 결제 요청 {len(requests) - accept_count}건)')
    for request in requests:
        stages = [r['test']['stage'] for r in request['designed']]
        print(f"  {request['id']} ({buyer_label(request)}, {request['thread']}): "
              f"설계 문서의 로그 {len(request['designed'])}건{' + 요청별종료후확인 1줄' if request['after'] else ' (요청별종료후확인 없음)'}, "
              f"기존 로그 {len(request['records']) - len(request['designed'])}건, 단계 {'→'.join(stages)}, "
              f"시트 2 {'문제 있음' if problem_of[request['id']] else '문제 없음'}, 시트 3 구간 {interval_count[request['id']]}개")
    print(f"전체종료후확인: {'있음' if overall else '없음'}")
    print('순차 처리의 커밋 순서: ' + ', '.join(f"{r['id']}({'통과' if r['serial']['passes'] else '거절'})" for r in serial))
    rejected = [r for r in serial if not r['serial']['passes']]
    print(f"순차 처리 시 거절되는 요청: {len(rejected)}건 ({', '.join(r['id'] for r in rejected)})")
    # 기대 값 계산 표는 앞서 수락된 예약을 모두 유효 예약으로 더한다. 마지막 커밋·롤백 로그보다 먼저 만료된 예약이 있으면 알린다.
    expiries = [datetime.fromisoformat(item(r, '예약저장처리완료', '만료시각')) for r in requests
                if r['type'] == ACCEPT and item(r, '예약저장처리완료', '만료시각')]
    last_finish = max((r['serial']['finish_time'] for r in serial if r['serial']['finish_time']), default=None)
    if expiries and last_finish and min(expiries) <= last_finish:
        print('주의: 마지막 커밋·롤백 로그보다 먼저 만료된 예약이 있다. 기대 값 계산 표는 그 예약도 유효 예약으로 더했다.')
    if unknown:
        print('로그 종류별 항목 목록에 없는 항목(표 오른쪽 열로 더함): ' + ', '.join(unknown))
    print(f'저장한 파일: {output}')


if __name__ == '__main__':
    main()

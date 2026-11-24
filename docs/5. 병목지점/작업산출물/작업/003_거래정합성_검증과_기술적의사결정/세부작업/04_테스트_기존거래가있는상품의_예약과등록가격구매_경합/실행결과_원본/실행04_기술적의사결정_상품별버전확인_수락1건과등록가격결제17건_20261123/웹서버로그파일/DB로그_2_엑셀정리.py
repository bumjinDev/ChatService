"""
DB로그_1_p6spy기록_CSV추출.py가 만든 p6spy 기록 CSV와 웹애플리케이션로그_1_CSV추출.py가 만든 웹 애플리케이션 로그 CSV를 읽어,
기존 주문과 기존 예약이 있는 상품에 출발 시점을 나누어 보낸 수락·결제 검증 요청 18건(판매자가 새 흥정 구매자의 가격 제안을 수락하는 요청 1건과
새 등록 가격 구매자 17명의 결제 요청 17건)이 DB에 보낸 SQL과 커밋·롤백을 확인할 수 있게 엑셀 파일 하나로 정리한다.

요청과 p6spy 기록은 로깅 설계 문서(작업명세 04·05) 「1.1 애플리케이션 로그와 p6spy 기록」에 적힌 방법으로 잇는다.
같은 서버 스레드에서 그 요청의 요청수신 로그부터 요청응답 로그 사이에 남은 p6spy 기록이 그 요청의 것이다.
대상은 실행 ID가 --run-id와 같은 요청 가운데, JMeter가 실행 폴더에 남긴 검증 요청 결과 파일(검증요청_결과.csv)에 서버 로그 요청 ID가 적힌
검증 요청(accept-new, pay-01부터 pay-17까지)이다. 같은 실행 ID로 서버 로그에 남은 준비 요청 2건(기존 주문을 만든 준비 결제 prep-order-existing,
기존 예약을 만든 준비 수락 prep-accept-existing)은 엑셀에 넣지 않는다.

실행04의 서버는 가격 제안 수락과 결제를 낙관적 락으로 처리한다. 한 번의 처리가 커밋되지 못하고 롤백되면 서버가 같은 요청을 처음부터 다시 처리하므로,
요청 하나에 시작 로그(수락시작, 결제시작)부터 커밋완료·롤백완료 로그까지가 여러 번 남을 수 있다.
이 스크립트는 시작 로그 하나부터 그 처리의 커밋완료·롤백완료 로그까지를 시도 하나로 보고, 시작 로그가 남은 순서대로 시도 1, 시도 2로 번호를 붙인다.
요청수신 로그와 요청응답 로그는 요청에 한 번씩 남으므로 시도에 넣지 않는다. 웹 애플리케이션 로그 정리 엑셀과 같은 방법이고 시도 번호도 같다.
요청은 서버가 요청을 끝낸 순서(요청마다 마지막 시도의 커밋완료 로그나 롤백완료 로그가 서버 로그에 남은 시각이 이른 순서)로 둔다. 웹 애플리케이션 로그 정리 엑셀과 같은 순서다.

구간은 웹 애플리케이션 로그 엑셀의 시트 3과 같다. 요청의 로깅 설계 문서 로그 하나와, 같은 요청에서 그 바로 앞에 남은 로그 사이다.
구간은 끝 로그가 든 시도의 구간으로 본다. 한 시도의 롤백완료 로그부터 다음 시도의 시작 로그까지의 구간은 다음 시도의 구간이고,
마지막 시도의 커밋완료·롤백완료 로그부터 요청응답 로그까지의 구간은 어느 시도에도 들지 않는다.
p6spy 기록이 어느 구간에 드는지는 서버 로그 파일의 줄 순서로 정한다. p6spy 기록의 시각은 1/1,000초까지라,
1/1,000,000초까지 있는 설계 로그 시각과 비교하면 같은 1/1,000초 안에서는 어느 쪽이 먼저 남았는지 정할 수 없기 때문이다.

시트 구성
  1_요청별SQL         : 요청마다, 구간마다 그 요청 자신의 p6spy 기록을 표 하나에 둔다.
                        시도마다 「시도 1」 같은 줄을 두고, 그 줄 아래에 그 시도의 구간 표를 둔다.
                        「SQL 이름」 칸에는 로깅 설계 문서 「2.1 요청 하나에서 로그가 남는 순서」의 SQL 이름을 적는다.
                        수락 요청은 작업명세 04의 「직전에 실행되는 SQL」 열의 이름(가격 제안 조회, 상품의 예약 조회, 예약 INSERT 등)이고,
                        결제 요청은 작업명세 05의 「앞 로그부터 이 로그까지 p6spy에 남는 SQL」 열의 이름(상품 조회, 상품의 예약 조회, 주문 INSERT 등)이다.
                        수락 요청이 상품의 버전만 올리는 UPDATE 문은 작업명세 04에 없는 SQL이어서 「버전만 올리는 상품 UPDATE」로 적는다.
  2_요청별시간        : 요청 18건의 시간 값을 표 두 개에 시도마다 한 행씩 둔다. 두 표 모두 행은 서버가 요청을 끝낸 순서이고, 같은 요청 안에서는 시도 번호 순서다.
                        표 1 「시도별 상품 UPDATE와 커밋·롤백 시간」은 시도마다 상품 UPDATE 기록의 시각과 SQL 실행에 걸린 시간, 그 SQL의 조건에 적힌 버전과 올리려던 버전,
                        그 SQL에 적힌 남은 수량, 커밋 기록 시각, 롤백 기록 시각, 상품 UPDATE 기록부터 커밋 기록까지의 시간과 롤백 기록까지의 시간을 둔다.
                        결제 요청의 상품 UPDATE 기록은 남은 수량과 버전을 함께 쓰는 UPDATE 문이고, 수락 요청의 상품 UPDATE 기록은 버전만 올리는 UPDATE 문이다.
                        출발 시점이 같은 요청을 「출발 시점 60초」 같은 구분 줄 아래에 묶는다.
                        커밋된 시도는 커밋완료 구간의 commit 기록을 커밋 쪽 열에, 롤백된 시도는 롤백완료 구간의 rollback 기록을 롤백 쪽 열에 두고,
                        해당하지 않는 열의 칸은 「-」로 둔다. 수량 판단에서 거절된 시도는 상품 UPDATE 기록이 없어 상품 UPDATE 쪽 칸이 「-」다.
                        표 2 「요청별 그 밖의 시간」은 시도마다 첫 SQL 기록부터 커밋 기록까지의 시간(롤백된 시도는 롤백 기록까지의 시간),
                        반복하는 예약 조회의 건수와 시간, 상품 UPDATE 기록의 시각, SQL 실행에 걸린 시간, 그 SQL에 적힌 남은 수량을 둔다.
                        DB 연결 대기(추정)는 인증이 요청에 한 번 성공하므로 시도 1의 행에만 둔다.
  3_구간별다른요청SQL : 기준 요청마다, 구간마다 그 사이에 남은 다른 요청(수락 요청과 결제 요청 모두)의 p6spy 기록을 줄 순서대로 표 하나에 둔다.
                        구간 제목의 시작 로그와 끝 로그 가운데 시도에 든 로그에는 「시도 2의 결제시작」처럼 시도 번호를 붙인다.
각 시트 아래에는 열마다 무슨 데이터인지 적은 열 설명 표를 둔다.
입력 파일은 읽기만 한다. 출력 파일이 이미 있으면 덮어쓰지 않고 중단한다.
실행 방법은 같은 폴더의 DB로그_2_엑셀정리_실행방법.txt를 참고한다.
"""
import argparse
import csv
import json
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

# 요청의 종류. 검증 요청 결과 파일(검증요청_결과.csv)의 「요청」 열로 정한다.
ACCEPT = 'accept'      # 판매자가 새 흥정 구매자의 가격 제안을 수락하는 요청(작업명세 04)
BUY = 'buy'            # 새 등록 가격 구매자가 등록 가격으로 결제하는 요청(작업명세 05)
ROLE_OF = {ACCEPT: '새 흥정 구매자', BUY: '새 등록 가격 구매자'}
# 검증 요청 결과 파일의 「요청」 값 → 요청의 종류
VERIFY_REQUESTS = {'새 가격 제안 수락': ACCEPT, '새 등록 가격 결제': BUY}
# 요청 전 기준값 파일의 「회원」·「대화 ID」에서 새 흥정 구매자를 가리키는 키. 새 등록 가격 구매자의 키(pay01~pay17)는 회원 ID로 찾는다.
OFFER_BUYER_KEY = 'offer'

# 단계 로그를 남기는 코드 위치(자바 파일 이름.메서드 줄 번호). 실행04의 서버에 배포한 코드(낙관적 락을 반영해 커밋한 코드)의 줄 번호다.
# 커밋전은 수락 요청과 결제 요청이 남기는 곳이 다르다. 커밋완료·롤백완료·확정여부미확인은 트랜잭션이 끝난 뒤 호출되는 콜백이 남긴다.
LOG_POSITIONS = {
    '요청수신': 'TradeTestLogFilter.doFilterInternal 51줄',
    '요청응답': 'TradeTestLogFilter.doFilterInternal 63줄',
    '수락시작': 'OfferService.respond 137줄',
    '수량판단': 'ReservationService.reserve 120줄',
    '예약저장처리완료': 'ReservationService.reserve 141줄',
    (ACCEPT, '커밋전'): 'OfferService.respond 186줄',
    '결제시작': 'OrderService.placeOrder 98줄',
    '남은수량판단': 'OrderService.placeOrder 125줄',
    '구매가능수량판단': 'OrderService.placeOrder 136줄',
    '잔액판단': 'OrderService.placeOrder 186줄',
    '주문저장처리완료': 'OrderService.placeOrder 202줄',
    '차감처리완료': 'OrderService.placeOrder 219줄',
    (BUY, '커밋전'): 'OrderService.placeOrder 237줄',
    '커밋완료': 'TradeTestLog.registerTransactionEnd 163줄',
    '롤백완료': 'TradeTestLog.registerTransactionEnd 177줄',
    '확정여부미확인': 'TradeTestLog.registerTransactionEnd 177줄',
}


def stage_with_position(request_type, stage):
    """단계 이름 뒤에 그 로그를 남기는 코드 위치를 붙인다. 예) 수락시작 - OfferService.respond 137줄
    코드 위치가 없는 이름(JMeter가 쓰는 요청별종료후확인, 전체종료후확인 등)은 이름만 돌려준다."""
    position = LOG_POSITIONS.get((request_type, stage)) or LOG_POSITIONS.get(stage)
    return f'{stage} - {position}' if position else stage


# 커밋·롤백이 끝난 뒤 남는 단계. 이 단계로 끝나는 구간에 수락·결제 트랜잭션의 커밋·롤백 기록이 든다.
END_STAGES = ['커밋완료', '롤백완료', '확정여부미확인']
# 시도가 시작될 때 남는 로그의 단계. 이 로그가 나올 때마다 새 시도가 시작된다.
START_STAGE = {ACCEPT: '수락시작', BUY: '결제시작'}
# 요청에 한 번씩 남는 로그의 단계. 시도에 넣지 않는다.
REQUEST_STAGES = ['요청수신', '요청응답']
# 수락 요청의 커밋전 구간(예약저장처리완료부터 커밋전까지)의 SQL. 작업명세 04 「2.1」의 이름이다.
CONVERSATION_EVENT_QUERY = '대화 상태 이벤트를 만들기 위한 조회'
# 결제 요청의 커밋전 구간에서 대화 상태 이벤트를 만들려고 대화마다 반복하는 예약 조회. 작업명세 05 「2.1」의 이름이다.
REPEATED_QUERY = '대화마다 예약 조회'
# 결제 요청이 커밋할 때 상품의 남은 수량, 상태, 버전을 DB에 쓰는 SQL. 작업명세 05 「2.1」의 이름이다.
# 예) update PRODUCT set …,REMAINING_QUANTITY=9,…,VERSION=6,… where PRODUCT_ID=257021 and VERSION=5;
PRODUCT_UPDATE = '상품 UPDATE'
# 수락 요청이 커밋할 때 상품의 버전만 올리는 SQL. 작업명세 04 「2.1」에 없는 SQL이어서 이 스크립트에서 붙인 이름이다.
# 예) update PRODUCT set VERSION=10 where PRODUCT_ID=257021 and VERSION=9;
VERSION_ONLY_UPDATE = '버전만 올리는 상품 UPDATE'
PRODUCT_UPDATE_NAMES = (PRODUCT_UPDATE, VERSION_ONLY_UPDATE)
# 상품 UPDATE 기록의 바인드 값을 넣은 SQL에서 남은 수량 값을 꺼낸다. 예) …,REMAINING_QUANTITY=5,…
REMAINING_IN_UPDATE = re.compile(r'REMAINING_QUANTITY=(-?\d+)', re.IGNORECASE)
# 상품 UPDATE 기록의 바인드 값을 넣은 SQL에서 버전 값 두 개를 꺼낸다.
# where 앞의 VERSION 값이 올리려던 버전이고, where 뒤의 VERSION 값이 조건에 적힌 버전이다.
VERSION_IN_UPDATE = re.compile(r'\bVERSION=(\d+)\b.*\swhere\s.*\bVERSION=(\d+)\b', re.IGNORECASE | re.DOTALL)

TITLE_FONT = Font(bold=True, size=12)
REQUEST_FONT = Font(bold=True, size=12, color='FFFFFF')
REQUEST_FILL = PatternFill('solid', fgColor='305496')
TABLE_TITLE_FONT = Font(bold=True, size=11, color='FFFFFF')
TABLE_TITLE_FILL = PatternFill('solid', fgColor='548235')
HEADER_FONT = Font(bold=True)
HEADER_FILL = PatternFill('solid', fgColor='DDEBF7')
SLOT_FILL = PatternFill('solid', fgColor='E2EFDA')     # 시트 2 표 1의 출발 시점 구분 줄
# 시도 줄(「시도 1」 등): 요청 제목 줄과 표 제목 줄과 구별되게 옅은 노랑 바탕에 굵은 글씨로 쓴다. 웹 애플리케이션 로그 정리 엑셀과 같은 모양이다.
ATTEMPT_FONT = Font(bold=True, size=11)
ATTEMPT_FILL = PatternFill('solid', fgColor='FFE699')
LINK_FONT = Font(color='0563C1', underline='single')
WRAP = Alignment(wrap_text=True, vertical='top')
NO_WRAP = Alignment(wrap_text=False, vertical='top')
THIN = Side(style='thin', color='A6A6A6')
CELL_BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
WHOLE_NUMBER = re.compile(r'-?(?:0|[1-9]\d*)')
ZERO_PADDED = re.compile(r'0\d+')

SQL_HEADERS = ['chatservice.log 줄 번호', 'p6spy 기록 시각', 'SQL 실행에 걸린 시간', 'p6spy 기록 구분', 'DB 연결 번호', 'SQL 이름', '바인드 값을 넣은 SQL']
# 서버 로그에는 바인드 값을 넣은 SQL이 매우 긴 기록이 있을 수 있다(실행01의 서버 로그에는 25만 자를 넘는 예약 조회 기록이 있었다).
# csv 모듈의 칸 길이 한도(기본 131,072자)를 넘으면 CSV를 읽지 못하므로 한도를 늘린다.
csv.field_size_limit(2 ** 31 - 1)


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


def read_prepare(baseline_path, result_path):
    """요청 전 기준값 파일(요청전_기준값.json)과 검증 요청 결과 파일(검증요청_결과.csv)을 읽어,
    검증 요청마다(요청 ID → 값) 구매자 구분, 구매자 번호, 구매자 ID, 제안 ID, 대화 ID, 출발 시점을 돌려준다."""
    baseline = json.loads(baseline_path.read_text(encoding='utf-8'))
    members, conversations = baseline['회원'], baseline['대화 ID']
    offers = baseline['DB 조회 결과']['PRICE_OFFER']
    key_of_member = {member_id: key for key, member_id in members.items()}
    by_request = {}
    with result_path.open(encoding='utf-8-sig', newline='') as file:
        for row in csv.DictReader(file):
            if row['요청'] not in VERIFY_REQUESTS:
                continue
            request_type = VERIFY_REQUESTS[row['요청']]
            # 수락 요청의 「회원 ID」는 판매자이므로, 구매자는 요청 전 기준값 파일의 새 흥정 구매자다.
            # 결제 요청의 「회원 ID」는 결제하는 새 등록 가격 구매자이고, 「요청자」의 끝 두 자리가 구매자 번호다.
            if request_type == ACCEPT:
                key, buyer_no = OFFER_BUYER_KEY, ''
            else:
                key, buyer_no = key_of_member[row['회원 ID']], row['요청자'].split()[-1]
            buyer_id = members[key]
            offer = next((o for o in offers if o['BUYER_ID'] == buyer_id), None) if request_type == ACCEPT else None
            # sender_id는 그 요청을 보낸 회원의 ID다. 수락 요청은 판매자, 결제 요청은 결제하는 새 등록 가격 구매자다.
            by_request[row['서버 로그 요청 ID']] = {'role': ROLE_OF[request_type], 'buyer_no': buyer_no, 'buyer_id': buyer_id,
                                                  'sender_id': row['회원 ID'],
                                                  'offer_id': str(offer['OFFER_ID']) if offer else '',
                                                  'conversation_id': str(conversations[key]), 'slot': row['출발 시점(초)']}
    return by_request


# ---------------------------------------------------------------------
# 요청별로 묶기
# ---------------------------------------------------------------------
def request_type_of(prep, stages):
    """요청의 종류. read_prepare가 정한 role이 새 흥정 구매자이면 수락 요청, 새 등록 가격 구매자이면 결제 요청이다.
    role이 없으면 남은 로그의 단계(수락시작이 있으면 수락 요청)로 정한다."""
    role = prep.get('role', '')
    if role == ROLE_OF[ACCEPT]:
        return ACCEPT
    if role == ROLE_OF[BUY]:
        return BUY
    return ACCEPT if '수락시작' in stages else BUY


def accept_sql_name(end_stage, record):
    """수락 요청의 p6spy 기록 하나에 붙일 이름. 작업명세 04 「2.1」의 「직전에 실행되는 SQL」 열의 이름을 쓴다.

    커밋전 구간의 SQL은 모두 대화 상태 이벤트를 만들기 위한 조회다. 그 가운데 예약 조회는 수량판단 구간의 「상품의 예약 조회」와
    SQL 문장이 같으므로, 이름은 SQL 문장이 아니라 그 기록이 든 구간으로 정한다. 정하지 못하면 「-」다.
    상품의 버전만 올리는 UPDATE 문은 작업명세 04에 없는 SQL이어서 「버전만 올리는 상품 UPDATE」로 적는다.
    """
    if record['category'] == 'commit':
        return 'COMMIT'
    if record['category'] == 'rollback':
        return 'ROLLBACK'
    if record['sql'].lower().startswith('update product'):
        return VERSION_ONLY_UPDATE
    if end_stage == '커밋전':
        return CONVERSATION_EVENT_QUERY
    sql = record['sql'].lower()
    where = sql.split(' where ', 1)[1] if ' where ' in sql else ''
    if sql.startswith('insert into reservation'):
        return '예약 INSERT'
    if sql.startswith('update price_offer'):
        return '가격 제안 UPDATE'
    if ' from price_offer ' in sql:
        return '가격 제안 조회'
    if ' from product ' in sql:
        return '상품 조회'
    if ' from conversation ' in sql and 'conversation_id=' in where:
        return '대화 조회'
    if ' from reservation ' in sql and 'buyer_id=' in where:
        return '같은 구매자의 예약 조회'
    if ' from reservation ' in sql:
        return '상품의 예약 조회'
    return '-'


def buy_sql_name(end_stage, record):
    """결제 요청의 p6spy 기록 하나에 붙일 이름. 작업명세 05 「2.1」의 「앞 로그부터 이 로그까지 p6spy에 남는 SQL」 열의 이름을 쓴다.

    같은 SQL 문장이 여러 구간에 나오는 것이 있다. 상품의 예약 조회(상품 ID와 예약 상태로 조회)는 구매가능수량판단 구간에서는
    구매 가능 수량 계산의 조회이고, 커밋전 구간에서는 대화 상태 이벤트를 만들려고 대화마다 반복하는 조회다. 상품 조회도 남은수량판단 구간과
    요청응답 구간에 나온다. 그래서 이름은 SQL 문장과 그 기록이 든 구간(끝 로그의 단계)으로 함께 정한다. 정하지 못하면 「-」다.
    두 구간의 상품 조회 모두 작업명세 05의 이름대로 「상품 조회」로 적는다.
    실행04의 서버는 상품을 조회할 때 행 잠금을 얻지 않으므로 for update가 붙은 상품 조회는 없다.
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
        return PRODUCT_UPDATE
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


def sql_name(request_type, end_stage, record):
    """p6spy 기록 하나에 붙일 이름. 요청 종류에 따라 작업명세 04(수락)나 05(결제)의 「2.1」 이름을 쓴다."""
    return accept_sql_name(end_stage, record) if request_type == ACCEPT else buy_sql_name(end_stage, record)


def attempt_numbers(logs, request_type):
    """요청 하나의 설계 로그(서버 로그에 남은 순서)마다 시도 번호를 정해, 줄 번호 → 시도 번호 사전을 돌려준다.

    시작 로그(수락 요청은 수락시작, 결제 요청은 결제시작)가 나올 때마다 새 시도가 시작된다. 번호는 1부터다.
    요청수신 로그와 요청응답 로그는 시도에 넣지 않으므로 사전에 없다. 웹애플리케이션로그_2_엑셀정리.py와 같은 방법이다.
    """
    number, attempt_of_line = 0, {}
    for record in logs:
        stage = record['test']['stage']
        if stage in REQUEST_STAGES:
            continue
        if stage == START_STAGE[request_type] or number == 0:
            number += 1
        attempt_of_line[record['line']] = number
    return attempt_of_line


def group_requests(p6spy, web, run_id, prepare):
    """검증 요청(prepare에 있는 요청 ID)마다 설계 로그, 그 요청의 p6spy 기록, 구간, 시도, 인증 성공 시각을 묶는다.
    같은 실행 ID로 남은 준비 요청은 묶지 않는다.

    구간은 끝 로그가 든 시도의 구간으로 본다(interval['attempt']). 요청응답 로그로 끝나는 구간은 어느 시도에도 들지 않아 None이다.
    구간 안의 p6spy 기록에도 그 시도 번호를 적어 둔다(record['attempt']).
    """
    designed = {}
    for record in web:
        if record['test'] and record['test']['request'] in prepare:
            designed.setdefault(record['test']['request'], []).append(record)
    requests = []
    for request_id, logs in designed.items():
        logs.sort(key=lambda r: r['line'])
        stages = {r['test']['stage']: r for r in logs}
        prep = prepare.get(request_id, {})
        request_type = request_type_of(prep, stages)
        thread = stages['요청수신']['thread']
        low, high = stages['요청수신']['line'], stages['요청응답']['line']
        own = [r for r in p6spy if r['thread'] == thread and low < r['line'] < high]
        attempt_of_line = attempt_numbers(logs, request_type)
        intervals = []
        for previous, current in zip(logs, logs[1:]):
            inside = [r for r in own if previous['line'] < r['line'] < current['line']]
            attempt = attempt_of_line.get(current['line'])
            for r in inside:
                r['name'] = sql_name(request_type, current['test']['stage'], r)
                r['request'] = request_id
                r['attempt'] = attempt
            intervals.append({'start': previous, 'end': current, 'records': inside, 'attempt': attempt})
        auth = next((r for r in web if r['thread'] == thread and low < r['line'] < high
                     and not r['test'] and r['message'].startswith(AUTH_SUCCESS)), None)
        requests.append({'id': request_id, 'type': request_type, 'buyer_no': prep.get('buyer_no', ''), 'slot': prep.get('slot', ''),
                         'buyer_id': prep.get('buyer_id', ''), 'sender_id': prep.get('sender_id', ''),
                         'offer_id': prep.get('offer_id', ''),
                         'conversation_id': prep.get('conversation_id', ''), 'thread': thread, 'logs': logs, 'own': own,
                         'intervals': intervals, 'attempt_of_line': attempt_of_line,
                         'attempt_count': max(attempt_of_line.values(), default=0), 'auth': auth})
    requests.sort(key=lambda r: (0 if r['type'] == ACCEPT else 1, r['buyer_no'] or '99', r['id']))
    # 서버가 요청을 끝낸 순서로 둔다. 마지막 시도의 커밋완료·롤백완료·확정여부미확인 로그가 서버 로그에 남은 시각(같으면 줄 번호)이 이른 순서이고,
    # 웹 애플리케이션 로그 정리 엑셀과 같은 순서다. 끝 로그가 없는 요청은 위의 순서대로 뒤에 둔다.
    def finish(request):
        end = next((r for r in reversed(request['logs']) if r['test']['stage'] in END_STAGES), None)
        return (0, end['test']['time'], end['line']) if end else (1,)
    finished = sorted((r for r in requests if finish(r)[0] == 0), key=finish)
    return finished + [r for r in requests if finish(r)[0] == 1]


def attempt_records(request, number):
    """그 요청의 시도 하나에 든 p6spy 기록(서버 로그의 줄 순서)."""
    return [r for interval in request['intervals'] if interval['attempt'] == number for r in interval['records']]


def attempt_end_record(request, number):
    """그 시도의 수락·결제 트랜잭션이 끝날 때 남은 commit 기록이나 rollback 기록.
    그 시도의 커밋완료·롤백완료·확정여부미확인 로그로 끝나는 구간에 남은 기록이다. 없으면 None."""
    return next((r for interval in request['intervals']
                 if interval['attempt'] == number and interval['end']['test']['stage'] in END_STAGES
                 for r in interval['records'] if r['category'] in ('commit', 'rollback')), None)


def product_update_record(request, number):
    """그 시도의 상품 UPDATE 기록. 결제 요청은 「상품 UPDATE」, 수락 요청은 「버전만 올리는 상품 UPDATE」 기록이다. 없으면 None."""
    return next((r for r in attempt_records(request, number) if r.get('name') in PRODUCT_UPDATE_NAMES), None)


def repeated_records(request, number=None):
    """대화 상태 이벤트를 만들려고 대화마다 반복하는 예약 조회 기록. number를 주면 그 시도의 기록만 돌려준다.

    커밋전 구간에 남은 예약 조회 가운데 상품 ID와 예약 상태로 조회한 기록이다(같은 구매자의 예약 조회는 뺀다).
    결제 요청은 직전에 실행되는 SQL이 「대화마다 예약 조회」인 기록이고, 수락 요청은 「대화 상태 이벤트를 만들기 위한 조회」 가운데 예약 조회 기록이다.
    """
    found = []
    for interval in request['intervals']:
        if number is not None and interval['attempt'] != number:
            continue
        for r in interval['records']:
            if request['type'] == BUY:
                if r.get('name') == REPEATED_QUERY:
                    found.append(r)
                continue
            if interval['end']['test']['stage'] != '커밋전':
                continue
            sql = r['sql'].lower()
            where = sql.split(' where ', 1)[1] if ' where ' in sql else ''
            if r['category'] == 'statement' and ' from reservation ' in sql and 'buyer_id=' not in where:
                found.append(r)
    return found


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


def put_attempt_line(sheet, row, number, width):
    """시도 줄(「시도 1」 등). width 열까지 배경색을 칠한다. 이 줄 아래에 그 시도의 구간 표를 둔다."""
    put(sheet, row, 1, f'시도 {number}', font=ATTEMPT_FONT, fill=ATTEMPT_FILL, alignment=NO_WRAP)
    for col in range(2, width + 1):
        sheet.cell(row=row, column=col).fill = ATTEMPT_FILL


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


def buyer_label(request):
    """구매자 칸의 값. 예) 새 흥정 구매자, 새 등록 가격 구매자. 구매자 번호가 있으면 뒤에 붙인다."""
    return f"{ROLE_OF[request['type']]} {request['buyer_no']}".strip()


def request_title(request):
    if request['type'] == ACCEPT:
        return (f"판매자가 {buyer_label(request)}의 가격 제안을 수락하는 요청 · JMeter 헤더 로그용 요청 ID {request['id']}"
                f" · 요청을 보낸 판매자 ID {request['sender_id']} · 가격 제안을 낸 {ROLE_OF[ACCEPT]} ID {request['buyer_id']}"
                f" · 제안 ID {request['offer_id']} · 대화 ID {request['conversation_id']}"
                f" · 출발 시점 {request['slot']}초 · 서버 스레드 {request['thread']}")
    return (f"{buyer_label(request)}의 결제 요청 · JMeter 헤더 로그용 요청 ID {request['id']} · 요청을 보낸 {ROLE_OF[BUY]} ID {request['buyer_id']}"
            f" · 대화 ID {request['conversation_id']} · 출발 시점 {request['slot']}초 · 서버 스레드 {request['thread']}")


def stage_label(request, record):
    """구간 제목에 적는 로그 이름. 단계 이름 뒤에 그 로그를 남기는 코드 위치를 붙이고,
    시도에 든 로그는 「시도 2의 결제시작 - OrderService.placeOrder 98줄」처럼 시도 번호를 앞에 붙인다.
    요청수신 로그와 요청응답 로그는 시도에 들지 않으므로 시도 번호를 붙이지 않는다."""
    text = stage_with_position(request['type'], record['test']['stage'])
    number = request['attempt_of_line'].get(record['line'])
    return f'시도 {number}의 {text}' if number else text


def interval_title(request, interval):
    """구간 제목. 시작 로그와 끝 로그마다 시도 번호, 단계 이름, 그 로그를 남기는 코드 위치, 로그 시각을 적는다.
    예) 구간: 시도 2의 수락시작 - OfferService.respond 137줄 - 14:54:15.492272 → 시도 2의 수량판단 - ReservationService.reserve 120줄 - 14:54:15.497638"""
    start, end = interval['start'], interval['end']
    return (f"구간: {stage_label(request, start)} - {time_us(start['test']['time'])}"
            f" → {stage_label(request, end)} - {time_us(end['test']['time'])}")


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
        written_attempt = 0
        for interval in request['intervals']:
            # 시도가 바뀌는 곳에 「시도 N」 줄을 둔다. 요청응답 로그로 끝나는 구간은 시도에 들지 않으므로 줄을 두지 않는다.
            if interval['attempt'] and interval['attempt'] != written_attempt:
                put_attempt_line(sheet, row, interval['attempt'], width)
                row += 1
                written_attempt = interval['attempt']
            put_table_title(sheet, row, interval_title(request, interval), width)
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
    set_widths(sheet, [22, 14, 12, 11, 10, 34, 120])
    write_notes(sheet, row, [
        ('요청 제목 줄', '가격 제안 수락 요청은 새 흥정 구매자, JMeter 헤더 로그용 요청 ID, 요청을 보낸 판매자 ID, 가격 제안을 낸 새 흥정 구매자 ID, 제안 ID, 대화 ID, 출발 시점, 그 요청을 처리한 서버 스레드. '
                     '등록 가격 결제 요청은 새 등록 가격 구매자와 구매자 번호, JMeter 헤더 로그용 요청 ID, 요청을 보낸 새 등록 가격 구매자 ID, 대화 ID, 출발 시점, 그 요청을 처리한 서버 스레드. '
                     'JMeter 헤더 로그용 요청 ID는 JMeter가 요청마다 정해 X-Test-Log-Request-Id 헤더로 보낸 값이다. 서버가 만든 번호가 아니며, 서버는 이 값을 단계 로그의 요청ID 항목에 그대로 적는다. 회원 ID가 아니고, 결제 요청 본문에 담는 업무용 요청 ID(주문 테이블의 REQUEST_ID)와도 다른 값이다. 검증 요청 결과 파일(검증요청_결과.csv)의 「JMeter 헤더 로그용 요청 ID」 열과 같은 값이다. '
                     '웹 애플리케이션 로그 엑셀 시트 1과 같다. 같은 실행 ID로 남은 준비 요청 2건(prep-order-existing, prep-accept-existing)의 기록은 넣지 않았다'),
        ('요청을 둔 순서', '서버가 요청을 끝낸 순서. 요청마다 마지막 시도의 커밋완료 로그나 롤백완료 로그가 서버 로그에 남은 시각이 이른 순서이고, 웹 애플리케이션 로그 정리 엑셀과 같은 순서다. 시트 2의 행과 시트 3의 기준 요청도 같은 순서다'),
        ('시도 줄', '「시도 1」, 「시도 2」처럼 적은 줄. 그 줄 아래의 구간 표가 그 시도의 구간이다. '
                 '시도 하나는 시작 로그(수락 요청은 수락시작, 결제 요청은 결제시작) 하나부터 그 처리의 커밋완료 로그나 롤백완료 로그까지다. '
                 '실행04의 서버는 가격 제안 수락과 결제를 낙관적 락으로 처리하며, 한 번의 처리가 커밋되지 못하고 롤백되면 같은 요청을 처음부터 다시 처리한다. '
                 '그래서 요청 하나에 시도가 여러 번 남을 수 있고, 시작 로그가 남은 순서대로 번호를 붙였다. 웹 애플리케이션 로그 정리 엑셀의 시도 번호와 같다. '
                 '구간은 끝 로그가 든 시도의 구간으로 두었다. 요청수신 로그부터 시도 1의 시작 로그까지의 구간은 시도 1에, '
                 '한 시도의 롤백완료 로그부터 다음 시도의 시작 로그까지의 구간은 다음 시도에 두었다. '
                 '마지막 시도의 커밋완료 로그나 롤백완료 로그부터 요청응답 로그까지의 구간은 어느 시도에도 들지 않으며, 마지막 시도의 구간 표 아래에 두었다'),
        ('구간', '그 요청의 로깅 설계 문서 로그 하나와, 같은 요청에서 그 바로 앞에 남은 로그 사이. '
               '구간 제목은 시작 로그 → 끝 로그이고, 로그마다 「단계 이름 - 그 로그를 남기는 코드 위치(자바 파일 이름.메서드 줄 번호) - 서버가 그 로그를 남긴 시각」을 적었다. '
               '시도에 든 로그는 「시도 2의 결제시작」처럼 단계 이름 앞에 시도 번호를 붙였다. 요청수신 로그와 요청응답 로그는 시도에 들지 않아 시도 번호가 없다. '
               '줄 번호는 실행04의 서버에 배포한 코드의 줄 번호다. '
               '웹 애플리케이션 로그 엑셀 시트 3의 구간과 같다'),
        ('구간 안의 기록', '그 요청을 처리한 서버 스레드의 p6spy 기록 가운데, 서버 로그 파일에서 구간의 시작 로그 줄과 끝 로그 줄 사이에 있는 기록. '
                     '로깅 설계 문서 「1.1 애플리케이션 로그와 p6spy 기록」에 적힌 방법이다'),
        ('chatservice.log 줄 번호', '서버 로그 파일(chatservice.log)에서 그 p6spy 기록이 시작되는 줄 번호'),
        ('p6spy 기록 시각', '서버 로그 줄 맨 앞의 시각(1/1,000초까지). p6spy가 그 기록을 남긴 시각이며, 그 SQL·커밋·롤백의 실행이 끝난 뒤에 남는다'),
        ('SQL 실행에 걸린 시간', 'p6spy 기록의 「took」 값. 단위는 밀리초(ms)다'),
        ('p6spy 기록 구분', 'p6spy 기록의 구분을 로그에 적힌 그대로 적었다. statement는 SQL 실행, commit은 커밋, rollback은 롤백이다'),
        ('DB 연결 번호', 'p6spy 기록의 「connection」 값. 서버가 그 SQL을 보낸 DB 연결의 번호다'),
        ('SQL 이름(수락 요청)', '작업명세 04 「2.1 요청 하나에서 로그가 남는 순서」의 「직전에 실행되는 SQL」 열의 이름. '
                                 '커밋전 구간의 SQL은 SQL 문장이 수량판단 구간의 상품의 예약 조회와 같은 것이 있어, SQL 문장이 아니라 구간으로 이름(대화 상태 이벤트를 만들기 위한 조회)을 붙였다. '
                                 '상품의 버전만 올리는 UPDATE 문(update PRODUCT set VERSION=… where PRODUCT_ID=… and VERSION=…)은 작업명세 04에 없는 SQL이어서 '
                                 '이 엑셀에서 「버전만 올리는 상품 UPDATE」라는 이름을 붙였다'),
        ('SQL 이름(결제 요청)', '작업명세 05 「2.1 요청 하나에서 로그가 남는 순서」의 「앞 로그부터 이 로그까지 p6spy에 남는 SQL」 열의 이름. '
                                 '커밋전 구간의 대화마다 예약 조회는 SQL 문장이 구매가능수량판단 구간의 상품의 예약 조회와 같고, 요청응답 구간의 상품 조회는 남은수량판단 구간의 상품 조회와 '
                                 '같은 상품 테이블 조회여서, SQL 문장과 그 기록이 든 구간으로 함께 이름을 붙였다. '
                                 '롤백완료 구간에 ROLLBACK 뒤에 남은 상품 테이블 조회도 「상품 조회」로 적었다. '
                                 '수량 판단에서 거절된 시도는 구매가능수량판단 구간까지의 SQL과 롤백완료 구간의 ROLLBACK만 남는다'),
        ('상품 UPDATE·버전만 올리는 상품 UPDATE', '결제 요청의 「상품 UPDATE」는 상품의 남은 수량, 상태, 버전을 쓰는 UPDATE 문이고, '
                                         '수락 요청의 「버전만 올리는 상품 UPDATE」는 상품의 버전만 올리는 UPDATE 문이다. '
                                         '두 UPDATE 문 모두 where 조건에 상품 ID와 버전이 함께 적혀 있다. 시도마다 커밋완료 구간이나 롤백완료 구간에 한 건씩 있고, '
                                         '수량 판단에서 거절된 시도에는 없다. 시트 2의 표 1이 이 기록의 값을 시도마다 한 행에 둔다'),
        ('바인드 값을 넣은 SQL', 'p6spy 기록에서 값 자리에 실제 값을 넣은 SQL. 커밋·롤백 기록에는 SQL 문장이 없어 「-」'),
    ])
    return row_of_line


# ---------------------------------------------------------------------
# 시트 2: 요청별 시간
# ---------------------------------------------------------------------
def build_sheet_times(workbook, requests):
    """2_요청별시간 시트를 쓰고, 시도마다의 요약(요청 ID, 시도 번호, 상품 UPDATE 기록이 있는지, 끝 기록의 구분) 목록을 돌려준다."""
    sheet = workbook.create_sheet('2_요청별시간')
    # 표 1의 열. 행은 시도 하나다. 출발 시점은 구분 줄에 적는다.
    update_headers = ['JMeter 헤더 로그용 요청 ID', '구매자', '시도 번호',
                      '상품 UPDATE 기록 시각', '상품 UPDATE의 SQL 실행에 걸린 시간', '조건에 적힌 버전', '올리려던 버전',
                      '상품 UPDATE에 적힌 남은 수량', '커밋 기록 시각', '롤백 기록 시각',
                      '상품 UPDATE 기록부터 커밋 기록까지의 시간', '상품 UPDATE 기록부터 롤백 기록까지의 시간']
    # 표 2의 열. 표 1에 두지 않은 값이다. 행은 시도 하나다.
    other_headers = ['JMeter 헤더 로그용 요청 ID', '구매자', '출발 시점(초)', '시도 번호', '첫 SQL 기록 시각',
                     '첫 SQL 기록부터 커밋 기록까지의 시간', '첫 SQL 기록부터 롤백 기록까지의 시간',
                     '인증 성공 시각', '첫 SQL 시작 시각', 'DB 연결 대기',
                     '반복하는 예약 조회 건수', '반복하는 예약 조회 시간(첫 기록부터 마지막 기록까지)',
                     '반복하는 예약 조회 시간(SQL 실행에 걸린 시간의 합)',
                     '상품 UPDATE 기록 시각', '상품 UPDATE의 SQL 실행에 걸린 시간', '상품 UPDATE에 적힌 남은 수량']
    update_rows, other_rows, attempt_summaries = [], [], []
    for request in requests:
        auth = request['auth']['server_time'] if request['auth'] else None
        for number in range(1, request['attempt_count'] + 1):
            records = attempt_records(request, number)
            first = records[0] if records else None
            # 그 시도의 수락·결제 트랜잭션의 커밋·롤백 기록: 커밋완료·롤백완료·확정여부미확인으로 끝나는 구간의 기록이다.
            # 결제 요청은 요청응답 구간에도 응답 본문을 조회한 읽기 전용 트랜잭션의 commit 기록이 남지만, 그 구간은 시도에 들지 않는다.
            end = attempt_end_record(request, number)
            # 끝 기록의 p6spy 기록 구분이 commit이면 커밋 쪽 열에, rollback이면 롤백 쪽 열에 값을 두고 다른 쪽 칸은 「-」로 둔다.
            committed = bool(end) and end['category'] == 'commit'
            rolled_back = bool(end) and end['category'] == 'rollback'
            # 상품 UPDATE 기록: 결제 요청은 남은 수량, 상태, 버전을 쓰는 UPDATE 문이고, 수락 요청은 버전만 올리는 UPDATE 문이다.
            # 수량 판단에서 거절된 시도에는 없다.
            update = product_update_record(request, number)
            versions = VERSION_IN_UPDATE.search(update['sql']) if update else None
            saved_remaining = REMAINING_IN_UPDATE.search(update['sql']) if update else None
            update_time = time_ms(update['time']) if update else '-'
            update_took = update['took'] if update else '-'
            remaining = saved_remaining.group(1) if saved_remaining else '-'
            from_update = ms_between(update['time'], end['time']) if update and end else '-'
            from_first = ms_between(first['time'], end['time']) if first and end else '-'
            first_start = first['time'] - timedelta(milliseconds=first['took']) if first else None
            repeated = repeated_records(request, number)
            update_rows.append((request['slot'], [
                request['id'], buyer_label(request), number, update_time, update_took,
                versions.group(2) if versions else '-', versions.group(1) if versions else '-', remaining,
                time_ms(end['time']) if committed else '-', time_ms(end['time']) if rolled_back else '-',
                from_update if committed else '-', from_update if rolled_back else '-',
            ]))
            # 인증은 요청에 한 번 성공하므로, 인증 성공 시각과 DB 연결 대기는 시도 1의 행에만 둔다.
            other_rows.append([
                request['id'], buyer_label(request), request['slot'], number,
                time_ms(first['time']) if first else '-',
                from_first if committed else '-', from_first if rolled_back else '-',
                time_ms(auth) if number == 1 else '-', time_ms(first_start),
                ms_between(auth, first_start) if number == 1 and auth and first_start else '-',
                len(repeated), ms_between(repeated[0]['time'], repeated[-1]['time']) if repeated else '-',
                sum(r['took'] for r in repeated) if repeated else '-',
                update_time, update_took, remaining,
            ])
            attempt_summaries.append({'request': request['id'], 'number': number, 'update': bool(update),
                                      'end': end['category'] if end else None})

    # 표 1: 시도별 상품 UPDATE와 커밋·롤백 시간. 출발 시점이 바뀔 때마다 구분 줄을 둔다.
    put_table_title(sheet, 1, '시도별 상품 UPDATE와 커밋·롤백 시간', len(update_headers))
    put_header(sheet, 2, update_headers)
    row, slot = 2, None
    for request_slot, values in update_rows:
        if request_slot != slot:
            slot = request_slot
            row += 1
            put(sheet, row, 1, f'출발 시점 {slot}초', font=HEADER_FONT, fill=SLOT_FILL, alignment=NO_WRAP, border=CELL_BORDER)
            for col in range(2, len(update_headers) + 1):
                put(sheet, row, col, None, fill=SLOT_FILL, border=CELL_BORDER)
        row += 1
        put_values(sheet, row, values)

    # 표 2: 요청별 그 밖의 시간
    row += 3
    put_table_title(sheet, row, '요청별 그 밖의 시간: 처리 전체 시간, DB 연결 대기, 반복하는 예약 조회, 상품 UPDATE', len(other_headers))
    row += 1
    put_header(sheet, row, other_headers)
    for values in other_rows:
        row += 1
        put_values(sheet, row, values)
    set_widths(sheet, [28, 24, 14, 16, 18, 18, 18, 18, 16, 16, 22, 22, 22, 16, 16, 16])
    write_notes(sheet, row + 3, [
        ('표 1 시도별 상품 UPDATE와 커밋·롤백 시간', '시도마다 상품 UPDATE 기록의 값과 그 시도의 커밋 기록이나 롤백 기록의 값을 한 행에 두었다. '
                                        '출발 시점이 같은 요청을 「출발 시점 60초」 같은 구분 줄 아래에 묶었다. 행은 서버가 요청을 끝낸 순서이고, 같은 요청 안에서는 시도 번호 순서다. '
                                        '출발 시점은 검증 요청 결과 파일(검증요청_결과.csv)의 「출발 시점(초)」이다'),
        ('JMeter 헤더 로그용 요청 ID·구매자', 'JMeter가 요청마다 정해 X-Test-Log-Request-Id 헤더로 보낸 값(서버가 만든 번호가 아니다)과 그 요청의 구매자. 수락 요청은 가격 제안을 수락받는 새 흥정 구매자, 결제 요청은 결제하는 새 등록 가격 구매자다'),
        ('시도 번호', '그 요청에서 몇 번째 시도인지. 시작 로그(수락 요청은 수락시작, 결제 요청은 결제시작)가 남은 순서대로 붙인 번호다. '
                   '시트 1의 시도 줄, 웹 애플리케이션 로그 정리 엑셀의 시도 번호와 같다'),
        ('상품 UPDATE 기록 시각', '그 시도의 p6spy 기록 가운데 상품 UPDATE 기록의 시각(1/1,000초까지). p6spy가 그 SQL의 실행을 마친 뒤 기록을 남긴 시각이다. '
                           '결제 요청은 SQL 이름이 「상품 UPDATE」인 기록(상품의 남은 수량, 상태, 버전을 쓰는 UPDATE 문)이고, '
                           '수락 요청은 SQL 이름이 「버전만 올리는 상품 UPDATE」인 기록이다. 그 시도의 커밋완료 구간이나 롤백완료 구간에 있다. '
                           '수량 판단에서 거절된 시도는 상품 UPDATE 기록이 남지 않으므로 「-」'),
        ('상품 UPDATE의 SQL 실행에 걸린 시간', '그 상품 UPDATE 기록의 「took」 값. 단위는 밀리초(ms)다. 다른 요청이 같은 상품 행을 먼저 고치고 아직 커밋하지 않았으면, '
                                     '그 커밋을 기다린 시간이 이 값에 남는다고 추론한다(작업명세 05 「2.1 요청 하나에서 로그가 남는 순서」)'),
        ('조건에 적힌 버전·올리려던 버전', '그 상품 UPDATE 기록의 바인드 값을 넣은 SQL에 적힌 VERSION 값 두 개. '
                               '조건에 적힌 버전은 where 뒤에 적힌 VERSION 값이고, 올리려던 버전은 where 앞에 적힌 VERSION 값이다. '
                               '예) update PRODUCT set …,VERSION=6,… where PRODUCT_ID=257021 and VERSION=5 이면 조건에 적힌 버전은 5, 올리려던 버전은 6이다. '
                               '이 UPDATE 문은 상품의 버전이 조건에 적힌 버전과 같을 때만 그 상품 행을 고친다'),
        ('상품 UPDATE에 적힌 남은 수량', '그 상품 UPDATE 기록의 바인드 값을 넣은 SQL에 적힌 REMAINING_QUANTITY 값. 그 시도가 DB에 쓰려던 상품의 남은 수량이다. '
                               '롤백된 시도의 값은 DB에 남지 않는다. 수락 요청의 버전만 올리는 상품 UPDATE에는 남은 수량이 적혀 있지 않으므로 「-」'),
        ('커밋 기록 시각·롤백 기록 시각', '그 시도의 p6spy 기록 가운데 커밋완료(또는 롤백완료, 확정여부미확인) 구간에 남은, p6spy 기록 구분이 commit이나 rollback인 기록의 시각. '
                        'p6spy 기록 구분이 commit이면 「커밋 기록 시각」 칸에, rollback이면 「롤백 기록 시각」 칸에 적고 다른 쪽 칸은 「-」로 두었다. '
                        '결제 요청의 요청응답 구간에 남은 응답 조회 트랜잭션의 commit 기록은 쓰지 않았다'),
        ('상품 UPDATE 기록부터 커밋 기록까지의 시간·상품 UPDATE 기록부터 롤백 기록까지의 시간',
         '커밋된 시도는 커밋 기록 시각 − 상품 UPDATE 기록 시각을 「상품 UPDATE 기록부터 커밋 기록까지의 시간」 칸에, '
         '롤백된 시도는 롤백 기록 시각 − 상품 UPDATE 기록 시각을 「상품 UPDATE 기록부터 롤백 기록까지의 시간」 칸에 적고 다른 쪽 칸은 「-」로 두었다. 단위는 밀리초(ms)다. '
         '상품 UPDATE 기록이 없는 시도는 두 칸 모두 「-」'),
        ('표 2 요청별 그 밖의 시간', '표 1에 두지 않은 값을 시도마다 한 행에 두었다. 행의 순서는 표 1과 같다'),
        ('출발 시점(초)', '검증 요청 결과 파일(검증요청_결과.csv)의 「출발 시점(초)」. 모든 요청이 준비된 순간부터 그 요청이 출발하기까지의 시간이다'),
        ('첫 SQL 기록 시각', '그 시도의 p6spy 기록 가운데 처음 남은 기록의 시각. 시도 1은 요청수신 로그 뒤에, 시도 2부터는 앞 시도의 롤백완료 로그 뒤에 처음 남은 기록이다'),
        ('첫 SQL 기록부터 커밋 기록까지의 시간·첫 SQL 기록부터 롤백 기록까지의 시간', '커밋된 시도는 커밋 기록 시각 − 첫 SQL 기록 시각을 「첫 SQL 기록부터 커밋 기록까지의 시간」 칸에, 롤백된 시도는 롤백 기록 시각 − 첫 SQL 기록 시각을 「첫 SQL 기록부터 롤백 기록까지의 시간」 칸에 적고 다른 쪽 칸은 「-」로 두었다. 단위는 밀리초(ms)다. 첫 SQL 기록 시각은 그 SQL의 실행이 끝난 뒤의 시각이므로 첫 SQL의 실행에 걸린 시간은 들어 있지 않다. 결제 요청의 첫 SQL은 상품 조회이고, 수락 요청의 첫 SQL은 가격 제안 조회다'),
        ('인증 성공 시각', '같은 서버 스레드에서 요청수신 로그 뒤에 처음 남은 「JWT 인증 성공」 로그의 서버 시각. 인증은 요청에 한 번 성공하므로 시도 1의 행에만 적고, 다른 시도의 행은 「-」로 두었다'),
        ('첫 SQL 시작 시각', '첫 SQL 기록 시각 − 그 기록의 SQL 실행에 걸린 시간'),
        ('DB 연결 대기', '첫 SQL 시작 시각 − 인증 성공 시각. 단위는 밀리초(ms)다. 추정한 값이며, 대기 시간을 직접 기록한 값이 아니다. 시도 1의 행에만 적었다'),
        ('반복하는 예약 조회 건수', '그 시도의 커밋전 구간에 남은 예약 조회 가운데, 대화 상태 이벤트를 만들려고 대화마다 반복하는 조회(상품 ID와 예약 상태로 조회한 기록)의 수. '
                         '수락 요청은 예약저장처리완료 로그부터 커밋전 로그 사이의 「대화 상태 이벤트를 만들기 위한 조회」 가운데 예약 조회 기록이고(작업명세 04 「1.1」), '
                         '결제 요청은 차감처리완료 로그부터 커밋전 로그 사이의 「대화마다 예약 조회」 기록이다(작업명세 05 「2.1」)'),
        ('반복하는 예약 조회 시간(첫 기록부터 마지막 기록까지)', '위 예약 조회 기록 가운데 마지막 기록의 시각 − 첫 기록의 시각. 단위는 밀리초(ms)다'),
        ('반복하는 예약 조회 시간(SQL 실행에 걸린 시간의 합)', '위 예약 조회 기록마다의 SQL 실행에 걸린 시간을 더한 값. 단위는 밀리초(ms)다'),
        ('표 2의 상품 UPDATE 기록 시각·상품 UPDATE의 SQL 실행에 걸린 시간·상품 UPDATE에 적힌 남은 수량', '표 1의 같은 이름의 열과 같은 값이다'),
    ])
    return attempt_summaries


# ---------------------------------------------------------------------
# 시트 3: 구간별 다른 요청 SQL
# ---------------------------------------------------------------------
def build_sheet_others(workbook, requests, row_of_line):
    sheet = workbook.create_sheet('3_구간별다른요청SQL')
    sheet.sheet_properties.outlinePr.summaryBelow = False
    headers = ['구간 안 순번', '다른 요청의 JMeter 헤더 로그용 요청 ID', '구매자'] + SQL_HEADERS
    width = len(headers)
    request_of = {r['id']: r for r in requests}
    all_records = sorted((record for request in requests for record in request['own']), key=lambda r: r['line'])
    row, total = 1, 0
    for base in requests:
        title_row = row
        put_request_title(sheet, row, '기준 요청: ' + request_title(base), width)
        row += 1
        for interval in base['intervals']:
            low, high = interval['start']['line'], interval['end']['line']
            put_table_title(sheet, row, interval_title(base, interval), width)
            row += 1
            put_header(sheet, row, headers)
            inside = [r for r in all_records if r['request'] != base['id'] and low < r['line'] < high]
            if not inside:
                row += 1
                put_values(sheet, row, ['-'] * (width - 1) + ['이 구간에 남은 다른 요청의 p6spy 기록 없음'], no_wrap_last=True)
            for number, record in enumerate(inside, start=1):
                row += 1
                put_values(sheet, row, [number, record['request'], buyer_label(request_of[record['request']])] + sql_values(record),
                           no_wrap_last=True)
                link = sheet.cell(row=row, column=4)
                link.hyperlink = Hyperlink(ref=link.coordinate, location=f"'1_요청별SQL'!A{row_of_line[record['line']]}")
                link.font = LINK_FONT
            total += len(inside)
            row += 2
        group_rows(sheet, title_row + 1, row - 1)
    set_widths(sheet, [10, 36, 24, 22, 14, 12, 11, 10, 34, 120])
    write_notes(sheet, row, [
        ('기준 요청 제목 줄', '구간을 나누는 기준이 되는 요청. 시트 1의 요청 제목 줄과 같다'),
        ('구간', '기준 요청의 로깅 설계 문서 로그 하나와, 같은 요청에서 그 바로 앞에 남은 로그 사이. 시트 1의 구간과 같다. '
               '구간 제목의 시작 로그와 끝 로그 가운데 시도에 든 로그는 「시도 2의 결제시작」처럼 단계 이름 앞에 시도 번호를 붙였다. 시도 번호는 시트 1의 시도 줄과 같다'),
        ('구간에 넣은 기록', '다른 요청(가격 제안 수락 요청 1건과 등록 가격 결제 요청 17건 가운데 기준 요청이 아닌 요청)의 p6spy 기록 가운데, 서버 로그 파일에서 '
                    '기준 요청의 구간 시작 로그 줄과 끝 로그 줄 사이에 있는 기록. 시각이 아니라 서버 로그 파일의 줄 순서로 정했다'),
        ('구간 안 순번', '구간 안의 다른 요청 기록을 서버 로그 파일의 줄 순서로 매긴 번호'),
        ('다른 요청의 JMeter 헤더 로그용 요청 ID·구매자', '그 기록을 남긴 요청의 JMeter 헤더 로그용 요청 ID와 그 요청의 구매자. 수락 요청은 가격 제안을 수락받는 새 흥정 구매자, 결제 요청은 결제하는 새 등록 가격 구매자다'),
        ('chatservice.log 줄 번호', '서버 로그 파일(chatservice.log)에서 그 기록이 시작되는 줄 번호. 누르면 시트 1에서 그 기록이 있는 행으로 이동한다'),
        ('시각·SQL 실행에 걸린 시간·구분·연결 번호·바인드 값을 넣은 SQL', '시트 1과 같다'),
        ('SQL 이름', '그 기록을 남긴 요청에서 그 기록이 든 구간으로 붙인 이름. 시트 1과 같다'),
    ])
    return total


def main():
    parser = argparse.ArgumentParser(description='p6spy 기록 CSV와 웹 애플리케이션 로그 CSV를 수락·결제 경합 확인용 엑셀 파일로 정리한다.')
    parser.add_argument('--p6spy', required=True, help='DB로그_1_p6spy기록_CSV추출.py가 만든 p6spy 기록 CSV')
    parser.add_argument('--web-csv', required=True, help='웹애플리케이션로그_1_CSV추출.py가 만든 웹 애플리케이션 로그 CSV')
    parser.add_argument('--run-id', required=True, help='정리할 수락·결제 경합의 실행 ID. 예) mpay04-20261123-142659')
    parser.add_argument('--prepare-baseline', required=True, help='JMeter의 「요청 전 확인」 그룹이 남긴 요청 전 기준값 파일(요청전_기준값.json)')
    parser.add_argument('--request-result', required=True, help='JMeter의 「결과 집계」 그룹이 남긴 검증 요청 결과 파일(검증요청_결과.csv)')
    parser.add_argument('--output', required=True, help='만들 엑셀 파일. 같은 이름의 파일이 있으면 중단한다')
    args = parser.parse_args()

    output = Path(args.output)
    if output.exists():
        sys.exit(f'출력 파일이 이미 있습니다. 다른 이름을 지정하세요: {output}')

    p6spy = read_p6spy(Path(args.p6spy))
    web = read_web(Path(args.web_csv), args.run_id)
    prepare = read_prepare(Path(args.prepare_baseline), Path(args.request_result))
    requests = group_requests(p6spy, web, args.run_id, prepare)
    excluded = sorted({r['test']['request'] for r in web if r['test'] and r['test']['request'] not in prepare})
    if not requests:
        sys.exit(f'실행 ID {args.run_id}의 로그가 없습니다.')

    workbook = Workbook()
    row_of_line = build_sheet_sql(workbook, requests)
    attempt_summaries = build_sheet_times(workbook, requests)
    total = build_sheet_others(workbook, requests, row_of_line)
    output.parent.mkdir(parents=True, exist_ok=True)
    workbook.save(output)

    unnamed = sum(1 for request in requests for r in request['own'] if r.get('name', '-') == '-')
    accept_count = sum(1 for r in requests if r['type'] == ACCEPT)
    print(f'실행 ID {args.run_id}의 요청: {len(requests)}건(가격 제안 수락 요청 {accept_count}건, 등록 가격 결제 요청 {len(requests) - accept_count}건)')
    if excluded:
        print(f"같은 실행 ID로 남았지만 엑셀에 넣지 않은 준비 요청: {', '.join(excluded)}")
    for request in requests:
        parts = [f"{i['end']['test']['stage']} {len(i['records'])}" for i in request['intervals']]
        print(f"  {request['id']} ({buyer_label(request)}, {request['thread']}): 시도 {request['attempt_count']}번, p6spy 기록 {len(request['own'])}건 "
              f"[구간별 {', '.join(parts)}], 반복하는 예약 조회 {len(repeated_records(request))}건")
    print(f'요청 {len(requests)}건의 p6spy 기록 합계: {sum(len(r["own"]) for r in requests)}건')
    committed = [a for a in attempt_summaries if a['end'] == 'commit']
    rolled_back = [a for a in attempt_summaries if a['end'] == 'rollback']
    no_end = [a for a in attempt_summaries if a['end'] is None]
    print(f'요청 {len(requests)}건의 시도 합계: {len(attempt_summaries)}번(커밋 기록으로 끝난 시도 {len(committed)}번, 롤백 기록으로 끝난 시도 {len(rolled_back)}번)')
    print(f"상품 UPDATE 기록이 있는 시도: {sum(1 for a in attempt_summaries if a['update'])}번"
          f"(커밋 {sum(1 for a in committed if a['update'])}번, 롤백 {sum(1 for a in rolled_back if a['update'])}번), "
          f"상품 UPDATE 기록이 없는 시도: {sum(1 for a in attempt_summaries if not a['update'])}번")
    if no_end:
        print('커밋 기록도 롤백 기록도 찾지 못한 시도: ' + ', '.join(f"{a['request']} 시도 {a['number']}" for a in no_end))
    if unnamed:
        print(f'SQL 이름을 정하지 못한 기록: {unnamed}건(「-」로 적음)')
    print(f'시트 3에 넣은 다른 요청의 p6spy 기록: {total}행')
    print(f'저장한 파일: {output}')


if __name__ == '__main__':
    main()

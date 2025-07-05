import pandas as pd  # 데이터 처리를 위한 라이브러리
import re             # 정규 표현식을 위한 라이브러리
import os             # 운영체제 기능을 위한 라이브러리
import shutil         # 파일 복사/이동을 위한 라이브러리
import argparse       # 명령줄 인자 처리를 위한 라이브러리
from openpyxl import load_workbook  # Excel 파일 처리를 위한 라이브러리

# 상수 정의
LOG_FILE = 'ChatService.log'  # 기본 로그 파일명
NEW_LOG_PATH = r'E:\devSpace\ChatServiceTest\log\ChatService.log'  # 새 로그 파일 경로

def replace_log_file():
    """
    로그 파일을 새로운 버전으로 교체하는 함수
    - 기존 로그 파일이 있으면 삭제
    - 새 로그 파일을 복사해서 가져옴
    """
    # 기존 로그 파일이 존재하면 삭제
    if os.path.exists(LOG_FILE):
        os.remove(LOG_FILE)
    
    # 새 로그 파일을 현재 디렉토리로 복사
    shutil.copy(NEW_LOG_PATH, LOG_FILE)

def parse_logs(filepath, room_number=None):
    """
    로그 파일을 파싱해서 동시성 제어 구조별 시간 측정 이벤트들을 추출하는 함수
    
    🔧 동시성 제어 시간 측정 목적:
    - INCREMENT_BEFORE: 실제 증가 작업 직전 (동시성 제어 시작점)
    - INCREMENT_AFTER: 실제 증가 작업 직후 (동시성 제어 종료점)
    
    입력:
    - filepath: 로그 파일 경로
    - room_number: 특정 방 번호만 필터링 (None이면 모든 방)
    
    출력: DataFrame - 파싱된 이벤트 데이터
    """
    
    # 정규 표현식 패턴 정의
    # 동시성 제어 구조별 시간 측정을 위한 핵심 이벤트:
    # - INCREMENT_BEFORE: 실제 증가 작업 직전
    # - INCREMENT_AFTER: 실제 증가 작업 직후
    pattern = re.compile(
        r'timestampIso=(?P<timestamp>\S+).*?'  # 시간 정보 추출
        r'event=(?P<event>INCREMENT_BEFORE|INCREMENT_AFTER).*?'  # 동시성 제어 핵심 이벤트만
        r'roomNumber=(?P<roomNumber>\d+).*?'    # 방 번호
        r'userId=(?P<userId>\S+).*?'            # 사용자 ID
        r'currentPeople=(?P<currentPeople>\d+).*?'  # 현재 인원수
        r'maxPeople=(?P<maxPeople>\d+)'         # 최대 정원
    )
    
    records = []  # 파싱된 레코드들을 저장할 리스트
    
    # 로그 파일을 한 줄씩 읽으면서 파싱
    with open(filepath, encoding='utf-8') as f:
        for line in f:
            # 정규식으로 매칭 시도
            match = pattern.search(line)
            if match:
                # 매칭된 그룹들을 딕셔너리로 변환
                data = match.groupdict()
                
                # 문자열로 추출된 숫자들을 정수형으로 변환
                data['roomNumber'] = int(data['roomNumber'])
                data['currentPeople'] = int(data['currentPeople'])
                data['maxPeople'] = int(data['maxPeople'])
                
                # 🔧 나노초 정밀도 정보 추출 (동시성 제어 시간 측정 정밀도)
                nano_match = re.search(r'nanoTime=(\d+)', line)
                epoch_match = re.search(r'epochNano=(\d+)', line)
                thread_match = re.search(r'threadId=(\d+)', line)
                
                if nano_match:
                    data['nanoTime'] = int(nano_match.group(1))
                if epoch_match:
                    data['epochNano'] = int(epoch_match.group(1))
                if thread_match:
                    data['threadId'] = int(thread_match.group(1))
                
                # 방 번호 필터링 적용
                if room_number is None or data['roomNumber'] == room_number:
                    records.append(data)
    
    # 리스트를 DataFrame으로 변환해서 반환
    return pd.DataFrame(records)

def build_paired_data_with_enhanced_binning(df):
    """
    🔧 동시성 제어 구조별 시간 측정을 위한 페어링 로직
    - 방 번호별로 각각 10개 구간으로 분할
    - 각 방 내에서 타임스탬프 기준 정렬 및 구간 할당
    - INCREMENT_BEFORE ↔ INCREMENT_AFTER 페어링으로 실제 동시성 제어 시간 측정
    
    입력: df (DataFrame) - 파싱된 로그 데이터
    출력: DataFrame - 페어링된 동시성 제어 시간 측정 데이터
    """
    
    # 빈 데이터면 빈 DataFrame 반환
    if df.empty:
        return pd.DataFrame()
    
    # === 타임스탬프 변환 ===
    df['timestamp'] = pd.to_datetime(df['timestamp'])
    
    # === 동시성 제어 이벤트별로 데이터 분리 ===
    before = df[df['event'] == 'INCREMENT_BEFORE'].copy()    # 증가 작업 직전
    after = df[df['event'] == 'INCREMENT_AFTER'].copy()      # 증가 작업 직후

    # === 🎯 방 번호별 타임스탬프 기준 정렬 및 구간 분할 ===
    enhanced_results = []
    
    # 각 방 번호별로 처리
    unique_rooms = df['roomNumber'].unique()
    
    for room_num in unique_rooms:
        print(f"  처리 중: 방 번호 {room_num}")
        
        # 해당 방의 INCREMENT_BEFORE 이벤트만 추출
        room_before = before[before['roomNumber'] == room_num].copy()
        room_after = after[after['roomNumber'] == room_num].copy()
        
        if room_before.empty:
            continue
        
        # === 방별 타임스탬프 기준 정렬 ===
        room_before = room_before.sort_values('timestamp').reset_index(drop=True)
        
        # === 🎯 방별 10개 구간 분할 ===
        total_requests = len(room_before)
        if total_requests <= 10:
            # 요청이 10개 이하면 각각 하나씩 구간 할당
            room_before['bin'] = range(1, total_requests + 1)
        else:
            # 10개 구간으로 균등 분할
            room_before['bin'] = pd.cut(range(total_requests), bins=10, labels=range(1, 11)).astype(int)
        
        # === 방별 순번 부여 ===
        room_before['room_entry_sequence'] = range(1, len(room_before) + 1)
        
        # === 페어링을 위한 인덱스 생성 ===
        # 같은 방, 같은 사용자의 이벤트들을 순서대로 연결하기 위한 인덱스
        for event_df in [room_before, room_after]:
            if not event_df.empty:
                # 사용자 ID별로 그룹화해서 순차 번호 부여
                event_df['pair_idx'] = event_df.groupby('userId').cumcount()

        # === INCREMENT_BEFORE ↔ INCREMENT_AFTER 페어링 ===
        room_paired = pd.DataFrame()
        if not room_after.empty:
            # userId, pair_idx가 같은 레코드들을 연결
            room_paired = pd.merge(room_before, room_after, on=['userId', 'pair_idx'], suffixes=('_before', '_after'))
            
            # 동시성 제어 시간 측정 필드 생성
            room_paired['concurrency_result'] = 'COMPLETED'
            room_paired['before_people'] = room_paired['currentPeople_before']
            room_paired['after_people'] = room_paired['currentPeople_after']
            room_paired['people_increment'] = room_paired['after_people'] - room_paired['before_people']

        if not room_paired.empty:
            enhanced_results.append(room_paired)
    
    # === 모든 방 결과 통합 ===
    if not enhanced_results:
        return pd.DataFrame()
    
    result = pd.concat(enhanced_results, ignore_index=True)
    
    # === 🔧 방 번호별 구간별 스레드 정렬 ===
    # 방 번호 → 구간 번호 → 타임스탬프 순으로 정렬
    result['timestamp_before'] = pd.to_datetime(result['timestamp_before'])
    result = result.sort_values(['roomNumber_before', 'bin', 'timestamp_before']).reset_index(drop=True)
    
    # === 🔧 최종 컬럼 정리 (동시성 제어 시간 측정 중심) ===
    final_columns = {
        'roomNumber_before': 'roomNumber',
        'bin': 'bin',
        'userId': 'user_id',
        'before_people': 'before_people',
        'after_people': 'after_people',
        'people_increment': 'people_increment',
        'maxPeople_before': 'max_people',
        'room_entry_sequence': 'room_entry_sequence',
        'concurrency_result': 'concurrency_result',
        'timestamp_before': 'increment_start_time',
        'timestamp_after': 'increment_end_time'
    }
    
    # === 🔧 동시성 제어 나노초 정밀도 필드 추가 ===
    if 'nanoTime_before' in result.columns:
        final_columns['nanoTime_before'] = 'increment_nanoTime_start'
    if 'epochNano_before' in result.columns:
        final_columns['epochNano_before'] = 'increment_epochNano_start'
    if 'threadId_before' in result.columns:
        final_columns['threadId_before'] = 'thread_id_start'
    
    if 'nanoTime_after' in result.columns:
        final_columns['nanoTime_after'] = 'increment_nanoTime_end'
    if 'epochNano_after' in result.columns:
        final_columns['epochNano_after'] = 'increment_epochNano_end'
    if 'threadId_after' in result.columns:
        final_columns['threadId_after'] = 'thread_id_end'
    
    # === 최종 컬럼 선택 및 이름 변경 ===
    existing_columns = {old: new for old, new in final_columns.items() if old in result.columns}
    
    # 원하는 순서대로 컬럼 정렬 (동시성 제어 시간 측정 중심)
    desired_order = ['roomNumber', 'bin', 'user_id', 'before_people', 'after_people', 'people_increment', 'max_people', 
                     'room_entry_sequence', 'concurrency_result', 'increment_start_time', 'increment_end_time',
                     'thread_id_start', 'thread_id_end',
                     'increment_nanoTime_start', 'increment_epochNano_start',
                     'increment_nanoTime_end', 'increment_epochNano_end']
    
    # DataFrame 재구성
    result = result[list(existing_columns.keys())].rename(columns=existing_columns)
    
    # 존재하는 컬럼들만 원하는 순서로 재배열
    final_order = [col for col in desired_order if col in result.columns]
    result = result[final_order]
    
    return result

def get_enhanced_desc_table():
    """
    🔧 동시성 제어 시간 측정 중심 설명 테이블
    """
    return [
        ["속성명", "분석 목적", "도출 방법"],
        ["roomNumber", "방 번호 식별", "로그 필드: roomNumber"],
        ["bin", "방별 분석 구간", "각 방의 요청을 10개 구간으로 균등 분할"],
        ["user_id", "사용자 식별", "로그 필드: userId"],
        ["before_people", "증가 작업 전 인원수", "INCREMENT_BEFORE의 currentPeople"],
        ["after_people", "증가 작업 후 인원수", "INCREMENT_AFTER의 currentPeople"],
        ["people_increment", "실제 증가량", "after_people - before_people"],
        ["max_people", "최대 정원", "로그 필드: maxPeople"],
        ["room_entry_sequence", "방별 처리 순번", "방별 타임스탬프 기준 순번"],
        ["concurrency_result", "동시성 제어 결과", "COMPLETED (성공적으로 페어링됨)"],
        ["increment_start_time", "증가 작업 시작 시간", "INCREMENT_BEFORE 타임스탬프"],
        ["increment_end_time", "증가 작업 완료 시간", "INCREMENT_AFTER 타임스탬프"],
        ["thread_id_start", "시작 스레드 ID", "INCREMENT_BEFORE의 threadId"],
        ["thread_id_end", "종료 스레드 ID", "INCREMENT_AFTER의 threadId"],
        ["increment_nanoTime_start", "증가 작업 시작 나노초", "INCREMENT_BEFORE의 nanoTime"],
        ["increment_epochNano_start", "증가 작업 시작 Epoch 나노초", "INCREMENT_BEFORE의 epochNano"],
        ["increment_nanoTime_end", "증가 작업 완료 나노초", "INCREMENT_AFTER의 nanoTime"],
        ["increment_epochNano_end", "증가 작업 완료 Epoch 나노초", "INCREMENT_AFTER의 epochNano"]
    ]

def save_with_side_table(df_result, out_xlsx, desc_table):
    """
    Excel 파일에 데이터와 설명 테이블을 함께 저장하는 함수
    results 폴더에 저장
    """
    # results 폴더 생성
    results_dir = 'results'
    if not os.path.exists(results_dir):
        os.makedirs(results_dir)
        print(f"[폴더 생성] '{results_dir}' 폴더가 생성되었습니다.")
    
    # 파일 경로를 results 폴더 안으로 설정
    full_path = os.path.join(results_dir, out_xlsx)
    
    df_result.to_excel(full_path, index=False)
    
    wb = load_workbook(full_path)
    ws = wb.active
    
    start_col = len(df_result.columns) + 2
    
    for i, row in enumerate(desc_table):
        for j, val in enumerate(row):
            ws.cell(row=i + 1, column=start_col + j, value=val)
    
    wb.save(full_path)
    
    return full_path

def analyze_enhanced_results(df):
    """
    동시성 제어 시간 측정 분석 결과 출력
    """
    if df.empty:
        print("분석할 데이터가 없습니다.")
        return
    
    # 기본 통계 계산
    total_operations = len(df)
    completed_count = len(df[df['concurrency_result'] == 'COMPLETED'])
    
    print(f"\n=== 🔧 동시성 제어 시간 측정 분석 결과 ===")
    print(f"전체 동시성 제어 작업 수: {total_operations}")
    print(f"완료된 작업: {completed_count}건 ({completed_count/total_operations*100:.1f}%)")
    
    # 방별 통계
    room_stats = df.groupby('roomNumber').agg({
        'user_id': 'count',
        'people_increment': ['sum', 'mean'],
        'bin': 'nunique'
    })
    room_stats.columns = ['total_operations', 'total_increment', 'avg_increment', 'bin_count']
    
    print(f"\n=== 방별 동시성 제어 통계 ===")
    for room_num, stats in room_stats.iterrows():
        print(f"방 {room_num}: 총 {stats['total_operations']}회 작업, "
              f"총 증가량 {stats['total_increment']}, "
              f"평균 증가량 {stats['avg_increment']:.1f}, "
              f"구간 수: {stats['bin_count']}")
    
    # 구간별 통계 (대표적인 방 하나만)
    if 'bin' in df.columns and not df.empty:
        sample_room = df['roomNumber'].iloc[0]
        sample_room_data = df[df['roomNumber'] == sample_room]
        
        print(f"\n=== 방 {sample_room} 구간별 동시성 제어 통계 (샘플) ===")
        bin_stats = sample_room_data.groupby('bin').agg({
            'user_id': 'count',
            'people_increment': 'sum'
        }).rename(columns={'user_id': 'operations', 'people_increment': 'total_increment'})
        
        for bin_num, stats in bin_stats.iterrows():
            print(f"  구간 {bin_num}: {stats['operations']}회 작업, 총 증가량 {stats['total_increment']}")
    
    # 동시성 이슈 분석
    if 'people_increment' in df.columns:
        # 정상적이지 않은 증가량 (1이 아닌 경우)
        abnormal_increments = df[df['people_increment'] != 1]
        if not abnormal_increments.empty:
            print(f"\n비정상적인 증가량 감지: {len(abnormal_increments)}건")
            increment_counts = abnormal_increments['people_increment'].value_counts()
            for increment, count in increment_counts.items():
                print(f"  증가량 {increment}: {count}건")
        else:
            print(f"\n모든 증가량이 정상적임 (증가량 1)")
    
    # 스레드 분석
    if 'thread_id_start' in df.columns:
        unique_threads = df['thread_id_start'].nunique()
        print(f"\n스레드 분석: {unique_threads}개 고유 스레드 활동")
        
        # 스레드별 작업량
        thread_stats = df.groupby('thread_id_start').size().sort_values(ascending=False)
        print("스레드별 작업량 (상위 5개):")
        for thread_id, count in thread_stats.head().items():
            print(f"  스레드 {thread_id}: {count}회 작업")

def main():
    """
    🔧 동시성 제어 시간 측정 중심 메인 함수
    """
    parser = argparse.ArgumentParser(description="동시성 제어 시간 측정 전처리기 (INCREMENT_BEFORE ↔ INCREMENT_AFTER)")
    parser.add_argument('--room', type=int, help='특정 방 번호만 처리 (옵션)')
    parser.add_argument('--csv', type=str, help='추가 CSV 파일명 (옵션)')
    parser.add_argument('--xlsx', type=str, help='Excel 파일명 (옵션)')
    
    args = parser.parse_args()
    
    try:
        print("🔧 동시성 제어 시간 측정 전처리기 시작...")
        print("📋 INCREMENT_BEFORE ↔ INCREMENT_AFTER 페어링으로 동시성 제어 시간 측정")
        print("📋 각 방별로 10개 구간 분할, 타임스탬프 기준 스레드 정렬")
        
        # 1단계: 로그 파일 교체
        print("1. 로그 파일 교체 중...")
        replace_log_file()
        
        # 2단계: 로그 파싱 (동시성 제어 이벤트)
        print("2. 동시성 제어 이벤트 파싱 중...")
        df = parse_logs(LOG_FILE, room_number=args.room)
        print(f"   파싱된 이벤트 수: {len(df)}")
        
        # 3단계: 방별 구간 분할 및 페어링
        print("3. 방별 구간 분할 및 동시성 제어 페어링 처리 중...")
        result = build_paired_data_with_enhanced_binning(df)
        print(f"   페어링된 작업 수: {len(result)}")
        
        # 4단계: 결과 저장
        print("4. 결과 저장 중...")
        
        # results 폴더 생성
        results_dir = 'results'
        if not os.path.exists(results_dir):
            os.makedirs(results_dir)
            print(f"[폴더 생성] '{results_dir}' 폴더가 생성되었습니다.")
        
        # 기본 CSV 파일 저장
        if args.room:
            base_filename = f'concurrency_timing_room{args.room}.csv'
        else:
            base_filename = 'concurrency_timing_all_rooms.csv'
        
        # CSV 파일도 results 폴더에 저장
        csv_path = os.path.join(results_dir, base_filename)
        result.to_csv(csv_path, index=False, encoding='utf-8-sig')
        print(f"   CSV 저장 완료: {csv_path}")
        
        # 추가 파일 저장
        if args.csv:
            additional_csv_path = os.path.join(results_dir, args.csv)
            result.to_csv(additional_csv_path, index=False, encoding='utf-8-sig')
            print(f"   추가 CSV 저장 완료: {additional_csv_path}")
        
        if args.xlsx:
            desc_table = get_enhanced_desc_table()
            xlsx_full_path = save_with_side_table(result, args.xlsx, desc_table)
            print(f"   Excel 저장 완료: {xlsx_full_path}")
        
        # 5단계: 결과 분석
        print("5. 결과 분석 중...")
        analyze_enhanced_results(result)
        
        print("\n✅ 동시성 제어 시간 측정 전처리 완료!")
        print("🎯 방별 10개 구간 분할 및 INCREMENT 이벤트 페어링 완료!")
        
    except Exception as e:
        print(f"❌ 오류 발생: {e}")
        import traceback
        traceback.print_exc()

if __name__ == '__main__':
    main()
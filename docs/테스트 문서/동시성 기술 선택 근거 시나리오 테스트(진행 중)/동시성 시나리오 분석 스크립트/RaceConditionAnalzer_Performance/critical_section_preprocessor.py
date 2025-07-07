import pandas as pd
import re
import os
import shutil
import argparse
from openpyxl import load_workbook

LOG_FILE = 'ChatService.log'
NEW_LOG_PATH = r'E:\devSpace\ChatServiceTest\log\ChatService.log'

def replace_log_file():
    if os.path.exists(LOG_FILE):
        os.remove(LOG_FILE)
    shutil.copy(NEW_LOG_PATH, LOG_FILE)

def parse_five_events_clean(filepath, room_number=None):
    critical_pattern = re.compile(
        r'CRITICAL_SECTION_MARK tag=(?P<tag>WAITING_START|CRITICAL_ENTER|CRITICAL_LEAVE)'
        r' timestampIso=(?P<timestamp>[\w\-\:\.TZ]+)'
        r' event=(?P<event>\w+)'
        r'.* roomNumber=(?P<roomNumber>\d+)'
        r' userId=(?P<userId>[\w\-\_]+)'
        r'.* nanoTime=(?P<nanoTime>\d+)'
        r'.* epochNano=(?P<epochNano>\d+)'
    )
    
    increment_pattern = re.compile(
        r'timestampIso=(?P<timestamp>[\w\-\:\.TZ]+)'
        r' event=(?P<tag>INCREMENT_BEFORE|INCREMENT_AFTER)'
        r' roomNumber=(?P<roomNumber>\d+)'
        r' userId=(?P<userId>[\w\-\_]+)'
        r'.* epochNano=(?P<epochNano>\d+)'
        r'.* nanoTime=(?P<nanoTime>\d+)'
    )
    
    records = []
    
    with open(filepath, encoding='utf-8') as f:
        for line in f:
            data = None
            
            match = critical_pattern.search(line)
            if match:
                data = match.groupdict()
                data['event_type'] = data['event']
            else:
                match = increment_pattern.search(line)
                if match:
                    data = match.groupdict()
                    data['event_type'] = None
            
            if data:
                data['roomNumber'] = int(data['roomNumber'])
                # 나노초 값들을 문자열로 유지 (정밀도 보존)
                data['nanoTime'] = data['nanoTime']
                data['epochNano'] = data['epochNano']
                
                if room_number is None or data['roomNumber'] == room_number:
                    records.append(data)
    
    return pd.DataFrame(records)

def build_clean_performance_data(df):
    if df.empty:
        return pd.DataFrame()
    
    df['timestamp'] = pd.to_datetime(df['timestamp'])
    
    # nanoTime과 epochNano를 숫자로 변환하여 정렬에 사용
    df['nanoTime_num'] = df['nanoTime'].astype(float)
    df['epochNano_num'] = df['epochNano'].astype(float)
    
    # 전체 데이터를 타임스탬프와 나노타임 기준으로 정렬
    df = df.sort_values(['timestamp', 'nanoTime_num', 'epochNano_num']).reset_index(drop=True)
    
    performance_results = []
    unique_rooms = df['roomNumber'].unique()
    
    for room_num in unique_rooms:
        room_df = df[df['roomNumber'] == room_num].copy()
        
        # 방별로도 타임스탬프 기준 정렬
        room_df = room_df.sort_values(['timestamp', 'nanoTime_num', 'epochNano_num']).reset_index(drop=True)
        
        user_groups = room_df.groupby('userId')
        room_sequence = 0
        
        # 사용자별 첫 번째 이벤트 시간으로 사용자 순서 결정
        user_first_times = []
        for user_id, user_data in user_groups:
            first_time = user_data.sort_values(['timestamp', 'nanoTime_num', 'epochNano_num']).iloc[0]
            user_first_times.append((first_time['timestamp'], first_time['nanoTime_num'], first_time['epochNano_num'], user_id, user_data))
        
        # 첫 번째 이벤트 시간 순으로 사용자 정렬
        user_first_times.sort(key=lambda x: (x[0], x[1], x[2]))
        
        for first_time, first_nano, first_epoch, user_id, user_data in user_first_times:
            # 사용자별 이벤트를 시간순으로 정렬
            user_data = user_data.sort_values(['timestamp', 'nanoTime_num', 'epochNano_num']).reset_index(drop=True)
            room_sequence += 1
            
            events = {}
            for _, row in user_data.iterrows():
                event_name = row['tag']
                events[event_name] = {
                    'timestamp': row['timestamp'],
                    'nanoTime': str(int(float(row['nanoTime']))),
                    'epochNano': str(int(float(row['epochNano']))),
                    'event_type': row.get('event_type')
                }
            
            profile = {
                'roomNumber': room_num,
                'user_id': user_id,
                'room_entry_sequence': room_sequence
            }
            
            if 'WAITING_START' in events:
                profile.update({
                    'waiting_start_time': events['WAITING_START']['timestamp'],
                    'waiting_start_nanoTime': events['WAITING_START']['nanoTime'],
                    'waiting_start_epochNano': events['WAITING_START']['epochNano'],
                    'waiting_start_event_type': events['WAITING_START']['event_type']
                })
            
            if 'CRITICAL_ENTER' in events:
                profile.update({
                    'critical_enter_time': events['CRITICAL_ENTER']['timestamp'],
                    'critical_enter_nanoTime': events['CRITICAL_ENTER']['nanoTime'],
                    'critical_enter_epochNano': events['CRITICAL_ENTER']['epochNano'],
                    'critical_enter_event_type': events['CRITICAL_ENTER']['event_type']
                })
            
            if 'CRITICAL_LEAVE' in events:
                profile.update({
                    'critical_leave_time': events['CRITICAL_LEAVE']['timestamp'],
                    'critical_leave_nanoTime': events['CRITICAL_LEAVE']['nanoTime'],
                    'critical_leave_epochNano': events['CRITICAL_LEAVE']['epochNano'],
                    'critical_leave_event_type': events['CRITICAL_LEAVE']['event_type']
                })
                
                if events['CRITICAL_LEAVE']['event_type'] == 'SUCCESS':
                    profile['join_result'] = 'SUCCESS'
                elif events['CRITICAL_LEAVE']['event_type'] == 'FAIL_OVER_CAPACITY':
                    profile['join_result'] = 'FAIL_OVER_CAPACITY'
                else:
                    profile['join_result'] = 'UNKNOWN'
            
            if 'INCREMENT_BEFORE' in events:
                profile.update({
                    'increment_before_time': events['INCREMENT_BEFORE']['timestamp'],
                    'increment_before_nanoTime': events['INCREMENT_BEFORE']['nanoTime'],
                    'increment_before_epochNano': events['INCREMENT_BEFORE']['epochNano']
                })
            
            if 'INCREMENT_AFTER' in events:
                profile.update({
                    'increment_after_time': events['INCREMENT_AFTER']['timestamp'],
                    'increment_after_nanoTime': events['INCREMENT_AFTER']['nanoTime'],
                    'increment_after_epochNano': events['INCREMENT_AFTER']['epochNano']
                })
            
            performance_results.append(profile)
    
    if not performance_results:
        return pd.DataFrame()
    
    result_df = pd.DataFrame(performance_results)
    
    # 모든 나노초 컬럼을 문자열로 확실히 변환
    nano_columns = [col for col in result_df.columns if 'nanoTime' in col or 'epochNano' in col]
    for col in nano_columns:
        if col in result_df.columns:
            result_df[col] = result_df[col].astype(str)
    
    # 방별 10개 구간 분할
    for room_num in result_df['roomNumber'].unique():
        room_mask = result_df['roomNumber'] == room_num
        room_data = result_df[room_mask]
        
        total_requests = len(room_data)
        if total_requests <= 10:
            bins = range(1, total_requests + 1)
        else:
            bins = pd.cut(range(total_requests), bins=10, labels=range(1, 11)).astype(int)
        
        result_df.loc[room_mask, 'bin'] = bins
    
    base_columns = ['roomNumber', 'bin', 'user_id', 'room_entry_sequence', 'join_result']
    
    event_columns = [
        'waiting_start_time', 'waiting_start_nanoTime', 'waiting_start_epochNano', 'waiting_start_event_type',
        'critical_enter_time', 'critical_enter_nanoTime', 'critical_enter_epochNano', 'critical_enter_event_type',
        'critical_leave_time', 'critical_leave_nanoTime', 'critical_leave_epochNano', 'critical_leave_event_type',
        'increment_before_time', 'increment_before_nanoTime', 'increment_before_epochNano',
        'increment_after_time', 'increment_after_nanoTime', 'increment_after_epochNano'
    ]
    
    all_columns = base_columns + event_columns
    existing_columns = [col for col in all_columns if col in result_df.columns]
    
    result_df = result_df[existing_columns]
    
    # 최종 정렬: 방 번호 -> 첫 번째 이벤트 시간 순서
    if 'waiting_start_time' in result_df.columns:
        result_df['waiting_start_nanoTime_num'] = pd.to_numeric(result_df['waiting_start_nanoTime'])
        result_df['waiting_start_epochNano_num'] = pd.to_numeric(result_df['waiting_start_epochNano'])
        
        result_df = result_df.sort_values([
            'roomNumber', 
            'waiting_start_time', 
            'waiting_start_nanoTime_num', 
            'waiting_start_epochNano_num'
        ])
        
        result_df = result_df.drop(columns=['waiting_start_nanoTime_num', 'waiting_start_epochNano_num'])
    elif 'critical_enter_time' in result_df.columns:
        result_df['critical_enter_nanoTime_num'] = pd.to_numeric(result_df['critical_enter_nanoTime'])
        result_df['critical_enter_epochNano_num'] = pd.to_numeric(result_df['critical_enter_epochNano'])
        
        result_df = result_df.sort_values([
            'roomNumber', 
            'critical_enter_time', 
            'critical_enter_nanoTime_num', 
            'critical_enter_epochNano_num'
        ])
        
        result_df = result_df.drop(columns=['critical_enter_nanoTime_num', 'critical_enter_epochNano_num'])
    else:
        result_df = result_df.sort_values(['roomNumber', 'room_entry_sequence'])
    
    result_df = result_df.reset_index(drop=True)
    
    return result_df

def get_clean_event_desc_table():
    return [
        ["속성명", "측정 목적", "도출 방법"],
        ["roomNumber", "방 번호 식별", "로그 필드: roomNumber"],
        ["bin", "방별 분석 구간", "각 방의 요청을 10개 구간으로 균등 분할"],
        ["user_id", "사용자 식별", "로그 필드: userId"],
        ["room_entry_sequence", "방별 처리 순번", "방별 타임스탬프 기준 순번"],
        ["join_result", "입장 결과 구분", "SUCCESS/FAIL_OVER_CAPACITY/UNKNOWN"],
        ["waiting_start_*", "대기 시작 시점", "WAITING_START 이벤트 속성들"],
        ["critical_enter_*", "임계구역 진입 시점", "CRITICAL_ENTER 이벤트 속성들"],
        ["critical_leave_*", "임계구역 진출 시점", "CRITICAL_LEAVE 이벤트 속성들"],
        ["increment_before_*", "증가 작업 시작 시점", "INCREMENT_BEFORE 이벤트 속성들"],
        ["increment_after_*", "증가 작업 완료 시점", "INCREMENT_AFTER 이벤트 속성들"],
        ["*_time", "이벤트 발생 시간", "timestampIso (스레드 요청 순서)"],
        ["*_nanoTime", "나노초 정밀도 시간", "System.nanoTime()"],
        ["*_epochNano", "Epoch 나노초 시간", "Epoch 기준 나노초"],
        ["*_event_type", "이벤트 상세 타입", "SUCCESS/FAIL_OVER_CAPACITY 등"]
    ]

def save_with_side_table(df_result, out_xlsx, desc_table):
    results_dir = 'results'
    if not os.path.exists(results_dir):
        os.makedirs(results_dir)
    
    full_path = os.path.join(results_dir, out_xlsx)
    
    # timezone을 제거한 복사본 생성
    df_excel = df_result.copy()
    
    # datetime 컬럼들의 timezone 제거
    for col in df_excel.columns:
        if df_excel[col].dtype == 'datetime64[ns, UTC]' or pd.api.types.is_datetime64_tz_dtype(df_excel[col]):
            df_excel[col] = df_excel[col].dt.tz_localize(None)
    
    # Excel 저장 시에도 나노초 값들을 문자열로 처리
    with pd.ExcelWriter(full_path, engine='openpyxl') as writer:
        df_excel.to_excel(writer, index=False)
        
        # 워크북과 시트 가져오기
        workbook = writer.book
        worksheet = writer.sheets['Sheet1']
        
        # 나노초 컬럼들을 텍스트 형식으로 설정
        nano_columns = [col for col in df_result.columns if 'nanoTime' in col or 'epochNano' in col]
        for col_name in nano_columns:
            col_idx = df_result.columns.get_loc(col_name) + 1  # Excel은 1부터 시작
            for row in range(2, len(df_result) + 2):  # 헤더 제외
                cell = worksheet.cell(row=row, column=col_idx)
                cell.number_format = '@'  # 텍스트 형식
    
    # 설명 테이블 추가
    wb = load_workbook(full_path)
    ws = wb.active
    
    start_col = len(df_result.columns) + 2
    
    for i, row in enumerate(desc_table):
        for j, val in enumerate(row):
            ws.cell(row=i + 1, column=start_col + j, value=val)
    
    wb.save(full_path)
    
    return full_path

def analyze_clean_results(df):
    if df.empty:
        print("분석할 데이터가 없습니다.")
        return
    
    total_operations = len(df)
    
    print(f"\n=== 레이스 컨디션 지표 제거된 5개 이벤트 성능 측정 분석 결과 ===")
    print(f"전체 처리 작업 수: {total_operations}")
    
    if 'join_result' in df.columns:
        print(f"\n=== 입장 결과별 분석 ===")
        join_result_counts = df['join_result'].value_counts()
        for result, count in join_result_counts.items():
            print(f"{result}: {count}건 ({count/total_operations*100:.1f}%)")
    
    events = ['waiting_start_time', 'critical_enter_time', 'critical_leave_time', 'increment_before_time', 'increment_after_time']
    
    print(f"\n=== 이벤트 완성도 분석 ===")
    for event in events:
        if event in df.columns:
            complete_count = df[event].notna().sum()
            print(f"{event}: {complete_count}건 ({complete_count/total_operations*100:.1f}%)")
    
    if 'critical_leave_event_type' in df.columns:
        print(f"\n=== CRITICAL_LEAVE 이벤트 타입별 분석 ===")
        critical_leave_types = df['critical_leave_event_type'].value_counts()
        for event_type, count in critical_leave_types.items():
            if pd.notna(event_type):
                print(f"{event_type}: {count}건")
    
    complete_sessions = 0
    success_complete_sessions = 0
    fail_complete_sessions = 0
    
    for _, row in df.iterrows():
        if all(pd.notna(row.get(event)) for event in events):
            complete_sessions += 1
            if row.get('join_result') == 'SUCCESS':
                success_complete_sessions += 1
            elif row.get('join_result') == 'FAIL_OVER_CAPACITY':
                fail_complete_sessions += 1
    
    print(f"\n=== 완전한 5개 이벤트 세션 분석 ===")
    print(f"완전한 5개 이벤트 세션: {complete_sessions}건 ({complete_sessions/total_operations*100:.1f}%)")
    print(f"  └ 성공 세션: {success_complete_sessions}건")
    print(f"  └ 실패 세션: {fail_complete_sessions}건")
    
    room_stats = df.groupby('roomNumber').agg({
        'user_id': 'count',
        'bin': 'nunique'
    })
    room_stats.columns = ['total_operations', 'bin_count']
    
    print(f"\n=== 방별 성능 통계 ===")
    for room_num, stats in room_stats.iterrows():
        print(f"방 {room_num}: 총 {stats['total_operations']}회 작업, 구간 수: {stats['bin_count']}")
        
        if 'join_result' in df.columns:
            room_data = df[df['roomNumber'] == room_num]
            room_results = room_data['join_result'].value_counts()
            for result, count in room_results.items():
                print(f"  └ {result}: {count}건")
    
    print(f"\n=== 타임스탬프 정렬 확인 ===")
    if 'waiting_start_time' in df.columns:
        first_time = df['waiting_start_time'].iloc[0] if len(df) > 0 else None
        last_time = df['waiting_start_time'].iloc[-1] if len(df) > 0 else None
        print(f"첫 번째 이벤트 시간: {first_time}")
        print(f"마지막 이벤트 시간: {last_time}")
    
    # 나노초 값 샘플 출력 (정밀도 확인)
    print(f"\n=== 나노초 정밀도 확인 (첫 5개 샘플) ===")
    nano_columns = [col for col in df.columns if 'nanoTime' in col]
    if nano_columns:
        for i in range(min(5, len(df))):
            print(f"샘플 {i+1}:")
            for col in nano_columns[:2]:
                if col in df.columns:
                    print(f"  {col}: {df.iloc[i][col]}")

def main():
    parser = argparse.ArgumentParser(description="레이스 컨디션 지표 제거된 5개 이벤트 성능 측정 전처리기")
    parser.add_argument('--room', type=int, help='특정 방 번호만 처리 (옵션)')
    parser.add_argument('--csv', type=str, help='추가 CSV 파일명 (옵션)')
    parser.add_argument('--xlsx', type=str, help='Excel 파일명 (옵션)')
    
    args = parser.parse_args()
    
    try:
        replace_log_file()
        df = parse_five_events_clean(LOG_FILE, room_number=args.room)
        result = build_clean_performance_data(df)
        
        results_dir = 'results'
        if not os.path.exists(results_dir):
            os.makedirs(results_dir)
        
        if args.room:
            base_filename = f'clean_five_events_performance_room{args.room}.csv'
        else:
            base_filename = 'clean_five_events_performance_all_rooms.csv'
        
        csv_path = os.path.join(results_dir, base_filename)
        
        # 나노초 컬럼들이 이미 문자열인지 확인
        nano_columns = [col for col in result.columns if 'nanoTime' in col or 'epochNano' in col]
        
        # CSV 저장 시 float_format 옵션 사용하여 지수 표기법 방지
        # 나노초 컬럼들을 임시로 object 타입으로 변환
        result_copy = result.copy()
        for col in nano_columns:
            if col in result_copy.columns:
                # 정수로 변환 후 문자열로 저장 (NaN 처리 개선)
                def convert_nano_value(x):
                    if pd.isna(x) or x == '' or str(x).lower() == 'nan':
                        return ''
                    try:
                        return str(int(float(x)))
                    except (ValueError, TypeError):
                        return str(x)  # 변환 실패 시 원본 문자열 반환
                
                result_copy[col] = result_copy[col].apply(convert_nano_value)
        
        # CSV 저장 (지수 표기법 방지)
        result_copy.to_csv(csv_path, index=False, encoding='utf-8-sig')
        
        if args.csv:
            additional_csv_path = os.path.join(results_dir, args.csv)
            result_copy.to_csv(additional_csv_path, index=False, encoding='utf-8-sig')
        
        if args.xlsx:
            desc_table = get_clean_event_desc_table()
            save_with_side_table(result, args.xlsx, desc_table)
        
        analyze_clean_results(result)
        
    except Exception as e:
        print(f"오류 발생: {e}")
        import traceback
        traceback.print_exc()

if __name__ == '__main__':
    main()
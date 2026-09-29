#!/usr/bin/env python3
"""
Semaphore 전용 Race Condition 분석기
- 정원 초과 검증에만 집중
- CAS 특성 반영: 대기/진입 개념 제거, tryAcquire() 즉시 반환 특성 고려
- 규칙 2: contention_group_size, contention_user_ids 컬럼 추가
"""

import pandas as pd
import numpy as np
from datetime import datetime
import argparse
from openpyxl import load_workbook

def find_semaphore_concurrent_groups(room_df):
    """세마포어 동시 실행 그룹 찾기 (나노초 정밀도 기반)"""
    concurrent_groups = {}
    
    for i, row1 in room_df.iterrows():
        start1_nano = row1['true_critical_section_nanoTime_start']
        end1_nano = row1['true_critical_section_nanoTime_end']
        user1 = row1['user_id']
        
        if pd.isna(start1_nano) or pd.isna(end1_nano):
            continue
            
        overlapping_users = [user1]
        
        for j, row2 in room_df.iterrows():
            if i == j:
                continue
                
            start2_nano = row2['true_critical_section_nanoTime_start']
            end2_nano = row2['true_critical_section_nanoTime_end']
            user2 = row2['user_id']
            
            if pd.isna(start2_nano) or pd.isna(end2_nano):
                continue
            
            # 나노초 기준 시간 겹침 확인
            if not (end1_nano <= start2_nano or end2_nano <= start1_nano):
                overlapping_users.append(user2)
        
        # 동시 실행이 발생한 경우 (2명 이상)
        if len(overlapping_users) >= 2:
            for user_id in overlapping_users:
                concurrent_groups[user_id] = {
                    'group_size': len(overlapping_users),
                    'user_ids': overlapping_users
                }
    
    return concurrent_groups

def detect_semaphore_anomalies(df):
    """
    Semaphore 전용 이상 현상 탐지: 정원 초과 + 동시 실행 패턴
    """
    
    print("🔍 Semaphore 이상 현상 탐지 시작...")
    
    # 결과 저장 리스트
    anomalies = []
    detailed_analysis = []
    
    print(f"분석 대상 방: {df['roomNumber'].unique()}")
    
    # 방별로 분석
    for room_num in df['roomNumber'].unique():
        print(f"  방 {room_num} 분석 중...")
        room_df = df[df['roomNumber'] == room_num].copy()
        
        # === 세마포어 동시 실행 그룹 찾기 ===
        concurrent_groups = find_semaphore_concurrent_groups(room_df)
        
        # === 각 레코드 검사 ===
        for idx in room_df.index:
            row = room_df.loc[idx]
            anomaly_types = []
            anomaly_details = {}
            
            # 규칙 3: 정원 초과 오류만 검사
            if row['curr_people'] > row['max_people']:
                anomaly_types.append('정원 초과 오류')
                anomaly_details['over_capacity_amount'] = row['curr_people'] - row['max_people']
                anomaly_details['over_capacity_curr'] = row['curr_people']
                anomaly_details['over_capacity_max'] = row['max_people']
            
            # 결과 행 생성 (이상현상 여부와 관계없이)
            result_row = row.to_dict()
            result_row['anomaly_type'] = ', '.join(anomaly_types) if anomaly_types else ''
            
            # 기본값 설정 (정원 초과 관련 + 규칙 2 컬럼)
            user_id = row['user_id']
            default_values = {
                'over_capacity_amount': 0,
                'over_capacity_curr': 0,
                'over_capacity_max': 0,
                'contention_group_size': 1,
                'contention_user_ids': user_id
            }
            
            # 규칙 2: 동시 실행 그룹 정보 설정
            if user_id in concurrent_groups:
                concurrent_info = concurrent_groups[user_id]
                default_values['contention_group_size'] = concurrent_info['group_size']
                default_values['contention_user_ids'] = ', '.join(concurrent_info['user_ids'])
            
            # 기본값 적용 후 실제값으로 덮어쓰기
            for key, default_val in default_values.items():
                if key not in anomaly_details:
                    result_row[key] = default_val
                else:
                    result_row[key] = anomaly_details[key]
            
            # 나머지 anomaly_details 추가
            for key, value in anomaly_details.items():
                if key not in default_values:
                    result_row[key] = value
            
            anomalies.append(result_row)
            
            # 이상 현상이 있는 경우만 상세 분석 텍스트 생성
            if anomaly_types:
                detailed_text = generate_analysis_text(row, anomaly_types, anomaly_details, room_num)
                detailed_analysis.append(detailed_text)
    
    print(f"✅ 전체 레코드 처리 완료: {len(anomalies)}건")
    
    # 이상현상이 있는 레코드만 카운트
    actual_anomalies = [a for a in anomalies if a['anomaly_type'] != '']
    print(f"✅ 정원 초과 오류 탐지 완료: {len(actual_anomalies)}건 발견")
    
    return anomalies, detailed_analysis

def generate_analysis_text(row, anomaly_types, anomaly_details, room_num):
    """정원 초과 케이스에 대한 분석 텍스트만 생성"""
    text = f"""
================================================================================
Semaphore 정원 초과 오류 분석
================================================================================
방 번호: {room_num}
사용자 ID: {row['user_id']}
Bin: {row['bin']}
입장 결과: {row['join_result']}
원본 방 입장 순번: {row['room_entry_sequence']}

기본 정보:
 이전 인원 (prev_people): {row['prev_people']}명
 현재 인원 (curr_people): {row['curr_people']}명
 최대 정원 (max_people): {row['max_people']}명

tryAcquire() 실행 정보:
 나노초 시작: {row.get('true_critical_section_nanoTime_start', 'N/A')}
 나노초 끝: {row.get('true_critical_section_nanoTime_end', 'N/A')}

상세 분석:"""

    for anomaly_type in anomaly_types:
        if anomaly_type == '정원 초과 오류':
            text += f"""
 [정원 초과 오류 (Capacity Exceeded Error)]
  - 최대 정원: {anomaly_details.get('over_capacity_max', 'N/A')}명
  - 실제 인원: {anomaly_details.get('over_capacity_curr', 'N/A')}명
  - 초과 인원: {anomaly_details.get('over_capacity_amount', 'N/A')}명
  → Semaphore 허가 시스템이 정원 제한을 올바르게 수행하지 못한 심각한 오류"""

    # tryAcquire() 실행 시간 계산
    start_nano = row.get('true_critical_section_nanoTime_start')
    end_nano = row.get('true_critical_section_nanoTime_end')
    if pd.notna(start_nano) and pd.notna(end_nano):
        execution_time = end_nano - start_nano
        text += f"""

tryAcquire() 성능 정보:
 실행 시간: {execution_time} 나노초
 실행 시간 (마이크로초): {execution_time / 1000:.3f} μs
"""
    
    return text

def print_semaphore_statistics(df, anomaly_df):
    """Semaphore 전용 분석 결과 통계 출력"""
    print("\n=== 🎯 Semaphore 분석 결과 ===")
    print(f"전체 요청 수: {len(df)}")
    
    # 허가 성공/실패 통계
    success_count = len(df[df['join_result'] == 'SUCCESS'])
    fail_count = len(df[df['join_result'] == 'FAIL'])
    total_requests = len(df)
    
    success_rate = (success_count / total_requests * 100) if total_requests > 0 else 0
    fail_rate = (fail_count / total_requests * 100) if total_requests > 0 else 0
    
    print(f"허가 획득 성공: {success_count}건 ({success_rate:.1f}%)")
    print(f"허가 획득 실패: {fail_count}건 ({fail_rate:.1f}%)")
    
    # 정원 초과 통계
    actual_anomalies = anomaly_df[anomaly_df['anomaly_type'] != '']
    capacity_exceeded = len(actual_anomalies)
    capacity_exceeded_rate = (capacity_exceeded / total_requests * 100) if total_requests > 0 else 0
    
    print(f"정원 초과 오류: {capacity_exceeded}건 ({capacity_exceeded_rate:.1f}%)")
    
    # 동시 실행 패턴 통계 (규칙 2)
    concurrent_2plus = len(anomaly_df[anomaly_df['contention_group_size'] >= 2])
    if concurrent_2plus > 0:
        concurrent_rate = (concurrent_2plus / total_requests * 100) if total_requests > 0 else 0
        max_group_size = anomaly_df['contention_group_size'].max()
        print(f"동시 실행 패턴: {concurrent_2plus}건 ({concurrent_rate:.1f}%) - 최대 {max_group_size}개 스레드")
    
    # tryAcquire() 실행 시간 통계
    if 'true_critical_section_nanoTime_start' in df.columns and 'true_critical_section_nanoTime_end' in df.columns:
        execution_times = df['true_critical_section_nanoTime_end'] - df['true_critical_section_nanoTime_start']
        execution_times = execution_times.dropna()
        
        if len(execution_times) > 0:
            print(f"\n=== ⚡ tryAcquire() 성능 지표 ===")
            print(f"평균 실행 시간: {execution_times.mean():.0f} ns")
            print(f"최소 실행 시간: {execution_times.min():.0f} ns")
            print(f"최대 실행 시간: {execution_times.max():.0f} ns")
            print(f"중앙값 실행 시간: {execution_times.median():.0f} ns")
            print(f"실행 시간 데이터: {len(execution_times)}건 ({len(execution_times)/len(df)*100:.1f}%)")
    
    # Semaphore 효과성 평가
    print(f"\n=== 🛡️ Semaphore 효과성 평가 ===")
    if capacity_exceeded == 0:
        print("✅ 정원 초과 방지: 완벽 (0건)")
        print("✅ Semaphore가 정원 제한 기능을 올바르게 수행")
    else:
        print(f"❌ 정원 초과 방지: 실패 ({capacity_exceeded}건)")
        print("❌ Semaphore 구현 또는 설정에 문제 가능성")
    
    print(f"허가 기반 처리량 제어: {success_rate:.1f}% 성공률로 시스템 보호")

def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(description="Semaphore 전용 Race Condition 분석기")
    parser.add_argument('input_csv', help='입력 CSV 파일 (preprocessor_semaphore.csv)')
    parser.add_argument('output_csv', help='출력 CSV 파일')
    parser.add_argument('--detailed_output', default='semaphore_detailed_analysis.txt', help='상세 분석 텍스트 파일')
    parser.add_argument('--rooms', help='분석할 방 번호 (쉼표로 구분)')
    parser.add_argument('--xlsx_output', help='Excel 출력 파일 (선택사항)')
    
    args = parser.parse_args()
    
    try:
        print("🚀 Semaphore 전용 Race Condition 분석기 시작...")
        
        # CSV 파일 읽기
        df = pd.read_csv(args.input_csv)
        print(f"✅ CSV 파일 읽기 완료: {len(df)}행, {len(df.columns)}컬럼")
        
        # 필수 컬럼 확인 (Semaphore 전용)
        required_columns = ['roomNumber', 'bin', 'user_id', 'prev_people', 'curr_people', 
                           'max_people', 'room_entry_sequence', 'join_result',
                           'true_critical_section_nanoTime_start', 'true_critical_section_nanoTime_end']
        
        missing_columns = [col for col in required_columns if col not in df.columns]
        if missing_columns:
            print(f"❌ 필수 컬럼 누락: {missing_columns}")
            return
        
        print("✅ Semaphore 전용 필수 컬럼 확인 완료")
        
        # 방 번호 필터링
        if args.rooms:
            room_numbers = [int(room.strip()) for room in args.rooms.split(',')]
            df = df[df['roomNumber'].isin(room_numbers)]
            print(f"🔍 방 번호 {room_numbers}로 필터링: {len(df)} 레코드")
        
        # 메인 분석 실행
        all_records, detailed_analysis = detect_semaphore_anomalies(df)
        
        # 결과 DataFrame 생성 (모든 레코드 포함)
        result_df = pd.DataFrame(all_records)
        
        # 컬럼 순서 정리 (Semaphore 전용 + 규칙 2 컬럼 추가)
        basic_cols = ['roomNumber', 'bin', 'user_id', 'prev_people', 'curr_people', 
                     'max_people', 'join_result', 'room_entry_sequence',
                     'true_critical_section_nanoTime_start', 'true_critical_section_nanoTime_end',
                     'anomaly_type']
        
        detail_cols = ['over_capacity_amount', 'over_capacity_curr', 'over_capacity_max',
                      'contention_group_size', 'contention_user_ids']
        
        # 모든 컬럼 포함 (기존 14개 + 규칙 2 컬럼 2개 = 16개)
        all_cols = basic_cols + detail_cols
        existing_cols = [col for col in all_cols if col in result_df.columns]
        result_df = result_df[existing_cols]
        result_df = result_df.fillna('')
        
        # CSV 저장
        result_df.to_csv(args.output_csv, index=False, encoding='utf-8-sig')
        print(f"💾 Semaphore 분석 결과 {len(result_df)}개가 {args.output_csv}에 저장되었습니다.")
        
        # Excel 저장 (항상 생성)
        excel_filename = args.xlsx_output if args.xlsx_output else args.output_csv.replace('.csv', '.xlsx')
        result_df.to_excel(excel_filename, index=False)
        print(f"📊 Excel 파일 저장됨: {excel_filename}")
        
        # 상세 분석 텍스트 저장 (정원 초과 오류가 있는 경우만)
        with open(args.detailed_output, 'w', encoding='utf-8') as f:
            f.write("Semaphore 정원 초과 오류 상세 분석 결과\n")
            f.write(f"생성일시: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write(f"입력 파일: {args.input_csv}\n")
            f.write(f"총 정원 초과 오류 수: {len(detailed_analysis)}\n\n")
            
            if len(detailed_analysis) == 0:
                f.write("🎉 정원 초과 오류 없음 - Semaphore가 올바르게 작동함\n")
            else:
                for detailed_text in detailed_analysis:
                    f.write(detailed_text)
                    f.write("\n")
        
        print(f"📄 상세 분석 결과 저장: {args.detailed_output}")
        
        # 통계 출력
        print_semaphore_statistics(df, result_df)
        
        print("\n🎉 Semaphore 분석 완료!")
        
    except Exception as e:
        print(f"❌ 오류 발생: {e}")
        import traceback
        traceback.print_exc()

if __name__ == '__main__':
    main()
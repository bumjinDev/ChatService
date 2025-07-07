#!/usr/bin/env python3
"""
성능 분석 지표 계산 스크립트 (v4.0) - 나노초 컬럼 직접 활용
전처리된 성능 테스트 결과(clean_*.csv)를 입력받아 통계 지표를 계산하고 Excel로 저장
"""

import pandas as pd
import numpy as np
import os
import argparse
from datetime import datetime
import sys
from openpyxl import load_workbook

def calculate_rate(count, total):
    """전체 대비 비율 계산 (0으로 나누기 방지)"""
    if isinstance(count, pd.Series) or isinstance(total, pd.Series):
        return np.where(total != 0, (count / total) * 100, 0)
    return (count / total) * 100 if total > 0 else 0

def get_stats(series, metric_name=""):
    """시리즈의 통계값 계산 (Mean, Median, Max)"""
    if len(series) == 0:
        return {
            'Mean': 0.0,
            'Median': 0.0,
            'Max': 0.0
        }
    
    # NaN이 아닌 값들만 사용
    valid_series = series.dropna()
    if len(valid_series) == 0:
        return {
            'Mean': 0.0,
            'Median': 0.0,
            'Max': 0.0
        }
    
    # 기본 통계
    mean_val = float(valid_series.mean())
    median_val = float(valid_series.median())
    max_val = float(valid_series.max())
    
    print(f"    - {metric_name} 통계: Mean={mean_val:.3f}ms, Median={median_val:.3f}ms, Max={max_val:.3f}ms (유효 샘플: {len(valid_series)}개)")
    
    # 0이 아닌 값들의 통계도 출력
    non_zero = valid_series[valid_series > 0]
    if len(non_zero) > 0:
        print(f"      0이 아닌 값들: Mean={non_zero.mean():.3f}ms, Median={non_zero.median():.3f}ms (샘플: {len(non_zero)}개)")
    
    return {
        'Mean': mean_val,
        'Median': median_val,
        'Max': max_val
    }

def calculate_time_diff_nano(start_nano_str, end_nano_str):
    """나노초 문자열 간의 시간 차이를 밀리초로 계산"""
    if pd.isna(start_nano_str) or pd.isna(end_nano_str):
        return np.nan
    
    try:
        # 문자열을 float로 변환
        start_nano = float(start_nano_str)
        end_nano = float(end_nano_str)
        
        # 나노초 차이를 밀리초로 변환 (1ms = 1,000,000ns)
        diff_nano = end_nano - start_nano
        return diff_nano / 1_000_000
    except (ValueError, TypeError) as e:
        return np.nan

def process_performance_data(csv_path, label):
    """
    단일 CSV 파일을 처리하여 성능 통계를 계산하고 Excel로 저장
    
    Args:
        csv_path: 입력 CSV 파일 경로
        label: 출력 파일명에 사용할 레이블
    """
    print(f"\n처리 중: {csv_path} (레이블: {label})")
    
    # 1. 원본 데이터 로드 - 나노초 컬럼들을 문자열로 읽기
    try:
        # 나노초 컬럼들을 문자열로 지정
        dtype_spec = {}
        nano_columns = [
            'waiting_start_nanoTime', 'waiting_start_epochNano',
            'critical_enter_nanoTime', 'critical_enter_epochNano',
            'critical_leave_nanoTime', 'critical_leave_epochNano',
            'increment_before_nanoTime', 'increment_before_epochNano',
            'increment_after_nanoTime', 'increment_after_epochNano'
        ]
        
        # CSV 읽기 전에 컬럼 확인
        df_check = pd.read_csv(csv_path, nrows=1)
        existing_nano_cols = [col for col in nano_columns if col in df_check.columns]
        
        for col in existing_nano_cols:
            dtype_spec[col] = str
        
        df_total = pd.read_csv(csv_path, dtype=dtype_spec)
        print(f"  - 총 {len(df_total)}개의 레코드 로드됨")
        
        # 나노초 값 샘플 확인
        if len(existing_nano_cols) > 0 and len(df_total) > 0:
            print(f"  - 나노초 값 샘플 (첫 번째 행):")
            for col in existing_nano_cols[:2]:  # 처음 2개만
                print(f"    {col}: {df_total.iloc[0][col]}")
        
    except FileNotFoundError:
        print(f"오류: CSV 파일을 찾을 수 없습니다 - {csv_path}")
        return False
    except Exception as e:
        print(f"오류: CSV 파일 로드 실패 - {e}")
        return False
    
    # 2. 데이터 그룹 분류
    df_success = df_total[df_total['join_result'] == 'SUCCESS'].copy()
    df_lock_failed = df_total[df_total['critical_enter_time'].isnull()].copy()
    df_capacity_failed = df_total[df_total['join_result'] == 'FAIL_OVER_CAPACITY'].copy()
    
    print(f"  - 성공: {len(df_success)}, 진입실패: {len(df_lock_failed)}, 정원초과실패: {len(df_capacity_failed)}")
    
    # 3. 성공 그룹의 시간차 계산 - 나노초 컬럼 사용
    if len(df_success) > 0:
        print("  - 성공 그룹 시간 계산 중 (나노초 정밀도)...")
        
        # nanoTime 컬럼 사용 (System.nanoTime() 기반)
        if 'waiting_start_nanoTime' in df_success.columns and 'critical_enter_nanoTime' in df_success.columns:
            df_success['wait_time_ms'] = df_success.apply(
                lambda row: calculate_time_diff_nano(
                    row['waiting_start_nanoTime'], 
                    row['critical_enter_nanoTime']
                ),
                axis=1
            )
        else:
            print("    경고: nanoTime 컬럼이 없습니다. 타임스탬프 기반 계산으로 대체")
            df_success['wait_time_ms'] = pd.to_datetime(df_success['critical_enter_time']) - pd.to_datetime(df_success['waiting_start_time'])
            df_success['wait_time_ms'] = df_success['wait_time_ms'].dt.total_seconds() * 1000
        
        if 'critical_enter_nanoTime' in df_success.columns and 'critical_leave_nanoTime' in df_success.columns:
            df_success['dwell_time_ms'] = df_success.apply(
                lambda row: calculate_time_diff_nano(
                    row['critical_enter_nanoTime'], 
                    row['critical_leave_nanoTime']
                ),
                axis=1
            )
        else:
            print("    경고: nanoTime 컬럼이 없습니다. 타임스탬프 기반 계산으로 대체")
            df_success['dwell_time_ms'] = pd.to_datetime(df_success['critical_leave_time']) - pd.to_datetime(df_success['critical_enter_time'])
            df_success['dwell_time_ms'] = df_success['dwell_time_ms'].dt.total_seconds() * 1000
        
        # 유효한 데이터만 필터링 (NaN이 아니고 음수가 아닌 값)
        valid_success = df_success[
            (df_success['wait_time_ms'].notna()) & 
            (df_success['dwell_time_ms'].notna()) &
            (df_success['wait_time_ms'] >= 0) & 
            (df_success['dwell_time_ms'] >= 0)
        ]
        
        print(f"  - 성공 그룹 시간 계산 완료 (유효 데이터: {len(valid_success)}개/{len(df_success)}개)")
        if len(valid_success) > 0:
            wait_stats = valid_success['wait_time_ms'].describe()
            dwell_stats = valid_success['dwell_time_ms'].describe()
            print(f"    Wait time - Min: {wait_stats['min']:.6f}, Max: {wait_stats['max']:.6f}, Mean: {wait_stats['mean']:.6f}")
            print(f"    Dwell time - Min: {dwell_stats['min']:.6f}, Max: {dwell_stats['max']:.6f}, Mean: {dwell_stats['mean']:.6f}")
            
            # 샘플 데이터 출력
            sample_data = valid_success[['roomNumber', 'bin', 'wait_time_ms', 'dwell_time_ms']].head()
            print("    샘플 데이터:")
            for idx, row in sample_data.iterrows():
                print(f"      방 {row['roomNumber']}, 빈 {row['bin']}: 대기 {row['wait_time_ms']:.6f}ms, 체류 {row['dwell_time_ms']:.6f}ms")
        
        df_success = valid_success
    
    # 4. 정원초과 실패 그룹의 시간차 계산 - 나노초 컬럼 사용
    if len(df_capacity_failed) > 0:
        print("  - 정원초과 실패 그룹 시간 계산 중...")
        
        # nanoTime 컬럼 사용
        if 'waiting_start_nanoTime' in df_capacity_failed.columns and 'critical_enter_nanoTime' in df_capacity_failed.columns:
            df_capacity_failed['wait_time_ms'] = df_capacity_failed.apply(
                lambda row: calculate_time_diff_nano(
                    row['waiting_start_nanoTime'], 
                    row['critical_enter_nanoTime']
                ),
                axis=1
            )
        else:
            df_capacity_failed['wait_time_ms'] = pd.to_datetime(df_capacity_failed['critical_enter_time']) - pd.to_datetime(df_capacity_failed['waiting_start_time'])
            df_capacity_failed['wait_time_ms'] = df_capacity_failed['wait_time_ms'].dt.total_seconds() * 1000
        
        if 'critical_enter_nanoTime' in df_capacity_failed.columns and 'critical_leave_nanoTime' in df_capacity_failed.columns:
            df_capacity_failed['fail_processing_time_ms'] = df_capacity_failed.apply(
                lambda row: calculate_time_diff_nano(
                    row['critical_enter_nanoTime'], 
                    row['critical_leave_nanoTime']
                ),
                axis=1
            )
        else:
            df_capacity_failed['fail_processing_time_ms'] = pd.to_datetime(df_capacity_failed['critical_leave_time']) - pd.to_datetime(df_capacity_failed['critical_enter_time'])
            df_capacity_failed['fail_processing_time_ms'] = df_capacity_failed['fail_processing_time_ms'].dt.total_seconds() * 1000
        
        # 유효한 데이터만 필터링
        valid_capacity_failed = df_capacity_failed[
            (df_capacity_failed['wait_time_ms'].notna()) & 
            (df_capacity_failed['fail_processing_time_ms'].notna()) &
            (df_capacity_failed['wait_time_ms'] >= 0) & 
            (df_capacity_failed['fail_processing_time_ms'] >= 0)
        ]
        
        print(f"  - 정원초과 실패 그룹 시간 계산 완료 (유효 데이터: {len(valid_capacity_failed)}개/{len(df_capacity_failed)}개)")
        
        df_capacity_failed = valid_capacity_failed
    
    # 5. Sheet 1: Overall_Summary 계산
    total_requests = len(df_total)
    success_count = len(df_success)
    lock_failed_count = len(df_lock_failed)
    capacity_failed_count = len(df_capacity_failed)
    
    summary_data = {
        'Category': [
            'Total Requests',
            'Success',
            'Entry Failed (Lock, etc)',
            'Capacity Failed'
        ],
        'Count': [
            total_requests,
            success_count,
            lock_failed_count,
            capacity_failed_count
        ],
        'Percentage (%)': [
            100.0,
            calculate_rate(success_count, total_requests),
            calculate_rate(lock_failed_count, total_requests),
            calculate_rate(capacity_failed_count, total_requests)
        ]
    }
    df_summary = pd.DataFrame(summary_data)
    
    # 6. Sheet 2: Overall_Success_Stats 계산
    if len(df_success) > 0:
        print("  - 성공 그룹 통계 계산 중...")
        wait_time_stats = get_stats(df_success['wait_time_ms'], "Wait Time")
        dwell_time_stats = get_stats(df_success['dwell_time_ms'], "Dwell Time")
        
        success_stats_data = {
            'Metric': ['Wait Time'] * 3 + ['Dwell Time (Critical Section)'] * 3,
            'Statistic': ['Mean', 'Median', 'Max'] * 2,
            'Value (ms)': [
                wait_time_stats['Mean'], wait_time_stats['Median'], wait_time_stats['Max'],
                dwell_time_stats['Mean'], dwell_time_stats['Median'], dwell_time_stats['Max']
            ]
        }
        df_success_stats = pd.DataFrame(success_stats_data)
    else:
        df_success_stats = pd.DataFrame({'Metric': [], 'Statistic': [], 'Value (ms)': []})
    
    # 7. Sheet 3: Overall_Capacity_Failed_Stats 계산
    if len(df_capacity_failed) > 0:
        print("  - 정원초과 실패 그룹 통계 계산 중...")
        capacity_wait_stats = get_stats(df_capacity_failed['wait_time_ms'], "Wait Time")
        fail_proc_stats = get_stats(df_capacity_failed['fail_processing_time_ms'], "Fail Processing Time")
        
        capacity_failed_stats_data = {
            'Metric': ['Wait Time'] * 3 + ['Fail Processing Time'] * 3,
            'Statistic': ['Mean', 'Median', 'Max'] * 2,
            'Value (ms)': [
                capacity_wait_stats['Mean'], capacity_wait_stats['Median'], capacity_wait_stats['Max'],
                fail_proc_stats['Mean'], fail_proc_stats['Median'], fail_proc_stats['Max']
            ]
        }
        df_capacity_failed_stats = pd.DataFrame(capacity_failed_stats_data)
    else:
        df_capacity_failed_stats = pd.DataFrame({'Metric': [], 'Statistic': [], 'Value (ms)': []})
    
    # 8. Sheet 4: Per_Room_Stats 계산
    print("  - Per_Room_Stats 계산 중...")
    all_rooms = sorted(df_total['roomNumber'].unique())
    
    room_stats_list = []
    for room in all_rooms:
        room_total = df_total[df_total['roomNumber'] == room]
        room_success = df_success[df_success['roomNumber'] == room] if len(df_success) > 0 else pd.DataFrame()
        room_capacity_failed = df_capacity_failed[df_capacity_failed['roomNumber'] == room] if len(df_capacity_failed) > 0 else pd.DataFrame()
        room_lock_failed = df_lock_failed[df_lock_failed['roomNumber'] == room] if len(df_lock_failed) > 0 else pd.DataFrame()
        
        stats = {
            'roomNumber': room,
            'total_requests': len(room_total),
            'success_count': len(room_success),
            'capacity_failed_count': len(room_capacity_failed),
            'entry_failed_count': len(room_lock_failed),
            'success_rate(%)': calculate_rate(len(room_success), len(room_total)),
            'capacity_failed_rate(%)': calculate_rate(len(room_capacity_failed), len(room_total)),
            'entry_failed_rate(%)': calculate_rate(len(room_lock_failed), len(room_total)),
            'success_avg_wait_time(ms)': room_success['wait_time_ms'].mean() if len(room_success) > 0 else 0,
            'success_avg_dwell_time(ms)': room_success['dwell_time_ms'].mean() if len(room_success) > 0 else 0,
            'capacity_failed_avg_wait_time(ms)': room_capacity_failed['wait_time_ms'].mean() if len(room_capacity_failed) > 0 else 0,
            'capacity_failed_avg_fail_processing_time(ms)': room_capacity_failed['fail_processing_time_ms'].mean() if len(room_capacity_failed) > 0 else 0
        }
        
        room_stats_list.append(stats)
    
    df_per_room_stats = pd.DataFrame(room_stats_list)
    
    # 9. Sheet 5: Per_Bin_Stats 계산
    print("  - Per_Bin_Stats 계산 중...")
    if 'bin' in df_total.columns:
        all_combinations = df_total.groupby(['roomNumber', 'bin']).size().index.tolist()
        
        bin_stats_list = []
        for room, bin_num in all_combinations:
            combo_total = df_total[(df_total['roomNumber'] == room) & (df_total['bin'] == bin_num)]
            combo_success = df_success[(df_success['roomNumber'] == room) & (df_success['bin'] == bin_num)] if len(df_success) > 0 else pd.DataFrame()
            combo_capacity_failed = df_capacity_failed[(df_capacity_failed['roomNumber'] == room) & (df_capacity_failed['bin'] == bin_num)] if len(df_capacity_failed) > 0 else pd.DataFrame()
            combo_lock_failed = df_lock_failed[(df_lock_failed['roomNumber'] == room) & (df_lock_failed['bin'] == bin_num)] if len(df_lock_failed) > 0 else pd.DataFrame()
            
            stats = {
                'roomNumber': room,
                'bin': bin_num,
                'total_requests': len(combo_total),
                'success_count': len(combo_success),
                'capacity_failed_count': len(combo_capacity_failed),
                'entry_failed_count': len(combo_lock_failed),
                'success_rate(%)': calculate_rate(len(combo_success), len(combo_total)),
                'capacity_failed_rate(%)': calculate_rate(len(combo_capacity_failed), len(combo_total)),
                'entry_failed_rate(%)': calculate_rate(len(combo_lock_failed), len(combo_total)),
                'success_avg_wait_time(ms)': combo_success['wait_time_ms'].mean() if len(combo_success) > 0 else 0,
                'success_avg_dwell_time(ms)': combo_success['dwell_time_ms'].mean() if len(combo_success) > 0 else 0,
                'capacity_failed_avg_wait_time(ms)': combo_capacity_failed['wait_time_ms'].mean() if len(combo_capacity_failed) > 0 else 0,
                'capacity_failed_avg_fail_processing_time(ms)': combo_capacity_failed['fail_processing_time_ms'].mean() if len(combo_capacity_failed) > 0 else 0
            }
            
            bin_stats_list.append(stats)
        
        df_per_bin_stats = pd.DataFrame(bin_stats_list)
        
        # 디버그: 일부 통계 출력
        mixed_bins = df_per_bin_stats[(df_per_bin_stats['success_count'] > 0) & (df_per_bin_stats['capacity_failed_count'] > 0)]
        if len(mixed_bins) > 0:
            print(f"    성공/실패 혼재 빈: {len(mixed_bins)}개")
            for _, row in mixed_bins.head(3).iterrows():
                print(f"      방 {row['roomNumber']}, 빈 {row['bin']}: 대기 {row['success_avg_wait_time(ms)']:.6f}ms, 체류 {row['success_avg_dwell_time(ms)']:.6f}ms")
    else:
        df_per_bin_stats = pd.DataFrame()
    
    # 10. Excel 파일로 저장
    output_dir = 'performance_reports'
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)
    
    output_path = os.path.join(output_dir, f"{label}_stats.xlsx")
    
    try:
        with pd.ExcelWriter(output_path, engine='openpyxl') as writer:
            df_summary.to_excel(writer, sheet_name='Overall_Summary', index=False)
            df_success_stats.to_excel(writer, sheet_name='Overall_Success_Stats', index=False)
            df_capacity_failed_stats.to_excel(writer, sheet_name='Overall_Capacity_Failed_Stats', index=False)
            df_per_room_stats.to_excel(writer, sheet_name='Per_Room_Stats', index=False)
            df_per_bin_stats.to_excel(writer, sheet_name='Per_Bin_Stats', index=False)
        
        # Excel 파일 후처리로 숫자 포맷 설정
        wb = load_workbook(output_path)
        
        # Overall_Summary의 Percentage 컬럼 포맷
        if 'Overall_Summary' in wb.sheetnames:
            ws = wb['Overall_Summary']
            for row in range(2, ws.max_row + 1):
                cell = ws[f'C{row}']
                if cell.value is not None:
                    cell.number_format = '0.00"%"'
        
        # Overall_Success_Stats와 Overall_Capacity_Failed_Stats의 Value 컬럼 포맷 (마이크로초 정밀도)
        for sheet_name in ['Overall_Success_Stats', 'Overall_Capacity_Failed_Stats']:
            if sheet_name in wb.sheetnames:
                ws = wb[sheet_name]
                for row in range(2, ws.max_row + 1):
                    cell = ws[f'C{row}']
                    if cell.value is not None:
                        cell.number_format = '0.000000'  # 마이크로초 정밀도
        
        # Per_Room_Stats의 포맷 설정
        if 'Per_Room_Stats' in wb.sheetnames:
            ws = wb['Per_Room_Stats']
            # 비율 컬럼들
            for col in ['F', 'G', 'H']:
                for row in range(2, ws.max_row + 1):
                    cell = ws[f'{col}{row}']
                    if cell.value is not None:
                        cell.number_format = '0.00"%"'
            
            # 시간 컬럼들 (마이크로초 정밀도)
            for col in ['I', 'J', 'K', 'L']:
                for row in range(2, ws.max_row + 1):
                    cell = ws[f'{col}{row}']
                    if cell.value is not None:
                        cell.number_format = '0.000000'
        
        # Per_Bin_Stats의 포맷 설정
        if 'Per_Bin_Stats' in wb.sheetnames:
            ws = wb['Per_Bin_Stats']
            # 비율 컬럼들
            for col in ['G', 'H', 'I']:
                for row in range(2, ws.max_row + 1):
                    cell = ws[f'{col}{row}']
                    if cell.value is not None:
                        cell.number_format = '0.00"%"'
            
            # 시간 컬럼들 (마이크로초 정밀도)
            for col in ['J', 'K', 'L', 'M']:
                for row in range(2, ws.max_row + 1):
                    cell = ws[f'{col}{row}']
                    if cell.value is not None:
                        cell.number_format = '0.000000'
        
        wb.save(output_path)
        
        print(f"  - Excel 파일 저장 완료: {output_path}")
        return True
    except Exception as e:
        print(f"오류: Excel 파일 저장 실패 - {e}")
        return False

def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description='성능 테스트 결과 CSV 파일을 분석하여 통계 지표를 계산하고 Excel로 저장합니다.'
    )
    parser.add_argument(
        '--inputs',
        type=str,
        required=True,
        help='분석할 CSV 파일 경로들 (콤마로 구분)'
    )
    parser.add_argument(
        '--labels',
        type=str,
        required=True,
        help='각 CSV 파일에 해당하는 출력 레이블 (콤마로 구분)'
    )
    
    args = parser.parse_args()
    
    # 입력 파일과 레이블 파싱
    input_files = [f.strip() for f in args.inputs.split(',')]
    labels = [l.strip() for l in args.labels.split(',')]
    
    if len(input_files) != len(labels):
        print("오류: 입력 파일 수와 레이블 수가 일치하지 않습니다.")
        sys.exit(1)
    
    # 시작 시간 기록
    start_time = datetime.now()
    print(f"성능 분석 시작: {start_time.strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"나노초 컬럼을 직접 활용한 고정밀 계산")
    
    # 파일 처리
    success_count = 0
    for csv_path, label in zip(input_files, labels):
        if process_performance_data(csv_path, label):
            success_count += 1
    
    # 종료 시간 및 소요 시간 계산
    end_time = datetime.now()
    elapsed_time = end_time - start_time
    
    print(f"\n성능 분석 완료: {end_time.strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"소요 시간: {elapsed_time}")
    print(f"처리 결과: {success_count}/{len(input_files)} 파일 성공")

if __name__ == "__main__":
    main()
import pandas as pd
import numpy as np
import os
from datetime import datetime

def read_critical_section_logs(input_file):
    """
    CRITICAL_SECTION_MARK 로그 파일 읽기
    """
    if not os.path.exists(input_file):
        print(f"파일을 찾을 수 없습니다: {input_file}")
        return pd.DataFrame()
    
    df = pd.read_csv(input_file)
    if 'timestamp' in df.columns:
        df['timestamp'] = pd.to_datetime(df['timestamp'])
    
    return df

def read_increment_logs(input_file):
    """
    INCREMENT 로그 파일 읽기
    """
    if not os.path.exists(input_file):
        print(f"파일을 찾을 수 없습니다: {input_file}")
        return pd.DataFrame()
    
    df = pd.read_csv(input_file, parse_dates=['increment_start_time', 'increment_end_time'])
    return df

def analyze_critical_section_timing(df_critical):
    """
    WAITING_START → CRITICAL_ENTER → CRITICAL_LEAVE 구간별 시간 분석
    """
    if df_critical.empty:
        return pd.DataFrame(), {}
    
    timing_result = []
    stats = {
        'total_sessions': 0,
        'complete_sessions': 0,
        'waiting_stats': {},
        'critical_stats': {},
        'total_stats': {},
        'concurrency_stats': {}
    }
    
    # 사용자별 그룹화
    grouped = df_critical.groupby(['roomNumber', 'userId'])
    stats['total_sessions'] = len(grouped)
    
    for (room, user), group in grouped:
        # 각 태그별 시간 추출
        marks = {}
        for tag in ['WAITING_START', 'CRITICAL_ENTER', 'CRITICAL_LEAVE']:
            sub = group[group['tag'] == tag]
            if not sub.empty:
                row = sub.iloc[0]
                marks[tag] = {
                    'timestamp': row['timestamp'],
                    'nanoTime': row.get('nanoTime', None),
                    'epochNano': row.get('epochNano', None),
                    'threadId': row.get('threadId', None)
                }
        
        # 완전한 세션인지 확인
        if set(marks.keys()) == {'WAITING_START', 'CRITICAL_ENTER', 'CRITICAL_LEAVE'}:
            stats['complete_sessions'] += 1
            
            # 시간 계산
            t_wait = marks['WAITING_START']['timestamp']
            t_enter = marks['CRITICAL_ENTER']['timestamp']
            t_leave = marks['CRITICAL_LEAVE']['timestamp']
            
            waiting_time_ms = (t_enter - t_wait).total_seconds() * 1000
            critical_time_ms = (t_leave - t_enter).total_seconds() * 1000
            total_time_ms = (t_leave - t_wait).total_seconds() * 1000
            
            # 나노초 정밀도 계산
            nano_waiting_ns = None
            nano_critical_ns = None
            nano_total_ns = None
            
            if all(marks[tag].get('nanoTime') for tag in marks):
                nano_waiting_ns = marks['CRITICAL_ENTER']['nanoTime'] - marks['WAITING_START']['nanoTime']
                nano_critical_ns = marks['CRITICAL_LEAVE']['nanoTime'] - marks['CRITICAL_ENTER']['nanoTime']
                nano_total_ns = marks['CRITICAL_LEAVE']['nanoTime'] - marks['WAITING_START']['nanoTime']
            
            timing_result.append({
                'roomNumber': room,
                'userId': user,
                'waiting_time_ms': waiting_time_ms,
                'critical_section_ms': critical_time_ms,
                'total_time_ms': total_time_ms,
                'waiting_time_ns': nano_waiting_ns,
                'critical_section_ns': nano_critical_ns,
                'total_time_ns': nano_total_ns,
                't_wait': t_wait,
                't_enter': t_enter,
                't_leave': t_leave,
                'thread_wait': marks['WAITING_START'].get('threadId'),
                'thread_enter': marks['CRITICAL_ENTER'].get('threadId'),
                'thread_leave': marks['CRITICAL_LEAVE'].get('threadId'),
                'thread_consistency': (
                    marks['WAITING_START'].get('threadId') == 
                    marks['CRITICAL_ENTER'].get('threadId') == 
                    marks['CRITICAL_LEAVE'].get('threadId')
                )
            })
    
    timing_df = pd.DataFrame(timing_result)
    
    if not timing_df.empty:
        # 통계 계산
        for phase, col in [('waiting', 'waiting_time_ms'), 
                          ('critical', 'critical_section_ms'), 
                          ('total', 'total_time_ms')]:
            data = timing_df[col]
            stats[f'{phase}_stats'] = {
                'count': len(data),
                'mean': data.mean(),
                'median': data.median(),
                'std': data.std(),
                'min': data.min(),
                'max': data.max(),
                'q25': data.quantile(0.25),
                'q75': data.quantile(0.75),
                'q95': data.quantile(0.95),
                'q99': data.quantile(0.99)
            }
        
        # 동시성 통계
        stats['concurrency_stats'] = {
            'unique_threads': timing_df['thread_wait'].nunique(),
            'thread_consistency_rate': timing_df['thread_consistency'].mean() * 100,
            'avg_threads_per_room': timing_df.groupby('roomNumber')['thread_wait'].nunique().mean()
        }
    
    return timing_df, stats

def analyze_increment_timing(df_increment):
    """
    INCREMENT_BEFORE → INCREMENT_AFTER 구간 분석
    """
    if df_increment.empty:
        return pd.DataFrame(), {}
    
    # 시간 계산
    df_increment = df_increment.copy()
    df_increment['increment_duration_ms'] = (
        df_increment['increment_end_time'] - df_increment['increment_start_time']
    ).dt.total_seconds() * 1000
    
    # 나노초 계산 (가능한 경우)
    if 'increment_nanoTime_start' in df_increment.columns and 'increment_nanoTime_end' in df_increment.columns:
        df_increment['increment_duration_ns'] = (
            df_increment['increment_nanoTime_end'] - df_increment['increment_nanoTime_start']
        )
    
    # 통계 계산
    duration_data = df_increment['increment_duration_ms']
    stats = {
        'total_operations': len(df_increment),
        'rooms_count': df_increment['roomNumber'].nunique(),
        'users_count': df_increment['user_id'].nunique(),
        'threads_count': df_increment['thread_id_start'].nunique(),
        
        # 시간 통계
        'duration_stats_ms': {
            'mean': duration_data.mean(),
            'median': duration_data.median(),
            'std': duration_data.std(),
            'min': duration_data.min(),
            'max': duration_data.max(),
            'q25': duration_data.quantile(0.25),
            'q75': duration_data.quantile(0.75),
            'q95': duration_data.quantile(0.95),
            'q99': duration_data.quantile(0.99)
        },
        
        # 정확성 통계
        'accuracy_stats': {
            'normal_increments': len(df_increment[df_increment['people_increment'] == 1]),
            'abnormal_increments': len(df_increment[df_increment['people_increment'] != 1]),
            'accuracy_rate': (df_increment['people_increment'] == 1).mean() * 100,
            'increment_distribution': df_increment['people_increment'].value_counts().to_dict()
        },
        
        # 처리량 통계
        'throughput_stats': {
            'operations_per_room': df_increment.groupby('roomNumber').size().to_dict(),
            'operations_per_thread': df_increment.groupby('thread_id_start').size().to_dict(),
            'avg_operations_per_room': df_increment.groupby('roomNumber').size().mean(),
            'avg_operations_per_thread': df_increment.groupby('thread_id_start').size().mean()
        },
        
        # 부하별 성능 (구간별)
        'load_performance': {},
        
        # 스레드 일관성
        'thread_consistency': {
            'same_thread_operations': len(df_increment[
                df_increment['thread_id_start'] == df_increment['thread_id_end']
            ]),
            'thread_switch_operations': len(df_increment[
                df_increment['thread_id_start'] != df_increment['thread_id_end']
            ]),
            'thread_consistency_rate': (
                df_increment['thread_id_start'] == df_increment['thread_id_end']
            ).mean() * 100
        }
    }
    
    # 구간별(부하별) 성능 분석
    if 'bin' in df_increment.columns:
        bin_stats = df_increment.groupby('bin').agg({
            'increment_duration_ms': ['count', 'mean', 'median', 'std'],
            'people_increment': lambda x: (x == 1).mean() * 100,
            'thread_id_start': 'nunique'
        }).round(3)
        
        bin_stats.columns = ['operations', 'avg_time_ms', 'median_time_ms', 'std_time_ms', 
                            'accuracy_rate', 'thread_count']
        stats['load_performance'] = bin_stats.to_dict('index')
    
    # 나노초 통계 (가능한 경우)
    if 'increment_duration_ns' in df_increment.columns:
        nano_data = df_increment['increment_duration_ns'].dropna()
        if not nano_data.empty:
            stats['duration_stats_ns'] = {
                'mean': nano_data.mean(),
                'median': nano_data.median(),
                'std': nano_data.std(),
                'min': nano_data.min(),
                'max': nano_data.max(),
                'q25': nano_data.quantile(0.25),
                'q75': nano_data.quantile(0.75)
            }
    
    return df_increment, stats

def generate_comprehensive_report(critical_timing, critical_stats, increment_timing, increment_stats, tech_name):
    """
    종합 리포트 생성
    """
    report_lines = []
    report_lines.append(f"=== {tech_name.upper()} 동시성 제어 기술 분석 리포트 ===")
    report_lines.append(f"생성 시간: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    report_lines.append("")
    
    # 1. 전체 개요
    report_lines.append("📊 전체 개요")
    report_lines.append("-" * 50)
    if critical_stats:
        report_lines.append(f"임계구역 세션 수: {critical_stats['complete_sessions']} / {critical_stats['total_sessions']}")
        completion_rate = critical_stats['complete_sessions'] / critical_stats['total_sessions'] * 100
        report_lines.append(f"완료율: {completion_rate:.1f}%")
    
    if increment_stats:
        report_lines.append(f"증가 작업 수: {increment_stats['total_operations']}")
        report_lines.append(f"처리된 방 수: {increment_stats['rooms_count']}")
        report_lines.append(f"활성 스레드 수: {increment_stats['threads_count']}")
        report_lines.append(f"정확성: {increment_stats['accuracy_stats']['accuracy_rate']:.1f}%")
    report_lines.append("")
    
    # 2. 대기 시간 분석 (WAITING_START → CRITICAL_ENTER)
    if critical_stats and 'waiting_stats' in critical_stats:
        report_lines.append("⏰ 대기 시간 분석 (WAITING → ENTER)")
        report_lines.append("-" * 50)
        ws = critical_stats['waiting_stats']
        report_lines.append(f"평균 대기 시간: {ws['mean']:.3f} ms")
        report_lines.append(f"중앙값 대기 시간: {ws['median']:.3f} ms")
        report_lines.append(f"최대 대기 시간: {ws['max']:.3f} ms")
        report_lines.append(f"95% 대기 시간: {ws['q95']:.3f} ms")
        report_lines.append(f"99% 대기 시간: {ws['q99']:.3f} ms")
        report_lines.append("")
    
    # 3. 임계구역 실행 시간 분석 (CRITICAL_ENTER → CRITICAL_LEAVE)
    if critical_stats and 'critical_stats' in critical_stats:
        report_lines.append("🔒 임계구역 실행 시간 분석 (ENTER → LEAVE)")
        report_lines.append("-" * 50)
        cs = critical_stats['critical_stats']
        report_lines.append(f"평균 실행 시간: {cs['mean']:.3f} ms")
        report_lines.append(f"중앙값 실행 시간: {cs['median']:.3f} ms")
        report_lines.append(f"최대 실행 시간: {cs['max']:.3f} ms")
        report_lines.append(f"표준편차: {cs['std']:.3f} ms")
        report_lines.append(f"95% 실행 시간: {cs['q95']:.3f} ms")
        report_lines.append("")
    
    # 4. 증가 작업 시간 분석 (INCREMENT_BEFORE → INCREMENT_AFTER)
    if increment_stats and 'duration_stats_ms' in increment_stats:
        report_lines.append("⚡ 증가 작업 시간 분석 (INCREMENT_BEFORE → INCREMENT_AFTER)")
        report_lines.append("-" * 50)
        ds = increment_stats['duration_stats_ms']
        report_lines.append(f"평균 처리 시간: {ds['mean']:.3f} ms")
        report_lines.append(f"중앙값 처리 시간: {ds['median']:.3f} ms")
        report_lines.append(f"최소/최대 시간: {ds['min']:.3f} / {ds['max']:.3f} ms")
        report_lines.append(f"표준편차: {ds['std']:.3f} ms")
        report_lines.append(f"95% 처리 시간: {ds['q95']:.3f} ms")
        report_lines.append(f"99% 처리 시간: {ds['q99']:.3f} ms")
        
        # 나노초 정밀도 (가능한 경우)
        if 'duration_stats_ns' in increment_stats:
            ns = increment_stats['duration_stats_ns']
            report_lines.append(f"나노초 평균: {ns['mean']:.0f} ns")
            report_lines.append(f"나노초 중앙값: {ns['median']:.0f} ns")
        report_lines.append("")
    
    # 5. 동시성 정확성 분석
    if increment_stats and 'accuracy_stats' in increment_stats:
        report_lines.append("✅ 동시성 정확성 분석")
        report_lines.append("-" * 50)
        acc = increment_stats['accuracy_stats']
        report_lines.append(f"정상 증가 (1): {acc['normal_increments']}건")
        report_lines.append(f"비정상 증가: {acc['abnormal_increments']}건")
        report_lines.append(f"정확성 비율: {acc['accuracy_rate']:.2f}%")
        
        if acc['abnormal_increments'] > 0:
            report_lines.append("증가량별 분포:")
            for increment, count in acc['increment_distribution'].items():
                report_lines.append(f"  증가량 {increment}: {count}건")
        report_lines.append("")
    
    # 6. 스레드 활용 분석
    if increment_stats and 'thread_consistency' in increment_stats:
        report_lines.append("🧵 스레드 활용 분석")
        report_lines.append("-" * 50)
        tc = increment_stats['thread_consistency']
        tp = increment_stats['throughput_stats']
        
        report_lines.append(f"총 활성 스레드: {increment_stats['threads_count']}")
        report_lines.append(f"스레드 일관성: {tc['thread_consistency_rate']:.1f}%")
        report_lines.append(f"동일 스레드 작업: {tc['same_thread_operations']}건")
        report_lines.append(f"스레드 전환 작업: {tc['thread_switch_operations']}건")
        report_lines.append(f"스레드당 평균 작업: {tp['avg_operations_per_thread']:.1f}건")
        report_lines.append("")
    
    # 7. 부하별 성능 분석 (구간별)
    if increment_stats and 'load_performance' in increment_stats and increment_stats['load_performance']:
        report_lines.append("📈 부하별 성능 분석 (10개 구간)")
        report_lines.append("-" * 50)
        report_lines.append("구간 | 작업수 | 평균시간(ms) | 정확성(%) | 스레드수")
        report_lines.append("-" * 50)
        
        for bin_num, stats in increment_stats['load_performance'].items():
            report_lines.append(f"{bin_num:3d}  | {stats['operations']:5.0f} | {stats['avg_time_ms']:9.3f} | {stats['accuracy_rate']:7.1f} | {stats['thread_count']:6.0f}")
        report_lines.append("")
    
    # 8. 처리량 분석
    if increment_stats and 'throughput_stats' in increment_stats:
        report_lines.append("🚀 처리량 분석")
        report_lines.append("-" * 50)
        tp = increment_stats['throughput_stats']
        report_lines.append(f"방당 평균 작업: {tp['avg_operations_per_room']:.1f}건")
        report_lines.append(f"스레드당 평균 작업: {tp['avg_operations_per_thread']:.1f}건")
        
        # 방별 처리량 (상위 5개)
        room_ops = sorted(tp['operations_per_room'].items(), key=lambda x: x[1], reverse=True)[:5]
        report_lines.append("방별 처리량 (상위 5개):")
        for room, ops in room_ops:
            report_lines.append(f"  방 {room}: {ops}건")
        report_lines.append("")
    
    # 9. 성능 요약 및 권장사항
    report_lines.append("📋 성능 요약")
    report_lines.append("-" * 50)
    
    if increment_stats and 'duration_stats_ms' in increment_stats:
        avg_time = increment_stats['duration_stats_ms']['mean']
        accuracy = increment_stats['accuracy_stats']['accuracy_rate']
        
        # 성능 등급 매기기
        if avg_time < 1.0 and accuracy > 99.0:
            grade = "A+ (매우 우수)"
        elif avg_time < 2.0 and accuracy > 95.0:
            grade = "A (우수)"
        elif avg_time < 5.0 and accuracy > 90.0:
            grade = "B (양호)"
        elif avg_time < 10.0 and accuracy > 80.0:
            grade = "C (보통)"
        else:
            grade = "D (개선 필요)"
        
        report_lines.append(f"종합 성능 등급: {grade}")
        report_lines.append(f"주요 지표: 평균 {avg_time:.3f}ms, 정확성 {accuracy:.1f}%")
        
        # 권장사항
        if accuracy < 100.0:
            report_lines.append("⚠️  권장사항: Race Condition 발생으로 동시성 제어 개선 필요")
        if avg_time > 5.0:
            report_lines.append("⚠️  권장사항: 처리 시간이 길어 성능 최적화 필요")
        if increment_stats['thread_consistency']['thread_consistency_rate'] < 90:
            report_lines.append("⚠️  권장사항: 스레드 일관성 부족으로 스레드 관리 개선 필요")
    
    return "\n".join(report_lines)

def save_comprehensive_analysis(critical_timing, critical_stats, increment_timing, increment_stats, output_name, display_name):
    """
    종합 분석 결과 저장 (사용자 지정 파일명)
    """
    # concurrency_reports 폴더 생성
    reports_dir = 'concurrency_reports'
    if not os.path.exists(reports_dir):
        os.makedirs(reports_dir)
        print(f"[폴더 생성] '{reports_dir}' 폴더가 생성되었습니다.")
    
    # 파일명 설정 (사용자 지정)
    base_name = output_name
    
    # 1. 상세 타이밍 데이터 저장
    if not critical_timing.empty:
        critical_file = os.path.join(reports_dir, f"{base_name}_critical_timing.csv")
        critical_timing.to_csv(critical_file, index=False, encoding='utf-8-sig')
        print(f"임계구역 타이밍 저장: {critical_file}")
    
    if not increment_timing.empty:
        increment_file = os.path.join(reports_dir, f"{base_name}_increment_timing.csv")
        increment_timing.to_csv(increment_file, index=False, encoding='utf-8-sig')
        print(f"증가 작업 타이밍 저장: {increment_file}")
    
    # 2. 종합 리포트 저장
    report_content = generate_comprehensive_report(critical_timing, critical_stats, increment_timing, increment_stats, display_name)
    report_file = os.path.join(reports_dir, f"{base_name}_report.txt")
    
    with open(report_file, 'w', encoding='utf-8') as f:
        f.write(report_content)
    
    print(f"종합 리포트 저장: {report_file}")
    
    return report_content

def main():
    """
    메인 함수 - 단일 동시성 제어 기술 분석 (파일명만 지정)
    """
    import argparse
    
    parser = argparse.ArgumentParser(description="동시성 제어 기술 종합 분석기")
    parser.add_argument('--output', type=str, default='concurrency_analysis',
                       help='출력 파일명 접두사 (기본: concurrency_analysis)')
    parser.add_argument('--room', type=int, help='특정 방 번호만 분석 (옵션)')
    
    args = parser.parse_args()
    
    # 파일 경로 설정
    critical_log = 'critical_section_marks.csv'
    
    if args.room:
        increment_log = f'results/concurrency_timing_room{args.room}.csv'
        # 현재 디렉토리에도 확인
        if not os.path.exists(increment_log):
            increment_log = f'concurrency_timing_room{args.room}.csv'
        display_name = f"동시성 제어 (방 {args.room})"
    else:
        increment_log = 'results/concurrency_timing_all_rooms.csv'
        # 현재 디렉토리에도 확인
        if not os.path.exists(increment_log):
            increment_log = 'concurrency_timing_all_rooms.csv'
        display_name = "동시성 제어 (전체 방)"
    
    print("🔧 동시성 제어 기술 종합 분석 시작...")
    print("📊 분석 대상: WAITING_START, CRITICAL_ENTER, CRITICAL_LEAVE, INCREMENT_BEFORE, INCREMENT_AFTER")
    print(f"📄 임계구역 로그: {critical_log}")
    print(f"📄 증가 작업 로그: {increment_log}")
    print(f"📄 출력 파일명: {args.output}")
    print("")
    
    print(f"{'='*60}")
    print(f"분석 중: {display_name}")
    print(f"{'='*60}")
    
    # 1. 임계구역 로그 분석 (WAITING_START, CRITICAL_ENTER, CRITICAL_LEAVE)
    print("1. 임계구역 분석 중...")
    df_critical = read_critical_section_logs(critical_log)
    critical_timing, critical_stats = analyze_critical_section_timing(df_critical)
    
    # 2. 증가 작업 로그 분석 (INCREMENT_BEFORE, INCREMENT_AFTER)
    print("2. 증가 작업 분석 중...")
    df_increment = read_increment_logs(increment_log)
    increment_timing, increment_stats = analyze_increment_timing(df_increment)
    
    # 3. 종합 분석 결과 저장
    print("3. 종합 분석 결과 저장 중...")
    report_content = save_comprehensive_analysis(critical_timing, critical_stats, increment_timing, increment_stats, args.output, display_name)
    
    # 4. 간단한 요약 출력
    print(f"\n📊 {display_name} 요약:")
    if increment_stats:
        print(f"  평균 처리 시간: {increment_stats['duration_stats_ms']['mean']:.3f} ms")
        print(f"  정확성: {increment_stats['accuracy_stats']['accuracy_rate']:.1f}%")
        print(f"  총 작업 수: {increment_stats['total_operations']}")
    if critical_stats:
        print(f"  평균 대기 시간: {critical_stats.get('waiting_stats', {}).get('mean', 0):.3f} ms")
        print(f"  평균 임계구역 시간: {critical_stats.get('critical_stats', {}).get('mean', 0):.3f} ms")
    
    print(f"\n✅ {display_name} 분석 완료!")
    print(f"📁 분석 결과가 'concurrency_reports' 폴더에 저장되었습니다.")

if __name__ == '__main__':
    main()
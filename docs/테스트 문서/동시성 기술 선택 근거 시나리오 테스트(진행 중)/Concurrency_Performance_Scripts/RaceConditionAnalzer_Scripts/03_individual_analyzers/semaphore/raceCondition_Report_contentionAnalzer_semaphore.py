"""
세마포어 전용 동시 실행 패턴 분석기 (결과 파일 기반)
결과 파일의 contention_group_size, contention_user_ids 컬럼 활용
"""
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import os
import warnings
import platform
import matplotlib.font_manager as fm
import argparse
warnings.filterwarnings('ignore')

def setup_korean_font():
    """한글 폰트 설정"""
    system = platform.system()
    font_name = {'Windows': 'Malgun Gothic', 'Darwin': 'AppleGothic'}.get(system, 'DejaVu Sans')
    if system == 'Linux':
        available_fonts = [f.name for f in fm.fontManager.ttflist]
        font_name = next((f for f in ['NanumGothic', 'DejaVu Sans'] if f in available_fonts), 'sans-serif')
    plt.rcParams['font.family'] = font_name
    plt.rcParams['axes.unicode_minus'] = False

# 한글 폰트 설정 실행
setup_korean_font()

class SemaphoreConcurrencyAnalyzer:
    def __init__(self, room_number=None, preprocessor_file=None, result_file=None, output_dir=None):
        """
        세마포어 동시 실행 패턴 분석기 초기화
        
        Args:
            room_number (int): 분석할 특정 방 번호 (간트 차트는 단일방만 지원)
            preprocessor_file (str): 전처리 데이터 파일 경로 (preprocessor_semaphore.csv)
            result_file (str): 분석 결과 파일 경로 (semaphore_analysis_result.csv)
            output_dir (str): 출력 디렉토리 경로
        """
        self.room_number = room_number
        self.preprocessor_file = preprocessor_file
        self.result_file = result_file
        self.output_dir = output_dir
        
        # 데이터 저장용 변수
        self.df_preprocessor = None
        self.df_result = None
        
        # 세마포어 동시 실행 분석 결과 (결과 파일 기반)
        self.total_requests = 0
        self.concurrent_executions = 0
        self.max_concurrent_level = 0
        
        # 시간 범위 정보
        self.global_time_start = None
        self.global_time_end = None
    
    def load_data(self):
        """CSV 파일을 로드하고 세마포어 데이터 전처리"""
        try:
            # 1. 전처리 파일 로드 (차트용 - preprocessor_semaphore.csv)
            self.df_preprocessor = pd.read_csv(self.preprocessor_file)
            print(f"✅ 세마포어 전처리 파일 로드 완료: {len(self.df_preprocessor)}건")
            
            # 2. 결과 파일 로드 (분석용 - semaphore_analysis_result.csv)
            self.df_result = pd.read_csv(self.result_file)
            print(f"✅ 세마포어 분석 결과 파일 로드 완료: {len(self.df_result)}건")
            
        except FileNotFoundError as e:
            print(f"❌ 파일을 찾을 수 없습니다: {e}")
            return False
        except Exception as e:
            print(f"❌ 데이터 로딩 오류: {e}")
            return False
        
        # 3. 결과 파일에 필수 컬럼 확인
        required_result_columns = ['contention_group_size', 'contention_user_ids']
        missing_columns = [col for col in required_result_columns if col not in self.df_result.columns]
        
        if missing_columns:
            print(f"❌ 결과 파일에 필수 컬럼 누락: {missing_columns}")
            return False
        else:
            print("✅ 결과 파일 contention 컬럼 확인 완료")
        
        # 4. 나노초 정밀도 데이터 확인
        nano_columns = ['true_critical_section_nanoTime_start', 'true_critical_section_nanoTime_end']
        has_nano = all(col in self.df_preprocessor.columns for col in nano_columns)
        
        if has_nano:
            print("✅ 나노초 정밀도 데이터 확인 완료")
        else:
            print("⚠️ 나노초 정밀도 데이터 없음 - 차트 생성 제한됨")
        
        # 5. 방 번호로 필터링 (지정된 경우만)
        if self.room_number is not None:
            before_filter = len(self.df_preprocessor)
            self.df_preprocessor = self.df_preprocessor[self.df_preprocessor['roomNumber'] == self.room_number]
            print(f"✅ 전처리 데이터 방 {self.room_number} 필터링: {before_filter} → {len(self.df_preprocessor)}건")
            
            before_filter_result = len(self.df_result)
            self.df_result = self.df_result[self.df_result['roomNumber'] == self.room_number]
            print(f"✅ 결과 데이터 방 {self.room_number} 필터링: {before_filter_result} → {len(self.df_result)}건")
        
        # 6. 세마포어 동시성 통계 계산 (결과 파일 기반)
        self._calculate_concurrency_statistics()
        
        return True
    
    def _calculate_concurrency_statistics(self):
        """세마포어 동시성 통계 계산 (결과 파일 기반)"""
        self.total_requests = len(self.df_result)
        
        # 결과 파일의 contention_group_size 컬럼에서 직접 통계 계산
        group_sizes = self.df_result['contention_group_size']
        
        # 동시 실행 발생 건수 (group_size >= 2)
        self.concurrent_executions = len(group_sizes[group_sizes >= 2])
        
        # 최대 동시 실행 수준
        self.max_concurrent_level = group_sizes.max()
        
        print(f"📊 세마포어 동시성 통계 (결과 파일 기반):")
        print(f"   총 permit 요청: {self.total_requests}건")
        print(f"   동시 실행 발생: {self.concurrent_executions}건")
        print(f"   최대 동시 실행 수준: {self.max_concurrent_level}개 스레드")
        
        # 동시성 분포 출력
        size_distribution = group_sizes.value_counts().sort_index()
        print(f"   동시성 분포:")
        for size, count in size_distribution.items():
            print(f"     크기 {size}: {count}건")
    
    def create_output_folders(self):
        """출력 폴더 생성"""
        if not os.path.exists(self.output_dir):
            os.makedirs(self.output_dir)
            print(f"✅ 출력 디렉토리 생성: {self.output_dir}")
        else:
            print(f"✅ 출력 디렉토리 확인: {self.output_dir}")
    
    def calculate_global_time_range(self):
        """전체 시간 범위 계산"""
        if ('true_critical_section_nanoTime_start' not in self.df_preprocessor.columns or
            'true_critical_section_nanoTime_end' not in self.df_preprocessor.columns):
            print("❌ 나노초 데이터 없음 - 시간 범위 계산 불가")
            return False
        
        # 유효한 시간 데이터만 필터링
        valid_data = self.df_preprocessor[
            self.df_preprocessor['true_critical_section_nanoTime_start'].notna() & 
            self.df_preprocessor['true_critical_section_nanoTime_end'].notna()
        ]
        
        if valid_data.empty:
            print("❌ 유효한 시간 데이터가 없음")
            return False
        
        self.global_time_start = valid_data['true_critical_section_nanoTime_start'].min()
        self.global_time_end = valid_data['true_critical_section_nanoTime_end'].max()
        
        print(f"🌍 전체 시간 범위:")
        print(f"   시작: {self.global_time_start} 나노초")
        print(f"   종료: {self.global_time_end} 나노초")
        print(f"   범위: {self.global_time_end - self.global_time_start} 나노초")
        
        return True
    
    def determine_time_unit(self, time_range):
        """시간 범위에 따른 최적 단위 결정"""
        if time_range < 1000:
            return 'ns', 1, 'ns'
        elif time_range < 1000000:
            return 'μs', 1000, 'μs'
        elif time_range < 1000000000:
            return 'ms', 1000000, 'ms'
        else:
            return 's', 1000000000, 's'
    
    def format_time_with_unit(self, nano_time, divisor, unit):
        """지정된 단위로 시간 변환"""
        if unit == 'ns':
            return f"{nano_time:.0f}{unit}"
        elif unit == 'μs':
            return f"{nano_time/divisor:.1f}{unit}"
        elif unit == 'ms':
            return f"{nano_time/divisor:.1f}{unit}"
        else:
            return f"{nano_time/divisor:.1f}{unit}"
    
    def create_semaphore_concurrency_gantt_chart(self):
        """세마포어 동시 실행 패턴 간트 차트 생성"""
        if self.room_number is None:
            print("❌ 간트 차트는 단일 방만 지원합니다. --room_number 옵션을 추가해주세요.")
            return
        
        print(f"📊 세마포어 동시 실행 패턴 간트 차트 생성 (방 {self.room_number})")
        
        # 나노초 데이터 확인
        if not self.calculate_global_time_range():
            return
        
        # 시간 단위 결정
        time_range = self.global_time_end - self.global_time_start
        time_unit_name, time_divisor, time_unit_symbol = self.determine_time_unit(time_range)
        
        # bin별로 차트 생성
        bins = sorted(self.df_preprocessor['bin'].unique())
        print(f"분석할 bin 수: {len(bins)}개")
        
        for bin_value in bins:
            print(f"📊 bin {bin_value} 세마포어 동시 실행 패턴 차트 생성...")
            
            # 해당 bin 데이터 필터링 (전처리 파일과 결과 파일 매칭)
            bin_preprocessor = self.df_preprocessor[self.df_preprocessor['bin'] == bin_value].copy()
            bin_result = self.df_result[self.df_result['bin'] == bin_value].copy()
            
            if bin_preprocessor.empty or bin_result.empty:
                print(f"❌ bin {bin_value} 데이터 없음")
                continue
            
            # 시간 순서로 정렬
            bin_preprocessor_sorted = bin_preprocessor.sort_values('true_critical_section_nanoTime_start')
            
            # 차트 생성
            fig, ax = plt.subplots(1, 1, figsize=(20, 12))
            
            # 성공적인 동시 실행 강조 제목
            success_indicator = "✅ CAS 기반 동시 실행 성공" if self.concurrent_executions > 0 else "단일 실행"
            title = f'세마포어 동시 실행 패턴 분석 - 방 {self.room_number}, bin {bin_value} ({success_indicator})'
            ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
            
            # 동시 실행 레벨 계산 및 시각화 (결과 파일 기반)
            self._draw_concurrent_execution_bars(ax, bin_preprocessor_sorted, bin_result)
            
            # 범례 설정
            self._setup_legend(ax)
            
            # 축 설정
            self._setup_axes(ax, bin_preprocessor_sorted, time_range, time_divisor, time_unit_symbol)
            
            # 통계 정보 박스 (결과 파일 기반)
            self._add_statistics_box(ax, bin_result)
            
            plt.tight_layout()
            
            # 파일 저장
            filename = f'semaphore_concurrency_pattern_room{self.room_number}_bin{bin_value}.png'
            chart_path = os.path.join(self.output_dir, filename)
            plt.savefig(chart_path, dpi=300, bbox_inches='tight')
            plt.close()
            
            print(f"✅ bin {bin_value} 차트 저장: {chart_path}")
    
    def _draw_concurrent_execution_bars(self, ax, preprocessor_data, result_data):
        """동시 실행 막대 그리기 (결과 파일 기반)"""
        user_ids = preprocessor_data['user_id'].unique()
        y_positions = {user_id: i for i, user_id in enumerate(user_ids)}
        
        # 결과 파일을 user_id 기준으로 딕셔너리 생성
        result_dict = {row['user_id']: row for _, row in result_data.iterrows()}
        
        for _, row in preprocessor_data.iterrows():
            user_id = row['user_id']
            start_time = row['true_critical_section_nanoTime_start']
            end_time = row['true_critical_section_nanoTime_end']
            duration = end_time - start_time
            
            y_pos = y_positions[user_id]
            
            # 결과 파일에서 동시 실행 레벨 가져오기
            concurrent_level = 1  # 기본값
            if user_id in result_dict:
                concurrent_level = result_dict[user_id]['contention_group_size']
            
            # 색상과 투명도 결정 (동시 실행 수준에 따라)
            if concurrent_level == 1:
                color = 'lightblue'
                alpha = 0.6
                label_text = '단독 실행 (permit 1개)'
            elif concurrent_level <= 3:
                color = 'blue'
                alpha = 0.7
                label_text = f'동시 실행 (permit {concurrent_level}개)'
            else:
                color = 'darkblue'
                alpha = 0.8
                label_text = f'고도 동시 실행 (permit {concurrent_level}개)'
            
            # 막대 그리기
            ax.barh(y_pos, duration, left=start_time, height=0.6,
                   alpha=alpha, color=color, edgecolor=color, linewidth=1)
            
            # 동시 실행 수준 표시
            ax.text(end_time, y_pos, f' {concurrent_level}', 
                   va='center', ha='left', fontsize=9, fontweight='bold')
        
        # Y축 설정
        ax.set_yticks(range(len(user_ids)))
        ax.set_yticklabels(user_ids, fontsize=10)
        ax.set_ylabel('사용자 ID (permit 획득 순서)', fontsize=12, fontweight='bold')
    
    def _setup_legend(self, ax):
        """범례 설정"""
        # 통합된 단일 범례
        ax.barh([], [], height=0.6, alpha=0.7, color='blue', 
               label='동시 실행 구간 표시')
        
        ax.legend(fontsize=12, loc='upper right', framealpha=0.9)
    
    def _setup_axes(self, ax, data, time_range, time_divisor, time_unit_symbol):
        """축 설정"""
        # X축 범위 설정
        ax.set_xlim(self.global_time_start, self.global_time_end)
        
        # X축 틱 설정
        num_ticks = 11
        tick_positions = np.linspace(self.global_time_start, self.global_time_end, num_ticks)
        
        tick_labels = []
        for pos in tick_positions:
            relative_time = pos - self.global_time_start
            tick_labels.append(self.format_time_with_unit(relative_time, time_divisor, time_unit_symbol))
        
        ax.set_xticks(tick_positions)
        ax.set_xticklabels(tick_labels, rotation=45)
        
        # X축 레이블
        time_range_display = self.format_time_with_unit(time_range, time_divisor, time_unit_symbol)
        ax.set_xlabel(f'세마포어 permit 실행 시간 (총 범위: {time_range_display})', 
                     fontsize=12, fontweight='bold')
        
        ax.grid(True, alpha=0.3)
    
    def _add_statistics_box(self, ax, result_data):
        """통계 정보 박스 추가 (결과 파일 기반)"""
        bin_requests = len(result_data)
        bin_concurrent = len(result_data[result_data['join_result'] == 'SUCCESS'])
        
        # 동시 실행 통계 (결과 파일의 contention_group_size 사용)
        concurrent_permits = len(result_data[result_data['contention_group_size'] >= 2])
        
        stats_text = (f'bin 총 요청: {bin_requests}건\n'
                     f'permit 성공: {bin_concurrent}건\n'
                     f'동시 permit 실행: {concurrent_permits}건')
        
        if bin_requests > 0:
            concurrency_rate = concurrent_permits / bin_requests * 100
            stats_text += f'\n동시성 활용률: {concurrency_rate:.1f}%'
        
        ax.text(0.02, 0.98, stats_text, transform=ax.transAxes, fontsize=11,
                verticalalignment='top', 
                bbox=dict(boxstyle='round', facecolor='lightcyan', alpha=0.9))
    
    def generate_semaphore_concurrency_csv_report(self):
        """세마포어 동시성 패턴 CSV 보고서 생성"""
        print("📋 세마포어 동시성 패턴 CSV 보고서 생성")
        
        # 결과 데이터 사용 (contention 정보 포함)
        all_data = self.df_result.copy()
        
        # 방 번호 필터링 (이중 확인)
        if self.room_number is not None:
            before_filter = len(all_data)
            all_data = all_data[all_data['roomNumber'] == self.room_number]
            print(f"방 {self.room_number} 필터링: {before_filter} → {len(all_data)}건")
        
        # 파일명 생성
        if self.room_number:
            csv_filename = f'semaphore_concurrency_pattern_room{self.room_number}.csv'
        else:
            csv_filename = 'semaphore_concurrency_pattern_all_rooms.csv'
        
        csv_path = os.path.join(self.output_dir, csv_filename)
        
        # 세마포어 동시성 특화 컬럼 (contention 정보 포함)
        semaphore_columns = [
            'roomNumber', 'bin', 'room_entry_sequence', 'user_id',
            'prev_people', 'curr_people', 'max_people', 'join_result',
            'true_critical_section_nanoTime_start', 'true_critical_section_nanoTime_end',
            'contention_group_size', 'contention_user_ids'
        ]
        
        if all_data.empty:
            empty_df = pd.DataFrame(columns=semaphore_columns)
            empty_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
            print(f"빈 CSV 파일 생성: {csv_path}")
        else:
            # 사용 가능한 컬럼만 선택
            available_columns = [col for col in semaphore_columns if col in all_data.columns]
            csv_df = all_data[available_columns].copy()
            
            # 누락된 컬럼은 빈 문자열로 추가
            for col in semaphore_columns:
                if col not in csv_df.columns:
                    csv_df[col] = ''
            
            # 컬럼 순서 정렬
            csv_df = csv_df[semaphore_columns]
            
            # CSV 저장
            csv_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
            print(f"세마포어 동시성 패턴 CSV 보고서: {len(csv_df)}건 → {csv_path}")
            print(f"contention_group_size, contention_user_ids 컬럼 포함된 완전한 동시성 데이터")
        
        return csv_path
    
    def run_analysis(self):
        """세마포어 동시 실행 패턴 분석 실행"""
        print("🚀 세마포어 동시 실행 패턴 분석 시작 (결과 파일 기반)")
        print("🎯 목표: CAS 기반 permit 시스템의 동시 실행 특성 관찰")
        
        # 1. 데이터 로딩
        if not self.load_data():
            print("❌ 데이터 로딩 실패")
            return False
        
        # 2. 출력 폴더 생성
        self.create_output_folders()
        
        try:
            # 3. 동시 실행 패턴 간트 차트 생성
            self.create_semaphore_concurrency_gantt_chart()
            
            # 4. 동시성 패턴 CSV 보고서 생성
            self.generate_semaphore_concurrency_csv_report()
            
            # 5. 최종 결과 요약
            self._print_concurrency_summary()
            
        except Exception as e:
            print(f"❌ 분석 중 오류 발생: {e}")
            import traceback
            traceback.print_exc()
            return False
        
        return True
    
    def _print_concurrency_summary(self):
        """동시성 분석 결과 요약"""
        print("\n" + "="*60)
        print("🏆 세마포어 동시 실행 패턴 분석 최종 결과 (결과 파일 기반)")
        print("="*60)
        
        if self.max_concurrent_level > 1:
            print("✅ CAS 기반 동시 permit 실행 성공!")
            print(f"🔄 최대 {self.max_concurrent_level}개 스레드가 동시에 permit 획득")
            print("🚀 세마포어의 높은 동시성 처리 능력 확인")
        else:
            print("📊 단일 permit 실행 패턴 관찰")
            print("🔒 순차적 permit 처리로 안정적 동작")
        
        print(f"\n📊 상세 통계:")
        print(f"   총 permit 요청: {self.total_requests}건")
        print(f"   동시 실행 발생: {self.concurrent_executions}건")
        print(f"   최대 동시 실행 수준: {self.max_concurrent_level}개 스레드")
        
        if self.total_requests > 0:
            concurrency_rate = (self.concurrent_executions / self.total_requests) * 100
            print(f"   동시성 활용률: {concurrency_rate:.1f}%")
        
        print("\n🎯 결론: 결과 파일의 contention 데이터를 활용한 정확한 동시 실행 분석 완료!")
        print("="*60)

def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description='세마포어 동시 실행 패턴 분석 및 시각화 (결과 파일 기반)',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
사용 예시:
  # 전체 방 동시성 패턴 분석
  python semaphore_concurrency_analyzer.py --preprocessor_file preprocessor_semaphore.csv --result_file semaphore_analysis_result.csv --output_dir output/
  
  # 특정 방 동시성 패턴 분석  
  python semaphore_concurrency_analyzer.py --room_number 1294 --preprocessor_file preprocessor_semaphore.csv --result_file semaphore_analysis_result.csv --output_dir output/
        """
    )
    
    parser.add_argument(
        '--room_number', 
        type=int, 
        help='분석할 특정 방 번호 (간트 차트 생성시 필수)'
    )
    
    parser.add_argument(
        '--preprocessor_file',
        type=str,
        required=True,
        help='세마포어 전처리 데이터 CSV 파일 경로 (preprocessor_semaphore.csv)'
    )
    
    parser.add_argument(
        '--result_file',
        type=str,
        required=True,
        help='세마포어 분석 결과 CSV 파일 경로 (semaphore_analysis_result.csv)'
    )
    
    parser.add_argument(
        '--output_dir',
        type=str,
        required=True,
        help='분석 결과를 저장할 디렉토리 경로'
    )
    
    args = parser.parse_args()
    
    # 세마포어 동시성 분석기 생성 및 실행
    analyzer = SemaphoreConcurrencyAnalyzer(
        room_number=args.room_number,
        preprocessor_file=args.preprocessor_file,
        result_file=args.result_file,
        output_dir=args.output_dir
    )
    
    success = analyzer.run_analysis()
    
    if not success:
        print("❌ 세마포어 동시성 분석 실패")
        exit(1)
    else:
        print("🎉 세마포어 동시성 분석 완료!")

if __name__ == "__main__":
    main()
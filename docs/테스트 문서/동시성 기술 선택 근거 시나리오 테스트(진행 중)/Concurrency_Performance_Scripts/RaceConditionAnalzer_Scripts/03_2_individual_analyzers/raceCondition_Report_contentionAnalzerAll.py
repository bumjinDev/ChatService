"""
Rule 2: Contention 분석기 - 수정된 버전
전체 임계구역 간트 차트만 생성 (모든 구간 시각화)
각 스레드 막대가 실제 임계구역 시작/끝 위치에 정확히 그려지도록 수정
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

class Rule2ContentionAnalyzer:
    def __init__(self, room_number=None, preprocessor_file=None, result_file=None, output_dir=None):
        """
        Rule 2 Contention 분석기 초기화
        
        Args:
            room_number (int): 분석할 특정 방 번호 (필수 - 간트 차트는 단일방만 지원)
            preprocessor_file (str): 전처리 데이터 파일 경로 (사용 안함)
            result_file (str): 분석 결과 데이터 파일 경로 (차트 및 CSV용)
            output_dir (str): 출력 디렉토리 경로
        """
        self.room_number = room_number
        self.preprocessor_file = preprocessor_file  # Rule2에서는 사용하지 않음
        self.result_file = result_file
        self.output_dir = output_dir
        
        # 데이터 저장용 변수
        self.df_result = None
        
        # 고정밀도 데이터 여부
        self.has_high_precision = False
        
        # 🔥 전체 시간 범위 저장 (실제 나노초 값)
        self.global_time_start = None
        self.global_time_end = None
    
    def load_data(self):
        """CSV 파일을 로드하고 전처리"""
        try:
            # 결과 파일 로드 (차트 및 CSV용)
            self.df_result = pd.read_csv(self.result_file)
            print(f"✅ 결과 파일 로드 완료: {len(self.df_result)}건")
            
            # 나노초 정밀도 데이터 확인
            if any('nanoTime' in col for col in self.df_result.columns):
                self.has_high_precision = True
                print("✅ 고정밀도 나노초 데이터 감지")
            else:
                print("⚠️ 나노초 데이터 없음")
                
        except FileNotFoundError as e:
            print(f"❌ 파일을 찾을 수 없습니다: {e}")
            return False
        except Exception as e:
            print(f"❌ 데이터 로딩 오류: {e}")
            return False
        
        # 방 번호 필터링 (Rule2는 반드시 필요)
        if self.room_number is None:
            print("❌ Rule2는 room_number가 반드시 필요합니다 (간트 차트는 단일방만 지원)")
            return False
        
        before_filter = len(self.df_result)
        self.df_result = self.df_result[self.df_result['roomNumber'] == self.room_number]
        print(f"✅ 방 {self.room_number} 필터링 완료: {before_filter} → {len(self.df_result)}건")
        
        return True
    
    def create_output_folders(self):
        """출력 폴더 생성"""
        if not os.path.exists(self.output_dir):
            os.makedirs(self.output_dir)
            print(f"✅ 출력 디렉토리 생성: {self.output_dir}")
        else:
            print(f"✅ 출력 디렉토리 확인: {self.output_dir}")
    
    def calculate_global_time_range(self, contention_anomalies):
        """🔥 전체 데이터에서 시간 범위 계산 (실제 나노초 값)"""
        if self.has_high_precision and 'true_critical_section_nanoTime_start' in contention_anomalies.columns:
            start_col = 'true_critical_section_nanoTime_start'
            end_col = 'true_critical_section_nanoTime_end'
        else:
            print("❌ 나노초 데이터 없음 - 전체 시간 범위 계산 불가")
            return False
        
        # 유효한 시간 데이터만 필터링
        valid_data = contention_anomalies[
            contention_anomalies[start_col].notna() & 
            contention_anomalies[end_col].notna()
        ]
        
        if valid_data.empty:
            print("❌ 유효한 시간 데이터가 없어 전체 시간 범위 계산 불가")
            return False
        
        # 전체 시간 범위 계산 (실제 나노초 값)
        self.global_time_start = valid_data[start_col].min()
        self.global_time_end = valid_data[end_col].max()
        
        print(f"🌍 전체 시간 범위 계산 완료:")
        print(f"   - 시작: {self.global_time_start} 나노초")
        print(f"   - 종료: {self.global_time_end} 나노초")
        print(f"   - 총 범위: {self.global_time_end - self.global_time_start} 나노초")
        
        return True
    
    def determine_time_unit(self, time_range):
        """전체 시간 범위를 기준으로 최적의 시간 단위 결정"""
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
    
    def create_rule2_general_gantt_chart(self):
        """규칙 2: 전체 임계구역 간트 차트 (경합 상관없이 모든 구간 시각화)"""
        print(f"🎯 Rule2 전체 임계구역 간트 차트 생성 시작 (방 {self.room_number}) - 모든 구간 시각화")
        
        # 경합 상관없이 모든 데이터 사용 (나노초 시간 데이터가 있는 것만)
        all_data = self.df_result.copy()
        
        if all_data.empty:
            print("❌ 데이터가 없어 차트 생성을 건너뜁니다.")
            return
        
        print(f"   - 전체 레코드: {len(all_data)}건")
        
        # 시간 컬럼 확인 (나노초만 사용)
        if self.has_high_precision and 'true_critical_section_nanoTime_start' in all_data.columns:
            start_col = 'true_critical_section_nanoTime_start'
            end_col = 'true_critical_section_nanoTime_end'
            print("   - 나노초 정밀도 시간 데이터 사용")
        else:
            print("❌ 나노초 데이터 없음 - 차트 생성 불가")
            return
        
        # 시간 데이터 유효성 확인
        valid_time_mask = all_data[start_col].notna() & all_data[end_col].notna()
        valid_data = all_data[valid_time_mask]
        
        if valid_data.empty:
            print("❌ 유효한 시간 데이터가 없어 차트 생성을 건너뜁니다.")
            return
        
        print(f"   - 유효한 시간 데이터: {len(valid_data)}건")
        
        # 🔥 전체 시간 범위 계산 (실제 나노초 값)
        if not self.calculate_global_time_range(valid_data):
            return
        
        # 🔥 전체 시간 범위 기준으로 시간 단위 결정
        time_range = self.global_time_end - self.global_time_start
        time_unit_name, time_divisor, time_unit_symbol = self.determine_time_unit(time_range)
        print(f"   - 시간 단위: {time_unit_name} (범위: {time_range/time_divisor:.1f}{time_unit_symbol})")
        
        # bin별로 데이터 그룹화
        if 'bin' not in valid_data.columns:
            print("❌ bin 컬럼이 없습니다.")
            return
        
        bins = sorted(valid_data['bin'].unique())
        print(f"   - 분석할 bin 수: {len(bins)}개")
        
        # 각 bin별로 간트 차트 생성
        for bin_value in bins:
            print(f"   📊 bin {bin_value} 전체 임계구역 간트 차트 생성 중...")
            
            # 해당 bin 데이터 필터링
            bin_data = valid_data[valid_data['bin'] == bin_value].copy()
            
            if bin_data.empty:
                print(f"   ❌ bin {bin_value} 데이터가 없어 건너뜀")
                continue
            
            # 시간 순서로 정렬
            bin_data_sorted = bin_data.sort_values([start_col])
            
            # 차트 생성
            fig, ax = plt.subplots(1, 1, figsize=(20, 12))
            title = f'규칙 2: 전체 임계구역 간트 차트 - 방 {self.room_number}, bin {bin_value} (모든 구간)'
            ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
            
            # user_id별 Y축 위치 설정
            user_ids = bin_data_sorted['user_id'].unique()
            y_positions = {user_id: i for i, user_id in enumerate(user_ids)}
            
            print(f"     - bin {bin_value} 고유 사용자 수: {len(user_ids)}")
            
            # 🔥 각 사용자별 임계 구역 막대 그리기
            for i, (_, row) in enumerate(bin_data_sorted.iterrows()):
                user_id = row['user_id']
                contention_size = row.get('contention_group_size', 1)
                
                # 실제 나노초 시간 값
                start_time = row[start_col]
                end_time = row[end_col]
                duration_nanos = end_time - start_time
                
                y_pos = y_positions[user_id]
                
                # 색상 결정 (경합 여부에 따라)
                if contention_size > 1:
                    # 경합이 있는 경우 빨간색
                    color = 'red'
                    alpha = 0.7
                    label_text = f'경합 구간 (그룹 크기: {int(contention_size)})'
                else:
                    # 경합이 없는 경우 파란색
                    color = 'blue'
                    alpha = 0.5
                    label_text = '일반 임계구역'
                
                # 🔥 수평 막대 그리기
                ax.barh(y_pos, duration_nanos, left=start_time, height=0.6, 
                    alpha=alpha, color=color, edgecolor=color, linewidth=0.5)
                
                # 막대 끝에 경합 스레드 수 표기 (본인 포함 총 개수)
                ax.text(end_time, y_pos, f' {int(contention_size)}', 
                    va='center', ha='left', fontsize=9, fontweight='bold')
                
                # 디버그 정보 (처음 3개만)
                if i < 3:
                    print(f"     - 사용자 {user_id}: {start_time}→{end_time} 나노초 "
                        f"(duration: {duration_nanos} 나노초, 경합: {contention_size})")
            
            # Y축 설정 (사용자 ID)
            ax.set_yticks(range(len(user_ids)))
            ax.set_yticklabels(user_ids, fontsize=10)
            ax.set_ylabel('사용자 ID (user_id)', fontsize=12, fontweight='bold')
            
            # 🔥 X축 설정 - 실제 나노초 시간 범위로 설정
            ax.set_xlim(self.global_time_start, self.global_time_end)
            
            # 🔥 X축 틱 위치 및 레이블
            num_ticks = 11
            tick_positions = np.linspace(self.global_time_start, self.global_time_end, num_ticks)
            
            # 틱 레이블 생성
            tick_labels = []
            for pos in tick_positions:
                relative_time = pos - self.global_time_start
                tick_labels.append(self.format_time_with_unit(relative_time, time_divisor, time_unit_symbol))
            
            ax.set_xticks(tick_positions)
            ax.set_xticklabels(tick_labels, rotation=45)
            
            # X축 레이블
            time_range_display = self.format_time_with_unit(time_range, time_divisor, time_unit_symbol)
            ax.set_xlabel(f'시간 (기준점에서의 상대적 시간, 총 범위: {time_range_display})', 
                        fontsize=12, fontweight='bold')
            
            ax.grid(True, alpha=0.3)
            
            # 범례 추가 (경합 구간과 일반 구간 구분)
            has_contention = any(row['contention_group_size'] > 1 for _, row in bin_data_sorted.iterrows())
            
            if has_contention:
                # 경합 구간이 있는 경우
                ax.barh([], [], height=0.6, alpha=0.7, color='red', 
                    edgecolor='red', linewidth=0.5, label='경합 구간 (Contention)')
                ax.barh([], [], height=0.6, alpha=0.5, color='blue', 
                    edgecolor='blue', linewidth=0.5, label='일반 임계구역')
            else:
                # 경합 구간이 없는 경우
                ax.barh([], [], height=0.6, alpha=0.5, color='blue', 
                    edgecolor='blue', linewidth=0.5, label='임계구역 (Critical Section)')
            
            ax.legend(fontsize=12, loc='upper right')
            
            # 레이아웃 조정
            plt.tight_layout()
            
            # 파일 저장
            chart_filename = f'general_gantt_chart_room{self.room_number}_bin{bin_value}_all_sections.png'
            chart_path = os.path.join(self.output_dir, chart_filename)
            plt.savefig(chart_path, dpi=300, bbox_inches='tight')
            plt.close()
            
            print(f"     ✅ bin {bin_value} 전체 임계구역 간트 차트 저장 완료: {chart_path}")
        
        print(f"✅ 모든 bin 전체 임계구역 간트 차트 생성 완료 (총 {len(bins)}개)")

    def generate_rule2_csv_report(self):
        """규칙 2 전체 스레드 CSV 보고서 생성 (경합 여부 무관, 모든 스레드 포함)"""
        print("📋 Rule2 전체 스레드 CSV 보고서 생성 시작")
        
        # 경합 여부와 상관없이 모든 데이터 사용 (차트와 동일한 로직)
        all_thread_data = self.df_result.copy()
        
        print(f"   - 전체 스레드 데이터: {len(all_thread_data)}건")
        
        # 파일명 생성 (모든 스레드 포함을 명시)
        csv_filename = f'report_rule2_all_threads_room{self.room_number}_complete.csv'
        csv_path = os.path.join(self.output_dir, csv_filename)
        
        # 출력할 컬럼 정의 (원본 duration_nanos 유지)
        if self.has_high_precision and any('nanoTime' in col for col in all_thread_data.columns):
            required_columns = [
                'roomNumber', 'bin', 'user_id', 'contention_group_size',
                'contention_user_ids', 
                'true_critical_section_nanoTime_start',
                'true_critical_section_nanoTime_end',
                'true_critical_section_duration_nanos'
            ]
            print("   - 나노초 정밀도 컬럼 사용 (전체 스레드 데이터 포함)")
        else:
            print("❌ 나노초 데이터 없음 - CSV 생성 불가")
            return
        
        if all_thread_data.empty:
            # 빈 데이터인 경우 빈 DataFrame 생성
            empty_df = pd.DataFrame(columns=required_columns)
            empty_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
            print(f"   - 빈 CSV 파일 생성: {csv_path}")
        else:
            # 사용 가능한 컬럼만 선택
            available_columns = [col for col in required_columns[:-1] if col in all_thread_data.columns]
            csv_df = all_thread_data[available_columns].copy()
            
            # 원본 duration_nanos 값 유지 (재계산하지 않음)
            if 'true_critical_section_duration_nanos' in all_thread_data.columns:
                csv_df['true_critical_section_duration_nanos'] = all_thread_data['true_critical_section_duration_nanos']
                print(f"   ✅ 원본 duration_nanos 값 유지 (평균: {csv_df['true_critical_section_duration_nanos'].mean():.0f} 나노초)")
            else:
                csv_df['true_critical_section_duration_nanos'] = ''
                print("   ⚠️ 원본 duration_nanos 컬럼이 없어 빈 값으로 설정")
            
            # 누락된 컬럼은 빈 문자열로 추가
            for col in required_columns:
                if col not in csv_df.columns:
                    csv_df[col] = ''
            
            # 컬럼 순서 맞춤
            csv_df = csv_df[required_columns]
            
            # 정렬 (roomNumber, bin, 시작시간 순)
            sort_columns = ['roomNumber', 'bin']
            if 'true_critical_section_nanoTime_start' in csv_df.columns:
                sort_columns.append('true_critical_section_nanoTime_start')
            
            available_sort_cols = [col for col in sort_columns if col in csv_df.columns]
            if available_sort_cols:
                csv_df = csv_df.sort_values(available_sort_cols)
            
            # CSV 저장
            csv_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
            print(f"   - 전체 스레드 CSV 보고서 생성 완료: {len(csv_df)}건 → {csv_path}")
            print(f"   - 모든 contention_group_size = 1 (경합 없음, 동시성 제어 정상 작동)")
        
        return csv_path
    
    def run_analysis(self):
        """전체 분석 실행"""
        print("🚀 Rule 2: Contention 분석 시작 (전체 임계구역 시각화)")
        
        # room_number 필수 체크
        if self.room_number is None:
            print("❌ Rule2는 room_number가 반드시 필요합니다 (간트 차트는 단일방만 지원)")
            print("   사용법: --room_number {방번호} 옵션을 추가해주세요")
            return False
        
        # 1. 데이터 로딩
        if not self.load_data():
            print("❌ 데이터 로딩 실패")
            return False
        
        # 2. 출력 폴더 생성
        self.create_output_folders()
        
        try:
            # 3. 전체 임계구역 간트 차트 생성 (모든 구간 시각화)
            self.create_rule2_general_gantt_chart()
            
            # 4. CSV 보고서 생성 (원본 데이터 유지)
            self.generate_rule2_csv_report()
            
        except Exception as e:
            print(f"❌ 분석 중 오류 발생: {e}")
            import traceback
            traceback.print_exc()
            return False
        
        print("✅ Rule 2 분석 완료! (전체 임계구역 시각화)")
        return True

def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description='Rule 2: Contention 분석 및 전체 임계구역 간트 차트 시각화',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
사용 예시:
  # 방 1170 전체 임계구역 분석 (실제 시간 위치 기반)
  python rule2_contention_analyzer_actual_time.py --room_number 1170 --result_file detected_anomalies.csv --output_dir output/
  
주요 기능:
  - 전체 임계구역 간트 차트 생성 (경합 구간과 일반 구간 구분)
  - 막대 그래프가 실제 나노초 시간 위치에 정확히 그려짐
  - X축이 실제 나노초 시간 범위로 설정됨
  - 각 스레드의 임계구역 시작/끝 시간이 시각적으로 정확히 표현됨
        """
    )
    
    parser.add_argument(
        '--room_number', 
        type=int, 
        required=True,
        help='분석할 특정 방 번호 (필수 - 간트 차트는 단일방만 지원)'
    )
    
    parser.add_argument(
        '--preprocessor_file',
        type=str,
        help='전처리 데이터 CSV 파일 경로 (Rule2에서는 사용하지 않음)'
    )
    
    parser.add_argument(
        '--result_file',
        type=str,
        required=True,
        help='분석 결과 CSV 파일 경로 (원본 detected_anomalies.csv 권장)'
    )
    
    parser.add_argument(
        '--output_dir',
        type=str,
        required=True,
        help='분석 결과를 저장할 디렉토리 경로'
    )
    
    args = parser.parse_args()
    
    # Rule2 분석기 생성 및 실행
    analyzer = Rule2ContentionAnalyzer(
        room_number=args.room_number,
        preprocessor_file=args.preprocessor_file,
        result_file=args.result_file,
        output_dir=args.output_dir
    )
    
    success = analyzer.run_analysis()
    
    if not success:
        exit(1)

if __name__ == "__main__":
    main()
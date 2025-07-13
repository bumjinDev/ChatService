"""
Rule 2: Contention 분석기 - 완전 독립 실행 가능 파일
경합 발생 분석 및 간트 차트 시각화
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
    
    def create_rule2_contention_gantt_chart(self):
        """규칙 2: 경합 발생 상세 분석 - bin별 간트 차트"""
        print(f"🎯 Rule2 경합 발생 간트 차트 생성 시작 (방 {self.room_number})")
        
        # '경합 발생' 포함된 이상 현상만 필터링
        contention_anomalies = self.df_result[
            self.df_result['anomaly_type'].str.contains('경합 발생', na=False)
        ].copy()
        
        if contention_anomalies.empty:
            print("❌ 경합 발생 데이터가 없어 차트 생성을 건너뜁니다.")
            return
        
        print(f"   - 경합 발생 레코드: {len(contention_anomalies)}건")
        
        # 시간 컬럼 선택 (나노초만 사용)
        if self.has_high_precision and 'true_critical_section_nanoTime_start' in contention_anomalies.columns:
            start_col = 'true_critical_section_nanoTime_start'
            end_col = 'true_critical_section_nanoTime_end'
            print("   - 나노초 정밀도 시간 데이터 사용")
        else:
            print("❌ 나노초 데이터 없음 - 차트 생성 불가")
            return
        
        # 시간 컬럼 확인
        if start_col not in contention_anomalies.columns or end_col not in contention_anomalies.columns:
            print(f"❌ 필수 시간 컬럼이 없습니다: {start_col}, {end_col}")
            return
        
        # 시간 데이터 유효성 확인
        valid_time_mask = (
            contention_anomalies[start_col].notna() & 
            contention_anomalies[end_col].notna()
        )
        contention_anomalies = contention_anomalies[valid_time_mask]
        
        if contention_anomalies.empty:
            print("❌ 유효한 시간 데이터가 없어 차트 생성을 건너뜁니다.")
            return
        
        print(f"   - 유효한 시간 데이터: {len(contention_anomalies)}건")
        
        # bin별로 데이터 그룹화
        if 'bin' not in contention_anomalies.columns:
            print("❌ bin 컬럼이 없습니다.")
            return
        
        bins = contention_anomalies['bin'].unique()
        print(f"   - 분석할 bin 수: {len(bins)}개")
        
        # 전체 데이터의 나노초 시간 범위 계산
        global_start_nano = contention_anomalies[start_col].min()
        global_end_nano = contention_anomalies[end_col].max()
        total_duration_nanos = global_end_nano - global_start_nano
        total_duration_seconds = total_duration_nanos / 1_000_000_000
        
        print(f"   - 전체 나노초 범위: {global_start_nano} ~ {global_end_nano}")
        print(f"   - 전체 지속시간: {total_duration_seconds:.9f}초 ({total_duration_nanos:,} 나노초)")
        
        # X축 설정: 고정 10개 구간 분할
        if total_duration_seconds <= 1:
            tick_label = "X축 10구간 분할 (1초 이하)"
        elif total_duration_seconds <= 10:
            tick_label = "X축 10구간 분할 (1-10초)"
        else:
            tick_label = "X축 10구간 분할 (10초 이상)"
        
        print(f"   - X축 설정: {tick_label}")
        
        # 각 bin별로 간트 차트 생성
        for bin_value in bins:
            print(f"   📊 bin {bin_value} 간트 차트 생성 중...")
            
            # 해당 bin 데이터 필터링
            bin_data = contention_anomalies[contention_anomalies['bin'] == bin_value].copy()
            
            if bin_data.empty:
                print(f"   ❌ bin {bin_value} 데이터가 없어 건너뜀")
                continue
            
            # 차트 생성
            fig, ax = plt.subplots(1, 1, figsize=(20, 12))
            title = f'규칙 2: 경합 발생 간트 차트 - 방 {self.room_number}, bin {bin_value}'
            ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
            
            # user_id별 정렬 및 Y축 위치 설정
            bin_data_sorted = bin_data.sort_values([
                start_col, 'room_entry_sequence'
            ])
            
            user_ids = bin_data_sorted['user_id'].unique()
            y_positions = {user_id: i for i, user_id in enumerate(user_ids)}
            
            print(f"     - bin {bin_value} 고유 사용자 수: {len(user_ids)}")
            
            # 각 사용자별 임계 구역 막대 그리기 (나노초 직접 사용)
            for i, (_, row) in enumerate(bin_data_sorted.iterrows()):
                user_id = row['user_id']
                start_nano = row[start_col]
                end_nano = row[end_col]
                contention_size = row.get('contention_group_size', 1)
                
                y_pos = y_positions[user_id]
                
                if pd.notna(start_nano) and pd.notna(end_nano):
                    # 나노초 지속시간 계산
                    duration_nanos = end_nano - start_nano
                    
                    # 0 이하의 지속시간은 1000 나노초로 보정
                    if duration_nanos <= 0:
                        duration_nanos = 1000
                    
                    # 나노초를 상대적 위치로 변환 (0~1 범위)
                    start_relative = (start_nano - global_start_nano) / total_duration_nanos
                    duration_relative = duration_nanos / total_duration_nanos
                    
                    # 수평 막대 그리기 (나노초 기반)
                    ax.barh(y_pos, duration_relative, left=start_relative, height=0.6, 
                           alpha=0.7, color='red', edgecolor='red', linewidth=0.5)
                    
                    # 막대 끝에 경합 스레드 수 표기
                    end_relative = start_relative + duration_relative
                    ax.text(end_relative, y_pos, f' {int(contention_size)}', 
                           va='center', ha='left', fontsize=9, fontweight='bold')
            
            # Y축 설정 (사용자 ID)
            ax.set_yticks(range(len(user_ids)))
            ax.set_yticklabels(user_ids, fontsize=10)
            ax.set_ylabel('사용자 ID (user_id)', fontsize=12, fontweight='bold')
            
            # X축 설정 - 나노초 기반, 사용자 친화적 표시
            ax.set_xlim(0, 1)
            
            # 고정 10개 tick 위치
            tick_positions = np.linspace(0, 1, 11)
            ax.set_xticks(tick_positions)
            
            # X축 레이블: 사용자 친화적 표시 (나노초 계산과 분리)
            try:
                tick_labels = []
                for pos in tick_positions:
                    nano_value = global_start_nano + pos * total_duration_nanos
                    # 나노초를 datetime으로 변환하여 시각 표시
                    dt = pd.to_datetime(nano_value, unit='ns')
                    if total_duration_seconds <= 1:
                        # 1초 이하: 밀리초까지 표시
                        tick_labels.append(dt.strftime('%H시 %M분 %S초 ') + f"{int(dt.strftime('%f')[:3])}밀리초")
                    else:
                        # 1초 이상: 초까지 표시
                        tick_labels.append(dt.strftime('%H시 %M분 %S초'))
                
                ax.set_xticklabels(tick_labels, rotation=45)
            except Exception as e:
                print(f"     ⚠️ X축 tick 설정 실패: {e}")
                # 폴백: 상대적 시간 표시
                tick_labels = [f'{pos*total_duration_seconds:.3f}s' for pos in tick_positions]
                ax.set_xticklabels(tick_labels, rotation=45)
            
            ax.set_xlabel('시간 (나노초 정밀도)', fontsize=12, fontweight='bold')
            ax.grid(True, alpha=0.3)
            
            # X축 레이블 회전
            ax.tick_params(axis='x', rotation=45)
            
            # 범례 추가
            ax.barh([], [], height=0.6, alpha=0.7, color='red', 
                   edgecolor='red', linewidth=0.5, label='임계 구역 (Critical Section)')
            ax.legend(fontsize=12, loc='upper right')
            
            # 레이아웃 조정
            plt.tight_layout()
            
            # 파일 저장
            chart_filename = f'contention_gantt_chart_room{self.room_number}_bin{bin_value}.png'
            chart_path = os.path.join(self.output_dir, chart_filename)
            plt.savefig(chart_path, dpi=300, bbox_inches='tight')
            plt.close()
            
            print(f"     ✅ bin {bin_value} 간트 차트 저장 완료: {chart_path}")
        
        print(f"✅ 모든 bin 간트 차트 생성 완료 (총 {len(bins)}개)")
    
    def generate_rule2_csv_report(self):
        """규칙 2 경합 발생 CSV 보고서 생성"""
        print("📋 Rule2 CSV 보고서 생성 시작")
        
        # '경합 발생' 포함된 이상 현상만 필터링
        contention_anomalies = self.df_result[
            self.df_result['anomaly_type'].str.contains('경합 발생', na=False)
        ].copy()
        
        print(f"   - 경합 발생 이상 현상: {len(contention_anomalies)}건")
        
        # 파일명 생성 (Rule2는 항상 단일방)
        csv_filename = f'report_rule2_contention_details_room{self.room_number}.csv'
        csv_path = os.path.join(self.output_dir, csv_filename)
        
        # 출력할 컬럼 정의 (나노초만 사용)
        if self.has_high_precision and any('nanoTime' in col for col in contention_anomalies.columns):
            required_columns = [
                'roomNumber', 'bin', 'user_id', 'contention_group_size',
                'contention_user_ids', 
                'true_critical_section_nanoTime_start',
                'true_critical_section_nanoTime_end',
                'true_critical_section_duration_nanos'
            ]
            print("   - 나노초 정밀도 컬럼 사용")
        else:
            print("❌ 나노초 데이터 없음 - CSV 생성 불가")
            return
        
        if contention_anomalies.empty:
            # 빈 데이터인 경우 빈 DataFrame 생성
            empty_df = pd.DataFrame(columns=required_columns)
            empty_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
            print(f"   - 빈 CSV 파일 생성: {csv_path}")
        else:
            # 사용 가능한 나노초 컬럼만 선택
            available_columns = [col for col in required_columns[:-1] if col in contention_anomalies.columns]
            csv_df = contention_anomalies[available_columns].copy()
            
            # 나노초 지속시간 계산
            if ('true_critical_section_nanoTime_start' in csv_df.columns and 
                'true_critical_section_nanoTime_end' in csv_df.columns):
                try:
                    csv_df['true_critical_section_duration_nanos'] = (
                        csv_df['true_critical_section_nanoTime_end'] - 
                        csv_df['true_critical_section_nanoTime_start']
                    )
                    print("   - 나노초 지속시간 계산 완료")
                except Exception as e:
                    print(f"   ⚠️ 나노초 지속시간 계산 실패: {e}")
                    csv_df['true_critical_section_duration_nanos'] = ''
            else:
                csv_df['true_critical_section_duration_nanos'] = ''
            
            # 누락된 컬럼은 빈 문자열로 추가
            for col in required_columns:
                if col not in csv_df.columns:
                    csv_df[col] = ''
            
            # 컬럼 순서 맞춤
            csv_df = csv_df[required_columns]
            
            # 정렬 (roomNumber, bin, room_entry_sequence 순 - 가능한 컬럼만)
            sort_columns = ['roomNumber', 'bin']
            if 'room_entry_sequence' in csv_df.columns:
                sort_columns.append('room_entry_sequence')
            
            available_sort_cols = [col for col in sort_columns if col in csv_df.columns]
            if available_sort_cols:
                csv_df = csv_df.sort_values(available_sort_cols)
            
            # CSV 저장
            csv_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
            print(f"   - CSV 보고서 생성 완료: {len(csv_df)}건 → {csv_path}")
        
        return csv_path
    
    def run_analysis(self):
        """전체 분석 실행"""
        print("🚀 Rule 2: Contention 분석 시작")
        
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
            # 3. 간트 차트 생성
            self.create_rule2_contention_gantt_chart()
            
            # 4. CSV 보고서 생성
            self.generate_rule2_csv_report()
            
        except Exception as e:
            print(f"❌ 분석 중 오류 발생: {e}")
            import traceback
            traceback.print_exc()
            return False
        
        print("✅ Rule 2 분석 완료!")
        return True

def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description='Rule 2: Contention 분석 및 간트 차트 시각화',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
사용 예시:
  # 방 1135 경합 분석
  python rule2_contention_analyzer.py --room_number 1135 --result_file analysis.csv --output_dir output/
  
참고사항:
  - Rule2는 간트 차트 특성상 room_number가 반드시 필요합니다
  - 전체 방 분석은 지원하지 않습니다 (단일방만 지원)
  - preprocessor_file은 사용하지 않습니다 (result_file만 사용)
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
        help='분석 결과 CSV 파일 경로 (차트 및 CSV 보고서용 데이터)'
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
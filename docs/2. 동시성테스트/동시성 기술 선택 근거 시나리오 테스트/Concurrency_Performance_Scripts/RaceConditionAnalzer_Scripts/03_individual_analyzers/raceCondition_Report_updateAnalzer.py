"""
Rule 1: Lost Update 분석기 - 완전 독립 실행 가능 파일
값 불일치(Lost Update) 분석 및 시각화
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

class Rule1LostUpdateAnalyzer:
    def __init__(self, room_number=None, preprocessor_file=None, result_file=None, output_dir=None):
        """
        Rule 1 Lost Update 분석기 초기화
        
        Args:
            room_number (int, optional): 분석할 특정 방 번호
            preprocessor_file (str): 전처리 데이터 파일 경로 (차트용)
            result_file (str): 분석 결과 데이터 파일 경로 (CSV용)
            output_dir (str): 출력 디렉토리 경로
        """
        self.room_number = room_number
        self.preprocessor_file = preprocessor_file
        self.result_file = result_file
        self.output_dir = output_dir
        
        # 데이터 저장용 변수
        self.df_preprocessor = None
        self.df_result = None
        
        # 고정밀도 데이터 여부
        self.has_high_precision = False
    
    def load_data(self):
        """CSV 파일들을 로드하고 전처리"""
        try:
            # 전처리 파일 로드 (차트용)
            self.df_preprocessor = pd.read_csv(self.preprocessor_file)
            print(f"✅ 전처리 파일 로드 완료: {len(self.df_preprocessor)}건")
            
            # 결과 파일 로드 (CSV용)
            self.df_result = pd.read_csv(self.result_file)
            print(f"✅ 결과 파일 로드 완료: {len(self.df_result)}건")
            
            # 나노초 정밀도 데이터 확인
            if any('nanoTime' in col or 'epochNano' in col for col in self.df_preprocessor.columns):
                self.has_high_precision = True
                print("✅ 고정밀도 나노초 데이터 감지")
                
        except FileNotFoundError as e:
            print(f"❌ 파일을 찾을 수 없습니다: {e}")
            return False
        except Exception as e:
            print(f"❌ 데이터 로딩 오류: {e}")
            return False
        
        # 날짜 컬럼 변환
        date_columns = ['prev_entry_time', 'curr_entry_time']
        for col in date_columns:
            # 전처리 데이터에서 날짜 변환
            if col in self.df_preprocessor.columns:
                self.df_preprocessor[col] = pd.to_datetime(self.df_preprocessor[col])
                print(f"✅ 전처리 데이터 {col} 컬럼 datetime 변환 완료")
            
            # 결과 데이터에서 날짜 변환
            if col in self.df_result.columns:
                self.df_result[col] = pd.to_datetime(self.df_result[col])
                print(f"✅ 결과 데이터 {col} 컬럼 datetime 변환 완료")
        
        # 방 번호 필터링 (지정된 경우만)
        if self.room_number is not None:
            before_filter_preprocessor = len(self.df_preprocessor)
            before_filter_result = len(self.df_result)
            
            self.df_preprocessor = self.df_preprocessor[self.df_preprocessor['roomNumber'] == self.room_number]
            self.df_result = self.df_result[self.df_result['roomNumber'] == self.room_number]
            
            print(f"✅ 방 {self.room_number} 필터링 완료:")
            print(f"   - 전처리 데이터: {before_filter_preprocessor} → {len(self.df_preprocessor)}건")
            print(f"   - 결과 데이터: {before_filter_result} → {len(self.df_result)}건")
        
        # 인덱스 부여 (정렬 제거 - preprocessor.csv 순서 그대로 사용)
        self.df_preprocessor = self.df_preprocessor.reset_index(drop=True)
        self.df_preprocessor['request_index'] = range(len(self.df_preprocessor))
        print(f"✅ 인덱스 부여 완료 (정렬 없이 원본 순서 유지)")
        
        return True
    
    def create_output_folders(self):
        """출력 폴더 생성"""
        if not os.path.exists(self.output_dir):
            os.makedirs(self.output_dir)
            print(f"✅ 출력 디렉토리 생성: {self.output_dir}")
        else:
            print(f"✅ 출력 디렉토리 확인: {self.output_dir}")
    
    def create_rule1_single_room_chart(self):
        """규칙 1: 단일 방 상세 분석 차트 생성"""
        room_data = self.df_preprocessor.copy()
        if room_data.empty:
            print("❌ 단일 방 데이터가 비어있어 차트 생성을 건너뜁니다.")
            return
        
        print(f"🎯 단일 방 {self.room_number} Rule1 차트 생성 시작")
        
        # X축: 스레드 요청 순서 (인덱스)
        total_requests = len(room_data)
        x_positions = list(range(total_requests))
        print(f"   - 총 요청 수: {total_requests}")
        
        # Y축 데이터 준비
        expected_people_raw = room_data['expected_people'].tolist()
        curr_people_values = room_data['curr_people'].tolist()
        max_people = room_data['max_people'].iloc[0] if not room_data.empty else 20
        print(f"   - 최대 정원: {max_people}")
        
        # expected_people 시각화용 처리 (NaN을 max_people로 대체)
        expected_people_viz = [max_people if pd.isna(x) else x for x in expected_people_raw]
        nan_count = sum(1 for x in expected_people_raw if pd.isna(x))
        print(f"   - NaN 값 {nan_count}개를 max_people({max_people})로 대체")
        
        # Y축 최댓값 동적 계산
        y_max = max(max(expected_people_viz), max(curr_people_values)) * 1.2
        print(f"   - Y축 최댓값: {y_max:.1f}")
        
        # 차트 생성
        fig, ax = plt.subplots(figsize=(20, 12))
        title = f'규칙 1: 값 불일치(Lost Update) 분석 - 방 {self.room_number}'
        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        
        # 1. 연산 시점의 기대값 (파란색 실선 + 작은 원점)
        ax.plot(x_positions, expected_people_viz, 'b-', linewidth=2, 
                marker='o', markersize=3, markerfacecolor='blue', markeredgecolor='blue',
                label='연산 시점의 기대값 (expected_people)', alpha=0.8)
        
        # 2. 실제 기록된 최종값 (주황색 실선 + 작은 원점)
        ax.plot(x_positions, curr_people_values, color='orange', linewidth=2,
                marker='o', markersize=3, markerfacecolor='orange', markeredgecolor='orange',
                label='실제 기록된 최종값 (curr_people)', alpha=0.8)
        
        # 3. 값 불일치 강조 표식 (빨간색 수직 음영)
        mismatch_count = 0
        for i in range(total_requests):
            original_expected = room_data.iloc[i]['expected_people']
            actual = curr_people_values[i]
            
            if pd.notna(original_expected) and original_expected != actual:
                ax.axvspan(i-0.3, i+0.3, ymin=0, ymax=1, color='red', alpha=0.3, 
                        label='값 불일치 (Lost Update)' if mismatch_count == 0 else '')
                mismatch_count += 1
        
        print(f"   - 값 불일치 발생: {mismatch_count}건")
        
        # X축 10개 동일 간격 눈금
        tick_positions = [int(i * total_requests / 10) for i in range(11) if int(i * total_requests / 10) < total_requests]
        if tick_positions and tick_positions[-1] != total_requests - 1:
            tick_positions.append(total_requests - 1)
        ax.set_xticks(tick_positions)
        ax.set_xticklabels(tick_positions)
        
        # Y축 설정
        ax.set_ylim(1, y_max)
        
        # 범례를 좌측 상단에 배치
        ax.legend(fontsize=12, loc='upper left', framealpha=0.9)
        
        # 통계 정보 박스를 범례 우측에 배치 (약 2cm 간격)
        valid_expected_count = len([x for x in expected_people_raw if pd.notna(x)])
        stats_text = (f'총 요청: {total_requests:,}건\n'
                    f'유효 기대값: {valid_expected_count:,}건\n'
                    f'값 불일치: {mismatch_count:,}건')
        
        if valid_expected_count > 0:
            mismatch_rate = mismatch_count / valid_expected_count * 100
            stats_text += f'\n불일치 비율: {mismatch_rate:.1f}%'
        
        ax.text(0.2, 0.98, stats_text, transform=ax.transAxes, fontsize=11,
                verticalalignment='top', bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.9))
        
        # 차트 마무리
        ax.set_xlabel('스레드 요청 순서 (Index)', fontsize=12, fontweight='bold')
        ax.set_ylabel('측정값', fontsize=12, fontweight='bold')
        ax.grid(True, alpha=0.3, linestyle='--')
        plt.tight_layout()
        
        # 파일 저장
        filename = f'rule1_lost_update_analysis_room{self.room_number}.png'
        chart_path = os.path.join(self.output_dir, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
        
        print(f"✅ 단일 방 차트 저장 완료: {chart_path}")
    
    def create_rule1_multi_room_chart(self):
        """규칙 1: 전체 방 종합 분석 차트 생성"""
        rooms = self.df_preprocessor['roomNumber'].unique()
        print(f"🎯 전체 {len(rooms)}개 방 Rule1 종합 차트 생성 시작")
        
        # 각 방별 데이터 정리 (정렬 제거 - 원본 순서 유지)
        room_datasets = {}
        max_requests = 0
        
        for room in rooms:
            room_subset = self.df_preprocessor[
                self.df_preprocessor['roomNumber'] == room
            ].reset_index(drop=True)  # 정렬 제거, 원본 순서 유지
            room_datasets[room] = room_subset
            max_requests = max(max_requests, len(room_subset))
        
        print(f"   - 최대 요청 수: {max_requests}")
        x_positions = list(range(max_requests))
        
        # 평균과 표준편차 계산
        mean_expected, std_expected, mean_curr, std_curr = [], [], [], []
        
        for i in range(max_requests):
            expected_values_at_i, curr_values_at_i = [], []
            
            for room, dataset in room_datasets.items():
                if i < len(dataset):
                    expected_val = dataset.iloc[i]['expected_people']
                    if pd.isna(expected_val):
                        expected_val = dataset.iloc[i]['max_people']
                    expected_values_at_i.append(expected_val)
                    curr_values_at_i.append(dataset.iloc[i]['curr_people'])
            
            if expected_values_at_i and curr_values_at_i:
                mean_expected.append(np.mean(expected_values_at_i))
                std_expected.append(np.std(expected_values_at_i))
                mean_curr.append(np.mean(curr_values_at_i))
                std_curr.append(np.std(curr_values_at_i))
            else:
                mean_expected.append(0)
                std_expected.append(0)
                mean_curr.append(0)
                std_curr.append(0)
        
        print(f"   - 평균/표준편차 계산 완료")
        
        # 차트 생성
        fig, ax = plt.subplots(figsize=(20, 12))
        title = f"규칙 1: 값 불일치(Lost Update) 분석 - 전체 {len(rooms)}개 방 평균 및 표준편차"
        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        
        # numpy 배열로 변환
        mean_expected_array = np.array(mean_expected)
        std_expected_array = np.array(std_expected)
        mean_curr_array = np.array(mean_curr)
        std_curr_array = np.array(std_curr)
        
        # 표준편차 시각화 (평균 ± 표준편차)
        ax.fill_between(x_positions, 
                    mean_expected_array - std_expected_array, 
                    mean_expected_array + std_expected_array, 
                    alpha=0.3, color='blue', label='기대값 표준편차 (±1σ)')
        
        ax.fill_between(x_positions, 
                    mean_curr_array - std_curr_array, 
                    mean_curr_array + std_curr_array, 
                    alpha=0.3, color='orange', label='실제값 표준편차 (±1σ)')
        
        # 평균선 - 실선 + 작은 원점
        ax.plot(x_positions, mean_expected_array, 'b-', linewidth=2, 
                marker='o', markersize=3, markerfacecolor='blue', markeredgecolor='blue',
                label='연산 시점의 기대값 (expected_people)', alpha=0.4)
        ax.plot(x_positions, mean_curr_array, color='orange', linewidth=2, 
                marker='o', markersize=3, markerfacecolor='orange', markeredgecolor='orange',
                label='실제 기록된 최종값 (curr_people)', alpha=0.4)
        
        # 값 불일치 표식 - 수직 음영
        mismatch_count = 0
        for room, dataset in room_datasets.items():
            for i, row in dataset.iterrows():
                original_expected = row['expected_people']
                actual = row['curr_people']
                if pd.notna(original_expected) and original_expected != actual and i < len(x_positions):
                    ax.axvspan(i-0.2, i+0.2, ymin=0, ymax=1, color='red', alpha=0.2,
                            label='값 불일치 (Lost Update)' if mismatch_count == 0 else '')
                    mismatch_count += 1
        
        print(f"   - 전체 값 불일치: {mismatch_count}건")
        
        # X축 10개 동일 간격 눈금
        tick_positions = [int(i * max_requests / 10) for i in range(11) if int(i * max_requests / 10) < max_requests]
        if tick_positions and tick_positions[-1] != max_requests - 1:
            tick_positions.append(max_requests - 1)
        ax.set_xticks(tick_positions)
        ax.set_xticklabels(tick_positions)
        
        # Y축 표준편차 상단 경계의 최댓값 * 1.2
        max_upper_bound = max(
            np.max(mean_expected_array + std_expected_array) if len(mean_expected_array) > 0 else 0,
            np.max(mean_curr_array + std_curr_array) if len(mean_curr_array) > 0 else 0
        )
        y_max = max_upper_bound * 1.2 if max_upper_bound > 0 else 20
        ax.set_ylim(1, y_max)
        print(f"   - Y축 최댓값: {y_max:.1f}")
        
        # 범례를 좌측 상단에 배치
        ax.legend(fontsize=12, loc='upper left', framealpha=0.9)
        
        # 통계 정보를 범례 우측에 배치 (약 2cm 간격)
        total_lost_updates = len(self.df_result[self.df_result['anomaly_type'].fillna('').str.contains('값 불일치', na=False)])
        total_requests = len(self.df_preprocessor)
        
        stats_text = (f'분석 방 수: {len(rooms)}개\n'
                    f'총 요청: {total_requests:,}건\n'
                    f'값 불일치: {total_lost_updates:,}건')
        
        if total_requests > 0:
            overall_rate = total_lost_updates / total_requests * 100
            stats_text += f'\n전체 불일치 비율: {overall_rate:.2f}%'
        
        ax.text(0.2, 0.98, stats_text, transform=ax.transAxes, fontsize=11,
                verticalalignment='top', bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.9))
        
        # 차트 마무리
        ax.set_xlabel('스레드 요청 순서 (Index)', fontsize=12, fontweight='bold')
        ax.set_ylabel('측정값', fontsize=12, fontweight='bold')
        ax.grid(True, alpha=0.3, linestyle='--')
        plt.tight_layout()
        
        # 파일 저장
        filename = 'rule1_lost_update_analysis_all_rooms.png'
        chart_path = os.path.join(self.output_dir, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
        
        print(f"✅ 전체 방 차트 저장 완료: {chart_path}")
    
    def generate_rule1_csv_report(self):
        """규칙 1 오류 데이터 CSV 보고서 생성"""
        print("📋 Rule1 CSV 보고서 생성 시작")
        
        # anomaly_type에 '값 불일치' 포함된 레코드 필터링
        lost_update_anomalies = self.df_result[
            self.df_result['anomaly_type'].fillna('').str.contains('값 불일치', na=False)
        ].copy()
        
        print(f"   - 값 불일치 이상 현상: {len(lost_update_anomalies)}건")
        
        # 파일명 생성
        if self.room_number:
            csv_filename = f'report_rule1_lost_update_errors_room{self.room_number}.csv'
        else:
            csv_filename = 'report_rule1_lost_update_errors.csv'
        
        csv_path = os.path.join(self.output_dir, csv_filename)
        
        # 출력할 컬럼 정의
        required_columns = [
            'roomNumber', 'bin', 'room_entry_sequence', 'user_id',
            'true_critical_section_start', 'true_critical_section_end',
            'prev_people', 'expected_people', 'curr_people', 'lost_update_diff'
        ]
        
        if lost_update_anomalies.empty:
            # 빈 데이터인 경우 빈 DataFrame 생성
            empty_df = pd.DataFrame(columns=required_columns)
            empty_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
            print(f"   - 빈 CSV 파일 생성: {csv_path}")
        else:
            # 사용 가능한 컬럼만 선택
            available_columns = [col for col in required_columns if col in lost_update_anomalies.columns]
            csv_df = lost_update_anomalies[available_columns].copy()
            
            # 누락된 컬럼은 빈 문자열로 추가
            for col in required_columns:
                if col not in csv_df.columns:
                    csv_df[col] = ''
            
            # 컬럼 순서 맞춤
            csv_df = csv_df[required_columns]
            
            # 방 번호 필터링 (이중 확인)
            if self.room_number is not None:
                csv_df = csv_df[csv_df['roomNumber'] == self.room_number]
            
            # 정렬 (CSV 출력용 - 보고서 가독성을 위해 유지)
            if not csv_df.empty:
                sort_columns = ['roomNumber', 'bin', 'room_entry_sequence']
                available_sort_cols = [col for col in sort_columns if col in csv_df.columns]
                if available_sort_cols:
                    csv_df = csv_df.sort_values(available_sort_cols)
            
            # CSV 저장
            csv_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
            print(f"   - CSV 보고서 생성 완료: {len(csv_df)}건 → {csv_path}")
        
        return csv_path
    
    def run_analysis(self):
        """전체 분석 실행"""
        print("🚀 Rule 1: Lost Update 분석 시작")
        
        # 1. 데이터 로딩
        if not self.load_data():
            print("❌ 데이터 로딩 실패")
            return False
        
        # 2. 출력 폴더 생성
        self.create_output_folders()
        
        try:
            # 3. 차트 생성 (단일방 또는 전체방)
            if self.room_number is not None:
                self.create_rule1_single_room_chart()
            else:
                self.create_rule1_multi_room_chart()
            
            # 4. CSV 보고서 생성
            self.generate_rule1_csv_report()
            
        except Exception as e:
            print(f"❌ 분석 중 오류 발생: {e}")
            import traceback
            traceback.print_exc()
            return False
        
        print("✅ Rule 1 분석 완료!")
        return True

def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description='Rule 1: Lost Update 분석 및 시각화',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
사용 예시:
  # 전체 방 분석
  python rule1_lost_update_analyzer.py --preprocessor_file data.csv --result_file result.csv --output_dir output/
  
  # 특정 방 분석  
  python rule1_lost_update_analyzer.py --room_number 1135 --preprocessor_file data.csv --result_file result.csv --output_dir output/
        """
    )
    
    parser.add_argument(
        '--room_number', 
        type=int, 
        help='분석할 특정 방 번호 (생략시 전체 방 종합 분석)'
    )
    
    parser.add_argument(
        '--preprocessor_file',
        type=str,
        required=True,
        help='전처리 데이터 CSV 파일 경로 (차트용 데이터)'
    )
    
    parser.add_argument(
        '--result_file',
        type=str,
        required=True,
        help='분석 결과 CSV 파일 경로 (CSV 보고서용 데이터)'
    )
    
    parser.add_argument(
        '--output_dir',
        type=str,
        required=True,
        help='분석 결과를 저장할 디렉토리 경로'
    )
    
    args = parser.parse_args()
    
    # Rule1 분석기 생성 및 실행
    analyzer = Rule1LostUpdateAnalyzer(
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
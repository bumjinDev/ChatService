"""
Rule 4: State Transition 분석기 - 완전 독립 실행 가능 파일 (All Threads 버전)
상태 전이 오류 분석 및 시각화 (detected_anomalies.csv 기반)
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

class Rule4StateTransitionAnalyzer:
    def __init__(self, room_number=None, preprocessor_file=None, result_file=None, output_dir=None):
        """
        Rule 4 State Transition 분석기 초기화
        
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
            
            # 상태 전이 오류 데이터 확인 (정보용)
            if 'anomaly_type' in self.df_result.columns:
                # NaN 값을 빈 문자열로 채우고 문자열로 변환
                self.df_result['anomaly_type'] = self.df_result['anomaly_type'].fillna('').astype(str)
                state_errors = self.df_result[self.df_result['anomaly_type'].str.contains('상태 전이 오류', na=False)]
                print(f"✅ 상태 전이 오류 데이터 확인: {len(state_errors)}건")
            else:
                print("⚠️ anomaly_type 컬럼이 없음 - 전체 데이터 사용")
                
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
                try:
                    self.df_preprocessor[col] = pd.to_datetime(self.df_preprocessor[col])
                    print(f"✅ 전처리 데이터 {col} 컬럼 datetime 변환 완료")
                except Exception as e:
                    print(f"⚠️ 전처리 데이터 {col} 컬럼 datetime 변환 실패: {e}")
            
            # 결과 데이터에서 날짜 변환
            if col in self.df_result.columns:
                try:
                    self.df_result[col] = pd.to_datetime(self.df_result[col])
                    print(f"✅ 결과 데이터 {col} 컬럼 datetime 변환 완료")
                except Exception as e:
                    print(f"⚠️ 결과 데이터 {col} 컬럼 datetime 변환 실패: {e}")
        
        # 방 번호로 필터링 (지정된 경우만)
        if self.room_number is not None:
            before_filter_preprocessor = len(self.df_preprocessor)
            before_filter_result = len(self.df_result)
            
            self.df_preprocessor = self.df_preprocessor[self.df_preprocessor['roomNumber'] == self.room_number]
            self.df_result = self.df_result[self.df_result['roomNumber'] == self.room_number]
            
            print(f"✅ 방 {self.room_number} 필터링 완료:")
            print(f"   - 전처리 데이터: {before_filter_preprocessor} → {len(self.df_preprocessor)}건")
            print(f"   - 결과 데이터: {before_filter_result} → {len(self.df_result)}건")
        
        # 원천 데이터 순서 유지 - 인덱스만 부여 (정렬 제거)
        self.df_preprocessor = self.df_preprocessor.reset_index(drop=True)
        self.df_preprocessor['request_index'] = range(len(self.df_preprocessor))
        print(f"✅ 원천 데이터 순서 유지 및 request_index 컬럼 추가 완료")
        
        return True
    
    def create_output_folders(self):
        """출력 폴더 생성"""
        if not os.path.exists(self.output_dir):
            os.makedirs(self.output_dir)
            print(f"✅ 출력 디렉토리 생성: {self.output_dir}")
        else:
            print(f"✅ 출력 디렉토리 확인: {self.output_dir}")
    
    def create_rule4_state_transition_chart(self):
        """규칙 4: 상태 전이 오류 분석 차트"""
        if self.room_number is not None:
            self._create_rule4_single_room_chart()
        else:
            self._create_rule4_multi_room_chart()
    
    def _create_rule4_single_room_chart(self):
        """규칙 4: 단일 방 상태 전이 분석 차트 - detected_anomalies 기반"""
        room_data = self.df_preprocessor.copy()
        if room_data.empty:
            print("❌ 단일 방 데이터가 비어있어 차트 생성을 건너뜁니다.")
            return
        
        print(f"🎯 단일 방 {self.room_number} Rule4 차트 생성 시작")
        
        # X축: 스레드 요청 순서 (인덱스)
        total_requests = len(room_data)
        x_positions = list(range(total_requests))
        print(f"   - 총 요청 수: {total_requests}")
        
        # 해당 방의 최대 정원 확인
        max_people = room_data['max_people'].iloc[0] if not room_data.empty else 20
        print(f"   - 최대 정원: {max_people}")
        
        # 데이터 1: 이상적 기대값 (알고리즘으로 생성: index + 2, 단 max_people 초과 안함)
        ideal_expected_values = [min(i + 2, max_people) for i in x_positions]
        print(f"   - 이상적 기대값 범위: {min(ideal_expected_values)} ~ {max(ideal_expected_values)}")
        
        # 데이터 2: 실제 기록된 인원수
        curr_people_values = room_data['curr_people'].tolist()
        print(f"   - 실제값 범위: {min(curr_people_values)} ~ {max(curr_people_values)}")
        
        # detected_anomalies에서 해당 방의 상태 전이 오류 위치 확인
        error_positions = set()
        if 'anomaly_type' in self.df_result.columns:
            room_state_errors = self.df_result[
                (self.df_result['roomNumber'] == self.room_number) &
                (self.df_result['anomaly_type'].str.contains('상태 전이 오류', na=False))
            ]
            
            # room_entry_sequence를 request_index로 변환 (1-based → 0-based)
            for _, error_row in room_state_errors.iterrows():
                sequence = error_row['room_entry_sequence']
                if sequence >= 1 and sequence <= total_requests:
                    error_positions.add(sequence - 1)  # 0-based index로 변환
        
        print(f"   - detected_anomalies 기반 상태 전이 오류: {len(error_positions)}건")
        
        # Y축 최댓값 동적 계산
        y_max = max(max(ideal_expected_values), max(curr_people_values)) * 1.2
        print(f"   - Y축 최댓값: {y_max:.1f}")
        
        # 차트 생성
        fig, ax = plt.subplots(figsize=(20, 12))
        title = f'규칙 4: 상태 전이 오류 분석 - 방 {self.room_number}'
        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        
        # 1. 이상적 기대값 (파란색 점선 + 작은 원점)
        ax.plot(x_positions, ideal_expected_values, 'b--', linewidth=2, 
                marker='o', markersize=3, markerfacecolor='blue', markeredgecolor='blue',
                label='이상적 기대 인원수', alpha=0.8)
        
        # 2. 실제 기록된 인원수 (주황색 실선 + 작은 원점)
        ax.plot(x_positions, curr_people_values, color='orange', linewidth=2,
                marker='o', markersize=3, markerfacecolor='orange', markeredgecolor='orange',
                label='실제 기록된 인원수 (curr_people)', alpha=0.8)
        
        # 3. detected_anomalies 기반 상태 전이 오류 표식 (빨간색 수직 음영)
        error_marked = False
        for i in error_positions:
            ax.axvspan(i-0.3, i+0.3, ymin=0, ymax=1, color='red', alpha=0.3,
                    label='상태 전이 오류 발생' if not error_marked else '')
            error_marked = True
        
        print(f"   - 차트에 표시된 상태 전이 오류: {len(error_positions)}건")
        
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
        
        # 통계 정보 박스를 범례 우측에 배치
        stats_text = (f'총 요청: {total_requests:,}건\n'
                    f'상태 전이 오류: {len(error_positions):,}건')
        
        if total_requests > 0:
            error_rate = len(error_positions) / total_requests * 100
            stats_text += f'\n오류 비율: {error_rate:.1f}%'
        
        ax.text(0.2, 0.98, stats_text, transform=ax.transAxes, fontsize=11,
                verticalalignment='top', bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.9))
        
        # 차트 마무리
        ax.set_xlabel('스레드 요청 순서 (Index)', fontsize=12, fontweight='bold')
        ax.set_ylabel('측정값', fontsize=12, fontweight='bold')
        ax.grid(True, alpha=0.3, linestyle='--')
        plt.tight_layout()
        
        # 파일 저장
        filename = f'rule4_state_transition_analysis_room{self.room_number}.png'
        chart_path = os.path.join(self.output_dir, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
        
        print(f"✅ 단일 방 차트 저장 완료: {chart_path}")
    
    def _create_rule4_multi_room_chart(self):
        """규칙 4: 전체 방 상태 전이 종합 분석 차트 - detected_anomalies 기반"""
        rooms = self.df_preprocessor['roomNumber'].unique()
        print(f"🎯 전체 {len(rooms)}개 방 Rule4 종합 차트 생성 시작")
        
        # 각 방별 데이터 정리 (정렬 제거 - 원천 데이터 순서 유지)
        room_datasets = {}
        max_requests = 0
        
        for room in rooms:
            room_subset = self.df_preprocessor[
                self.df_preprocessor['roomNumber'] == room
            ].reset_index(drop=True)
            room_datasets[room] = room_subset
            max_requests = max(max_requests, len(room_subset))
        
        print(f"   - 최대 요청 수: {max_requests}")
        x_positions = list(range(max_requests))
        
        # 전체 방의 평균 최대 정원 계산
        avg_max_people = int(self.df_preprocessor['max_people'].mean()) if not self.df_preprocessor.empty else 20
        print(f"   - 평균 최대 정원: {avg_max_people}")
        
        # 이상적 기대값 (평균 최대 정원 초과 안함: index + 2, 단 avg_max_people 초과 안함)
        ideal_expected_values = [min(i + 2, avg_max_people) for i in x_positions]
        
        # 평균과 표준편차 계산 (실제값만)
        mean_curr, std_curr = [], []
        
        for i in range(max_requests):
            curr_values_at_i = []
            
            for room, dataset in room_datasets.items():
                if i < len(dataset):
                    curr_values_at_i.append(dataset.iloc[i]['curr_people'])
            
            if curr_values_at_i:
                mean_curr.append(np.mean(curr_values_at_i))
                std_curr.append(np.std(curr_values_at_i))
            else:
                mean_curr.append(0)
                std_curr.append(0)
        
        print(f"   - 평균/표준편차 계산 완료")
        
        # detected_anomalies에서 모든 방의 상태 전이 오류 위치 확인
        room_error_positions = {}
        total_error_count = 0
        
        if 'anomaly_type' in self.df_result.columns:
            all_state_errors = self.df_result[
                self.df_result['anomaly_type'].str.contains('상태 전이 오류', na=False)
            ]
            
            # 방별 오류 위치 매핑
            for room in rooms:
                room_errors = all_state_errors[all_state_errors['roomNumber'] == room]
                error_positions = set()
                
                for _, error_row in room_errors.iterrows():
                    sequence = error_row['room_entry_sequence']
                    room_data_length = len(room_datasets[room])
                    if sequence >= 1 and sequence <= room_data_length:
                        error_positions.add(sequence - 1)  # 0-based index로 변환
                
                room_error_positions[room] = error_positions
                total_error_count += len(error_positions)
        
        print(f"   - detected_anomalies 기반 전체 상태 전이 오류: {total_error_count}건")
        
        # 차트 생성
        fig, ax = plt.subplots(figsize=(20, 12))
        title = f"규칙 4: 상태 전이 오류 분석 - 전체 {len(rooms)}개 방 평균 및 표준편차"
        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        
        # numpy 배열로 변환
        mean_curr_array = np.array(mean_curr)
        std_curr_array = np.array(std_curr)
        ideal_expected_array = np.array(ideal_expected_values)
        
        # 1. 이상적 기대값 (파란색 점선 + 작은 원점)
        ax.plot(x_positions, ideal_expected_array, 'b--', linewidth=2,
                marker='o', markersize=3, markerfacecolor='blue', markeredgecolor='blue',
                label='이상적 기대 인원수', alpha=0.8)
        
        # 2. 실제값 신뢰구간 (주황색 음영)
        ax.fill_between(x_positions, 
                    mean_curr_array - std_curr_array, 
                    mean_curr_array + std_curr_array, 
                    alpha=0.3, color='orange', label='실제값 변동 범위 (±1 표준편차)')
        
        # 3. 평균 실제 인원수 (주황색 실선 + 작은 원점)
        ax.plot(x_positions, mean_curr_array, color='orange', linewidth=2,
                marker='o', markersize=3, markerfacecolor='orange', markeredgecolor='orange',
                label='평균 실제 기록된 인원수 (curr_people)', alpha=0.8)
        
        # 4. detected_anomalies 기반 상태 전이 오류 표식 (빨간색 수직 음영)
        error_marked = False
        for room, error_positions in room_error_positions.items():
            for i in error_positions:
                if i < len(x_positions):
                    ax.axvspan(i-0.2, i+0.2, ymin=0, ymax=1, color='red', alpha=0.2,
                            label='상태 전이 오류 발생' if not error_marked else '')
                    error_marked = True
        
        print(f"   - 차트에 표시된 상태 전이 오류: {total_error_count}건")
        
        # X축 10개 동일 간격 눈금
        tick_positions = [int(i * max_requests / 10) for i in range(11) if int(i * max_requests / 10) < max_requests]
        if tick_positions and tick_positions[-1] != max_requests - 1:
            tick_positions.append(max_requests - 1)
        ax.set_xticks(tick_positions)
        ax.set_xticklabels(tick_positions)
        
        # Y축 동적 계산
        max_upper_bound = max(
            np.max(ideal_expected_array) if len(ideal_expected_array) > 0 else 0,
            np.max(mean_curr_array + std_curr_array) if len(mean_curr_array) > 0 else 0
        )
        y_max = max_upper_bound * 1.2 if max_upper_bound > 0 else 25
        ax.set_ylim(1, y_max)
        print(f"   - Y축 최댓값: {y_max:.1f}")
        
        # 범례를 좌측 상단에 배치
        ax.legend(fontsize=12, loc='upper left', framealpha=0.9)
        
        # 통계 정보를 범례 우측에 배치
        total_requests = len(self.df_preprocessor)
        total_state_errors = 0
        if 'anomaly_type' in self.df_result.columns:
            all_state_errors = self.df_result[
                self.df_result['anomaly_type'].str.contains('상태 전이 오류', na=False)
            ]
            total_state_errors = len(all_state_errors)
        
        stats_text = (f'분석 방 수: {len(rooms)}개\n'
                    f'총 요청: {total_requests:,}건\n'
                    f'상태 전이 오류: {total_state_errors:,}건')
        
        if total_requests > 0:
            overall_rate = total_state_errors / total_requests * 100
            stats_text += f'\n전체 오류 비율: {overall_rate:.2f}%'
        
        ax.text(0.2, 0.98, stats_text, transform=ax.transAxes, fontsize=11,
                verticalalignment='top', bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.9))
        
        # 차트 마무리
        ax.set_xlabel('스레드 요청 순서 (Index)', fontsize=12, fontweight='bold')
        ax.set_ylabel('측정값', fontsize=12, fontweight='bold')
        ax.grid(True, alpha=0.3, linestyle='--')
        plt.tight_layout()
        
        # 파일 저장
        filename = 'rule4_state_transition_analysis_all_rooms.png'
        chart_path = os.path.join(self.output_dir, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
        
        print(f"✅ 전체 방 차트 저장 완료: {chart_path}")
    
    def generate_rule4_csv_report(self):
        """규칙 4 전체 스레드 CSV 보고서 생성 (오류 여부 무관, 모든 스레드 포함)"""
        print("📋 Rule4 전체 스레드 CSV 보고서 생성 시작")
        
        # 오류 여부와 상관없이 모든 데이터 사용 (차트와 동일한 로직)
        all_thread_data = self.df_result.copy()
        
        print(f"   - 전체 스레드 데이터: {len(all_thread_data)}건")
        
        # 방 번호 필터링 (지정된 경우만)
        if self.room_number is not None:
            all_thread_data = all_thread_data[
                all_thread_data['roomNumber'] == self.room_number
            ]
            print(f"   - 방 {self.room_number} 필터링 후: {len(all_thread_data)}건")
        
        # 파일명 생성 (모든 스레드 포함을 명시)
        if self.room_number:
            csv_filename = f'report_rule4_all_threads_room{self.room_number}_complete.csv'
        else:
            csv_filename = 'report_rule4_all_threads_complete.csv'
        
        csv_path = os.path.join(self.output_dir, csv_filename)
        
        # 출력할 컬럼 정의
        required_columns = [
            'roomNumber', 'bin', 'room_entry_sequence', 'sorted_sequence_position',
            'user_id', 'prev_entry_time', 'curr_entry_time',
            'true_critical_section_start', 'true_critical_section_end',
            'prev_people', 'curr_people', 'expected_curr_by_sequence', 'actual_curr_people',
            'curr_sequence_diff', 'anomaly_type',
            'true_critical_section_duration', 'true_critical_section_nanoTime_start', 
            'true_critical_section_nanoTime_end', 'true_critical_section_duration_nanos',
            'contention_group_size', 'contention_user_ids', 'intervening_users_in_critical_section', 
            'intervening_user_count_critical'
        ]
        
        if all_thread_data.empty:
            # 빈 데이터인 경우 빈 DataFrame 생성
            empty_df = pd.DataFrame(columns=required_columns)
            empty_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
            print(f"   - 빈 CSV 파일 생성: {csv_path}")
        else:
            # 사용 가능한 컬럼만 선택
            available_columns = [col for col in required_columns if col in all_thread_data.columns]
            csv_df = all_thread_data[available_columns].copy()
            
            # 누락된 컬럼은 빈 문자열로 추가
            for col in required_columns:
                if col not in csv_df.columns:
                    csv_df[col] = ''
            
            # 컬럼 순서 맞춤
            csv_df = csv_df[required_columns]
            
            # 정렬 제거 - 원천 데이터 순서 유지
            # 기존 정렬 로직 완전 제거
            
            # CSV 저장
            csv_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
            print(f"   - 전체 스레드 CSV 보고서 생성 완료: {len(csv_df)}건 → {csv_path}")
            print(f"   - 오류 여부와 상관없이 모든 스레드 데이터 포함 (원본 순서 유지)")
        
        return csv_path
    
    def run_analysis(self):
        """전체 분석 실행"""
        print("🚀 Rule 4: State Transition 분석 시작")
        
        # 1. 데이터 로딩
        if not self.load_data():
            print("❌ 데이터 로딩 실패")
            return False
        
        # 2. 출력 폴더 생성
        self.create_output_folders()
        
        try:
            # 3. 차트 생성 (단일방 또는 전체방)
            self.create_rule4_state_transition_chart()
            
            # 4. 전체 스레드 CSV 보고서 생성 (오류 여부 무관)
            self.generate_rule4_csv_report()
            
        except Exception as e:
            print(f"❌ 분석 중 오류 발생: {e}")
            import traceback
            traceback.print_exc()
            return False
        
        print("✅ Rule 4 분석 완료! (전체 스레드 데이터 - 원본 순서 유지)")
        return True

def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description='Rule 4: State Transition 분석 및 시각화 (All Threads 버전)',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
사용 예시:
  # 전체 방 분석
  python rule4_state_transition_analyzer_all.py --preprocessor_file data.csv --result_file result.csv --output_dir output/
  
  # 특정 방 분석  
  python rule4_state_transition_analyzer_all.py --room_number 1135 --preprocessor_file data.csv --result_file result.csv --output_dir output/
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
    
    # Rule4 분석기 생성 및 실행
    analyzer = Rule4StateTransitionAnalyzer(
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
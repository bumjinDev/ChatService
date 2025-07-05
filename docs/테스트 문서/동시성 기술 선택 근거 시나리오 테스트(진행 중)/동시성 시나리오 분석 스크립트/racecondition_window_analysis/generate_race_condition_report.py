"""
Race Condition 분석 리포트 생성기 - 완전 버전
규칙 1-4 모든 기능 포함
"""
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import os
import warnings
import platform
import matplotlib.font_manager as fm
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

setup_korean_font()

class RaceConditionAnalyzer:
    def __init__(self, room_number=None):
        self.room_number = room_number
        self.df_preprocessor = None
        self.df_result = None
        self.base_folder = 'race_condition_analysis_results'
        self.rule_stats_folder = os.path.join(self.base_folder, '1_rule_statistics')
        self.rule1_folder = os.path.join(self.base_folder, '2_rule1_lost_update')
        self.rule2_folder = os.path.join(self.base_folder, '3_rule2_contention')
        self.rule3_folder = os.path.join(self.base_folder, '4_rule3_capacity_exceeded')
        self.rule4_folder = os.path.join(self.base_folder, '5_rule4_state_transition')
        self.has_high_precision = False
        self.rule_stats = {
            'lost_update': 0, 'contention': 0, 'capacity_exceeded': 0, 
            'stale_read': 0, 'total_anomalies': 0, 'total_requests': 0
        }
    
    def create_output_folders(self):
        """출력 폴더들 생성"""
        folders = [self.base_folder, self.rule_stats_folder, self.rule1_folder, self.rule2_folder, self.rule3_folder, self.rule4_folder]
        for folder in folders:
            if not os.path.exists(folder):
                os.makedirs(folder)

    def load_data(self):
        """CSV 파일들을 로드하고 전처리"""
        try:
            self.df_preprocessor = pd.read_csv('racecondition_event_preprocessor_result.csv')
            self.df_result = pd.read_csv('anomaly_result.csv')
            
            if any('nanoTime' in col or 'epochNano' in col for col in self.df_preprocessor.columns):
                self.has_high_precision = True
                
        except FileNotFoundError as e:
            print(f"❌ 파일을 찾을 수 없습니다: {e}")
            return False
        except Exception as e:
            print(f"❌ 데이터 로딩 오류: {e}")
            return False
            
        # 날짜 컬럼 변환
        for col in ['prev_entry_time', 'curr_entry_time']:
            for df in [self.df_preprocessor, self.df_result]:
                if col in df.columns:
                    df[col] = pd.to_datetime(df[col])
        
        # 방 번호로 필터링
        if self.room_number is not None:
            self.df_preprocessor = self.df_preprocessor[self.df_preprocessor['roomNumber'] == self.room_number]
            self.df_result = self.df_result[self.df_result['roomNumber'] == self.room_number]
        
        # 시간순 정렬 및 인덱스 부여
        self.df_preprocessor = self.df_preprocessor.sort_values('curr_entry_time').reset_index(drop=True)
        self.df_preprocessor['request_index'] = range(len(self.df_preprocessor))
        
        return True
    
    def calculate_rule_statistics(self):
        """4가지 규칙별 통계 계산"""
        self.rule_stats['lost_update'] = len(self.df_result[
            self.df_result['anomaly_type'].str.contains('값 불일치', na=False)
        ])
        self.rule_stats['contention'] = len(self.df_result[
            self.df_result['anomaly_type'].str.contains('경합 발생', na=False)
        ])
        self.rule_stats['capacity_exceeded'] = len(self.df_result[
            self.df_result['curr_people'] > self.df_result['max_people']
        ])
        self.rule_stats['stale_read'] = len(self.df_result[
            self.df_result['anomaly_type'].str.contains('상태 전이 오류', na=False)
        ])
        self.rule_stats['total_anomalies'] = len(self.df_result)
        self.rule_stats['total_requests'] = len(self.df_preprocessor)
    
    def create_rule1_lost_update_chart(self):
        """규칙 1: 값 불일치(Lost Update) 분석 차트"""
        if self.room_number is not None:
            self._create_rule1_single_room_chart()
        else:
            self._create_rule1_multi_room_chart()
    
    def create_rule3_capacity_exceeded_chart(self):
        """규칙 3: 정원 초과 오류 분석 차트"""
        if self.room_number is not None:
            self._create_rule3_single_room_chart()
        else:
            self._create_rule3_multi_room_chart()
    
    def create_rule4_state_transition_chart(self):
        """규칙 4: 상태 전이 오류 분석 차트"""
        if self.room_number is not None:
            self._create_rule4_single_room_chart()
        else:
            self._create_rule4_multi_room_chart()
    
    def _create_rule1_single_room_chart(self):
        """규칙 1: 단일 방 상세 분석 차트 - 실선 + 원점 + 좌우 배치 버전"""
        room_data = self.df_preprocessor.copy()
        if room_data.empty:
            return
        
        # X축: 스레드 요청 순서 (인덱스)
        total_requests = len(room_data)
        x_positions = list(range(total_requests))
        
        # Y축 데이터 준비
        expected_people_raw = room_data['expected_people'].tolist()
        curr_people_values = room_data['curr_people'].tolist()
        max_people = room_data['max_people'].iloc[0] if not room_data.empty else 20
        
        # expected_people 시각화용 처리 (NaN을 max_people로 대체)
        expected_people_viz = [max_people if pd.isna(x) else x for x in expected_people_raw]
        
        # Y축 최댓값 동적 계산
        y_max = max(max(expected_people_viz), max(curr_people_values)) * 1.2
        
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
        
        # 3. 값 불일치 강조 표식 (자홍색 수직 음영만)
        mismatch_count = 0
        for i in range(total_requests):
            original_expected = room_data.iloc[i]['expected_people']
            actual = curr_people_values[i]
            
            if pd.notna(original_expected) and original_expected != actual:
                ax.axvspan(i-0.3, i+0.3, ymin=0, ymax=1, color='red', alpha=0.3, 
                        label='값 불일치 (Lost Update)' if mismatch_count == 0 else '')
                mismatch_count += 1
        
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
        chart_path = os.path.join(self.rule1_folder, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
    
    def _create_rule1_multi_room_chart(self):
        """규칙 1: 전체 방 종합 분석 차트 - 실선 + 원점 + 좌우 배치 버전"""
        rooms = self.df_preprocessor['roomNumber'].unique()
        
        # 각 방별 데이터 정리
        room_datasets = {}
        max_requests = 0
        
        for room in rooms:
            room_subset = self.df_preprocessor[
                self.df_preprocessor['roomNumber'] == room
            ].sort_values('curr_entry_time').reset_index(drop=True)
            room_datasets[room] = room_subset
            max_requests = max(max_requests, len(room_subset))
        
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
        
        # 차트 생성
        fig, ax = plt.subplots(figsize=(20, 12))
        title = f"규칙 1: 값 불일치(Lost Update) 분석 - 전체 {len(rooms)}개 방 평균 및 신뢰구간"
        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        
        # numpy 배열로 변환
        mean_expected_array = np.array(mean_expected)
        std_expected_array = np.array(std_expected)
        mean_curr_array = np.array(mean_curr)
        std_curr_array = np.array(std_curr)
        
        # 신뢰구간 시각화 (평균 ± 표준편차)
        ax.fill_between(x_positions, 
                    mean_expected_array - std_expected_array, 
                    mean_expected_array + std_expected_array, 
                    alpha=0.3, color='blue', label='기대값 신뢰구간 (±1σ)')
        
        ax.fill_between(x_positions, 
                    mean_curr_array - std_curr_array, 
                    mean_curr_array + std_curr_array, 
                    alpha=0.3, color='orange', label='실제값 신뢰구간 (±1σ)')
        
        # 평균선 - 실선 + 작은 원점
        ax.plot(x_positions, mean_expected_array, 'b-', linewidth=2, 
                marker='o', markersize=3, markerfacecolor='blue', markeredgecolor='blue',
                label='연산 시점의 기대값 (expected_people)', alpha=0.8)
        ax.plot(x_positions, mean_curr_array, color='orange', linewidth=2, 
                marker='o', markersize=3, markerfacecolor='orange', markeredgecolor='orange',
                label='실제 기록된 최종값 (curr_people)', alpha=0.8)
        
        # 값 불일치 표식 - 수직 음영만
        mismatch_count = 0
        for room, dataset in room_datasets.items():
            for i, row in dataset.iterrows():
                original_expected = row['expected_people']
                actual = row['curr_people']
                if pd.notna(original_expected) and original_expected != actual and i < len(x_positions):
                    ax.axvspan(i-0.2, i+0.2, ymin=0, ymax=1, color='red', alpha=0.2,
                            label='값 불일치 (Lost Update)' if mismatch_count == 0 else '')
                    mismatch_count += 1
        
        # X축 10개 동일 간격 눈금
        tick_positions = [int(i * max_requests / 10) for i in range(11) if int(i * max_requests / 10) < max_requests]
        if tick_positions and tick_positions[-1] != max_requests - 1:
            tick_positions.append(max_requests - 1)
        ax.set_xticks(tick_positions)
        ax.set_xticklabels(tick_positions)
        
        # Y축 신뢰구간 상단 경계의 최댓값 * 1.2
        max_upper_bound = max(
            np.max(mean_expected_array + std_expected_array) if len(mean_expected_array) > 0 else 0,
            np.max(mean_curr_array + std_curr_array) if len(mean_curr_array) > 0 else 0
        )
        y_max = max_upper_bound * 1.2 if max_upper_bound > 0 else 20
        ax.set_ylim(1, y_max)
        
        # 범례를 좌측 상단에 배치
        ax.legend(fontsize=12, loc='upper left', framealpha=0.9)
        
        # 통계 정보를 범례 우측에 배치 (약 2cm 간격)
        total_lost_updates = len(self.df_result[self.df_result['anomaly_type'].str.contains('값 불일치', na=False)])
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
        chart_path = os.path.join(self.rule1_folder, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
    
    def _create_rule3_single_room_chart(self):
        """규칙 3: 단일 방 정원 초과 분석 차트 - 좌우 배치 버전"""
        room_data = self.df_preprocessor.copy()
        if room_data.empty:
            return
        
        # X축: 스레드 요청 순서 (인덱스)
        total_requests = len(room_data)
        x_positions = list(range(total_requests))
        
        # Y축 데이터 준비
        curr_people_values = room_data['curr_people'].tolist()
        max_people = room_data['max_people'].iloc[0] if not room_data.empty else 20
        
        # Y축 최댓값 동적 계산
        y_max = max(max(curr_people_values), max_people) * 1.2
        
        # 차트 생성
        fig, ax = plt.subplots(figsize=(20, 12))
        title = f'규칙 3: 정원 초과 오류 분석 - 방 {self.room_number}'
        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        
        # 1. 최대 정원 한계선 (붉은색 점선)
        ax.axhline(y=max_people, color='red', linestyle='--', linewidth=2, 
                   label=f'최대 정원 한계선 (max_people = {max_people})', alpha=0.8)
        
        # 2. 실제 기록된 인원수 (파란색 실선)
        ax.plot(x_positions, curr_people_values, color='blue', linewidth=3,
                label='실제 기록된 인원수 (curr_people)', alpha=0.8, marker='o', markersize=4)
        
        # 3. 정원 초과 발생 시점 강조 (Y축 전체 수직 기둥)
        exceeded_count = 0
        exceeded_positions = []
        
        for i in range(total_requests):
            curr = curr_people_values[i]
            
            # curr_people > max_people인 경우
            if curr > max_people:
                ax.axvspan(i-0.3, i+0.3, ymin=0, ymax=1, color='magenta', alpha=0.3,
                          label='정원 초과 발생 시점' if exceeded_count == 0 else '')
                ax.scatter(i, curr, color='red', s=100, alpha=1.0, zorder=5)
                exceeded_count += 1
                exceeded_positions.append(i)
        
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
        max_exceeded = max([curr_people_values[i] - max_people for i in exceeded_positions]) if exceeded_positions else 0
        stats_text = (f'총 요청: {total_requests:,}건\n'
                     f'최대 정원: {max_people}명\n'
                     f'정원 초과: {exceeded_count:,}건')
        
        if exceeded_count > 0:
            exceeded_rate = exceeded_count / total_requests * 100
            stats_text += f'\n초과 비율: {exceeded_rate:.1f}%'
            stats_text += f'\n최대 초과: +{max_exceeded}명'
        
        ax.text(0.2, 0.98, stats_text, transform=ax.transAxes, fontsize=11,
                verticalalignment='top', bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.9))
        
        # 차트 마무리
        ax.set_xlabel('스레드 요청 순서 (Index)', fontsize=12, fontweight='bold')
        ax.set_ylabel('측정값', fontsize=12, fontweight='bold')
        ax.grid(True, alpha=0.3, linestyle='--')
        plt.tight_layout()
        
        # 파일 저장
        filename = f'rule3_capacity_exceeded_analysis_room{self.room_number}.png'
        chart_path = os.path.join(self.rule3_folder, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
    
    def _create_rule3_multi_room_chart(self):
        """규칙 3: 전체 방 정원 초과 종합 분석 차트 - 좌우 배치 버전"""
        rooms = self.df_preprocessor['roomNumber'].unique()
        
        # 각 방별 데이터 정리
        room_datasets = {}
        max_requests = 0
        
        for room in rooms:
            room_subset = self.df_preprocessor[
                self.df_preprocessor['roomNumber'] == room
            ].sort_values('curr_entry_time').reset_index(drop=True)
            room_datasets[room] = room_subset
            max_requests = max(max_requests, len(room_subset))
        
        x_positions = list(range(max_requests))
        
        # 평균과 표준편차 계산
        mean_curr, std_curr = [], []
        mean_max_people = []
        
        for i in range(max_requests):
            curr_values_at_i = []
            max_people_values_at_i = []
            
            for room, dataset in room_datasets.items():
                if i < len(dataset):
                    curr_values_at_i.append(dataset.iloc[i]['curr_people'])
                    max_people_values_at_i.append(dataset.iloc[i]['max_people'])
            
            if curr_values_at_i:
                mean_curr.append(np.mean(curr_values_at_i))
                std_curr.append(np.std(curr_values_at_i))
                mean_max_people.append(np.mean(max_people_values_at_i))
            else:
                mean_curr.append(0)
                std_curr.append(0)
                mean_max_people.append(20)  # 기본값
        
        # 차트 생성
        fig, ax = plt.subplots(figsize=(20, 12))
        title = f"규칙 3: 정원 초과 오류 분석 - 전체 {len(rooms)}개 방 평균 및 신뢰구간"
        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        
        # numpy 배열로 변환
        mean_curr_array = np.array(mean_curr)
        std_curr_array = np.array(std_curr)
        mean_max_array = np.array(mean_max_people)
        
        # 1. 평균 최대 정원 한계선
        ax.plot(x_positions, mean_max_array, 'r--', linewidth=2,
                label='평균 최대 정원 한계선 (max_people)', alpha=0.8)
        
        # 2. 실제값 신뢰구간
        ax.fill_between(x_positions, 
                       mean_curr_array - std_curr_array, 
                       mean_curr_array + std_curr_array, 
                       alpha=0.3, color='blue', label='실제값 신뢰구간 (±1σ)')
        
        # 3. 평균 실제 인원수
        ax.plot(x_positions, mean_curr_array, color='blue', linewidth=3,
                label='평균 실제 기록된 인원수 (curr_people)', alpha=0.8)
        
        # 4. 정원 초과 표식 (Y축 전체 기둥)
        exceeded_count = 0
        for room, dataset in room_datasets.items():
            for i, row in dataset.iterrows():
                if i < len(x_positions) and row['curr_people'] > row['max_people']:
                    ax.axvspan(i-0.2, i+0.2, ymin=0, ymax=1, color='magenta', alpha=0.2,
                              label='정원 초과 발생 시점' if exceeded_count == 0 else '')
                    exceeded_count += 1
        
        # X축 10개 동일 간격 눈금
        tick_positions = [int(i * max_requests / 10) for i in range(11) if int(i * max_requests / 10) < max_requests]
        if tick_positions and tick_positions[-1] != max_requests - 1:
            tick_positions.append(max_requests - 1)
        ax.set_xticks(tick_positions)
        ax.set_xticklabels(tick_positions)
        
        # Y축 동적 계산
        max_upper_bound = np.max(mean_curr_array + std_curr_array) if len(mean_curr_array) > 0 else 20
        y_max = max(max_upper_bound, np.max(mean_max_array)) * 1.2 if len(mean_max_array) > 0 else 25
        ax.set_ylim(1, y_max)
        
        # 범례를 좌측 상단에 배치
        ax.legend(fontsize=12, loc='upper left', framealpha=0.9)
        
        # 통계 정보를 범례 우측에 배치 (약 2cm 간격)
        total_requests = len(self.df_preprocessor)
        total_exceeded = len(self.df_preprocessor[self.df_preprocessor['curr_people'] > self.df_preprocessor['max_people']])
        
        stats_text = (f'분석 방 수: {len(rooms)}개\n'
                    f'총 요청: {total_requests:,}건\n'
                    f'정원 초과: {total_exceeded:,}건')
       
        if total_requests > 0:
           overall_rate = total_exceeded / total_requests * 100
           stats_text += f'\n전체 초과 비율: {overall_rate:.2f}%'
       
        ax.text(0.2, 0.98, stats_text, transform=ax.transAxes, fontsize=11,
               verticalalignment='top', bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.9))
       
        # 차트 마무리
        ax.set_xlabel('스레드 요청 순서 (Index)', fontsize=12, fontweight='bold')
        ax.set_ylabel('측정값', fontsize=12, fontweight='bold')
        ax.grid(True, alpha=0.3, linestyle='--')
        plt.tight_layout()
       
        # 파일 저장
        filename = 'rule3_capacity_exceeded_analysis_all_rooms.png'
        chart_path = os.path.join(self.rule3_folder, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()

    def _create_rule4_single_room_chart(self):
        """규칙 4: 단일 방 상태 전이 분석 차트"""
        room_data = self.df_preprocessor.copy()
        if room_data.empty:
            return
        
        # X축: 스레드 요청 순서 (인덱스)
        total_requests = len(room_data)
        x_positions = list(range(total_requests))
        
        # 최대 정원 확인
        max_people = room_data['max_people'].iloc[0] if not room_data.empty else 20
        
        # 데이터 1: 이상적 기대값 (알고리즘으로 생성: index + 2, 단 max_people 초과 안함)
        ideal_expected_values = [min(i + 2, max_people) for i in x_positions]
        
        # 데이터 2: 실제 기록된 인원수
        curr_people_values = room_data['curr_people'].tolist()
        
        # Y축 최댓값 동적 계산
        y_max = max(max(ideal_expected_values), max(curr_people_values)) * 1.2
        
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
        
        # 3. 불일치 발생 강조 표식 (수직 막대)
        discrepancy_count = 0
        for i in range(total_requests):
            ideal_val = ideal_expected_values[i]
            actual_val = curr_people_values[i]
            
            if ideal_val != actual_val:
                ax.axvspan(i-0.3, i+0.3, ymin=0, ymax=1, color='red', alpha=0.3,
                        label='상태 전이 오류 발생' if discrepancy_count == 0 else '')
                discrepancy_count += 1
        
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
                    f'상태 전이 오류: {discrepancy_count:,}건')
        
        if total_requests > 0:
            error_rate = discrepancy_count / total_requests * 100
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
        chart_path = os.path.join(self.rule4_folder, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()


    def _create_rule4_multi_room_chart(self):
        """규칙 4: 전체 방 상태 전이 종합 분석 차트"""
        rooms = self.df_preprocessor['roomNumber'].unique()
        
        # 각 방별 데이터 정리
        room_datasets = {}
        max_requests = 0
        
        for room in rooms:
            room_subset = self.df_preprocessor[
                self.df_preprocessor['roomNumber'] == room
            ].sort_values('curr_entry_time').reset_index(drop=True)
            room_datasets[room] = room_subset
            max_requests = max(max_requests, len(room_subset))
        
        x_positions = list(range(max_requests))
        
        # 전체 방의 평균 최대 정원 계산
        avg_max_people = int(self.df_preprocessor['max_people'].mean()) if not self.df_preprocessor.empty else 20
        
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
        
        # 차트 생성
        fig, ax = plt.subplots(figsize=(20, 12))
        title = f"규칙 4: 상태 전이 오류 분석 - 전체 {len(rooms)}개 방 평균 및 신뢰구간"
        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        
        # numpy 배열로 변환
        mean_curr_array = np.array(mean_curr)
        std_curr_array = np.array(std_curr)
        ideal_expected_array = np.array(ideal_expected_values)
        
        # 1. 이상적 기대값 (파란색 점선 + 작은 원점)
        ax.plot(x_positions, ideal_expected_array, 'b--', linewidth=2,
                marker='o', markersize=3, markerfacecolor='blue', markeredgecolor='blue',
                label='이상적 기대 인원수', alpha=0.8)
        
        # 2. 실제값 신뢰구간
        ax.fill_between(x_positions, 
                    mean_curr_array - std_curr_array, 
                    mean_curr_array + std_curr_array, 
                    alpha=0.3, color='orange', label='실제값 신뢰구간 (±1σ)')
        
        # 3. 평균 실제 인원수 (주황색 실선 + 작은 원점)
        ax.plot(x_positions, mean_curr_array, color='orange', linewidth=2,
                marker='o', markersize=3, markerfacecolor='orange', markeredgecolor='orange',
                label='평균 실제 기록된 인원수 (curr_people)', alpha=0.8)
        
        # 4. 상태 전이 오류 표식 (수직 음영)
        discrepancy_count = 0
        for room, dataset in room_datasets.items():
            room_max_people = dataset['max_people'].iloc[0] if not dataset.empty else 20
            for i, row in dataset.iterrows():
                ideal_val = min(i + 2, room_max_people)  # 각 방의 최대 정원 적용
                actual_val = row['curr_people']
                if ideal_val != actual_val and i < len(x_positions):
                    ax.axvspan(i-0.2, i+0.2, ymin=0, ymax=1, color='red', alpha=0.2,
                            label='상태 전이 오류 발생' if discrepancy_count == 0 else '')
                    discrepancy_count += 1
        
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
        
        # 범례를 좌측 상단에 배치
        ax.legend(fontsize=12, loc='upper left', framealpha=0.9)
        
        # 통계 정보를 범례 우측에 배치
        total_requests = len(self.df_preprocessor)
        total_state_errors = len(self.df_result[self.df_result['anomaly_type'].str.contains('상태 전이 오류', na=False)])
        
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
        chart_path = os.path.join(self.rule4_folder, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()

    def generate_rule1_csv_report(self):
       """규칙 1 오류 데이터 CSV 보고서 생성"""
       lost_update_anomalies = self.df_result[
           self.df_result['anomaly_type'].str.contains('값 불일치', na=False)
       ].copy()
       
       if self.room_number:
           csv_filename = f'report_rule1_lost_update_errors_room{self.room_number}.csv'
       else:
           csv_filename = 'report_rule1_lost_update_errors.csv'
       
       csv_path = os.path.join(self.rule1_folder, csv_filename)
       
       required_columns = [
           'roomNumber', 'bin', 'room_entry_sequence', 'user_id',
           'true_critical_section_start', 'true_critical_section_end',
           'prev_people', 'expected_people', 'curr_people', 'lost_update_diff'
       ]
       
       if lost_update_anomalies.empty:
           empty_df = pd.DataFrame(columns=required_columns)
           empty_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
       else:
           available_columns = [col for col in required_columns if col in lost_update_anomalies.columns]
           csv_df = lost_update_anomalies[available_columns].copy()
           
           for col in required_columns:
               if col not in csv_df.columns:
                   csv_df[col] = ''
           
           csv_df = csv_df[required_columns]
           
           if self.room_number is not None:
               csv_df = csv_df[csv_df['roomNumber'] == self.room_number]
           
           if not csv_df.empty:
               sort_columns = ['roomNumber', 'bin', 'room_entry_sequence']
               available_sort_cols = [col for col in sort_columns if col in csv_df.columns]
               if available_sort_cols:
                   csv_df = csv_df.sort_values(available_sort_cols)
           
           csv_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
       
       return csv_path

    def generate_rule3_csv_report(self):
       """규칙 3 정원 초과 오류 CSV 보고서 생성"""
       capacity_exceeded = self.df_preprocessor[
           self.df_preprocessor['curr_people'] > self.df_preprocessor['max_people']
       ].copy()
       
       if self.room_number:
           csv_filename = f'report_rule3_capacity_exceeded_errors_room{self.room_number}.csv'
       else:
           csv_filename = 'report_rule3_capacity_exceeded_errors.csv'
       
       csv_path = os.path.join(self.rule3_folder, csv_filename)
       
       required_columns = [
           'roomNumber', 'bin', 'room_entry_sequence', 'user_id',
           'prev_entry_time', 'curr_entry_time',
           'curr_people', 'max_people'
       ]
       
       if capacity_exceeded.empty:
           empty_df = pd.DataFrame(columns=required_columns)
           empty_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
           return csv_path
       
       available_columns = [col for col in required_columns if col in capacity_exceeded.columns]
       csv_df = capacity_exceeded[available_columns].copy()
       
       for col in required_columns:
           if col not in csv_df.columns:
               csv_df[col] = ''
       
       csv_df = csv_df[required_columns]
       
       if self.room_number is not None:
           csv_df = csv_df[csv_df['roomNumber'] == self.room_number]
       
       if 'curr_entry_time' in csv_df.columns:
           csv_df = csv_df.sort_values('curr_entry_time')
       
       csv_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
       
       return csv_path

    def generate_rule4_csv_report(self):
       """규칙 4 상태 전이 오류 CSV 보고서 생성"""
       state_transition_anomalies = self.df_result[
           self.df_result['anomaly_type'].str.contains('상태 전이 오류', na=False)
       ].copy()
       
       if self.room_number:
           csv_filename = f'report_rule4_state_transition_errors_room{self.room_number}.csv'
       else:
           csv_filename = 'report_rule4_state_transition_errors.csv'
       
       csv_path = os.path.join(self.rule4_folder, csv_filename)
       
       required_columns = [
           'roomNumber', 'bin', 'room_entry_sequence', 'sorted_sequence_position',
           'user_id', 'true_critical_section_start', 'true_critical_section_end',
           'prev_people', 'expected_curr_by_sequence', 'actual_curr_people',
           'curr_sequence_diff'
       ]
       
       if state_transition_anomalies.empty:
           empty_df = pd.DataFrame(columns=required_columns)
           empty_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
           return csv_path
       
       available_columns = [col for col in required_columns if col in state_transition_anomalies.columns]
       csv_df = state_transition_anomalies[available_columns].copy()
       
       for col in required_columns:
           if col not in csv_df.columns:
               csv_df[col] = ''
       
       csv_df = csv_df[required_columns]
       
       if self.room_number is not None:
           csv_df = csv_df[csv_df['roomNumber'] == self.room_number]
       
       if not csv_df.empty:
           sort_columns = ['roomNumber', 'bin', 'room_entry_sequence']
           available_sort_cols = [col for col in sort_columns if col in csv_df.columns]
           if available_sort_cols:
               csv_df = csv_df.sort_values(available_sort_cols)
       
       csv_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
       
       return csv_path

    def create_rule_statistics_chart(self):
       """이상 현상 규칙별 통계 차트 생성"""
       rules = ['규칙1\n값 불일치', '규칙2\n경합 발생', '규칙3\n정원 초과', '규칙4\n상태 전이 오류']
       counts = [
           self.rule_stats['lost_update'], self.rule_stats['contention'], 
           self.rule_stats['capacity_exceeded'], self.rule_stats['stale_read']
       ]
       colors = ['#e74c3c', '#f39c12', '#9b59b6', '#8b4513']
       
       fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 8))
       
       if self.room_number:
           title_suffix = f"방 {self.room_number}"
       else:
           room_count = len(self.df_preprocessor['roomNumber'].unique()) if not self.df_preprocessor.empty else 0
           title_suffix = f"전체 {room_count}개 방"
       
       fig.suptitle(f'이상 현상 규칙별 분포 ({title_suffix})', fontsize=16, fontweight='bold')
       
       bars = ax1.bar(rules, counts, color=colors, alpha=0.8, edgecolor='black', linewidth=1)
       ax1.set_title('규칙별 이상 현상 발생 횟수', fontsize=14, fontweight='bold')
       ax1.set_ylabel('발생 횟수', fontsize=12)
       ax1.grid(True, alpha=0.3, axis='y')
       
       max_count = max(counts) if counts else 1
       for bar, count in zip(bars, counts):
           height = bar.get_height()
           ax1.text(bar.get_x() + bar.get_width()/2., height + max_count*0.01,
                  f'{count}건', ha='center', va='bottom', fontsize=11, fontweight='bold')
       
       non_zero_data = [(rule, count, color) for rule, count, color in zip(rules, counts, colors) if count > 0]
       
       if non_zero_data:
           non_zero_rules, non_zero_counts, non_zero_colors = zip(*non_zero_data)
           wedges, texts, autotexts = ax2.pie(
               non_zero_counts, labels=non_zero_rules, colors=non_zero_colors,
               autopct='%1.1f%%', startangle=90, textprops={'fontsize': 10}
           )
           for autotext in autotexts:
               autotext.set_color('white')
               autotext.set_fontweight('bold')
               autotext.set_fontsize(10)
       else:
           ax2.text(0.5, 0.5, '이상 현상 없음', ha='center', va='center', 
                  transform=ax2.transAxes, fontsize=16, fontweight='bold', color='gray')
       
       ax2.set_title('규칙별 이상 현상 비율', fontsize=14, fontweight='bold')
       
       plt.tight_layout()
       chart_path = os.path.join(self.rule_stats_folder, 'rule_statistics_chart.png')
       plt.savefig(chart_path, dpi=300, bbox_inches='tight')
       plt.close()

    def create_rule2_contention_gantt_chart(self):
       """규칙 2: 경합 발생 상세 분석 - 간트 차트"""
       if self.room_number is None:
           return
       
       contention_anomalies = self.df_result[
           self.df_result['anomaly_type'].str.contains('경합 발생', na=False)
       ].copy()
       
       if contention_anomalies.empty:
           return
       
       try:
           contention_anomalies['true_critical_section_start'] = pd.to_datetime(
               contention_anomalies['true_critical_section_start']
           )
           contention_anomalies['true_critical_section_end'] = pd.to_datetime(
               contention_anomalies['true_critical_section_end']
           )
       except Exception as e:
           return
       
       fig, ax = plt.subplots(1, 1, figsize=(20, 12))
       
       title = f'규칙 2: 경합 발생 간트 차트 - 방 {self.room_number}'
       ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
       
       contention_anomalies_sorted = contention_anomalies.sort_values([
           'true_critical_section_start', 'room_entry_sequence'
       ])
       user_ids = contention_anomalies_sorted['user_id'].unique()
       y_positions = {user_id: i for i, user_id in enumerate(user_ids)}
       
       for i, (_, row) in enumerate(contention_anomalies.iterrows()):
           user_id = row['user_id']
           start_time = row['true_critical_section_start']
           end_time = row['true_critical_section_end']
           contention_size = row.get('contention_group_size', 1)
           
           y_pos = y_positions[user_id]
           
           if pd.notna(start_time) and pd.notna(end_time):
               duration_seconds = (end_time - start_time).total_seconds()
               
               if duration_seconds <= 0:
                   duration_seconds = 0.001
               
               ax.barh(y_pos, duration_seconds, left=start_time, height=0.6, 
                      alpha=0.7, color='red', edgecolor='black', linewidth=0.5)
               
               actual_end_time = start_time + pd.Timedelta(seconds=duration_seconds)
               ax.text(actual_end_time, y_pos, f' {contention_size}', 
                      va='center', ha='left', fontsize=9, fontweight='bold')
       
       ax.set_yticks(range(len(user_ids)))
       ax.set_yticklabels(user_ids, fontsize=10)
       ax.set_ylabel('사용자 ID (user_id)', fontsize=12, fontweight='bold')
       ax.set_xlabel('시간 (Timestamp)', fontsize=12, fontweight='bold')
       ax.grid(True, alpha=0.3)
       
       ax.tick_params(axis='x', rotation=45)
       
       plt.tight_layout()
       
       chart_filename = f'contention_gantt_chart_room{self.room_number}.png'
       chart_path = os.path.join(self.rule2_folder, chart_filename)
       plt.savefig(chart_path, dpi=300, bbox_inches='tight')
       plt.close()

    def generate_rule2_csv_report(self):
       """규칙 2 경합 발생 CSV 보고서 생성"""
       if self.room_number is None:
           return None
       
       contention_anomalies = self.df_result[
           self.df_result['anomaly_type'].str.contains('경합 발생', na=False)
       ].copy()
       
       csv_filename = f'report_rule2_contention_details_room{self.room_number}.csv'
       csv_path = os.path.join(self.rule2_folder, csv_filename)
       
       required_columns = [
           'roomNumber', 'bin', 'user_id', 'contention_group_size',
           'contention_user_ids', 'true_critical_section_start',
           'true_critical_section_end', 'true_critical_section_duration'
       ]
       
       if contention_anomalies.empty:
           empty_df = pd.DataFrame(columns=required_columns)
           empty_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
           return csv_path
       
       available_columns = [col for col in required_columns[:-1] if col in contention_anomalies.columns]
       csv_df = contention_anomalies[available_columns].copy()
       
       if ('true_critical_section_start' in csv_df.columns and 
           'true_critical_section_end' in csv_df.columns):
           try:
               csv_df['true_critical_section_start'] = pd.to_datetime(csv_df['true_critical_section_start'])
               csv_df['true_critical_section_end'] = pd.to_datetime(csv_df['true_critical_section_end'])
               csv_df['true_critical_section_duration'] = (
                   csv_df['true_critical_section_end'] - csv_df['true_critical_section_start']
               ).dt.total_seconds()
           except Exception as e:
               csv_df['true_critical_section_duration'] = ''
       else:
           csv_df['true_critical_section_duration'] = ''
       
       for col in required_columns:
           if col not in csv_df.columns:
               csv_df[col] = ''
       
       csv_df = csv_df[required_columns]
       
       csv_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
       
       return csv_path

    def run_analysis(self):
       """전체 분석 실행"""
       print("🚀 Race Condition 분석 시작")
       
       if not self.load_data():
           print("❌ 데이터 로딩 실패")
           return False
       
       self.create_output_folders()
       self.calculate_rule_statistics()
       
       try:
           self.create_rule_statistics_chart()
           self.create_rule1_lost_update_chart()
           self.generate_rule1_csv_report()
           self.create_rule2_contention_gantt_chart()
           self.generate_rule2_csv_report()
           self.create_rule3_capacity_exceeded_chart()
           self.generate_rule3_csv_report()
           self.create_rule4_state_transition_chart()
           self.generate_rule4_csv_report()
           
       except Exception as e:
           print(f"❌ 차트 생성 중 오류 발생: {e}")
           import traceback
           traceback.print_exc()
           return False
       
       print("✅ 분석 완료!")
       return True

def main():
   """메인 함수"""
   import argparse
   
   parser = argparse.ArgumentParser(
       description='Race Condition 분석 및 시각화',
       formatter_class=argparse.RawDescriptionHelpFormatter
   )
   
   parser.add_argument(
       '--room_number', 
       type=int, 
       help='분석할 특정 방 번호 (생략시 전체 방 종합 분석)'
   )
   
   args = parser.parse_args()
   analyzer = RaceConditionAnalyzer(room_number=args.room_number)
   success = analyzer.run_analysis()
   
   if not success:
       exit(1)

if __name__ == "__main__":
   main()
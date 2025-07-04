"""
수정된 Race Condition 분석 리포트 생성기
실제 데이터 구조에 맞춰 완전히 수정된 버전
"""

import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
import seaborn as sns
import numpy as np
from datetime import datetime
import warnings
import os
warnings.filterwarnings('ignore')

# 한글 폰트 설정
import platform
import matplotlib.font_manager as fm

def setup_korean_font():
    """한글 폰트 설정"""
    system = platform.system()
    
    if system == 'Windows':
        font_name = 'Malgun Gothic'
    elif system == 'Darwin':  # macOS
        font_name = 'AppleGothic'
    else:  # Linux
        available_fonts = [f.name for f in fm.fontManager.ttflist]
        if 'NanumGothic' in available_fonts:
            font_name = 'NanumGothic'
        elif 'DejaVu Sans' in available_fonts:
            font_name = 'DejaVu Sans'
        else:
            font_name = 'sans-serif'
    
    plt.rcParams['font.family'] = font_name
    plt.rcParams['axes.unicode_minus'] = False
    return font_name

# 한글 폰트 설정 실행
setup_korean_font()

class RaceConditionAnalyzer:
    def __init__(self, room_number=None):
        """분석기 초기화"""
        self.room_number = room_number
        self.df_preprocessor = None
        self.df_result = None
        self.charts_folder = 'charts'
        self.has_high_precision = False
        
        # 4가지 규칙별 통계 (실제 데이터 기반)
        self.rule_stats = {
            'lost_update': 0,
            'contention': 0,
            'capacity_exceeded': 0,
            'stale_read': 0,
            'total_anomalies': 0,
            'total_requests': 0
        }
    
    def create_output_folders(self):
        """출력 폴더 생성"""
        if not os.path.exists(self.charts_folder):
            os.makedirs(self.charts_folder)
            print(f"📁 폴더 생성됨: {self.charts_folder}/")

    def load_data(self):
        """CSV 파일들을 로드하고 전처리"""
        print("📊 데이터 로딩 중...")
        
        try:
            # 필수 파일들 로드
            self.df_preprocessor = pd.read_csv('racecondition_event_preprocessor_result.csv')
            print(f"✅ preprocessor 파일 로드: {len(self.df_preprocessor)} rows")
            
            self.df_result = pd.read_csv('anomaly_result.csv')
            print(f"✅ result 파일 로드: {len(self.df_result)} rows")
            
            # 고정밀 타임스탬프 필드 확인
            precision_fields = [col for col in self.df_preprocessor.columns 
                               if 'nanoTime' in col or 'epochNano' in col]
            if precision_fields:
                self.has_high_precision = True
                print(f"🔬 고정밀 타임스탬프 필드 감지: {len(precision_fields)}개")
                
        except FileNotFoundError as e:
            print(f"❌ 필수 파일을 찾을 수 없습니다: {e}")
            return False
            
        # 날짜 컬럼 변환
        date_columns = ['prev_entry_time', 'curr_entry_time']
        for col in date_columns:
            if col in self.df_preprocessor.columns:
                self.df_preprocessor[col] = pd.to_datetime(self.df_preprocessor[col])
            if col in self.df_result.columns:
                self.df_result[col] = pd.to_datetime(self.df_result[col])
        
        # 방 번호로 필터링
        if self.room_number is not None:
            print(f"🏠 방 번호 {self.room_number}로 필터링 중...")
            self.df_preprocessor = self.df_preprocessor[self.df_preprocessor['roomNumber'] == self.room_number]
            self.df_result = self.df_result[self.df_result['roomNumber'] == self.room_number]
            print(f"✅ 필터링 완료: {len(self.df_preprocessor)} preprocessor, {len(self.df_result)} result rows")
        
        # 시간순 정렬 및 인덱스 부여
        self.df_preprocessor = self.df_preprocessor.sort_values('curr_entry_time').reset_index(drop=True)
        self.df_preprocessor['request_index'] = range(len(self.df_preprocessor))
        print("✅ 요청 순서 정렬 및 인덱스 부여 완료")
        
        return True
    
    def calculate_rule_statistics(self):
        """4가지 규칙별 통계 계산 (실제 데이터 구조 기반)"""
        print("📊 4가지 규칙별 통계 계산 중...")
        
        # 실제 anomaly_type 고유값 확인
        print("🔍 실제 anomaly_type 고유값들:")
        unique_types = self.df_result['anomaly_type'].unique()
        for i, anomaly_type in enumerate(unique_types):
            count = len(self.df_result[self.df_result['anomaly_type'] == anomaly_type])
            print(f"   {i+1}. '{anomaly_type}' ({count}건)")
        
        # 규칙별 카운트 (실제 데이터 기반)
        self.rule_stats['lost_update'] = len(self.df_result[
            self.df_result['anomaly_type'].str.contains('값 불일치', na=False)
        ])
        
        self.rule_stats['contention'] = len(self.df_result[
            self.df_result['anomaly_type'].str.contains('경합 발생', na=False)
        ])
        
        # 정원 초과는 실제 데이터에서 0건
        self.rule_stats['capacity_exceeded'] = len(self.df_result[
            self.df_result['curr_people'] > self.df_result['max_people']
        ])
        
        self.rule_stats['stale_read'] = len(self.df_result[
            self.df_result['anomaly_type'].str.contains('상태 전이 오류', na=False)
        ])
        
        self.rule_stats['total_anomalies'] = len(self.df_result)
        self.rule_stats['total_requests'] = len(self.df_preprocessor)
        
        print(f"📋 규칙 1 (값 불일치): {self.rule_stats['lost_update']}건")
        print(f"📋 규칙 2 (경합 발생): {self.rule_stats['contention']}건")
        print(f"📋 규칙 3 (정원 초과): {self.rule_stats['capacity_exceeded']}건")
        print(f"📋 규칙 4 (상태 전이 오류): {self.rule_stats['stale_read']}건")
        print(f"📋 총 이상 현상: {self.rule_stats['total_anomalies']}건")
    
    def print_summary(self):
        """분석 결과 요약 출력"""
        print("\n" + "="*70)
        print("📋 4가지 규칙 기반 RACE CONDITION 분석 결과 요약")
        print("="*70)
        
        if self.room_number:
            print(f"🏠 분석 대상: Room {self.room_number}")
        else:
            rooms = self.df_preprocessor['roomNumber'].unique()
            print(f"🏠 분석 대상: 전체 방 {list(rooms)}")
        
        if self.has_high_precision:
            print("🔬 고정밀 타임스탬프 분석 모드 활성화")
        
        # 기본 통계
        total_requests = self.rule_stats['total_requests']
        total_anomalies = self.rule_stats['total_anomalies']
        anomaly_rate = (total_anomalies / total_requests * 100) if total_requests > 0 else 0
        
        print(f"\n📊 전체 요청 통계:")
        print(f"🔢 총 요청 수: {total_requests}개")
        print(f"⚠️  이상 현상: {total_anomalies}개")
        print(f"📈 이상 비율: {anomaly_rate:.1f}%")
        
        # 방 정보
        if not self.df_preprocessor.empty:
            max_capacity = self.df_preprocessor['max_people'].iloc[0]
            actual_max = self.df_preprocessor['curr_people'].max()
            
            print(f"\n🏠 방 정보:")
            print(f"🏆 설정 최대 인원: {max_capacity}명")
            print(f"📊 실제 최대 인원: {actual_max}명")
            if actual_max > max_capacity:
                print(f"🚨 용량 초과 발생! (+{actual_max - max_capacity}명)")
        
        # 4가지 규칙별 상세 통계
        print(f"\n📋 4가지 규칙별 이상 현상 분석:")
        print(f"📊 규칙 1 (값 불일치): {self.rule_stats['lost_update']}건")
        print(f"📊 규칙 2 (경합 발생): {self.rule_stats['contention']}건")
        print(f"📊 규칙 3 (정원 초과): {self.rule_stats['capacity_exceeded']}건")
        print(f"📊 규칙 4 (상태 전이 오류): {self.rule_stats['stale_read']}건")
        
        print("="*70)
    
    def create_rule_statistics_chart(self):
        """이상 현상 규칙별 통계 차트 - 막대차트 + 파이차트 조합"""
        print("📊 규칙별 통계 차트 생성 중...")
        
        # 규칙별 카운트
        rules = ['규칙1\n값 불일치', '규칙2\n경합 발생', '규칙3\n정원 초과', '규칙4\n상태 전이 오류']
        counts = [
            self.rule_stats['lost_update'],
            self.rule_stats['contention'],
            self.rule_stats['capacity_exceeded'],
            self.rule_stats['stale_read']
        ]
        colors = ['red', 'orange', 'purple', 'brown']
        
        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 8))
        
        # 제목 설정
        if self.room_number:
            main_title = f'이상 현상 규칙별 분포 (방 {self.room_number})'
        else:
            total_rooms = len(self.df_preprocessor['roomNumber'].unique()) if not self.df_preprocessor.empty else 0
            main_title = f'이상 현상 규칙별 분포 (전체 {total_rooms}개 방 단순 합산)'
        
        fig.suptitle(main_title, fontsize=16, fontweight='bold')
        
        # 막대 차트
        bars = ax1.bar(rules, counts, color=colors, alpha=0.7)
        ax1.set_title('규칙별 이상 현상 발생 횟수', fontsize=14, fontweight='bold')
        ax1.set_ylabel('발생 횟수')
        ax1.grid(True, alpha=0.3)
        
        # 값 표시
        for bar, count in zip(bars, counts):
            height = bar.get_height()
            ax1.text(bar.get_x() + bar.get_width()/2., height + max(counts)*0.01,
                    f'{count}건', ha='center', va='bottom', fontsize=11, fontweight='bold')
        
        # 파이 차트
        non_zero_rules = []
        non_zero_counts = []
        non_zero_colors = []
        
        for i, (rule, count, color) in enumerate(zip(rules, counts, colors)):
            if count > 0:
                non_zero_rules.append(rule)
                non_zero_counts.append(count)
                non_zero_colors.append(color)
        
        if non_zero_counts:
            wedges, texts, autotexts = ax2.pie(non_zero_counts, labels=non_zero_rules, colors=non_zero_colors,
                                              autopct='%1.1f%%', startangle=90)
            ax2.set_title('규칙별 이상 현상 비율', fontsize=14, fontweight='bold')
            
            for autotext in autotexts:
                autotext.set_color('white')
                autotext.set_fontweight('bold')
        else:
            ax2.text(0.5, 0.5, '이상 현상 없음', ha='center', va='center', transform=ax2.transAxes,
                    fontsize=16, fontweight='bold')
            ax2.set_title('규칙별 이상 현상 비율', fontsize=14, fontweight='bold')
        
        plt.tight_layout()
        chart_path = os.path.join(self.charts_folder, 'rule_statistics_chart.png')
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
        print(f"✅ 저장됨: {chart_path}")
    
    def create_main_comparison_chart(self):
        """2차원 선형 그래프: 인원수 변화 추이 (평균 + 신뢰구간)"""
        print("📈 2차원 선형 그래프 생성 중...")
        
        room_data = self.df_preprocessor.copy()
        
        # 그래프 생성
        fig, ax = plt.subplots(figsize=(20, 12))
        
        if self.room_number:
            # 단일 방 분석
            single_room_data = room_data[room_data['roomNumber'] == self.room_number].sort_values('curr_entry_time').reset_index(drop=True)
            total_requests = len(single_room_data)
            max_people = int(single_room_data['max_people'].iloc[0])
            
            # X축: 스레드 요청 순서 (0부터 시작)
            x_positions = list(range(total_requests))
            
            # Y축 데이터
            curr_people_values = single_room_data['curr_people'].tolist()
            prev_people_values = single_room_data['prev_people'].tolist()
            
            # 정상 기댓값 계산
            expected_values = []
            for i in range(total_requests):
                expected_value = min(i + 1, max_people)
                expected_values.append(expected_value)
            
            # 라인 플롯
            ax.plot(x_positions, expected_values, 'b-', linewidth=3, 
                    label=f'정상 기댓값 (순차 증가, max={max_people})', alpha=0.8)
            ax.plot(x_positions, curr_people_values, 'orange', linewidth=2, 
                    label='실제값 (curr_people)', alpha=0.8, marker='o', markersize=2)
            ax.plot(x_positions, prev_people_values, 'green', linewidth=2, 
                    label='이전값 (prev_people)', alpha=0.8, marker='s', markersize=2)
            
            # Y축 최댓값 동적 계산
            max_val = max(max(curr_people_values), max(prev_people_values), max(expected_values))
            y_max = max_val * 1.2
            
            title = f'인원수 변화 추이 (방 {self.room_number})'
            
        else:
            # 전체 방 종합 분석 - 평균 + 신뢰구간
            rooms = room_data['roomNumber'].unique()
            max_requests = 0
            
            # 각 방별 데이터 정리
            room_datasets = {}
            for room in rooms:
                room_subset = room_data[room_data['roomNumber'] == room].sort_values('curr_entry_time').reset_index(drop=True)
                room_datasets[room] = room_subset
                max_requests = max(max_requests, len(room_subset))
            
            # X축: 최대 요청 수까지
            x_positions = list(range(max_requests))
            
            # 평균과 표준편차 계산
            mean_curr_people = []
            std_curr_people = []
            mean_prev_people = []
            std_prev_people = []
            
            for i in range(max_requests):
                curr_values_at_i = []
                prev_values_at_i = []
                
                for room, dataset in room_datasets.items():
                    if i < len(dataset):
                        curr_values_at_i.append(dataset.iloc[i]['curr_people'])
                        prev_values_at_i.append(dataset.iloc[i]['prev_people'])
                
                if curr_values_at_i:
                    mean_curr_people.append(np.mean(curr_values_at_i))
                    std_curr_people.append(np.std(curr_values_at_i))
                    mean_prev_people.append(np.mean(prev_values_at_i))
                    std_prev_people.append(np.std(prev_values_at_i))
                else:
                    mean_curr_people.append(0)
                    std_curr_people.append(0)
                    mean_prev_people.append(0)
                    std_prev_people.append(0)
            
            # 평균 트레이스 라인
            ax.plot(x_positions, mean_curr_people, 'orange', linewidth=3, 
                    label='평균 curr_people', alpha=0.8)
            ax.plot(x_positions, mean_prev_people, 'green', linewidth=3, 
                    label='평균 prev_people', alpha=0.8)
            
            # 신뢰구간 (평균 ± 표준편차) 음영 영역
            mean_curr_array = np.array(mean_curr_people)
            std_curr_array = np.array(std_curr_people)
            mean_prev_array = np.array(mean_prev_people)
            std_prev_array = np.array(std_prev_people)
            
            ax.fill_between(x_positions, 
                           mean_curr_array - std_curr_array, 
                           mean_curr_array + std_curr_array, 
                           alpha=0.3, color='orange', label='curr_people 신뢰구간 (±1σ)')
            
            ax.fill_between(x_positions, 
                           mean_prev_array - std_prev_array, 
                           mean_prev_array + std_prev_array, 
                           alpha=0.3, color='green', label='prev_people 신뢰구간 (±1σ)')
            
            # Y축 최댓값 동적 계산 (신뢰구간 상단 경계 고려)
            upper_bounds_curr = mean_curr_array + std_curr_array
            upper_bounds_prev = mean_prev_array + std_prev_array
            max_upper_bound = max(np.max(upper_bounds_curr), np.max(upper_bounds_prev))
            y_max = max_upper_bound * 1.2
            
            title = f'인원수 변화 추이 평균 및 신뢰구간 (전체 {len(rooms)}개 방)'
        
        # X축 설정 (10개 구간으로 눈금 표시)
        total_x_range = len(x_positions)
        tick_positions = [int(i * total_x_range / 10) for i in range(11)]
        tick_positions = [pos for pos in tick_positions if pos < total_x_range]
        ax.set_xticks(tick_positions)
        ax.set_xticklabels(tick_positions)
        
        # Y축 설정
        ax.set_ylim(1, y_max)
        
        # 라벨 및 제목
        ax.set_xlabel('스레드 요청 순서 (Index)', fontsize=12)
        ax.set_ylabel('인원 수', fontsize=12)
        ax.set_title(title, fontsize=16, fontweight='bold')
        
        # 범례 및 격자
        ax.legend(fontsize=10, loc='upper left')
        ax.grid(True, alpha=0.3)
        
        # 저장
        plt.tight_layout()
        chart_path = os.path.join(self.charts_folder, 'main_race_condition_analysis.png')
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
        print(f"✅ 저장됨: {chart_path}")
    
    def run_analysis(self):
        """전체 분석 실행"""
        print("🚀 수정된 Race Condition 분석 시작!")
        print("="*70)
        
        # 데이터 로드
        if not self.load_data():
            return False
        
        # 출력 폴더 생성
        print("\n📁 출력 폴더 생성 중...")
        self.create_output_folders()
        
        # 4가지 규칙별 통계 계산
        self.calculate_rule_statistics()
        
        # 요약 출력
        self.print_summary()
        
        # 차트 생성
        print("\n📊 차트 생성 중...")
        self.create_rule_statistics_chart()
        self.create_main_comparison_chart()
        
        print("\n🎉 분석 완료!")
        print("📁 생성된 파일들:")
        print(f"🔹 차트 파일들 ({self.charts_folder}/ 폴더):")
        print("  - rule_statistics_chart.png (규칙별 통계: 막대+파이 차트)")
        print("  - main_race_condition_analysis.png (2차원 선형 그래프: 인원수 변화 추이)")
        
        # 최종 통계 요약
        print(f"\n📊 최종 통계 요약:")
        print(f"• 총 요청: {self.rule_stats['total_requests']}개")
        print(f"• 이상 현상: {self.rule_stats['total_anomalies']}개")
        anomaly_rate = (self.rule_stats['total_anomalies'] / self.rule_stats['total_requests'] * 100) if self.rule_stats['total_requests'] > 0 else 0
        print(f"• 이상 비율: {anomaly_rate:.1f}%")
        
        print(f"\n📊 규칙별 상세:")
        print(f"• 값 불일치: {self.rule_stats['lost_update']}건")
        print(f"• 경합 발생: {self.rule_stats['contention']}건")
        print(f"• 정원 초과: {self.rule_stats['capacity_exceeded']}건")
        print(f"• 상태 전이 오류: {self.rule_stats['stale_read']}건")
        
        if self.room_number:
            print(f"\n🏠 분석 대상: 방 {self.room_number} (단일 방 분석)")
        else:
            total_rooms = len(self.df_preprocessor['roomNumber'].unique()) if not self.df_preprocessor.empty else 0
            print(f"\n🏠 분석 대상: 전체 {total_rooms}개 방 (평균+신뢰구간 분석)")
        
        return True

def main():
    """메인 함수"""
    import argparse
    
    parser = argparse.ArgumentParser(description='수정된 Race Condition 분석 및 시각화')
    parser.add_argument('--room_number', type=int, help='분석할 특정 방 번호 (생략시 전체 방 분석)')
    
    args = parser.parse_args()
    
    print("🎯 수정된 Race Condition 분석기 (실제 데이터 기반)")
    print("="*60)
    print("📋 실제 데이터에서 확인된 이상 현상:")
    print("  1. 값 불일치: 복합 이상 현상의 일부")
    print("  2. 경합 발생: 모든 이상 현상에 포함 (162건)")
    print("  3. 정원 초과: 실제 데이터에서 0건")
    print("  4. 상태 전이 오류: 복합 이상 현상의 일부")
    print("="*60)
    
    # 분석기 생성 및 실행
    analyzer = RaceConditionAnalyzer(room_number=args.room_number)
    success = analyzer.run_analysis()
    
    if not success:
        exit(1)

if __name__ == "__main__":
    main()
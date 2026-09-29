"""
세마포어 전용 순차적 일관성 검증 분석기 (최종 수정버전)
요구사항 명세서에 따라 규칙 1+4를 단일 '순차적 일관성 검증'으로 통합
이상적 순차 상태 vs 실제 기록값 비교를 통한 세마포어 한계 증명
최대 정원 제한 및 마지막 유효값 고정 로직 완전 적용
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
    if system == 'Windows':
        # 여러 폰트를 순서대로 시도
        font_candidates = ['NanumGothic', 'Malgun Gothic', 'Dotum', 'Gulim', 'Batang']
        available_fonts = [f.name for f in fm.fontManager.ttflist]
        font_name = 'DejaVu Sans'  # 기본값
        for font in font_candidates:
            if font in available_fonts:
                font_name = font
                break
    elif system == 'Darwin':
        font_name = 'AppleGothic'
    else:
        available_fonts = [f.name for f in fm.fontManager.ttflist]
        font_name = next((f for f in ['NanumGothic', 'DejaVu Sans'] if f in available_fonts), 'sans-serif')
    
    plt.rcParams['font.family'] = font_name
    plt.rcParams['axes.unicode_minus'] = False

# 한글 폰트 설정 실행
setup_korean_font()

class SemaphoreSequentialConsistencyAnalyzer:
    def __init__(self, room_number=None, preprocessor_file=None, output_dir=None):
        """
        세마포어 순차적 일관성 검증 분석기 초기화 (최종 수정버전)
        
        Args:
            room_number (int, optional): 분석할 특정 방 번호
            preprocessor_file (str): 전처리 데이터 파일 경로 (preprocessor_semaphore.csv)
            output_dir (str): 출력 디렉토리 경로
        """
        self.room_number = room_number
        self.preprocessor_file = preprocessor_file
        self.output_dir = output_dir
        
        # 데이터 저장용 변수
        self.df_preprocessor = None
        
        # 순차적 일관성 분석 결과 (단일 기준)
        self.total_requests = 0
        self.sequential_consistency_violations = 0
        self.initial_people_count = 1  # 방의 초기 인원 (가정)
    
    def load_data(self):
        """CSV 파일을 로드하고 세마포어 데이터 검증"""
        try:
            self.df_preprocessor = pd.read_csv(self.preprocessor_file)
            print(f"✅ 세마포어 전처리 파일 로드 완료: {len(self.df_preprocessor)}건")
            print(f"   컬럼: {list(self.df_preprocessor.columns)}")
            
            # 세마포어 필수 컬럼 검증
            required_columns = [
                'roomNumber', 'bin', 'user_id', 'curr_people', 
                'max_people', 'room_entry_sequence', 'join_result'
            ]
            
            missing_columns = [col for col in required_columns if col not in self.df_preprocessor.columns]
            if missing_columns:
                raise ValueError(f"필수 컬럼 누락: {missing_columns}")
            
            print("✅ 세마포어 데이터 구조 검증 완료")
            
        except FileNotFoundError as e:
            print(f"❌ 파일을 찾을 수 없습니다: {e}")
            return False
        except Exception as e:
            print(f"❌ 데이터 로딩 오류: {e}")
            return False
        
        # 방 번호로 필터링 (지정된 경우만)
        if self.room_number is not None:
            before_filter = len(self.df_preprocessor)
            self.df_preprocessor = self.df_preprocessor[self.df_preprocessor['roomNumber'] == self.room_number]
            print(f"✅ 데이터 방 {self.room_number} 필터링: {before_filter} → {len(self.df_preprocessor)}건")
        
        # 순차적 일관성 통계 계산
        self._calculate_sequential_consistency_statistics()
        
        return True
    
    def _calculate_sequential_consistency_statistics(self):
        """순차적 일관성 통계 계산 (단일 기준)"""
        self.total_requests = len(self.df_preprocessor)
        
        # 순차적 일관성 위반 카운트
        violations = 0
        
        for _, row in self.df_preprocessor.iterrows():
            # 이상적인 순차 상태 계산 (최대 정원 제한 적용)
            ideal_value = self.initial_people_count + row['room_entry_sequence']
            max_people = row['max_people']
            ideal_sequential_state = min(ideal_value, max_people)
            
            actual_people = row['curr_people']
            
            # 순차적 일관성 검증 (단일 기준)
            if actual_people != ideal_sequential_state:
                violations += 1
        
        self.sequential_consistency_violations = violations
        
        print(f"📊 세마포어 순차적 일관성 통계:")
        print(f"   총 permit 요청: {self.total_requests}건")
        print(f"   순차적 일관성 위반: {self.sequential_consistency_violations}건")
        
        if self.total_requests > 0:
            violation_rate = (self.sequential_consistency_violations / self.total_requests) * 100
            print(f"   위반률: {violation_rate:.1f}%")
    
    def create_output_folders(self):
        """출력 폴더 생성"""
        if not os.path.exists(self.output_dir):
            os.makedirs(self.output_dir)
            print(f"✅ 출력 디렉토리 생성: {self.output_dir}")
        else:
            print(f"✅ 출력 디렉토리 확인: {self.output_dir}")
    
    def create_sequential_consistency_chart(self):
        """순차적 일관성 비교 차트 생성 (2개 라인)"""
        if self.room_number is not None:
            self._create_single_room_chart()
        else:
            self._create_multi_room_chart()
    
    def _create_single_room_chart(self):
        """단일 방 순차적 일관성 비교 차트 (2개 라인)"""
        room_data = self.df_preprocessor.copy()
        if room_data.empty:
            print("❌ 단일 방 데이터가 비어있어 차트 생성을 건너뜁니다.")
            return
        
        print(f"📊 방 {self.room_number} 순차적 일관성 비교 차트 생성 시작")
        
        # room_entry_sequence 순서로 정렬
        room_data = room_data.sort_values('room_entry_sequence').reset_index(drop=True)
        
        # X축: room_entry_sequence (시간의 흐름)
        x_positions = room_data['room_entry_sequence'].tolist()
        
        # Y축 데이터 계산 (2개 라인만)
        actual_values = room_data['curr_people'].tolist()  # 실제 기록된 인원수 (주황색 실선)
        
        # 이상적인 기대 인원수 계산 (최대 정원 제한 적용)
        ideal_sequential_state = []
        for i, seq in enumerate(x_positions):
            ideal_value = self.initial_people_count + seq
            max_people = room_data.iloc[i]['max_people']
            
            # 최대 정원을 넘지 않도록 제한
            capped_ideal = min(ideal_value, max_people)
            ideal_sequential_state.append(capped_ideal)
        
        # 차트 생성
        fig, ax = plt.subplots(figsize=(20, 12))
        
        # 제목 설정
        title = f'세마포어 순차적 일관성 검증 - 방 {self.room_number}'
        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        
        # 1. 이상적인 기대 인원수 (파란색 점선) - 요구사항대로
        ax.plot(x_positions, ideal_sequential_state, color='blue', linewidth=2, linestyle='--',
                marker='s', markersize=3, markerfacecolor='blue', markeredgecolor='blue',
                label='이상적인 기대 인원수 (순차 처리시)', alpha=0.8)
        
        # 2. 실제 기록된 인원수 (주황색 실선) - 요구사항대로
        ax.plot(x_positions, actual_values, color='orange', linewidth=3, 
                marker='o', markersize=4, markerfacecolor='orange', markeredgecolor='orange',
                label='실제 기록된 인원수 (curr_people)', alpha=0.9)
        
        # 3. 불일치 지점 강조
        for i, (actual, ideal) in enumerate(zip(actual_values, ideal_sequential_state)):
            if actual != ideal:
                seq = x_positions[i]
                # 불일치 구간 강조
                ax.axvspan(seq-0.3, seq+0.3, ymin=0, ymax=1, color='red', alpha=0.2)
        
        # y 좌표를 0.9에서 0.85로 조정 (약간 아래로)
        ax.legend(fontsize=12, loc='upper left', bbox_to_anchor=(0.02, 0.88), framealpha=0.9)
        
        # 통계 정보 박스
        stats_text = (f'총 permit 요청: {len(room_data)}건\n'
                     f'순차적 일관성 위반: {self.sequential_consistency_violations}건')
        
        if self.sequential_consistency_violations == 0:
            stats_text += '\n\nO 완벽한 순차적 일관성 유지'
            stats_text += '\n목표: 세마포어가 순차 처리를 완벽 보장!'
        else:
            violation_rate = self.sequential_consistency_violations / len(room_data) * 100
            stats_text += f'\n위반률: {violation_rate:.1f}%'
        
        ax.text(0.02, 0.92, stats_text, transform=ax.transAxes, fontsize=11,
                verticalalignment='top', 
                bbox=dict(boxstyle='round', facecolor='lightcyan', alpha=0.9))
        
        # 축 설정
        ax.set_xlabel('room_entry_sequence (시간의 흐름)', fontsize=12, fontweight='bold')
        ax.set_ylabel('people_count (인원수)', fontsize=12, fontweight='bold')
        ax.grid(True, alpha=0.3, linestyle='--')
        
        # Y축 범위 설정
        all_values = actual_values + ideal_sequential_state
        y_max = max(all_values) * 1.2 if all_values else 10
        ax.set_ylim(0, y_max)
        
        plt.tight_layout()
        
        # 파일 저장
        filename = f'semaphore_sequential_consistency_room{self.room_number}.png'
        chart_path = os.path.join(self.output_dir, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
        
        print(f"✅ 단일 방 순차적 일관성 차트 저장: {chart_path}")
    
    def _create_multi_room_chart(self):
        """전체 방 순차적 일관성 종합 차트 (최대 정원 제한 및 마지막 유효값 고정 적용)"""
        rooms = self.df_preprocessor['roomNumber'].unique()
        print(f"📊 전체 {len(rooms)}개 방 순차적 일관성 종합 차트 생성 시작")
        
        # 방별 데이터 정리
        room_datasets = {}
        max_sequence = 0
        room_max_people = {}  # 방별 최대 정원 저장
        
        for room in rooms:
            room_subset = self.df_preprocessor[
                self.df_preprocessor['roomNumber'] == room
            ].sort_values('room_entry_sequence').reset_index(drop=True)
            room_datasets[room] = room_subset
            if not room_subset.empty:
                max_sequence = max(max_sequence, room_subset['room_entry_sequence'].max())
                room_max_people[room] = room_subset.iloc[0]['max_people']  # 방별 최대 정원 저장
        
        # 공통 X축 범위 생성
        x_positions = list(range(1, max_sequence + 1))
        
        # 평균값 계산 (최대 정원 제한 및 마지막 유효값 고정)
        mean_actual, std_actual = [], []
        mean_ideal = []
        last_valid_max_people = None  # 마지막 유효한 최대 정원 저장
        
        for seq in x_positions:
            actual_at_seq = []
            
            for room, dataset in room_datasets.items():
                matching_rows = dataset[dataset['room_entry_sequence'] == seq]
                if not matching_rows.empty:
                    actual_at_seq.append(matching_rows.iloc[0]['curr_people'])
            
            if actual_at_seq:
                mean_actual.append(np.mean(actual_at_seq))
                std_actual.append(np.std(actual_at_seq))
                
                # 이상적 값도 최대 정원 제한 적용
                # 각 방의 최대 정원을 고려한 평균 계산
                ideal_values_at_seq = []
                for room, dataset in room_datasets.items():
                    matching_rows = dataset[dataset['room_entry_sequence'] == seq]
                    if not matching_rows.empty:
                        ideal_value = self.initial_people_count + seq
                        max_people = room_max_people[room]  # 저장된 방별 최대 정원 사용
                        capped_ideal = min(ideal_value, max_people)
                        ideal_values_at_seq.append(capped_ideal)
                
                calculated_mean_ideal = np.mean(ideal_values_at_seq) if ideal_values_at_seq else self.initial_people_count + seq
                mean_ideal.append(calculated_mean_ideal)
                
                # 유효한 최대 정원값 업데이트 (실제 데이터가 있는 경우)
                if ideal_values_at_seq:
                    last_valid_max_people = max(ideal_values_at_seq)  # 해당 sequence에서의 최대값 저장
                
            else:
                mean_actual.append(0)
                std_actual.append(0)
                
                # 데이터가 없는 구간: 마지막 유효한 최대 정원값으로 고정
                if last_valid_max_people is not None:
                    mean_ideal.append(last_valid_max_people)  # 수평선 유지
                else:
                    # 첫 구간에서 데이터가 없는 경우: 활성 방들의 평균 최대 정원 사용
                    if room_max_people:
                        avg_max_people = np.mean(list(room_max_people.values()))
                        ideal_value = self.initial_people_count + seq
                        calculated_ideal = min(ideal_value, avg_max_people)
                        mean_ideal.append(calculated_ideal)
                        last_valid_max_people = avg_max_people  # 초기값 설정
                    else:
                        mean_ideal.append(self.initial_people_count + seq)
        
        # 차트 생성
        fig, ax = plt.subplots(figsize=(20, 12))
        
        # ✅ 제목 설정 수정 - 한글 폰트 문제 해결
        title = f"세마포어 순차적 일관성 검증 - 전체 {len(rooms)}개 방 종합"
        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        
        # numpy 배열로 변환
        mean_actual_array = np.array(mean_actual)
        std_actual_array = np.array(std_actual)
        mean_ideal_array = np.array(mean_ideal)
        
        # 1. 이상적인 기대 인원수 (파란색 점선) - 최대 정원에서 수평선 유지
        ax.plot(x_positions, mean_ideal_array, color='blue', linewidth=2, linestyle='--',
                marker='s', markersize=3, markerfacecolor='blue', markeredgecolor='blue',
                label='평균 이상적인 기대 인원수 (최대 정원 제한)', alpha=0.8)
        
        # 2. 실제값 신뢰구간
        ax.fill_between(x_positions, 
                    mean_actual_array - std_actual_array, 
                    mean_actual_array + std_actual_array, 
                    alpha=0.3, color='orange', label='실제값 표준편차 범위 (±1σ)')
        
        # 3. 평균 실제 기록 인원수 (주황색 실선)
        ax.plot(x_positions, mean_actual_array, color='orange', linewidth=3,
                marker='o', markersize=4, markerfacecolor='orange', markeredgecolor='orange',
                label='평균 실제 기록된 인원수', alpha=0.9)
        
        # 범례 설정
        ax.legend(fontsize=12, loc='upper left', framealpha=0.9)
        
        # 통계 정보 박스
        stats_text = (f'분석 방 수: {len(rooms)}개\n'
                    f'총 permit 요청: {self.total_requests}건\n'
                    f'순차적 일관성 위반: {self.sequential_consistency_violations}건')
        
        if self.sequential_consistency_violations == 0:
            stats_text += '\n\nO 전체 방 순차적 일관성 유지'
        else:
            overall_rate = self.sequential_consistency_violations / self.total_requests * 100
            stats_text += f'\n전체 위반률: {overall_rate:.1f}%'
            
        # 최대 정원 정보 추가
        if room_max_people:
            avg_max = np.mean(list(room_max_people.values()))
            stats_text += f'\n평균 최대 정원: {avg_max:.0f}명'
        
        ax.text(0.02, 0.92, stats_text, transform=ax.transAxes, fontsize=11,
                verticalalignment='top', 
                bbox=dict(boxstyle='round', facecolor='lightcyan', alpha=0.9))
        
        # 축 설정
        ax.set_xlabel('room_entry_sequence (시간의 흐름)', fontsize=12, fontweight='bold')
        ax.set_ylabel('people_count (인원수)', fontsize=12, fontweight='bold')
        ax.grid(True, alpha=0.3, linestyle='--')
        
        # Y축 범위 설정
        max_upper_bound = np.max(mean_actual_array + std_actual_array) if len(mean_actual_array) > 0 else 10
        y_max = max(max_upper_bound, np.max(mean_ideal_array)) * 1.2 if len(mean_ideal_array) > 0 else 10
        ax.set_ylim(0, y_max)
        
        plt.tight_layout()
        
        # 파일 저장
        filename = 'semaphore_sequential_consistency_all_rooms.png'
        chart_path = os.path.join(self.output_dir, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
        
        print(f"✅ 전체 방 순차적 일관성 종합 차트 저장: {chart_path}")
    
    def generate_sequential_consistency_csv_report(self):
        """순차적 일관성 오류 CSV 보고서 생성"""
        print("📋 순차적 일관성 오류 CSV 보고서 생성")
        
        # 모든 데이터 처리
        all_data = self.df_preprocessor.copy()
        
        # 방 번호 필터링 (이중 확인)
        if self.room_number is not None:
            before_filter = len(all_data)
            all_data = all_data[all_data['roomNumber'] == self.room_number]
            print(f"방 {self.room_number} 필터링: {before_filter} → {len(all_data)}건")
        
        # 순차적 일관성 분석 컬럼 추가
        all_data = all_data.copy()
        
        # 최대 정원 제한을 적용한 이상적 순차 상태 계산
        ideal_sequential_states = []
        for _, row in all_data.iterrows():
            ideal_value = self.initial_people_count + row['room_entry_sequence']
            max_people = row['max_people']
            capped_ideal = min(ideal_value, max_people)
            ideal_sequential_states.append(capped_ideal)
        
        all_data['ideal_sequential_state'] = ideal_sequential_states
        all_data['sequential_consistency_diff'] = all_data['curr_people'] - all_data['ideal_sequential_state']
        all_data['sequential_consistency_violation'] = (all_data['curr_people'] != all_data['ideal_sequential_state'])
        
        # 오류 발생 행만 필터링
        violation_data = all_data[all_data['sequential_consistency_violation'] == True].copy()
        
        # 파일명 생성
        if self.room_number:
            csv_filename = f'semaphore_sequential_consistency_violations_room{self.room_number}.csv'
        else:
            csv_filename = 'semaphore_sequential_consistency_violations_all_rooms.csv'
        
        csv_path = os.path.join(self.output_dir, csv_filename)
        
        # 순차적 일관성 오류 특화 컬럼 순서
        consistency_columns = [
            'roomNumber', 'bin', 'room_entry_sequence', 'user_id',
            'curr_people', 'ideal_sequential_state', 'sequential_consistency_diff',
            'max_people', 'join_result', 'sequential_consistency_violation'
        ]
        
        # 나노초 데이터 포함 (있는 경우)
        if 'true_critical_section_nanoTime_start' in all_data.columns:
            consistency_columns.extend([
                'true_critical_section_nanoTime_start', 'true_critical_section_nanoTime_end'
            ])
        
        if violation_data.empty:
            # 위반 사항이 없는 경우
            empty_df = pd.DataFrame(columns=consistency_columns)
            empty_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
            print(f"✅ 순차적 일관성 완벽 유지! 빈 오류 파일 생성: {csv_path}")
        else:
            # 사용 가능한 컬럼만 선택
            available_columns = [col for col in consistency_columns if col in violation_data.columns]
            csv_df = violation_data[available_columns].copy()
            
            # room_entry_sequence 순서로 정렬
            csv_df = csv_df.sort_values('room_entry_sequence')
            
            # CSV 저장
            csv_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
            print(f"순차적 일관성 오류 CSV 보고서: {len(csv_df)}건 → {csv_path}")
            print(f"순차적 일관성 위반률: {len(csv_df)/len(all_data)*100:.1f}%")
        
        return csv_path
    
    def run_analysis(self):
        """순차적 일관성 검증 분석 실행"""
        print("🚀 세마포어 순차적 일관성 검증 분석 시작")
        print("🎯 목표: 세마포어의 순차적 일관성 훼손을 데이터로 증명")
        print("📋 분석 방법: 이상적 순차 상태 vs 실제 기록값 비교")
        
        # 1. 데이터 로딩
        if not self.load_data():
            print("❌ 데이터 로딩 실패")
            return False
        
        # 2. 출력 폴더 생성
        self.create_output_folders()
        
        try:
            # 3. 순차적 일관성 비교 차트 생성
            self.create_sequential_consistency_chart()
            
            # 4. 순차적 일관성 오류 CSV 보고서 생성
            self.generate_sequential_consistency_csv_report()
            
            # 5. 최종 결과 요약
            self._print_final_summary()
            
            return True
            
        except Exception as e:
            print(f"❌ 분석 중 오류 발생: {e}")
            import traceback
            traceback.print_exc()
            return False
    
    def _print_final_summary(self):
        """최종 결과 요약 출력"""
        print("\n" + "="*60)
        print("🏆 세마포어 순차적 일관성 검증 최종 결과")
        print("="*60)
        
        print(f"📊 분석 결과:")
        print(f"   총 permit 요청: {self.total_requests}건")
        print(f"   순차적 일관성 위반: {self.sequential_consistency_violations}건")
        
        if self.total_requests > 0:
            consistency_rate = ((self.total_requests - self.sequential_consistency_violations) / self.total_requests) * 100
            violation_rate = (self.sequential_consistency_violations / self.total_requests) * 100
            print(f"   순차적 일관성 유지율: {consistency_rate:.1f}%")
            print(f"   순차적 일관성 위반률: {violation_rate:.1f}%")
        
        print(f"\n🎯 결론:")
        if self.sequential_consistency_violations == 0:
            print("✅ 세마포어가 순차적 일관성을 완벽하게 보장했습니다!")
            print("🎉 추가적인 원자성 보장 장치가 불필요합니다.")
        else:
            print("❌ 세마포어의 순차적 일관성 훼손이 데이터로 증명되었습니다!")
            print("⚠️ 세마포어는 동시 접근 '개수'는 제어하지만, 내부 작업의 '원자성'은 보장하지 않습니다.")
            print("💡 AtomicInteger와 같은 추가적인 원자성 보장 장치가 필요합니다.")
        
        print("="*60)

def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description='세마포어 순차적 일관성 검증 분석 및 시각화 (최종 수정버전)',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
사용 예시:
  # 전체 방 순차적 일관성 분석
  python semaphore_consistency_analyzer.py --preprocessor_file preprocessor_semaphore.csv --output_dir output/
  
  # 특정 방 순차적 일관성 분석  
  python semaphore_consistency_analyzer.py --room_number 1294 --preprocessor_file preprocessor_semaphore.csv --output_dir output/
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
        help='세마포어 전처리 데이터 CSV 파일 경로 (preprocessor_semaphore.csv)'
    )
    
    parser.add_argument(
        '--output_dir',
        type=str,
        required=True,
        help='분석 결과를 저장할 디렉토리 경로'
    )
    
    args = parser.parse_args()
    
    # 세마포어 순차적 일관성 분석기 생성 및 실행
    analyzer = SemaphoreSequentialConsistencyAnalyzer(
        room_number=args.room_number,
        preprocessor_file=args.preprocessor_file,
        output_dir=args.output_dir
    )
    
    success = analyzer.run_analysis()
    
    if not success:
        print("❌ 세마포어 순차적 일관성 분석 실패")
        exit(1)
    else:
        print("🎉 세마포어 순차적 일관성 분석 완료!")

if __name__ == "__main__":
    main()
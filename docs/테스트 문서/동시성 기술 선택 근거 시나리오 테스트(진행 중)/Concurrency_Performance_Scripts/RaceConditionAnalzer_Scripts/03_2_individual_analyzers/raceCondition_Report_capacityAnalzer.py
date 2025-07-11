"""
Rule 3: Capacity 분석기 - 완전 독립 실행 가능 파일
정원 초과 오류 분석 및 시각화
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

class Rule3CapacityAnalyzer:
    def __init__(self, room_number=None, preprocessor_file=None, result_file=None, output_dir=None):
        """
        Rule 3 Capacity 분석기 초기화
        
        Args:
            room_number (int, optional): 분석할 특정 방 번호
            preprocessor_file (str): 전처리 데이터 파일 경로 (차트 및 CSV용)
            result_file (str): 분석 결과 데이터 파일 경로 (사용 안함)
            output_dir (str): 출력 디렉토리 경로
        """
        self.room_number = room_number
        self.preprocessor_file = preprocessor_file
        self.result_file = result_file  # Rule3에서는 사용하지 않음
        self.output_dir = output_dir
        
        # 데이터 저장용 변수
        self.df_preprocessor = None
        
        # 고정밀도 데이터 여부
        self.has_high_precision = False
    
    def load_data(self):
        """CSV 파일을 로드하고 전처리"""
        try:
            # 전처리 파일 로드 (차트 및 CSV용)
            self.df_preprocessor = pd.read_csv(self.preprocessor_file)
            print(f"✅ 전처리 파일 로드 완료: {len(self.df_preprocessor)}건")
            
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
            if col in self.df_preprocessor.columns:
                try:
                    self.df_preprocessor[col] = pd.to_datetime(self.df_preprocessor[col])
                    print(f"✅ {col} 컬럼 datetime 변환 완료")
                except Exception as e:
                    print(f"⚠️ {col} 컬럼 datetime 변환 실패: {e}")
        
        # 방 번호로 필터링 (지정된 경우만)
        if self.room_number is not None:
            before_filter = len(self.df_preprocessor)
            self.df_preprocessor = self.df_preprocessor[self.df_preprocessor['roomNumber'] == self.room_number]
            print(f"✅ 방 {self.room_number} 필터링 완료: {before_filter} → {len(self.df_preprocessor)}건")
        
        # 시간순 정렬 및 인덱스 부여
        self.df_preprocessor = self.df_preprocessor.sort_values('curr_entry_time').reset_index(drop=True)
        self.df_preprocessor['request_index'] = range(len(self.df_preprocessor))
        print(f"✅ 시간순 정렬 및 request_index 컬럼 추가 완료")
        
        return True
    
    def create_output_folders(self):
        """출력 폴더 생성"""
        if not os.path.exists(self.output_dir):
            os.makedirs(self.output_dir)
            print(f"✅ 출력 디렉토리 생성: {self.output_dir}")
        else:
            print(f"✅ 출력 디렉토리 확인: {self.output_dir}")
    
    def create_rule3_capacity_exceeded_chart(self):
        """규칙 3: 정원 초과 오류 분석 차트"""
        if self.room_number is not None:
            self._create_rule3_single_room_chart()
        else:
            self._create_rule3_multi_room_chart()
    
    def _create_rule3_single_room_chart(self):
        """규칙 3: 단일 방 정원 초과 분석 차트"""
        room_data = self.df_preprocessor.copy()
        if room_data.empty:
            print("❌ 단일 방 데이터가 비어있어 차트 생성을 건너뜁니다.")
            return
        
        print(f"🎯 단일 방 {self.room_number} Rule3 차트 생성 시작")
        
        # X축: 스레드 요청 순서 (인덱스)
        total_requests = len(room_data)
        x_positions = list(range(total_requests))
        print(f"   - 총 요청 수: {total_requests}")
        
        # Y축 데이터 준비
        curr_people_values = room_data['curr_people'].tolist()
        max_people = room_data['max_people'].iloc[0] if not room_data.empty else 20
        print(f"   - 최대 정원: {max_people}")
        
        # Y축 최댓값 동적 계산
        y_max = max(max(curr_people_values), max_people) * 1.2
        print(f"   - Y축 최댓값: {y_max:.1f}")
        
        # 차트 생성
        fig, ax = plt.subplots(figsize=(20, 12))
        title = f'규칙 3: 정원 초과 오류 분석 - 방 {self.room_number}'
        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        
        # 1. 최대 정원 한계선 (붉은색 점선)
        ax.axhline(y=max_people, color='red', linestyle='--', linewidth=2, 
                   label=f'최대 정원 한계선 (max_people = {max_people})', alpha=0.8)
        
        # 2. 실제 기록된 인원수 (파란색 실선 + 작은 원점)
        ax.plot(x_positions, curr_people_values, color='blue', linewidth=2,
                marker='o', markersize=3, markerfacecolor='blue', markeredgecolor='blue',
                label='실제 기록된 인원수 (curr_people)', alpha=0.8)
        
        # 3. 정원 초과 발생 시점 강조 (자홍색 수직 음영)
        exceeded_count = 0
        exceeded_positions = []
        
        for i in range(total_requests):
            curr = curr_people_values[i]
            
            # curr_people > max_people인 경우
            if curr > max_people:
                ax.axvspan(i-0.3, i+0.3, ymin=0, ymax=1, color='magenta', alpha=0.3,
                          label='정원 초과 발생 시점' if exceeded_count == 0 else '')
                # 초과 지점에 빨간색 강조점 추가
                ax.scatter(i, curr, color='red', s=100, alpha=1.0, zorder=5)
                exceeded_count += 1
                exceeded_positions.append(i)
        
        print(f"   - 정원 초과 발생: {exceeded_count}건")
        
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
        chart_path = os.path.join(self.output_dir, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
        
        print(f"✅ 단일 방 차트 저장 완료: {chart_path}")
    
    def _create_rule3_multi_room_chart(self):
        """규칙 3: 전체 방 정원 초과 종합 분석 차트"""
        rooms = self.df_preprocessor['roomNumber'].unique()
        print(f"🎯 전체 {len(rooms)}개 방 Rule3 종합 차트 생성 시작")
        
        # 각 방별 데이터 정리
        room_datasets = {}
        max_requests = 0
        
        for room in rooms:
            room_subset = self.df_preprocessor[
                self.df_preprocessor['roomNumber'] == room
            ].sort_values('curr_entry_time').reset_index(drop=True)
            room_datasets[room] = room_subset
            max_requests = max(max_requests, len(room_subset))
        
        print(f"   - 최대 요청 수: {max_requests}")
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
        
        print(f"   - 평균/표준편차 계산 완료")
        
        # 차트 생성
        fig, ax = plt.subplots(figsize=(20, 12))
        title = f"규칙 3: 정원 초과 오류 분석 - 전체 {len(rooms)}개 방 평균 및 신뢰구간"
        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        
        # numpy 배열로 변환
        mean_curr_array = np.array(mean_curr)
        std_curr_array = np.array(std_curr)
        mean_max_array = np.array(mean_max_people)
        
        # 1. 평균 최대 정원 한계선 (붉은색 점선)
        ax.plot(x_positions, mean_max_array, 'r--', linewidth=2,
                label='평균 최대 정원 한계선 (max_people)', alpha=0.8)
        
        # 2. 실제값 신뢰구간 (파란색 음영)
        ax.fill_between(x_positions, 
                       mean_curr_array - std_curr_array, 
                       mean_curr_array + std_curr_array, 
                       alpha=0.3, color='blue', label='실제값 신뢰구간 (±1σ)')
        
        # 3. 평균 실제 인원수 (파란색 실선 + 작은 원점)
        ax.plot(x_positions, mean_curr_array, color='blue', linewidth=2,
                marker='o', markersize=3, markerfacecolor='blue', markeredgecolor='blue',
                label='평균 실제 기록된 인원수 (curr_people)', alpha=0.8)
        
        # 4. 정원 초과 표식 (자홍색 수직 음영)
        exceeded_count = 0
        for room, dataset in room_datasets.items():
            for i, row in dataset.iterrows():
                if i < len(x_positions) and row['curr_people'] > row['max_people']:
                    ax.axvspan(i-0.2, i+0.2, ymin=0, ymax=1, color='magenta', alpha=0.2,
                              label='정원 초과 발생 시점' if exceeded_count == 0 else '')
                    exceeded_count += 1
        
        print(f"   - 전체 정원 초과: {exceeded_count}건")
        
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
        print(f"   - Y축 최댓값: {y_max:.1f}")
        
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
        chart_path = os.path.join(self.output_dir, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
        
        print(f"✅ 전체 방 차트 저장 완료: {chart_path}")
    
    def generate_rule3_csv_report(self):
        """규칙 3 정원 초과 오류 CSV 보고서 생성"""
        print("📋 Rule3 CSV 보고서 생성 시작")
        
        # curr_people > max_people 조건으로 직접 필터링
        capacity_exceeded = self.df_preprocessor[
            self.df_preprocessor['curr_people'] > self.df_preprocessor['max_people']
        ].copy()
        
        print(f"   - 정원 초과 오류: {len(capacity_exceeded)}건")
        
        # 파일명 생성
        if self.room_number:
            csv_filename = f'report_rule3_capacity_exceeded_errors_room{self.room_number}.csv'
        else:
            csv_filename = 'report_rule3_capacity_exceeded_errors.csv'
        
        csv_path = os.path.join(self.output_dir, csv_filename)
        
        # 출력할 컬럼 정의
        required_columns = [
            'roomNumber', 'bin', 'room_entry_sequence', 'user_id',
            'prev_entry_time', 'curr_entry_time',
            'curr_people', 'max_people'
        ]
        
        if capacity_exceeded.empty:
            # 빈 데이터인 경우 빈 DataFrame 생성
            empty_df = pd.DataFrame(columns=required_columns)
            empty_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
            print(f"   - 빈 CSV 파일 생성: {csv_path}")
        else:
            # 사용 가능한 컬럼만 선택
            available_columns = [col for col in required_columns if col in capacity_exceeded.columns]
            csv_df = capacity_exceeded[available_columns].copy()
            
            # 누락된 컬럼은 빈 문자열로 추가
            for col in required_columns:
                if col not in csv_df.columns:
                    csv_df[col] = ''
            
            # 컬럼 순서 맞춤
            csv_df = csv_df[required_columns]
            
            # 방 번호 필터링 (이중 확인)
            if self.room_number is not None:
                csv_df = csv_df[csv_df['roomNumber'] == self.room_number]
            
            # 정렬 (curr_entry_time 순)
            if 'curr_entry_time' in csv_df.columns:
                csv_df = csv_df.sort_values('curr_entry_time')
            
            # CSV 저장
            csv_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
            print(f"   - CSV 보고서 생성 완료: {len(csv_df)}건 → {csv_path}")
        
        return csv_path
    
    def run_analysis(self):
        """전체 분석 실행"""
        print("🚀 Rule 3: Capacity 분석 시작")
        
        # 1. 데이터 로딩
        if not self.load_data():
            print("❌ 데이터 로딩 실패")
            return False
        
        # 2. 출력 폴더 생성
        self.create_output_folders()
        
        try:
            # 3. 차트 생성 (단일방 또는 전체방)
            self.create_rule3_capacity_exceeded_chart()
            
            # 4. CSV 보고서 생성
            self.generate_rule3_csv_report()
            
        except Exception as e:
            print(f"❌ 분석 중 오류 발생: {e}")
            import traceback
            traceback.print_exc()
            return False
        
        print("✅ Rule 3 분석 완료!")
        return True

def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description='Rule 3: Capacity 분석 및 시각화',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
사용 예시:
  # 전체 방 분석
  python rule3_capacity_analyzer.py --preprocessor_file data.csv --output_dir output/
  
  # 특정 방 분석  
  python rule3_capacity_analyzer.py --room_number 1135 --preprocessor_file data.csv --output_dir output/
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
        help='전처리 데이터 CSV 파일 경로 (차트 및 CSV 보고서용 데이터)'
    )
    
    parser.add_argument(
        '--result_file',
        type=str,
        help='분석 결과 CSV 파일 경로 (Rule3에서는 사용하지 않음)'
    )
    
    parser.add_argument(
        '--output_dir',
        type=str,
        required=True,
        help='분석 결과를 저장할 디렉토리 경로'
    )
    
    args = parser.parse_args()
    
    # Rule3 분석기 생성 및 실행
    analyzer = Rule3CapacityAnalyzer(
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
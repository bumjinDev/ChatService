"""
Semaphore 전용 정원 초과 방지 효과성 분석기 (새로 생성)
세마포어가 정원 초과를 완벽하게 방지했음을 증명하는 포트폴리오용 분석 도구
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

class SemaphoreCapacityAnalyzer:
    def __init__(self, room_number=None, preprocessor_file=None, result_file=None, output_dir=None):
        """
        Semaphore 정원 초과 방지 효과성 분석기 초기화
        """
        self.room_number = room_number
        self.preprocessor_file = preprocessor_file
        self.result_file = result_file
        self.output_dir = output_dir
        
        # 데이터 저장용 변수
        self.df_preprocessor = None
        self.df_result = None
        
        # 세마포어 특화 분석 결과
        self.total_requests = 0
        self.capacity_exceeded_count = 0
        self.perfect_success = False
    
    def load_data(self):
        """CSV 파일을 로드하고 세마포어 데이터 전처리"""
        try:
            # 1. 전처리 파일 로드
            self.df_preprocessor = pd.read_csv(self.preprocessor_file)
            print("전처리 파일 로드 완료: " + str(len(self.df_preprocessor)) + "건")
            
            # 2. 결과 파일 로드
            self.df_result = pd.read_csv(self.result_file)
            print("결과 파일 로드 완료: " + str(len(self.df_result)) + "건")
            
            # 3. 세마포어 특화 데이터 검증
            self._validate_semaphore_data()
            
        except FileNotFoundError as e:
            print("파일을 찾을 수 없습니다: " + str(e))
            return False
        except Exception as e:
            print("데이터 로딩 오류: " + str(e))
            return False
        
        # 4. 방 번호로 필터링
        if self.room_number is not None:
            before_filter = len(self.df_preprocessor)
            self.df_preprocessor = self.df_preprocessor[self.df_preprocessor['roomNumber'] == self.room_number]
            print("전처리 데이터 필터링: " + str(before_filter) + " → " + str(len(self.df_preprocessor)) + "건")
            
            before_filter_result = len(self.df_result)
            self.df_result = self.df_result[self.df_result['roomNumber'] == self.room_number]
            print("결과 데이터 필터링: " + str(before_filter_result) + " → " + str(len(self.df_result)) + "건")
        
        # 5. 인덱스 부여
        self.df_preprocessor = self.df_preprocessor.reset_index(drop=True)
        self.df_preprocessor['request_index'] = range(len(self.df_preprocessor))
        self.df_result = self.df_result.reset_index(drop=True)
        
        # 6. 세마포어 핵심 통계 계산
        self._calculate_semaphore_statistics()
        
        return True
    
    def _validate_semaphore_data(self):
        """세마포어 데이터 유효성 검증"""
        # 필수 컬럼 확인 (전처리 파일)
        required_preprocessor_cols = ['roomNumber', 'bin', 'user_id', 'prev_people', 'curr_people', 
                                    'max_people', 'room_entry_sequence', 'join_result']
        missing_cols = [col for col in required_preprocessor_cols if col not in self.df_preprocessor.columns]
        if missing_cols:
            raise ValueError("전처리 파일에 필수 컬럼 누락: " + str(missing_cols))
        
        # 필수 컬럼 확인 (결과 파일)
        required_result_cols = ['roomNumber', 'user_id', 'curr_people', 'max_people', 'anomaly_type']
        optional_cols = ['over_capacity_amount', 'over_capacity_curr', 'over_capacity_max']
        
        missing_cols = [col for col in required_result_cols if col not in self.df_result.columns]
        if missing_cols:
            raise ValueError("결과 파일에 필수 컬럼 누락: " + str(missing_cols))
        
        # 선택적 컬럼 누락 시 빈 컬럼으로 추가
        for col in optional_cols:
            if col not in self.df_result.columns:
                self.df_result[col] = 0.0
                print("누락된 컬럼 " + col + "을 0으로 초기화했습니다.")
        
        print("세마포어 데이터 유효성 검증 완료")
    
    def _calculate_semaphore_statistics(self):
        """세마포어 핵심 통계 계산"""
        self.total_requests = len(self.df_result)
        
        # anomaly_type 컬럼 처리
        self.df_result['anomaly_type'] = self.df_result['anomaly_type'].fillna('').astype(str)
        
        # 정원 초과 오류 카운트
        capacity_errors = self.df_result[
            self.df_result['anomaly_type'].str.contains('정원 초과 오류', na=False)
        ]
        self.capacity_exceeded_count = len(capacity_errors)
        
        # 완벽한 성공 여부
        self.perfect_success = (self.capacity_exceeded_count == 0)
        
        print("세마포어 핵심 통계:")
        print("   총 요청 수: " + str(self.total_requests) + "건")
        print("   정원 초과 오류: " + str(self.capacity_exceeded_count) + "건")
        if self.perfect_success:
            print("   완벽한 성공: YES")
        else:
            print("   완벽한 성공: NO")
        
        # 허가 성공/실패 통계
        if 'join_result' in self.df_preprocessor.columns and len(self.df_preprocessor) > 0:
            success_count = len(self.df_preprocessor[self.df_preprocessor['join_result'] == 'SUCCESS'])
            fail_count = len(self.df_preprocessor[self.df_preprocessor['join_result'].str.contains('FAIL')])
            
            total_preprocessor = len(self.df_preprocessor)
            success_rate = (success_count / total_preprocessor * 100) if total_preprocessor > 0 else 0.0
            fail_rate = (fail_count / total_preprocessor * 100) if total_preprocessor > 0 else 0.0
            
            print("   허가 획득 성공: " + str(success_count) + "건 (" + str(round(success_rate, 1)) + "%)")
            print("   허가 획득 실패: " + str(fail_count) + "건 (" + str(round(fail_rate, 1)) + "%)")
        else:
            print("   허가 성공/실패 통계: 데이터 없음")
    
    def create_output_folders(self):
        """출력 폴더 생성"""
        if not os.path.exists(self.output_dir):
            os.makedirs(self.output_dir)
            print("출력 디렉토리 생성: " + self.output_dir)
        else:
            print("출력 디렉토리 확인: " + self.output_dir)
    
    def create_semaphore_capacity_chart(self):
        """세마포어 정원 초과 방지 효과성 차트 생성"""
        if self.room_number is not None:
            self._create_single_room_semaphore_chart()
        else:
            self._create_multi_room_semaphore_chart()
    
    def _create_single_room_semaphore_chart(self):
        """단일 방 세마포어 정원 초과 방지 효과성 차트"""
        room_data = self.df_preprocessor.copy()
        data_exists = not room_data.empty
        
        if data_exists:
            print("방 " + str(self.room_number) + " 세마포어 효과성 차트 생성 시작")
        else:
            print("방 " + str(self.room_number) + " 세마포어 효과성 차트 생성 시작 (데이터 없음)")
        
        # 차트 생성
        fig, ax = plt.subplots(figsize=(20, 12))
        
        if data_exists:
            # X축: 세마포어 permit 요청 순서
            total_requests = len(room_data)
            x_positions = list(range(total_requests))
            
            # Y축 데이터: permit 개수
            curr_people_values = room_data['curr_people'].tolist()
            max_people = room_data['max_people'].iloc[0] if not room_data.empty else 20
            
            # Y축 범위 계산
            if curr_people_values:
                y_max = max(max(curr_people_values), max_people) * 1.2
            else:
                y_max = max_people * 1.2
            
            # 성공 상태에 따른 제목 설정
            if self.perfect_success:
                success_indicator = "완벽한 성공"
            else:
                success_indicator = "오류 발견"
            
            title = "세마포어 정원 초과 방지 효과성 분석 - 방 " + str(self.room_number) + " (" + success_indicator + ")"
            
            # 1. 최대 permit 한계선
            ax.axhline(y=max_people, color='red', linestyle='--', linewidth=3, 
                       label='세마포어 permit 한계선 (max_people = ' + str(max_people) + ')', alpha=0.9)
            
            # 2. 실제 permit 사용량
            if curr_people_values:
                ax.plot(x_positions, curr_people_values, color='blue', linewidth=2,
                        marker='o', markersize=3, markerfacecolor='blue', markeredgecolor='blue',
                        label='실제 permit 사용량 (curr_people)', alpha=0.8)
        else:
            # 데이터가 없는 경우 기본 설정
            total_requests = 0
            max_people = 20
            y_max = max_people * 1.2
            
            # 데이터 없음 제목
            title = "세마포어 정원 초과 방지 효과성 분석 - 방 " + str(self.room_number) + " (데이터 없음)"
            
            # 기본 permit 한계선
            ax.axhline(y=max_people, color='red', linestyle='--', linewidth=3, 
                       label='세마포어 permit 한계선 (기본값 = ' + str(max_people) + ')', alpha=0.9)
            
            # 데이터 없음 표시
            no_data_text = "방 " + str(self.room_number) + "에\n세마포어 데이터가 없습니다"
            ax.text(0.5, 0.5, no_data_text, 
                   transform=ax.transAxes, fontsize=20, fontweight='bold',
                   ha='center', va='center', 
                   bbox=dict(boxstyle='round,pad=1', facecolor='lightgray', alpha=0.8))
        
        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        
        # 3. 정원 초과 발생 시점 강조
        if data_exists and self.capacity_exceeded_count > 0:
            capacity_errors = self.df_result[
                self.df_result['anomaly_type'].str.contains('정원 초과 오류', na=False)
            ]
            
            error_marked = False
            for _, error_row in capacity_errors.iterrows():
                matching_rows = room_data[room_data['user_id'] == error_row['user_id']]
                
                if not matching_rows.empty:
                    idx = matching_rows.index[0]
                    ax.axvspan(idx-0.3, idx+0.3, ymin=0, ymax=1, color='red', alpha=0.5,
                              label='정원 초과 오류 발생' if not error_marked else '')
                    ax.scatter(idx, error_row['curr_people'], color='red', s=50, alpha=1.0, zorder=5)
                    error_marked = True
        
        # X축 눈금 설정
        if data_exists and total_requests > 0:
            tick_positions = [int(i * total_requests / 10) for i in range(11) if int(i * total_requests / 10) < total_requests]
            if tick_positions and tick_positions[-1] != total_requests - 1:
                tick_positions.append(total_requests - 1)
            ax.set_xticks(tick_positions)
            ax.set_xticklabels(tick_positions)
        else:
            ax.set_xticks([0, 10, 20, 30, 40, 50])
            ax.set_xticklabels([0, 10, 20, 30, 40, 50])
        
        # Y축 설정
        ax.set_ylim(0, y_max)
        
        # 범례 설정
        ax.legend(fontsize=12, loc='upper left', framealpha=0.9)
        
        # 통계 정보 박스
        if data_exists:
            stats_text = ('총 permit 요청: ' + str(total_requests) + '건\n' +
                         '최대 permit 한계: ' + str(max_people) + '개\n' +
                         '정원 초과 오류: ' + str(self.capacity_exceeded_count) + '건')
            
            if self.capacity_exceeded_count == 0:
                stats_text += '\n\n세마포어 방어 성공률: 100%'
            else:
                error_rate = (self.capacity_exceeded_count / total_requests * 100) if total_requests > 0 else 0.0
                stats_text += '\n오류 비율: ' + str(round(error_rate, 2)) + '%'
        else:
            no_data_msg = '방 ' + str(self.room_number) + '에 대한\n세마포어 데이터가 없습니다'
            stats_text = ('총 permit 요청: 0건\n' +
                         '최대 permit 한계: ' + str(max_people) + '개 (기본값)\n' +
                         '정원 초과 오류: 0건\n' +
                         '\n' + no_data_msg)
        
        ax.text(0.22, 0.98, stats_text, transform=ax.transAxes, fontsize=11,
                verticalalignment='top', 
                bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.9))
        
        # 축 레이블
        ax.set_xlabel('세마포어 permit 요청 순서', fontsize=12, fontweight='bold')
        ax.set_ylabel('permit 개수', fontsize=12, fontweight='bold')
        ax.grid(True, alpha=0.3, linestyle='--')
        plt.tight_layout()
        
        # 파일 저장
        filename = 'semaphore_capacity_analysis_room' + str(self.room_number) + '.png'
        chart_path = os.path.join(self.output_dir, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
    
    def _create_multi_room_semaphore_chart(self):
        """전체 방 세마포어 정원 초과 방지 효과성 종합 차트"""
        rooms = self.df_preprocessor['roomNumber'].unique()
        has_data = len(rooms) > 0
        
        if has_data:
            print("전체 " + str(len(rooms)) + "개 방 세마포어 종합 효과성 차트 생성 시작")
        else:
            print("전체 방 세마포어 종합 효과성 차트 생성 시작 (데이터 없음)")
        
        # 차트 생성
        fig, ax = plt.subplots(figsize=(20, 12))
        
        if has_data:
            # 방별 데이터 정리
            room_datasets = {}
            max_requests = 0
            
            for room in rooms:
                room_subset = self.df_preprocessor[
                    self.df_preprocessor['roomNumber'] == room
                ].reset_index(drop=True)
                room_datasets[room] = room_subset
                max_requests = max(max_requests, len(room_subset))
            
            if max_requests == 0:
                has_data = False
        
        if has_data:
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
                    mean_max_people.append(20)
            
            # 성공 상태에 따른 제목
            if self.perfect_success:
                success_indicator = "완벽한 성공"
            else:
                success_indicator = "오류 발견"
            
            title = "세마포어 정원 초과 방지 효과성 분석 - 전체 " + str(len(rooms)) + "개 방 종합 (" + success_indicator + ")"
            
            # numpy 배열로 변환
            mean_curr_array = np.array(mean_curr)
            std_curr_array = np.array(std_curr)
            mean_max_array = np.array(mean_max_people)
            
            # 1. 평균 permit 한계선
            ax.plot(x_positions, mean_max_array, 'r--', linewidth=3,
                    label='평균 세마포어 permit 한계선', alpha=0.9)
            
            # 2. permit 사용량 신뢰구간
            ax.fill_between(x_positions, 
                           mean_curr_array - std_curr_array, 
                           mean_curr_array + std_curr_array, 
                           alpha=0.3, color='blue', label='permit 사용량 표준편차 범위')
            
            # 3. 평균 permit 사용량
            ax.plot(x_positions, mean_curr_array, color='blue', linewidth=2,
                    marker='o', markersize=3, markerfacecolor='blue', markeredgecolor='blue',
                    label='평균 permit 사용량', alpha=0.8)
            
            # Y축 동적 계산
            if len(mean_curr_array) > 0 and len(mean_max_array) > 0:
                max_upper_bound = np.max(mean_curr_array + std_curr_array)
                y_max = max(max_upper_bound, np.max(mean_max_array)) * 1.2
            else:
                y_max = 25
                
            # X축 눈금 설정
            tick_positions = [int(i * max_requests / 10) for i in range(11) if int(i * max_requests / 10) < max_requests]
            if tick_positions and tick_positions[-1] != max_requests - 1:
                tick_positions.append(max_requests - 1)
            ax.set_xticks(tick_positions)
            ax.set_xticklabels(tick_positions)
            
        else:
            # 데이터가 없는 경우 기본 설정
            max_requests = 0
            y_max = 25
            
            # 데이터 없음 제목
            title = "세마포어 정원 초과 방지 효과성 분석 - 전체 방 종합 (데이터 없음)"
            
            # 기본 permit 한계선
            ax.axhline(y=20, color='red', linestyle='--', linewidth=3,
                      label='세마포어 permit 한계선 (기본값)', alpha=0.9)
            
            # 데이터 없음 표시
            no_data_msg = '세마포어 데이터가 없습니다\n(모든 방에서 데이터 없음)'
            ax.text(0.5, 0.5, no_data_msg, 
                   transform=ax.transAxes, fontsize=20, fontweight='bold',
                   ha='center', va='center', 
                   bbox=dict(boxstyle='round,pad=1', facecolor='lightgray', alpha=0.8))
            
            # 기본 X축
            ax.set_xticks([0, 10, 20, 30, 40, 50])
            ax.set_xticklabels([0, 10, 20, 30, 40, 50])
        
        ax.set_title(title, fontsize=16, fontweight='bold', pad=20)
        
        # 4. 정원 초과 발생 시점 표시
        if has_data and self.capacity_exceeded_count > 0:
            capacity_errors = self.df_result[
                self.df_result['anomaly_type'].str.contains('정원 초과 오류', na=False)
            ]
            
            exceeded_marked = 0
            for _, error_row in capacity_errors.iterrows():
                room = error_row['roomNumber']
                if room in room_datasets:
                    room_data = room_datasets[room]
                    matching_rows = room_data[room_data['user_id'] == error_row['user_id']]
                    
                    if not matching_rows.empty:
                        idx = matching_rows.index[0]
                        if idx < len(x_positions):
                            ax.axvspan(idx-0.2, idx+0.2, ymin=0, ymax=1, color='red', alpha=0.3,
                                      label='정원 초과 오류 발생' if exceeded_marked == 0 else '')
                            exceeded_marked += 1
        
        # Y축 설정
        ax.set_ylim(0, y_max)
        
        # 범례 설정
        ax.legend(fontsize=12, loc='upper left', framealpha=0.9)
        
        # 통계 정보 박스
        if has_data:
            stats_text = ('분석 방 수: ' + str(len(rooms)) + '개\n' +
                         '총 permit 요청: ' + str(self.total_requests) + '건\n' +
                         '정원 초과 오류: ' + str(self.capacity_exceeded_count) + '건')
            
            if self.capacity_exceeded_count == 0:
                stats_text += '\n\n성공률: 100%'
            else:
                overall_rate = (self.capacity_exceeded_count / self.total_requests * 100) if self.total_requests > 0 else 0.0
                stats_text += '\n전체 오류 비율: ' + str(round(overall_rate, 2)) + '%'
        else:
            no_data_summary = '세마포어 데이터가\n전체적으로 없습니다'
            stats_text = ('분석 방 수: 0개\n' +
                         '총 permit 요청: 0건\n' +
                         '정원 초과 오류: 0건\n' +
                         '\n' + no_data_summary)
        
        ax.text(0.2, 0.98, stats_text, transform=ax.transAxes, fontsize=11,
                verticalalignment='top', 
                bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.9))
        
        # 축 레이블
        ax.set_xlabel('세마포어 permit 요청 순서', fontsize=12, fontweight='bold')
        ax.set_ylabel('permit 개수', fontsize=12, fontweight='bold')
        ax.grid(True, alpha=0.3, linestyle='--')
        plt.tight_layout()
        
        # 파일 저장
        filename = 'semaphore_capacity_analysis_all_rooms.png'
        chart_path = os.path.join(self.output_dir, filename)
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()
    
    def generate_semaphore_csv_report(self):
        """세마포어 정원 초과 방지 효과성 CSV 보고서 생성"""
        print("세마포어 효과성 CSV 보고서 생성 시작")
        
        # 모든 데이터 사용
        all_data = self.df_result.copy()
        
        # 방 번호 필터링
        if self.room_number is not None:
            before_filter = len(all_data)
            all_data = all_data[all_data['roomNumber'] == self.room_number]
            print("방 " + str(self.room_number) + " 필터링: " + str(before_filter) + " → " + str(len(all_data)) + "건")
        
        # 파일명 생성
        if self.room_number:
            csv_filename = 'semaphore_effectiveness_report_room' + str(self.room_number) + '.csv'
        else:
            csv_filename = 'semaphore_effectiveness_report_all_rooms.csv'
        
        csv_path = os.path.join(self.output_dir, csv_filename)
        
        # 세마포어 특화 컬럼 순서 정의
        semaphore_columns = [
            'roomNumber', 'bin', 'room_entry_sequence', 'user_id',
            'prev_people', 'curr_people', 'max_people', 'join_result',
            'anomaly_type', 'over_capacity_amount', 'over_capacity_curr', 'over_capacity_max',
            'true_critical_section_nanoTime_start', 'true_critical_section_nanoTime_end'
        ]
        
        if all_data.empty:
            # 빈 데이터 처리
            empty_df = pd.DataFrame(columns=semaphore_columns)
            empty_df.to_csv(csv_path, index=False, encoding='utf-8-sig')
            print("빈 CSV 파일 생성: " + csv_path)
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
            print("세마포어 효과성 CSV 보고서 생성: " + str(len(csv_df)) + "건 → " + csv_path)
            print("정원 초과 오류: (완벽한 성공: " + str(self.perfect_success) + ")")
        
        return csv_path
    
    def run_analysis(self):
        """세마포어 정원 초과 방지 효과성 분석 실행"""
        print("세마포어 정원 초과 방지 효과성 분석 시작")
        print("목표: 세마포어가 정원 초과를 완벽하게 방지했음을 증명")
        
        # 1. 데이터 로딩
        if not self.load_data():
            print("데이터 로딩 실패")
            return False
        
        # 2. 출력 폴더 생성
        self.create_output_folders()
        
        try:
            # 3. 세마포어 효과성 차트 생성
            self.create_semaphore_capacity_chart()
            
            # 4. 세마포어 효과성 CSV 보고서 생성
            self.generate_semaphore_csv_report()
            
            # 5. 최종 결과 요약
            self._print_final_summary()
            
        except Exception as e:
            print("분석 중 오류 발생: " + str(e))
            import traceback
            traceback.print_exc()
            return False
        
        return True
    
    def _print_final_summary(self):
        """최종 결과 요약 출력"""
        print("\n" + "="*60)
        print("세마포어 정원 초과 방지 효과성 분석 최종 결과")
        print("="*60)
        
        if self.perfect_success:
            print("세마포어 정원 초과 방지: 완벽한 성공!")
            print("세마포어가 모든 정원 초과 시도를 성공적으로 차단했습니다.")
            print("포트폴리오 결론: 세마포어의 효과성이 100% 증명되었습니다.")
        else:
            print("세마포어 정원 초과 방지: 일부 실패")
            print(str(self.capacity_exceeded_count) + "건의 정원 초과 오류가 발견되었습니다.")
            print("추가 분석이 필요합니다.")
        
        print("\n상세 통계:")
        print("   총 permit 요청 수: " + str(self.total_requests) + "건")
        print("   정원 초과 오류 수: " + str(self.capacity_exceeded_count) + "건")
        
        if self.total_requests > 0:
            success_rate = ((self.total_requests - self.capacity_exceeded_count) / self.total_requests) * 100
            print("   세마포어 방어 성공률: " + str(round(success_rate, 2)) + "%")
        else:
            print("   세마포어 방어 성공률: 데이터 없음")
        
        print("="*60)

def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(
        description='세마포어 정원 초과 방지 효과성 분석 및 시각화',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
사용 예시:
  # 전체 방 분석
  python semaphore_capacity_analyzer.py --preprocessor_file preprocessor_semaphore.csv --result_file semaphore_analysis_result.csv --output_dir output/
  
  # 특정 방 분석  
  python semaphore_capacity_analyzer.py --room_number 1176 --preprocessor_file preprocessor_semaphore.csv --result_file semaphore_analysis_result.csv --output_dir output/
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
    
    # 세마포어 효과성 분석기 생성 및 실행
    analyzer = SemaphoreCapacityAnalyzer(
        room_number=args.room_number,
        preprocessor_file=args.preprocessor_file,
        result_file=args.result_file,
        output_dir=args.output_dir
    )
    
    success = analyzer.run_analysis()
    
    if not success:
        print("세마포어 분석 실패")
        exit(1)
    else:
        print("세마포어 분석 완료!")

if __name__ == "__main__":
    main()
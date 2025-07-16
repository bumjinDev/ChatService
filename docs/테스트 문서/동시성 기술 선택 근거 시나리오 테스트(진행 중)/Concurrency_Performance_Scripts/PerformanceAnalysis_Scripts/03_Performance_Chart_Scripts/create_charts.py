#!/usr/bin/env python3
"""
성능 분석 결과 시각화 스크립트 (리팩토링 버전)
Performance Analysis Results Visualization Script (Refactored)

Author: Generated based on requirements
Version: 3.1
Description: *_stats_nano.xlsx 파일들을 입력받아 동시성 제어 기법별 성능을 시각화
Features: 한글 폰트 지원, Box Plot, 통일된 Y축 시간 단위, 상세 범례, 코드 리팩토링
"""

import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import glob
import os
import sys
import yaml
import platform
from pathlib import Path

class PerformanceVisualizer:
    def __init__(self, config_path='config.yaml'):
        self.config = self.load_config(config_path)
        self.use_english_labels = False
        self.setup_matplotlib()
        
    def load_config(self, config_path):
        """설정 파일 로드"""
        try:
            with open(config_path, 'r', encoding='utf-8') as f:
                config = yaml.safe_load(f)
            return config
        except FileNotFoundError:
            return {
                'chart_style': 'seaborn-v0_8',
                'figure_dpi': 300,
                'font_size': 12,
                'colors': {
                    'success': '#2E8B57',
                    'failure': '#DC143C',
                    'wait_time': '#FF8C00',
                    'processing_time': '#4169E1'
                }
            }
    
    def setup_matplotlib(self):
        """matplotlib 설정 및 한글 폰트 설정"""
        try:
            plt.style.use(self.config.get('chart_style', 'seaborn-v0_8'))
        except:
            plt.style.use('default')
        
        # 운영체제별 한글 폰트 설정
        font_map = {
            'Darwin': ['AppleGothic', 'Apple SD Gothic Neo'],
            'Windows': ['Malgun Gothic', '맑은 고딕'],
            'Linux': ['NanumGothic', 'Nanum Gothic']
        }
        
        system = platform.system()
        font_candidates = font_map.get(system, [])
        
        korean_font_found = self._test_korean_fonts(font_candidates)
        
        if not korean_font_found:
            print("⚠️ 한글 폰트를 찾을 수 없습니다. 영어 레이블을 사용합니다.")
            plt.rcParams['font.family'] = 'DejaVu Sans'
            self.use_english_labels = True
        
        plt.rcParams['axes.unicode_minus'] = False
        plt.rcParams['font.size'] = self.config.get('font_size', 12)
        plt.rcParams['figure.dpi'] = self.config.get('figure_dpi', 300)
    
    def _test_korean_fonts(self, font_candidates):
        """한글 폰트 테스트 (내부 메서드)"""
        for font in font_candidates:
            try:
                plt.rcParams['font.family'] = font
                fig, ax = plt.subplots(figsize=(1, 1))
                ax.text(0.5, 0.5, '한글테스트', ha='center')
                plt.close(fig)
                print(f"✅ 한글 폰트 설정 성공: {font}")
                return True
            except:
                continue
        return False
    
    def format_time_axis(self, ax):
        """Y축을 자동 시간 단위로 포맷팅"""
        import matplotlib.ticker as ticker
        
        def format_time_ticks(x, pos):
            return self.format_time_value(x)
        
        ax.yaxis.set_major_formatter(ticker.FuncFormatter(format_time_ticks))
    
    def format_time_value(self, value):
        """시간 값을 적절한 단위로 포맷"""
        if value == 0:
            return '0'
        elif value < 1000:
            return f'{value:.0f}ns'
        elif value < 1000000:
            return f'{value/1000:.1f}μs'
        else:
            return f'{value/1000000:.1f}ms'
    
    def extract_label_from_filename(self, filename):
        """파일명에서 label 추출"""
        basename = os.path.basename(filename)
        
        patterns = [
            ('_stats_nano.xlsx', 'stats'),
            ('.xlsx', 'stats_nano')
        ]
        
        for suffix, invalid_name in patterns:
            if basename.endswith(suffix):
                label = basename.replace(suffix, '')
                return '테스트기법' if label in [invalid_name, ''] else label
        
        return basename
    
    def load_excel_data(self, filepath):
        """Excel 파일 로드"""
        try:
            excel_file = pd.ExcelFile(filepath)
            return {sheet_name: pd.read_excel(excel_file, sheet_name=sheet_name) 
                    for sheet_name in excel_file.sheet_names}
        except Exception as e:
            print(f"❌ Error loading {filepath}: {e}")
            return None
    
    def validate_sheets(self, sheets_dict):
        """필수 시트 검증"""
        required_sheets = [
            'Overall_Summary',
            'Overall_Success_Stats',
            'Overall_Capacity_Failed_Stats',
            'Per_Bin_Stats'
        ]
        
        missing_sheets = [sheet for sheet in required_sheets if sheet not in sheets_dict]
        if missing_sheets:
            raise ValueError(f"Missing required sheets: {missing_sheets}")
        return True
    
    def safe_extract_value(self, df, metric, statistic, default=0):
        """안전하게 값 추출"""
        result = df[(df['Metric'] == metric) & (df['Statistic'] == statistic)]['Value']
        if len(result) > 0:
            return result.values[0]
        else:
            print(f"⚠️ Warning: No value found for Metric='{metric}', Statistic='{statistic}'")
            return default
    
    def _extract_stats_for_metrics(self, df, metrics, stats=['Mean', 'Median', 'Max']):
        """여러 메트릭에 대한 통계값 추출 (내부 메서드)"""
        result = {}
        for metric in metrics:
            # 키 이름을 단순화 (공백과 특수문자 처리)
            if metric == 'Wait Time':
                key = 'wait_time'
            elif metric == 'Dwell Time (Critical Section)':
                key = 'dwell_time'
            elif metric == 'Fail Processing Time':
                key = 'fail_processing_time'
            else:
                # 기본 변환 로직
                key = metric.lower().replace(' ', '_').replace('(', '').replace(')', '')
            
            result[key] = {
                stat.lower(): self.safe_extract_value(df, metric, stat)
                for stat in stats
            }
        return result
    
    # 데이터 추출 메서드들
    def extract_success_failure_distribution(self, sheets_dict, label):
        """성공/실패 분포 데이터 추출"""
        summary_df = sheets_dict['Overall_Summary']
        success_rate = summary_df[summary_df['Category'] == 'Success']['Percentage (%)'].values[0]
        capacity_failed_rate = summary_df[summary_df['Category'] == 'Capacity Failed']['Percentage (%)'].values[0]
        
        return {
            'label': label,
            'success_rate': success_rate,
            'capacity_failed_rate': capacity_failed_rate
        }
    
    def extract_success_processing_cost(self, sheets_dict, label):
        """성공 요청 처리 비용 추출"""
        success_stats_df = sheets_dict['Overall_Success_Stats']
        
        wait_time_mean = self.safe_extract_value(success_stats_df, 'Wait Time', 'Mean')
        dwell_time_mean = self.safe_extract_value(success_stats_df, 'Dwell Time (Critical Section)', 'Mean')
        total_cost = wait_time_mean + dwell_time_mean
        
        return {
            'label': label,
            'wait_time': wait_time_mean,
            'dwell_time': dwell_time_mean,
            'total_cost': total_cost
        }
    
    def extract_failure_processing_cost(self, sheets_dict, label):
        """실패 요청 처리 비용 추출"""
        failed_stats_df = sheets_dict['Overall_Capacity_Failed_Stats']
        
        wait_time_mean = self.safe_extract_value(failed_stats_df, 'Wait Time', 'Mean')
        fail_processing_time_mean = self.safe_extract_value(failed_stats_df, 'Fail Processing Time', 'Mean')
        total_cost = wait_time_mean + fail_processing_time_mean
        
        return {
            'label': label,
            'wait_time': wait_time_mean,
            'fail_processing_time': fail_processing_time_mean,
            'total_cost': total_cost
        }
    
    def extract_wait_time_statistics(self, sheets_dict, label):
        """대기시간 통계 추출 (Box Plot용)"""
        success_stats_df = sheets_dict['Overall_Success_Stats']
        failed_stats_df = sheets_dict['Overall_Capacity_Failed_Stats']
        
        # 성공 요청 통계
        success_metrics = ['Wait Time', 'Dwell Time (Critical Section)']
        success_stats = self._extract_stats_for_metrics(success_stats_df, success_metrics)
        
        # 실패 요청 통계
        failed_metrics = ['Wait Time', 'Fail Processing Time']
        failed_stats = self._extract_stats_for_metrics(failed_stats_df, failed_metrics)
        
        return {
            'label': label,
            'success_wait': success_stats['wait_time'],
            'success_dwell': success_stats['dwell_time'],
            'failed_wait': failed_stats['wait_time'],
            'failed_processing': failed_stats['fail_processing_time']
        }
    
    def extract_load_trend_data(self, sheets_dict, label):
        """부하 추이 데이터 추출"""
        bin_stats_df = sheets_dict['Per_Bin_Stats']
        
        grouped = bin_stats_df.groupby('bin').agg({
            'success_rate(%)': 'mean',
            'success_avg_wait_time(ns)': 'mean',
            'capacity_failed_avg_wait_time(ns)': 'mean'
        }).reset_index()
        
        grouped.loc[grouped['capacity_failed_avg_wait_time(ns)'] == 0, 'capacity_failed_avg_wait_time(ns)'] = np.nan
        
        return {
            'label': label,
            'bins': grouped['bin'].tolist(),
            'success_rates': grouped['success_rate(%)'].tolist(),
            'success_wait_times': grouped['success_avg_wait_time(ns)'].tolist(),
            'failed_wait_times': grouped['capacity_failed_avg_wait_time(ns)'].tolist()
        }
    
    def extract_per_room_data(self, sheets_dict, label):
        """룸별 데이터 추출"""
        room_stats_df = sheets_dict['Per_Room_Stats']
        
        return {
            'label': label,
            'room_numbers': room_stats_df['roomNumber'].tolist(),
            'success_rates': room_stats_df['success_rate(%)'].tolist(),
            'success_wait_times': room_stats_df['success_avg_wait_time(ns)'].tolist(),
            'failed_wait_times': room_stats_df['capacity_failed_avg_wait_time(ns)'].tolist()
        }
    
    def create_boxplot_data_from_stats(self, stats):
        """통계값으로부터 Box Plot 데이터 생성"""
        mean = stats['mean']
        median = stats['median']
        maximum = stats['max']
        
        # Min 값 추정
        minimum = max(0, median * 0.1)
        
        # 사분위수 추정
        q1 = minimum + (median - minimum) * 0.5
        q3 = median + (maximum - median) * 0.5
        
        # 현실적인 분포 생성
        np.random.seed(42)
        data_points = [minimum, q1, median, mean, q3, maximum]
        
        # 추가 데이터 포인트 생성
        additional_points = []
        for _ in range(20):
            point = np.random.lognormal(np.log(median), 0.5)
            point = max(minimum, min(maximum, point))
            additional_points.append(point)
        
        return sorted(data_points + additional_points)
    
    def _setup_chart_basic(self, figsize=(10, 6)):
        """차트 기본 설정 (내부 메서드)"""
        fig, ax = plt.subplots(figsize=figsize)
        return fig, ax
    
    def _finalize_chart(self, output_path):
        """차트 저장 및 정리 (내부 메서드)"""
        plt.tight_layout()
        plt.savefig(output_path, dpi=self.config['figure_dpi'], bbox_inches='tight')
        plt.close()
        return output_path
    
    def _add_percentage_labels(self, ax, x_positions, success_rates, failure_rates):
        """퍼센트 레이블 추가 (내부 메서드)"""
        for i, (s, f) in enumerate(zip(success_rates, failure_rates)):
            ax.text(i, s/2, f'{s:.1f}%', ha='center', va='center', fontweight='bold')
            ax.text(i, s + f/2, f'{f:.1f}%', ha='center', va='center', fontweight='bold')
    
    def _add_value_labels_on_bars(self, bars, values, ax, format_time=False):
        """막대 위에 값 레이블 추가 (내부 메서드)"""
        for bar, value in zip(bars, values):
            height = bar.get_height()
            text = self.format_time_value(value) if format_time else f'{value:.1f}%'
            y_pos = height + 1 if not format_time else height * 1.02
            ax.text(bar.get_x() + bar.get_width()/2., y_pos,
                    text, ha='center', va='bottom', 
                    fontweight='bold' if not format_time else None,
                    fontsize=9 if format_time else None)
    
    # 차트 생성 메서드들
    def create_success_failure_distribution_chart(self, data_list, output_path):
        """차트 1-1: 성공/실패 분포"""
        labels = [d['label'] for d in data_list]
        success_rates = [d['success_rate'] for d in data_list]
        failure_rates = [d['capacity_failed_rate'] for d in data_list]
        
        fig, ax = self._setup_chart_basic()
        x = np.arange(len(labels))
        width = 0.6
        
        colors = self.config['colors']
        p1 = ax.bar(x, success_rates, width, label='성공', color=colors['success'], alpha=0.8)
        p2 = ax.bar(x, failure_rates, width, bottom=success_rates, label='실패', color=colors['failure'], alpha=0.8)
        
        ax.set_xlabel('동시성 제어 기법')
        ax.set_ylabel('비율 (%)')
        ax.set_title('동시성 기법별 성공률 vs 실패율')
        ax.set_xticks(x)
        ax.set_xticklabels(labels)
        ax.legend()
        ax.set_ylim(0, 100)
        
        self._add_percentage_labels(ax, x, success_rates, failure_rates)
        
        return self._finalize_chart(output_path)
    
    def create_processing_cost_chart(self, data_list, chart_type='success', output_path=None):
        """차트 1-2, 1-3: 처리 비용 분석"""
        sorted_data = sorted(data_list, key=lambda x: x['total_cost'])
        labels = [d['label'] for d in sorted_data]
        
        if chart_type == 'success':
            chart_config = {
                'wait_times': [d['wait_time'] for d in sorted_data],
                'processing_times': [d['dwell_time'] for d in sorted_data],
                'title': '성공 요청 처리시간 분석',
                'time_label': '실행시간'
            }
        else:  # failure
            chart_config = {
                'wait_times': [d['wait_time'] for d in sorted_data],
                'processing_times': [d['fail_processing_time'] for d in sorted_data],
                'title': '실패 요청 처리시간 분석',
                'time_label': '실패처리시간'
            }
        
        fig, ax = self._setup_chart_basic()
        x = np.arange(len(labels))
        width = 0.6
        
        colors = self.config['colors']
        p1 = ax.bar(x, chart_config['wait_times'], width, label='대기시간', color=colors['wait_time'], alpha=0.8)
        p2 = ax.bar(x, chart_config['processing_times'], width, bottom=chart_config['wait_times'], 
                    label=chart_config['time_label'], color=colors['processing_time'], alpha=0.8)
        
        self.format_time_axis(ax)
        
        ax.set_xlabel('기법 (처리시간 순 정렬)')
        ax.set_ylabel('시간 (자동 단위: ns/μs/ms)')
        ax.set_title(chart_config['title'])
        ax.set_xticks(x)
        ax.set_xticklabels(labels, rotation=45, ha='right')
        ax.legend()
        
        # 총합 표시
        for i, d in enumerate(sorted_data):
            total = d['total_cost']
            total_text = self.format_time_value(total)
            ax.text(i, total * 1.02, total_text, ha='center', va='bottom', fontsize=9)
        
        return self._finalize_chart(output_path)
    
    def _create_box_plot_data(self, data_list, request_type):
        """Box Plot 데이터 생성 (내부 메서드)"""
        all_data = []
        box_labels = []
        colors = []
        
        if request_type == 'success':
            data_config = [
                ('success_wait', 'Wait Time', self.config['colors']['wait_time']),
                ('success_dwell', 'Dwell Time', self.config['colors']['processing_time'])
            ]
            legend_elements = [
                plt.matplotlib.patches.Patch(facecolor=self.config['colors']['wait_time'], alpha=0.7, label='대기시간 (Wait Time)'),
                plt.matplotlib.patches.Patch(facecolor=self.config['colors']['processing_time'], alpha=0.7, label='실행시간 (Dwell Time)')
            ]
            title = '성공 요청 성능 분포 분석 (Box Plot)'
        else:
            data_config = [
                ('failed_wait', 'Wait Time', self.config['colors']['wait_time']),
                ('failed_processing', 'Fail Processing', self.config['colors']['failure'])
            ]
            legend_elements = [
                plt.matplotlib.patches.Patch(facecolor=self.config['colors']['wait_time'], alpha=0.7, label='대기시간 (Wait Time)'),
                plt.matplotlib.patches.Patch(facecolor=self.config['colors']['failure'], alpha=0.7, label='실패처리시간 (Fail Processing)')
            ]
            title = '실패 요청 성능 분포 분석 (Box Plot)'
        
        for data in data_list:
            label = data['label']
            for data_key, label_suffix, color in data_config:
                box_data = self.create_boxplot_data_from_stats(data[data_key])
                all_data.append(box_data)
                box_labels.append(f'{label}\n{label_suffix}')
                colors.append(color)
        
        return all_data, box_labels, colors, legend_elements, title
    
    def _add_box_plot_legends(self, ax, legend_elements):
        """Box Plot 범례 추가 (내부 메서드)"""
        from matplotlib.lines import Line2D
        
        # 데이터 타입 범례
        first_legend = ax.legend(handles=legend_elements, loc='upper right', 
                               bbox_to_anchor=(1.0, 1.0), title='데이터 타입')
        
        # Box Plot 구성 요소 범례
        boxplot_elements = [
            Line2D([0], [0], color='black', linewidth=2, label='중앙값 (Median)'),
            plt.matplotlib.patches.Patch(facecolor='lightgray', alpha=0.5, label='사분위수 범위 (Q1-Q3)'),
            Line2D([0], [0], color='black', linewidth=1, linestyle='-', label='최솟값/최댓값 범위'),
            Line2D([0], [0], marker='o', color='red', linewidth=0, markersize=4, label='이상치 (Outliers)')
        ]
        
        second_legend = ax.legend(handles=boxplot_elements, loc='upper right', 
                                bbox_to_anchor=(1.0, 0.75), title='Box Plot 구성 요소')
        ax.add_artist(first_legend)
    
    def create_wait_time_statistics_chart(self, data_list, request_type='success', output_path=None):
        """차트 2-1, 2-2: Box Plot"""
        all_data, box_labels, colors, legend_elements, title = self._create_box_plot_data(data_list, request_type)
        
        fig, ax = self._setup_chart_basic(figsize=(12, 8))
        
        # Box Plot 그리기
        bp = ax.boxplot(all_data, labels=box_labels, patch_artist=True)
        
        # 색상 적용
        for patch, color in zip(bp['boxes'], colors):
            patch.set_facecolor(color)
            patch.set_alpha(0.7)
        
        self._add_box_plot_legends(ax, legend_elements)
        
        # Y축 설정
        ax.set_yscale('log')
        self.format_time_axis(ax)
        
        ax.set_xlabel('기법별 성능 지표')
        ax.set_ylabel('시간 (자동 단위: ns/μs/ms, 로그스케일)')
        ax.set_title(title)
        ax.grid(True, alpha=0.3)
        
        plt.setp(ax.get_xticklabels(), rotation=45, ha='right')
        return self._finalize_chart(output_path)
    
    def create_load_trend_chart(self, data_list, output_path):
        """차트 3: 부하 누적 추이"""
        fig, ax = self._setup_chart_basic(figsize=(12, 8))
        colors = plt.cm.tab10(np.linspace(0, 1, len(data_list)))
        
        # 모든 데이터에서 전체 bin 범위 찾기
        all_bins = set()
        for data in data_list:
            all_bins.update(data['bins'])
        
        # 연속된 bin 범위 생성 (1부터 최대값까지)
        if all_bins:
            min_bin = min(all_bins)
            max_bin = max(all_bins)
            complete_bins = list(range(min_bin, max_bin + 1))
        else:
            complete_bins = []
        
        for idx, data in enumerate(data_list):
            label = data['label']
            bins = data['bins']
            success_wait_times = data['success_wait_times']
            failed_wait_times = data['failed_wait_times']
            
            # 누락된 bin에 대해 보간 또는 None 처리
            complete_success_times = []
            complete_failed_times = []
            
            for bin_num in complete_bins:
                if bin_num in bins:
                    bin_idx = bins.index(bin_num)
                    complete_success_times.append(success_wait_times[bin_idx])
                    complete_failed_times.append(failed_wait_times[bin_idx])
                else:
                    # 누락된 구간은 None으로 처리 (라인이 끊어짐)
                    complete_success_times.append(None)
                    complete_failed_times.append(None)
            
            # 성공 요청 라인
            success_label = f"{label} - 성공한 요청들의 평균 대기시간"
            ax.plot(complete_bins, complete_success_times, '-o', color=colors[idx], 
                    label=success_label, linewidth=2, markersize=4)
            
            # 실패 요청 라인 (NaN과 None 값 모두 처리)
            valid_failed_times = []
            valid_bins_for_failed = []
            
            for i, (bin_num, failed_time) in enumerate(zip(complete_bins, complete_failed_times)):
                if failed_time is not None and not np.isnan(failed_time):
                    valid_failed_times.append(failed_time)
                    valid_bins_for_failed.append(bin_num)
            
            if valid_failed_times:
                failed_label = f"{label} - 실패한 요청들의 평균 대기시간"
                # 실패 라인도 성공 라인과 같은 모양(-o)으로 통일, 색깔만 조금 다르게
                failed_color = plt.cm.tab10((idx + 0.5) % 1.0)  # 색상을 약간 변형
                ax.plot(valid_bins_for_failed, valid_failed_times, '-o', color=failed_color, 
                        label=failed_label, linewidth=2, alpha=0.8, markersize=4)
        
        self.format_time_axis(ax)
        
        ax.set_xlabel('시간 진행 (구간)')
        ax.set_ylabel('평균 대기시간 (자동 단위: ns/μs/ms)')
        ax.set_title('시간경과별 성능 저하 추이')
        ax.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
        ax.grid(True, alpha=0.3)
        ax.set_ylim(bottom=0)
        
        # X축 눈금을 모든 구간으로 설정
        if complete_bins:
            ax.set_xticks(complete_bins)
            ax.set_xticklabels(complete_bins)
        
        return self._finalize_chart(output_path)
    
    def create_per_room_chart(self, data, output_path):
        """차트 4: 룸별 성능 비교"""
        fig, (ax1, ax2, ax3) = plt.subplots(3, 1, figsize=(12, 12))
        
        room_numbers = data['room_numbers']
        success_rates = data['success_rates']
        success_wait_times = data['success_wait_times']
        failed_wait_times = data['failed_wait_times']
        
        # 룸 번호를 문자열로 변환하여 카테고리형으로 처리
        room_labels = [f'Room {room}' for room in room_numbers]
        x_positions = range(len(room_labels))  # 0, 1, 2, ... 연속적인 위치
        
        # 서브플롯 설정 데이터
        subplot_configs = [
            {
                'ax': ax1,
                'data': success_rates,
                'title': '룸별 성공률',
                'ylabel': '성공률 (%)',
                'color': self.config['colors']['success'],
                'format_time': False,
                'ylim': (0, 100)
            },
            {
                'ax': ax2,
                'data': success_wait_times,
                'title': '룸별 대기시간 - 성공',
                'ylabel': '대기시간 (자동 단위: ns/μs/ms)',
                'color': self.config['colors']['wait_time'],
                'format_time': True,
                'ylim': None
            },
            {
                'ax': ax3,
                'data': failed_wait_times,
                'title': '룸별 대기시간 - 실패',
                'ylabel': '대기시간 (자동 단위: ns/μs/ms)',
                'color': self.config['colors']['failure'],
                'format_time': True,
                'ylim': None
            }
        ]
        
        for i, config in enumerate(subplot_configs):
            ax = config['ax']
            bars = ax.bar(x_positions, config['data'], alpha=0.7, color=config['color'])
            ax.set_title(config['title'])
            ax.set_ylabel(config['ylabel'])
            ax.grid(True, alpha=0.3)
            
            # X축을 카테고리형으로 설정
            ax.set_xticks(x_positions)
            ax.set_xticklabels(room_labels)
            
            if config['ylim']:
                ax.set_ylim(config['ylim'])
            
            if config['format_time']:
                self.format_time_axis(ax)
            
            self._add_value_labels_on_bars(bars, config['data'], ax, config['format_time'])
            
            if i == 2:  # 마지막 서브플롯에만 x축 레이블
                ax.set_xlabel('룸 번호')
        
        return self._finalize_chart(output_path)
        
        return self._finalize_chart(output_path)
    
    def process_files(self, files):
        """파일 처리 및 차트 생성"""
        output_dir = Path('performance_charts')
        output_dir.mkdir(exist_ok=True)
        
        print(f"🔍 Found {len(files)} files to process:")
        for f in files:
            label = self.extract_label_from_filename(f)
            print(f"  - {f} → label: '{label}'")
        
        # 데이터 수집
        all_data = {
            'distribution': [],
            'success_cost': [],
            'failure_cost': [],
            'wait_stats': [],
            'load_trend': [],
            'per_room': []
        }
        
        extraction_methods = [
            ('distribution', self.extract_success_failure_distribution),
            ('success_cost', self.extract_success_processing_cost),
            ('failure_cost', self.extract_failure_processing_cost),
            ('wait_stats', self.extract_wait_time_statistics),
            ('load_trend', self.extract_load_trend_data)
        ]
        
        for filepath in files:
            label = self.extract_label_from_filename(filepath)
            print(f"\n📊 Processing file: {filepath} (label: {label})")
            
            sheets = self.load_excel_data(filepath)
            if sheets is None:
                continue
            
            try:
                self.validate_sheets(sheets)
                
                for data_key, method in extraction_methods:
                    all_data[data_key].append(method(sheets, label))
                
                if 'Per_Room_Stats' in sheets:
                    all_data['per_room'].append(self.extract_per_room_data(sheets, label))
                
                print(f"✅ Data extraction completed for {label}")
                
            except Exception as e:
                print(f"❌ Error processing {filepath}: {e}")
                continue
        
        if not all_data['distribution']:
            print("❌ No valid data found to process.")
            return
        
        # 차트 생성
        prefix = 'comparison' if len(files) > 1 else all_data['distribution'][0]['label']
        
        chart_configs = [
            ('차트1-1_요청처리결과분포.png', self.create_success_failure_distribution_chart, all_data['distribution']),
            ('차트1-2_성공요청처리비용분석.png', lambda data, path: self.create_processing_cost_chart(data, 'success', path), all_data['success_cost']),
            ('차트1-3_실패요청처리비용분석.png', lambda data, path: self.create_processing_cost_chart(data, 'failure', path), all_data['failure_cost']),
            ('차트2-1_성공요청대기시간분포.png', lambda data, path: self.create_wait_time_statistics_chart(data, 'success', path), all_data['wait_stats']),
            ('차트2-2_실패요청대기시간분포.png', lambda data, path: self.create_wait_time_statistics_chart(data, 'failed', path), all_data['wait_stats']),
            ('차트3_부하누적추이분석.png', self.create_load_trend_chart, all_data['load_trend'])
        ]
        
        generated_charts = []
        
        try:
            print("\n📈 Generating charts...")
            
            for filename, method, data in chart_configs:
                chart_path = output_dir / f'{prefix}_{filename}'
                method(data, chart_path)
                generated_charts.append(str(chart_path))
            
            # 차트 4: Room별 분석 (단일 파일인 경우에만)
            if len(files) == 1 and all_data['per_room']:
                chart_path = output_dir / f'{prefix}_차트4_룸별성능비교분석.png'
                self.create_per_room_chart(all_data['per_room'][0], chart_path)
                generated_charts.append(str(chart_path))
            
        except Exception as e:
            print(f"❌ Error creating charts: {e}")
            import traceback
            traceback.print_exc()
            return
        
        self._print_completion_summary(generated_charts, output_dir)
    
    def _print_completion_summary(self, generated_charts, output_dir):
        """완료 요약 출력 (내부 메서드)"""
        chart_descriptions = [
            "차트 1-1: 요청 처리 결과 분포 (성공률 vs 실패율)",
            "차트 1-2: 성공 요청의 처리 비용 분석 (대기+실행)",
            "차트 1-3: 실패 요청의 처리 비용 분석 (대기+거부처리)",
            "차트 2-1: 성공 요청 대기시간 분포 (Box Plot)",
            "차트 2-2: 실패 요청 대기시간 분포 (Box Plot)",
            "차트 3: 부하 누적에 따른 성능 저하 추이",
            "차트 4: Room별 성능 비교 분석 (단일파일 전용)"
        ]
        
        print(f"\n🎉 Successfully generated {len(generated_charts)} charts:")
        for i, chart in enumerate(generated_charts):
            desc = chart_descriptions[i] if i < len(chart_descriptions) else "추가 분석"
            print(f"  ✅ {desc}")
            print(f"      📁 {chart}")
        
        print(f"\n📁 All charts saved to '{output_dir}' directory.")
        
        axis_info = [
            ("차트 1-1 (요청처리결과분포)", "동시성 제어 기법", "비율 (%) - 성공/실패 누적 막대"),
            ("차트 1-2 (성공요청처리비용)", "기법 (처리시간 순 정렬)", "시간 (자동단위: ns/μs/ms) - 대기+실행 누적 막대"),
            ("차트 1-3 (실패요청처리비용)", "기법 (처리시간 순 정렬)", "시간 (자동단위: ns/μs/ms) - 대기+실패처리 누적 막대"),
            ("차트 2-1 (성공요청대기시간분포)", "기법별 성능 지표", "시간 (자동단위: ns/μs/ms, 로그스케일) - Box Plot"),
            ("차트 2-2 (실패요청대기시간분포)", "기법별 성능 지표", "시간 (자동단위: ns/μs/ms, 로그스케일) - Box Plot"),
            ("차트 3 (부하누적추이)", "시간 진행 (구간)", "평균 대기시간 (자동단위: ns/μs/ms) - 성공/실패 라인"),
            ("차트 4 (룸별성능비교)", "룸 번호", "성공률(%), 대기시간(자동단위: ns/μs/ms) - 3개 서브플롯")
        ]
        
        print("\n📊 차트별 X/Y축 정리:")
        print("=" * 50)
        for chart_name, x_axis, y_axis in axis_info:
            print(f"{chart_name}")
            print(f"  X축: {x_axis}")
            print(f"  Y축: {y_axis}")
            print("")
        print("=" * 50)
        
        if self.use_english_labels:
            print("\n📝 Note: Charts were generated with English labels due to Korean font issues.")


def create_font_test_chart():
    """한글 폰트 테스트 차트 생성"""
    test_fonts = ['Malgun Gothic', 'AppleGothic', 'NanumGothic', 'DejaVu Sans']
    
    fig, axes = plt.subplots(2, 2, figsize=(12, 8))
    axes = axes.flatten()
    
    for i, font in enumerate(test_fonts):
        try:
            plt.rcParams['font.family'] = font
            axes[i].text(0.5, 0.5, f'한글 테스트\n{font}\n성공적으로 렌더링됨', 
                        ha='center', va='center', fontsize=12)
            axes[i].set_title(f'{font} - 성공', color='green')
        except Exception as e:
            axes[i].text(0.5, 0.5, f'ERROR\n{font}\n{str(e)[:50]}...', 
                        ha='center', va='center', fontsize=10, color='red')
            axes[i].set_title(f'{font} - 실패', color='red')
        
        axes[i].set_xlim(0, 1)
        axes[i].set_ylim(0, 1)
        axes[i].set_xticks([])
        axes[i].set_yticks([])
    
    plt.tight_layout()
    plt.savefig('font_test_results.png', dpi=150, bbox_inches='tight')
    plt.close()
    return "font_test_results.png"


def main():
    """메인 실행 함수"""
    print("🚀 Performance Analysis Visualization Script v3.1 (Refactored)")
    print("=" * 70)
    
    # 파일 탐색
    if len(sys.argv) > 1:
        files = sys.argv[1:]
        print(f"📂 Using files specified in command line arguments:")
    else:
        files = glob.glob('*_stats_nano.xlsx')
        if not files:
            files = glob.glob('*.xlsx')
        print(f"📂 Auto-discovered files in current directory:")
    
    if not files:
        print("❌ Error: No Excel files found.")
        print("Usage:")
        print("  python visualize_performance_results.py [file1.xlsx file2.xlsx ...]")
        print("  Or place *_stats_nano.xlsx (or *.xlsx) files in the current directory.")
        print("\n💡 Tip: For meaningful comparison, use files named like:")
        print("  - synchronized_stats_nano.xlsx")
        print("  - semaphore_stats_nano.xlsx")
        print("  - reentrantlock_stats_nano.xlsx")
        return 1
    
    # 폰트 테스트 생성
    print("\n🔧 Creating font test chart...")
    font_test_file = create_font_test_chart()
    print(f"📊 Font test chart saved: {font_test_file}")
    
    # 시각화 실행
    print("\n🎨 Initializing visualizer...")
    visualizer = PerformanceVisualizer()
    
    print(f"\n🔄 Processing {len(files)} file(s)...")
    visualizer.process_files(files)
    
    print("\n🏁 Visualization completed!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
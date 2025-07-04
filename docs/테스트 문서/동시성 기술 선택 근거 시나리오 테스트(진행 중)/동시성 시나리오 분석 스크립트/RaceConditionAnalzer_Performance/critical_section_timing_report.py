import pandas as pd

# 입력 파일
df = pd.read_csv('critical_section_marks.csv', parse_dates=['timestamp'])

timing_result = []
grouped = df.groupby(['roomNumber', 'userId'])
for (room, user), group in grouped:
    mark = {}
    for tag in ['WAITING_START', 'CRITICAL_ENTER', 'CRITICAL_LEAVE']:
        sub = group[group['tag'] == tag]
        if not sub.empty:
            mark[tag] = sub.iloc[0]['timestamp']
    if set(mark) == {'WAITING_START', 'CRITICAL_ENTER', 'CRITICAL_LEAVE'}:
        t_wait = pd.to_datetime(mark['WAITING_START'])
        t_enter = pd.to_datetime(mark['CRITICAL_ENTER'])
        t_leave = pd.to_datetime(mark['CRITICAL_LEAVE'])
        timing_result.append({
            'roomNumber': room,
            'userId': user,
            'waiting_ms': (t_enter - t_wait).total_seconds() * 1000,
            'critical_section_ms': (t_leave - t_enter).total_seconds() * 1000,
            'total_ms': (t_leave - t_wait).total_seconds() * 1000,
            't_wait': t_wait,
            't_enter': t_enter,
            't_leave': t_leave
        })

timing_stats = pd.DataFrame(timing_result)
# WAITING_START 시점 오름차순 정렬
timing_stats = timing_stats.sort_values('t_wait')
timing_stats.to_csv('critical_section_timing_stats.csv', index=False)

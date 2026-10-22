import { CourseSchedule, WEEKDAY_NAMES, WeekRule } from './types';

export function renderWeekRule(rule: WeekRule): string {
  const range =
    rule.startWeek === rule.endWeek
      ? `${rule.startWeek}`
      : `${rule.startWeek}-${rule.endWeek}`;
  const parity =
    rule.parity === 'ODD' ? ' (单周)' : rule.parity === 'EVEN' ? ' (双周)' : '';
  return `${range}周${parity}`;
}

export function formatTimetableToText(courses: CourseSchedule[]): string {
  const sorted = [...courses].sort((a, b) => {
    const minA = a.meetings.reduce(
      (acc, m) => Math.min(acc, m.weekday * 100 + m.startSection),
      9999
    );
    const minB = b.meetings.reduce(
      (acc, m) => Math.min(acc, m.weekday * 100 + m.startSection),
      9999
    );
    return minA - minB;
  });

  if (sorted.length === 0) {
    return '暂无课程数据。';
  }

  const lines: string[] = [];
  lines.push(`吉林大学课表 (共 ${sorted.length} 门课程)`);
  lines.push(`导出时间: ${new Date().toLocaleDateString()}`);
  lines.push('----------------------------------------');

  sorted.forEach((course, index) => {
    lines.push('');
    lines.push(
      `${index + 1}. ${course.courseName} | ${course.teacher || '教师未指定'} | ${course.semester || '当前学期'}`
    );
    lines.push(`   学分: ${course.credit !== null ? course.credit : '暂无'}`);

    if (course.meetings.length === 0) {
      lines.push('   上课时间: 未安排');
    } else {
      course.meetings.forEach((meeting) => {
        const dayName = WEEKDAY_NAMES[meeting.weekday - 1] || `周${meeting.weekday}`;
        const weekText =
          meeting.weekRules.length === 0
            ? course.rawWeekText || '周次未知'
            : meeting.weekRules.map(renderWeekRule).join(', ');
        const loc = meeting.location || '地点待定';
        lines.push(
          `   - ${dayName} 第 ${meeting.startSection}-${meeting.endSection} 节 | ${weekText} | ${loc}`
        );
      });
    }
  });

  return lines.join('\n');
}

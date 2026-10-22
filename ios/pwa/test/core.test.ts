import { describe, it, expect, beforeEach } from 'vitest';
import { SimpleDate } from '../src/core/SimpleDate';
import { parseSchedule, parseWeekRules } from '../src/core/DoScheduleParser';
import {
  guessCurrentWeek,
  totalWeeks,
  displayMeetings,
  resolveOverlaps,
} from '../src/core/WeekScheduleCalculator';
import {
  defaultForToday,
  normalizeToWeekStart,
  inferFromSemesterLabelOrNull,
} from '../src/core/SemesterStartDatePolicy';
import {
  encodeBackup,
  decodeBackup,
  courseToBackup,
  backupToCourse,
} from '../src/core/ScheduleBackupCodec';
import { ProfileStore } from '../src/core/ProfileStore';

function scheduleJson(rows: Record<string, unknown>[]) {
  return JSON.stringify({ datas: { xskcb: { rows } } });
}

describe('DoScheduleParser', () => {
  it('accepts numeric fields as strings', () => {
    const courses = parseSchedule(
      scheduleJson([
        {
          KCM: '高等数学',
          SKJS: '张三',
          XNXQDM: '2026-2027-1',
          XF: '3.5',
          ZCMC: '1-16周',
          SKXQ: '1',
          KSJC: '3',
          JSJC: '4',
          JASMC: '逸夫楼A101',
        },
      ])
    );
    expect(courses).toHaveLength(1);
    expect(courses[0].courseName).toBe('高等数学');
    expect(courses[0].credit).toBe(3.5);
    expect(courses[0].meetings[0].weekday).toBe(1);
    expect(courses[0].meetings[0].startSection).toBe(3);
    expect(courses[0].meetings[0].endSection).toBe(4);
    expect(courses[0].meetings[0].location).toBe('逸夫楼A101');
  });

  it('splits multiple segments and parity', () => {
    const courses = parseSchedule(
      scheduleJson([
        {
          KCM: '大学物理',
          SKJS: '李四',
          XNXQDM_DISPLAY: '2026-2027学年第一学期',
          XF: 2,
          YPSJDD:
            '1-8周(单) 星期二 第1节-第2节 逸夫楼B201,9-16周(双) 星期四 第7节-第8节 中心校区C301',
          ZCMC: '1-16周',
          SKXQ: 2,
          KSJC: 1,
          JSJC: 2,
        },
      ])
    );
    expect(courses[0].semester).toBe('2026-2027学年第一学期');
    expect(courses[0].meetings).toHaveLength(2);
    expect(courses[0].meetings[0].weekday).toBe(2);
    expect(courses[0].meetings[0].weekRules[0].parity).toBe('ODD');
    expect(courses[0].meetings[1].weekday).toBe(4);
    expect(courses[0].meetings[1].weekRules[0].parity).toBe('EVEN');
  });

  it('supports discrete week list', () => {
    const courses = parseSchedule(
      scheduleJson([
        { KCM: '体育', YPSJDD: '第1,3,5,7周 星期三 第3节-第4节 田径场' },
      ])
    );
    const rules = courses[0].meetings[0].weekRules;
    expect(rules.map((r) => r.startWeek)).toEqual([1, 3, 5, 7]);
    expect(rules.map((r) => r.endWeek)).toEqual([1, 3, 5, 7]);
  });

  it('supports full-width parity with discrete weeks', () => {
    const courses = parseSchedule(
      scheduleJson([
        { KCM: '英语', YPSJDD: '第1,3,5周（单） 星期一 第1节-第2节 外语楼' },
      ])
    );
    const rules = courses[0].meetings[0].weekRules;
    expect(rules[0].parity).toBe('ODD');
  });
});

describe('WeekScheduleCalculator & SimpleDate', () => {
  it('guesses current week correctly', () => {
    const start = new SimpleDate(2026, 9, 1); // Tuesday -> previous monday 2026-08-31
    const monday = start.previousOrSameMonday();
    const today = monday.adding(14); // Exactly 2 weeks later -> week 3
    expect(guessCurrentWeek(monday, today, 20)).toBe(3);
  });

  it('calculates total weeks', () => {
    const courses = parseSchedule(
      scheduleJson([
        { KCM: 'A', YPSJDD: '1-18周 星期一 第1-2节 教室' },
        { KCM: 'B', YPSJDD: '1-8周 星期二 第1-2节 教室' },
      ])
    );
    expect(totalWeeks(courses)).toBe(18);
  });

  it('resolves overlaps favoring current week courses', () => {
    const courses = parseSchedule(
      scheduleJson([
        { KCM: '当前周课', YPSJDD: '1-10周 星期一 第1-2节 教室A' },
        { KCM: '非本周课', YPSJDD: '11-16周 星期一 第1-2节 教室B' },
      ])
    );
    const displayed = displayMeetings(courses, 2, true);
    const resolved = resolveOverlaps(displayed);
    expect(resolved).toHaveLength(1);
    expect(resolved[0].course.courseName).toBe('当前周课');
  });
});

describe('SemesterStartDatePolicy', () => {
  it('infers semester start date from labels', () => {
    const date1 = inferFromSemesterLabelOrNull('2026-2027学年第一学期');
    expect(date1).not.toBeNull();
    expect(date1?.weekdayMonBased()).toBe(1);

    const date2 = inferFromSemesterLabelOrNull('2026-2027-2');
    expect(date2).not.toBeNull();
    expect(date2?.weekdayMonBased()).toBe(1);

    const def = defaultForToday(new SimpleDate(2026, 9, 15));
    expect(def.weekdayMonBased()).toBe(1);

    const monday = normalizeToWeekStart(new SimpleDate(2026, 9, 3));
    expect(monday.weekdayMonBased()).toBe(1);

    const rules = parseWeekRules('1-16周(单)');
    expect(rules[0].parity).toBe('ODD');
  });
});

describe('ScheduleBackupCodec', () => {
  it('roundtrips backup format version 1', () => {
    const course = parseSchedule(
      scheduleJson([
        { KCM: '测试课', SKJS: '李老师', YPSJDD: '1-16周 星期三 第3-4节 基础楼' },
      ])
    )[0];
    const backup = courseToBackup(course);
    const restored = backupToCourse(backup);
    expect(restored.courseName).toBe('测试课');
    expect(restored.meetings[0].weekday).toBe(3);

    const json = encodeBackup('2026-10-01T00:00:00Z', [
      { name: '默认课表', semesterStartDate: '2026-08-31', courses: [backup] },
    ]);
    const decoded = decodeBackup(json);
    expect(decoded).toHaveLength(1);
    expect(decoded![0].name).toBe('默认课表');
    expect(decoded![0].courses).toHaveLength(1);
  });
});

describe('ProfileStore with mock localStorage', () => {
  const store: Record<string, string> = {};
  beforeEach(() => {
    globalThis.localStorage = {
      getItem: (k: string) => store[k] ?? null,
      setItem: (k: string, v: string) => {
        store[k] = v;
      },
      removeItem: (k: string) => {
        delete store[k];
      },
      clear: () => {
        Object.keys(store).forEach((k) => delete store[k]);
      },
      length: Object.keys(store).length,
      key: (i: number) => Object.keys(store)[i] ?? null,
    };
  });

  it('manages profiles and courses correctly', () => {
    const ps = ProfileStore.getInstance();
    const profiles = ps.listProfiles();
    expect(profiles.length).toBeGreaterThanOrEqual(1);

    const sampleCourses = parseSchedule(
      scheduleJson([
        { KCM: '算法设计', SKJS: '王老师', YPSJDD: '1-8周 星期四 第5-6节 计算机楼' },
      ])
    );
    const newId = ps.createProfile('第二学期课表', sampleCourses);
    expect(ps.listProfiles()).toHaveLength(2);

    const active = ps.getActiveProfile();
    expect(active.meta.id).toBe(newId);
    expect(active.courses).toHaveLength(1);
    expect(active.courses[0].courseName).toBe('算法设计');
  });
});

import {
  gradePointFor,
  calculateGpa,
  mergeImportedGrades,
  GpaCourse,
} from '../src/core/GpaCalculator';
import {
  isLikelyGradePayload,
  parseGradeTranscript,
} from '../src/core/GradeTranscriptParser';
import { GpaStore } from '../src/core/GpaStore';
import {
  isLikelyExamPayload,
  parseExamSchedule,
} from '../src/core/ExamScheduleParser';
import { ExamStore } from '../src/core/ExamStore';
import {
  isLikelyPyfaPayload,
  parseAcademicProgress,
} from '../src/core/PyfaTranscriptParser';
import { formatTimetableToText } from '../src/core/TimetableFormatter';

describe('GpaCalculator', () => {
  it('correctly maps scores to JLU 4.0 grade points', () => {
    expect(gradePointFor(95)).toBe(4.0);
    expect(gradePointFor(90)).toBe(4.0);
    expect(gradePointFor(88)).toBe(3.7);
    expect(gradePointFor(85)).toBe(3.3);
    expect(gradePointFor(81)).toBe(3.0);
    expect(gradePointFor(78)).toBe(2.7);
    expect(gradePointFor(75)).toBe(2.3);
    expect(gradePointFor(71)).toBe(2.0);
    expect(gradePointFor(68)).toBe(1.7);
    expect(gradePointFor(65)).toBe(1.3);
    expect(gradePointFor(61)).toBe(1.0);
    expect(gradePointFor(59.9)).toBe(0.0);
  });

  it('calculates weighted GPA, arithmetic average and weighted score', () => {
    const courses: GpaCourse[] = [
      { id: '1', name: '数学', gradeType: 'PERCENT', score: 90, level: '', credit: 4.0, included: true },
      { id: '2', name: '英语', gradeType: 'PERCENT', score: 80, level: '', credit: 2.0, included: true },
      { id: '3', name: '体育', gradeType: 'LEVEL5', score: 0, level: '优秀', credit: 1.0, included: true }, // 95 -> 4.0
    ];
    const summary = calculateGpa(courses);
    expect(summary.includedCount).toBe(3);
    expect(summary.includedCredits).toBe(7.0);

    // Weighted GPA: (4.0*4 + 3.0*2 + 4.0*1) / 7 = (16 + 6 + 4) / 7 = 26 / 7 ≈ 3.714
    expect(summary.recommendationGpa).toBe(3.714);

    // Weighted Score: (90*4 + 80*2 + 95*1) / 7 = (360 + 160 + 95) / 7 = 615 / 7 ≈ 87.86
    expect(summary.weightedAverage).toBe(87.86);

    // Arithmetic Score: (90 + 80 + 95) / 3 = 265 / 3 ≈ 88.33
    expect(summary.arithmeticAverage).toBe(88.33);
  });

  it('deduplicates retake courses keeping the highest score in mergeImportedGrades', () => {
    const grades = [
      { courseCode: 'CS101', name: '程序设计基础', credit: 3.0, scoreText: '58', semesterCode: '2024-2025-1' },
      { courseCode: 'CS101', name: '程序设计基础', credit: 3.0, scoreText: '88', semesterCode: '2024-2025-2' },
    ];
    const merged = mergeImportedGrades(grades);
    expect(merged).toHaveLength(1);
    expect(merged[0].score).toBe(88);
  });
});

describe('GradeTranscriptParser & GpaStore', () => {
  it('identifies and parses xscjcx payload', () => {
    const json = JSON.stringify({
      datas: {
        xscjcx: {
          rows: [
            { KCH: 'MATH001', KCM: '高等数学I', XF: '5.0', ZCJ: '92', XNXQDM: '2025-2026-1' },
            { KCH: 'ENG001', KCM: '大学英语I', XF: '3.0', DJCJMC: '良好', XNXQDM: '2025-2026-1' },
          ],
        },
      },
    });

    expect(isLikelyGradePayload(json)).toBe(true);
    const parsed = parseGradeTranscript(json);
    expect(parsed).toHaveLength(2);
    expect(parsed[0].name).toBe('高等数学I');
    expect(parsed[0].credit).toBe(5.0);
    expect(parsed[0].scoreText).toBe('92');
    expect(parsed[1].scoreText).toBe('良好');

    const count = GpaStore.getInstance().importFromTranscript(json);
    expect(count).toBe(2);
    const stored = GpaStore.getInstance().getCourses();
    expect(stored.length).toBeGreaterThanOrEqual(2);
  });
});

describe('ExamScheduleParser & ExamStore', () => {
  it('identifies and parses exam schedule payload', () => {
    const json = JSON.stringify({
      datas: {
        xskscx: {
          rows: [
            {
              KCM: '离散数学',
              KCH: 'CS201',
              KSSJMS: '2026-12-25 09:00',
              JASMC: '逸夫楼阶梯一',
              ZWH: '45',
              KSMC: '期末统考',
            },
          ],
        },
      },
    });

    expect(isLikelyExamPayload(json)).toBe(true);
    const exams = parseExamSchedule(json);
    expect(exams).toHaveLength(1);
    expect(exams[0].courseName).toBe('离散数学');
    expect(exams[0].location).toBe('逸夫楼阶梯一');
    expect(exams[0].seatNumber).toBe('45号');
    expect(exams[0].timestamp).toBeGreaterThan(0);

    const count = ExamStore.getInstance().importFromJson(json);
    expect(count).toBe(1);
    expect(ExamStore.getInstance().getExams()).toHaveLength(1);
  });
});

describe('PyfaTranscriptParser', () => {
  it('parses academic progress modules and credit totals', () => {
    const json = JSON.stringify({
      totalRequired: 160,
      totalEarned: 110,
      datas: {
        xsgzywcqk: {
          rows: [
            { FAJDMC: '通识教育必修课程', YQJDXF: 35.0, YHDXF: 35.0 },
            { FAJDMC: '学科专业必修课程', YQJDXF: 60.0, YHDXF: 50.0 },
            { FAJDMC: '专业拓展选修课程', YQJDXF: 25.0, YHDXF: 15.0 },
          ],
        },
      },
    });

    expect(isLikelyPyfaPayload(json)).toBe(true);
    const plan = parseAcademicProgress(json);
    expect(plan).not.toBeNull();
    expect(plan?.totalRequiredCredits).toBe(160);
    expect(plan?.totalEarnedCredits).toBe(110);
    expect(plan?.requirements).toHaveLength(3);
    expect(plan?.requirements[1].categoryName).toBe('学科专业必修课程');
    expect(plan?.requirements[1].earnedCredits).toBe(50.0);
  });
});

describe('TimetableFormatter', () => {
  it('formats schedule into clean text export', () => {
    const courses = parseSchedule(
      scheduleJson([
        {
          KCM: '操作系统',
          SKJS: '陈老师',
          XF: 3.5,
          YPSJDD: '1-16周 星期一 第1节-第2节 经信教学楼F1',
        },
      ])
    );

    const text = formatTimetableToText(courses);
    expect(text).toContain('吉林大学课表 (共 1 门课程)');
    expect(text).toContain('操作系统 | 陈老师');
    expect(text).toContain('学分: 3.5');
    expect(text).toContain('周一 第 1-2 节');
    expect(text).toContain('经信教学楼F1');
  });
});


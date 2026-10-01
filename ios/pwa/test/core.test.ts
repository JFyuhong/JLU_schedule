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

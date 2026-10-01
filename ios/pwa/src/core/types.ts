export const WEEK_MONDAY = 1;
export const WEEK_SUNDAY = 7;
export const PARITY_ALL = 'ALL';
export const PARITY_ODD = 'ODD';
export const PARITY_EVEN = 'EVEN';
export const DEFAULT_TOTAL_WEEKS = 20;
export const MAX_WEEK = 30;

export interface WeekRule {
  startWeek: number;
  endWeek: number;
  parity: string;
}

export interface MeetingTime {
  weekday: number;
  startSection: number;
  endSection: number;
  weekRules: WeekRule[];
  location: string;
}

export interface CourseSchedule {
  courseName: string;
  teacher: string;
  semester: string;
  credit: number | null;
  rawWeekText: string;
  meetings: MeetingTime[];
}

export interface CourseMeetingRef {
  courseIndex: number;
  course: CourseSchedule;
  meeting: MeetingTime;
  isCurrentWeek: boolean;
  nextActiveWeek: number;
}

export interface BackupWeekRule {
  startWeek: number;
  endWeek: number;
  parity: string;
}

export interface BackupMeeting {
  weekday: string;
  startSection: number;
  endSection: number;
  weekRules: BackupWeekRule[];
  location: string;
}

export interface BackupCourse {
  courseName: string;
  teacher: string;
  semester: string;
  credit: number | null;
  rawWeekText: string;
  meetings: BackupMeeting[];
}

export interface BackupProfile {
  name: string;
  semesterStartDate: string | null;
  courses: BackupCourse[];
}

export interface BackupFile {
  version: number;
  exportedAt: string;
  profiles: BackupProfile[];
}

export interface ProfileInfo {
  id: string;
  name: string;
  isActive: boolean;
  updatedAt: number;
  semesterStartDate: string | null;
  courseCount: number;
}

export interface StoredProfileMeta {
  id: string;
  name: string;
  createdAt: number;
  updatedAt: number;
  semesterStartDate?: string | null;
}

export interface StoredMeta {
  activeId: string;
  profiles: StoredProfileMeta[];
}

export type ThemeType = 'warm' | 'blue' | 'mint';
export type DarkModeType = 'system' | 'light' | 'dark';
export type FontSize = 'compact' | 'normal' | 'large';
export type DefaultTab = 'timetable' | 'today';

export interface AppPreferences {
  theme: ThemeType;
  darkMode: DarkModeType;
  showNonCurrentWeek: boolean;
  totalWeeks: number;
  fontSize: FontSize;
  defaultTab: DefaultTab;
}

export const SECTION_RANGES: string[] = [
  '08:00-08:45', '08:45-09:40', '10:00-10:45', '10:45-11:40',
  '13:30-14:15', '14:15-15:10', '15:30-16:15', '16:15-17:10',
  '18:20-19:05', '19:05-19:50', '20:00-20:45', '20:45-21:30',
];

export const WEEKDAY_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

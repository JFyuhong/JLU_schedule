import {
  BackupCourse,
  BackupFile,
  BackupProfile,
  CourseSchedule,
} from './types';

export function courseToBackup(course: CourseSchedule): BackupCourse {
  return {
    courseName: course.courseName,
    teacher: course.teacher,
    semester: course.semester,
    credit: course.credit,
    rawWeekText: course.rawWeekText,
    meetings: course.meetings.map((m) => ({
      weekday: String(m.weekday),
      startSection: m.startSection,
      endSection: m.endSection,
      weekRules: m.weekRules.map((r) => ({
        startWeek: r.startWeek,
        endWeek: r.endWeek,
        parity: r.parity,
      })),
      location: m.location,
    })),
  };
}

export function backupToCourse(backup: BackupCourse): CourseSchedule {
  return {
    courseName: backup.courseName,
    teacher: backup.teacher,
    semester: backup.semester,
    credit: backup.credit,
    rawWeekText: backup.rawWeekText,
    meetings: (backup.meetings ?? []).map((m) => ({
      weekday: Number(m.weekday) || 1,
      startSection: m.startSection,
      endSection: m.endSection,
      weekRules: (m.weekRules ?? []).map((r) => ({
        startWeek: r.startWeek,
        endWeek: r.endWeek,
        parity: r.parity,
      })),
      location: m.location,
    })),
  };
}

export function encodeBackup(exportedAt: string, profiles: BackupProfile[]): string {
  const file: BackupFile = {
    version: 1,
    exportedAt,
    profiles,
  };
  return JSON.stringify(file, null, 2);
}

export function decodeBackup(content: string): BackupProfile[] | null {
  try {
    const file = JSON.parse(content) as BackupFile;
    if (file.version !== 1 || !Array.isArray(file.profiles)) return null;
    return file.profiles;
  } catch {
    return null;
  }
}

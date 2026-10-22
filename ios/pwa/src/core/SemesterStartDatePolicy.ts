import { SimpleDate } from './SimpleDate';
import { CourseSchedule } from './types';

const codePattern = /(20\d{2})\D+(20\d{2})\D*([12])/;
const yearRangePattern = /(20\d{2})\D+(20\d{2})/;

export function normalizedSemesterKey(label: string): string | null {
  const normalized = label.trim();
  if (normalized.length === 0) return null;

  const codeMatch = codePattern.exec(normalized);
  if (codeMatch) {
    return `${codeMatch[1]}-${codeMatch[2]}-${codeMatch[3]}`;
  }
  const rangeMatch = yearRangePattern.exec(normalized);
  if (rangeMatch) {
    let term: string | null = null;
    if (normalized.includes('第一') || normalized.includes('第1') || normalized.includes('秋')) term = '1';
    else if (normalized.includes('第二') || normalized.includes('第2') || normalized.includes('春')) term = '2';
    if (term !== null) return `${rangeMatch[1]}-${rangeMatch[2]}-${term}`;
  }
  return null;
}

function mondayOnOrBefore(year: number, month: number, day: number): SimpleDate {
  return new SimpleDate(year, month, day).previousOrSameMonday();
}

export function inferFromSemesterLabelOrNull(label: string): SimpleDate | null {
  const key = normalizedSemesterKey(label);
  if (key === null) return null;
  const parts = key.split('-');
  if (parts.length !== 3) return null;
  const firstYear = Number(parts[0]);
  const secondYear = Number(parts[1]);
  const term = parts[2];
  if (term === '1') return mondayOnOrBefore(firstYear, 9, 1);
  if (term === '2') return mondayOnOrBefore(secondYear, 2, 24);
  return null;
}

export function inferFromCoursesOrNull(courses: CourseSchedule[]): SimpleDate | null {
  const counts = new Map<string, number>();
  for (const course of courses) {
    const label = course.semester.trim();
    if (label.length === 0) continue;
    counts.set(label, (counts.get(label) ?? 0) + 1);
  }
  let dominant: string | null = null;
  let best = 0;
  for (const label of counts.keys()) {
    const count = counts.get(label) ?? 0;
    if (count > best) {
      best = count;
      dominant = label;
    }
  }
  if (dominant === null) return null;
  return inferFromSemesterLabelOrNull(dominant);
}

export function defaultForToday(today: SimpleDate = SimpleDate.today()): SimpleDate {
  if (today.month >= 8) {
    return mondayOnOrBefore(today.year, 9, 1);
  }
  return mondayOnOrBefore(today.year, 2, 24);
}

export function inferFromCourses(courses: CourseSchedule[], fallback?: SimpleDate): SimpleDate {
  return inferFromCoursesOrNull(courses) ?? fallback ?? defaultForToday();
}

export function normalizeToWeekStart(date: SimpleDate): SimpleDate {
  return date.previousOrSameMonday();
}

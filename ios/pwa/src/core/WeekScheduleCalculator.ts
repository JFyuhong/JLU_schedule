import { SimpleDate } from './SimpleDate';
import {
  CourseMeetingRef,
  CourseSchedule,
  DEFAULT_TOTAL_WEEKS,
  MeetingTime,
  PARITY_EVEN,
  PARITY_ODD,
} from './types';

export function totalWeeks(courses: CourseSchedule[]): number {
  let maxWeek: number | null = null;
  for (const course of courses) {
    for (const meeting of course.meetings) {
      for (const rule of meeting.weekRules) {
        if (maxWeek === null || rule.endWeek > maxWeek) {
          maxWeek = rule.endWeek;
        }
      }
    }
  }
  return Math.max(maxWeek ?? DEFAULT_TOTAL_WEEKS, 1);
}

export function guessCurrentWeek(semesterStart: SimpleDate, today: SimpleDate, weeks: number): number {
  const days = today.days(semesterStart);
  const week = Math.floor(days / 7) + 1;
  return Math.min(Math.max(week, 1), Math.max(weeks, 1));
}

export function isMeetingActiveInWeek(meeting: MeetingTime, week: number): boolean {
  if (meeting.weekRules.length === 0) return true;
  return meeting.weekRules.some((rule) => {
    if (week < rule.startWeek || week > rule.endWeek) return false;
    if (rule.parity === PARITY_ODD) return week % 2 === 1;
    if (rule.parity === PARITY_EVEN) return week % 2 === 0;
    return true;
  });
}

export function nextActiveWeekOf(meeting: MeetingTime, fromWeek: number): number | null {
  if (meeting.weekRules.length === 0) return fromWeek;
  let maxEnd: number | null = null;
  for (const rule of meeting.weekRules) {
    if (maxEnd === null || rule.endWeek > maxEnd) maxEnd = rule.endWeek;
  }
  if (maxEnd === null || fromWeek > maxEnd) return null;
  for (let w = fromWeek; w <= maxEnd; w++) {
    if (isMeetingActiveInWeek(meeting, w)) return w;
  }
  return null;
}

export function displayMeetings(
  courses: CourseSchedule[],
  week: number,
  showNonCurrent: boolean
): CourseMeetingRef[] {
  const result: CourseMeetingRef[] = [];
  courses.forEach((course, index) => {
    for (const meeting of course.meetings) {
      if (isMeetingActiveInWeek(meeting, week)) {
        result.push({
          courseIndex: index,
          course,
          meeting,
          isCurrentWeek: true,
          nextActiveWeek: week,
        });
        continue;
      }
      if (!showNonCurrent) continue;
      const next = nextActiveWeekOf(meeting, week);
      if (next === null) continue;
      result.push({
        courseIndex: index,
        course,
        meeting,
        isCurrentWeek: false,
        nextActiveWeek: next,
      });
    }
  });
  return result;
}

export function resolveOverlaps(items: CourseMeetingRef[]): CourseMeetingRef[] {
  const occupied = new Array<boolean>(13).fill(false);
  const sorted = [...items].sort((a, b) => {
    if (a.isCurrentWeek !== b.isCurrentWeek) return a.isCurrentWeek ? -1 : 1;
    if (a.nextActiveWeek !== b.nextActiveWeek) return a.nextActiveWeek - b.nextActiveWeek;
    return a.meeting.startSection - b.meeting.startSection;
  });
  const selected: CourseMeetingRef[] = [];
  for (const item of sorted) {
    const start = Math.min(Math.max(item.meeting.startSection, 1), 12);
    const end = Math.min(Math.max(item.meeting.endSection, 1), 12);
    let overlap = false;
    for (let s = start; s <= end; s++) {
      if (occupied[s]) {
        overlap = true;
        break;
      }
    }
    if (overlap) continue;
    selected.push(item);
    for (let s = start; s <= end; s++) occupied[s] = true;
  }
  return selected.sort((a, b) => a.meeting.startSection - b.meeting.startSection);
}

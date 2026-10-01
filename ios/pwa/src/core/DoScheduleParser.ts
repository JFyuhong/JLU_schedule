import {
  CourseSchedule,
  MeetingTime,
  WeekRule,
  PARITY_ALL,
  PARITY_EVEN,
  PARITY_ODD,
  DEFAULT_TOTAL_WEEKS,
  MAX_WEEK,
} from './types';

const segmentRegex = /^\s*(.*?)\s*星期([一二三四五六日天])\s*第\s*(\d+)\s*节?\s*[-—–~～至]\s*第?\s*(\d+)\s*节?\s*(.*)$/;
const weekItemRegex = /第?(\d+)\s*(?:-\s*(\d+))?\s*周(?:\s*[（(]\s*(单|双)\s*[)）]|\s*[单双])?/;
const discreteWeeksRegex = /^第?\s*(\d+(?:\s*[,，、]\s*\d+)+)\s*周?\s*(?:[（(]\s*(单|双)\s*[)）])?\s*周?$/;

function asText(value: unknown): string {
  if (value === null || value === undefined) return '';
  return String(value);
}

function asInt(value: unknown): number | null {
  if (value === null || value === undefined) return null;
  const n = Number(value);
  return Number.isFinite(n) ? Math.trunc(n) : null;
}

function asDouble(value: unknown): number | null {
  if (value === null || value === undefined) return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

export function parseWeekRules(weekExpr: string): WeekRule[] {
  const expr = weekExpr.trim();
  if (expr.length === 0) {
    return [{ startWeek: 1, endWeek: DEFAULT_TOTAL_WEEKS, parity: PARITY_ALL }];
  }
  let hasDigit = false;
  for (const ch of expr) {
    if (ch >= '0' && ch <= '9') {
      hasDigit = true;
      break;
    }
  }
  if (!hasDigit) {
    const parity = expr.includes('单') ? PARITY_ODD : (expr.includes('双') ? PARITY_EVEN : PARITY_ALL);
    return [{ startWeek: 1, endWeek: DEFAULT_TOTAL_WEEKS, parity }];
  }

  const discrete = discreteWeeksRegex.exec(expr);
  if (discrete) {
    const parity = discrete[2] === '单' ? PARITY_ODD : (discrete[2] === '双' ? PARITY_EVEN : PARITY_ALL);
    const weeks = discrete[1]
      .split(/[,，、]/)
      .map((s) => Number(s.trim()))
      .filter((n) => Number.isInteger(n) && n >= 1 && n <= MAX_WEEK);
    if (weeks.length > 0) {
      return weeks.map((w) => ({ startWeek: w, endWeek: w, parity }));
    }
  }

  const rules = expr
    .split(/[,，、;；]/)
    .map((part) => parseWeekItem(part))
    .filter((rule): rule is WeekRule => rule !== null);

  return rules.length === 0 ? [{ startWeek: 1, endWeek: DEFAULT_TOTAL_WEEKS, parity: PARITY_ALL }] : rules;
}

function parseWeekItem(part: string): WeekRule | null {
  const match = weekItemRegex.exec(part.trim());
  if (!match) return null;
  const start = Number(match[1]);
  const end = match[2] !== undefined ? Number(match[2]) : start;
  const parity = match[3] === '单' ? PARITY_ODD : (match[3] === '双' ? PARITY_EVEN : PARITY_ALL);
  if (!Number.isInteger(start) || !Number.isInteger(end)) return null;
  if (start < 1 || start > MAX_WEEK || end < 1 || end > MAX_WEEK) return null;
  return start <= end
    ? { startWeek: start, endWeek: end, parity }
    : { startWeek: end, endWeek: start, parity };
}

type RowMap = Record<string, unknown>;

function splitSegments(text: string): string[] {
  const pieces = text.split(/[,，;；\n]/).map((s) => s.trim());
  const result: string[] = [];
  let buffer = '';
  for (const piece of pieces) {
    if (buffer.length > 0) buffer += '，';
    buffer += piece;
    if (buffer.includes('星期') && buffer.includes('节')) {
      result.push(buffer.trim());
      buffer = '';
    }
  }
  if (buffer.length > 0) result.push(buffer.trim());
  return result.filter((s) => s.length > 0);
}

function weekdayFromChinese(value: string): number | null {
  switch (value) {
    case '一': return 1;
    case '二': return 2;
    case '三': return 3;
    case '四': return 4;
    case '五': return 5;
    case '六': return 6;
    case '日': case '天': return 7;
    default: return null;
  }
}

function parseSegment(segment: string, row: RowMap): MeetingTime | null {
  const match = segmentRegex.exec(segment);
  if (!match) return null;
  const weekday = weekdayFromChinese(match[2]);
  const start = Number(match[3]);
  const end = Number(match[4]);
  if (weekday === null || !Number.isInteger(start) || !Number.isInteger(end)) return null;
  let location = match[5].trim();
  if (location.length === 0) {
    location = asText(row['JASMC']).length > 0 ? asText(row['JASMC']) : asText(row['JXLDM_DISPLAY']);
  }
  return {
    weekday,
    startSection: Math.min(start, end),
    endSection: Math.max(start, end),
    weekRules: parseWeekRules(match[1].trim()),
    location,
  };
}

function fallbackMeeting(row: RowMap): MeetingTime | null {
  const weekdayNumber = asInt(row['SKXQ']);
  const start = asInt(row['KSJC']);
  const end = asInt(row['JSJC']);
  if (weekdayNumber === null || start === null || end === null) return null;
  if (weekdayNumber < 1 || weekdayNumber > 7) return null;
  const location = asText(row['JASMC']).length > 0 ? asText(row['JASMC']) : asText(row['JXLDM_DISPLAY']);
  return {
    weekday: weekdayNumber,
    startSection: Math.min(start, end),
    endSection: Math.max(start, end),
    weekRules: parseWeekRules(asText(row['ZCMC'])),
    location,
  };
}

function meetingsOfRow(row: RowMap): MeetingTime[] {
  const arranged = asText(row['YPSJDD']).trim();
  if (arranged.length === 0) {
    const fallback = fallbackMeeting(row);
    return fallback ? [fallback] : [];
  }
  const parsed = splitSegments(arranged)
    .map((segment) => parseSegment(segment, row))
    .filter((m): m is MeetingTime => m !== null);
  if (parsed.length === 0) {
    const fallback = fallbackMeeting(row);
    return fallback ? [fallback] : [];
  }
  return parsed;
}

export function parseSchedule(rawContent: string): CourseSchedule[] {
  let root: unknown;
  try {
    root = JSON.parse(rawContent);
  } catch {
    throw new Error('invalid json');
  }
  if (!root || typeof root !== 'object') return [];

  const holder = root as Record<string, unknown>;
  const datas = holder['datas'] as Record<string, Record<string, unknown>> | null | undefined;
  if (!datas) return [];

  const rows: RowMap[] = [];
  for (const key of Object.keys(datas)) {
    const table = datas[key];
    if (!table) continue;
    const tableRows = table['rows'];
    if (Array.isArray(tableRows)) {
      for (const row of tableRows) {
        if (row && typeof row === 'object') rows.push(row as RowMap);
      }
    }
  }

  const seen = new Set<string>();
  const unique: RowMap[] = [];
  for (const row of rows) {
    const key = [
      asText(row['KCM']),
      asText(row['SKJS']),
      asText(row['YPSJDD']),
      asText(row['ZCMC']),
      asText(row['SKXQ']),
      asText(row['KSJC']),
      asText(row['JSJC']),
      asText(row['JASMC']),
      asText(row['JXLDM_DISPLAY']),
    ].join('|');
    if (!seen.has(key)) {
      seen.add(key);
      unique.push(row);
    }
  }

  return unique.map((row) => {
    const display = asText(row['XNXQDM_DISPLAY']).trim();
    const semester = display.length > 0 ? display : asText(row['XNXQDM']).trim();
    return {
      courseName: asText(row['KCM']),
      teacher: asText(row['SKJS']),
      semester,
      credit: asDouble(row['XF']),
      rawWeekText: asText(row['ZCMC']),
      meetings: meetingsOfRow(row),
    };
  });
}

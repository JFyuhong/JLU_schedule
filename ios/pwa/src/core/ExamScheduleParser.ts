export interface ExamItem {
  id: string;
  courseName: string;
  courseCode: string;
  examTimeText: string;
  location: string;
  seatNumber: string;
  examType: string;
  timestamp: number;
  isCustom?: boolean;
}

export function isLikelyExamPayload(text: string): boolean {
  const hasRows = text.includes('"rows"') && text.includes('"datas"');
  const hasKeywords =
    text.includes('KCM') ||
    text.includes('KSSJ') ||
    text.includes('xskscx') ||
    text.includes('wdksap') ||
    text.includes('cxxsksap');
  return hasRows && hasKeywords;
}

function parseExamTimestamp(timeText: string): number {
  if (!timeText.trim()) return 0;
  // Match YYYY-MM-DD HH:mm or YYYY-MM-DD
  const match = /(\d{4})-(\d{2})-(\d{2})(?:\s+(\d{2}):(\d{2}))?/.exec(timeText);
  if (!match) return 0;
  const year = Number(match[1]);
  const month = Number(match[2]) - 1;
  const day = Number(match[3]);
  const hour = match[4] ? Number(match[4]) : 9;
  const minute = match[5] ? Number(match[5]) : 0;
  const d = new Date(year, month, day, hour, minute);
  return d.getTime();
}

export function parseExamSchedule(payload: string): ExamItem[] {
  try {
    const root = JSON.parse(payload);
    if (!root || typeof root !== 'object') return [];

    const datas = (root as Record<string, unknown>)['datas'];
    if (!datas || typeof datas !== 'object') return [];

    // Find first table object containing 'rows'
    let rows: unknown[] = [];
    for (const val of Object.values(datas)) {
      if (val && typeof val === 'object' && Array.isArray((val as Record<string, unknown>)['rows'])) {
        rows = (val as Record<string, unknown>)['rows'] as unknown[];
        break;
      }
    }

    if (rows.length === 0) return [];

    const result: ExamItem[] = [];
    for (const row of rows) {
      if (!row || typeof row !== 'object') continue;
      const r = row as Record<string, unknown>;

      const courseName =
        String(r['KCM'] ?? r['KCMC'] ?? r['courseName'] ?? '').trim();
      if (!courseName) continue;

      const courseCode = String(r['KCH'] ?? r['courseCode'] ?? '').trim();
      const timeText = String(
        r['KSSJMS'] ?? r['KSSJ'] ?? r['examTime'] ?? ''
      ).trim();
      const location = String(
        r['JASMC'] ?? r['KSCDMC'] ?? r['KSDD'] ?? r['location'] ?? ''
      ).trim();
      const rawSeat = String(r['ZWH'] ?? r['seatNumber'] ?? '').trim();
      const seatNumber = rawSeat ? (rawSeat.endsWith('号') ? rawSeat : `${rawSeat}号`) : '';
      const examType = String(
        r['KSMC'] ?? r['KSLX'] ?? r['examType'] ?? '期末考试'
      ).trim();

      const timestamp = parseExamTimestamp(timeText);

      result.push({
        id: `exam-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
        courseName,
        courseCode,
        examTimeText: timeText,
        location,
        seatNumber,
        examType,
        timestamp,
        isCustom: false,
      });
    }

    // Sort by timestamp ascending
    return result.sort((a, b) => {
      if (a.timestamp && b.timestamp) return a.timestamp - b.timestamp;
      return a.courseName.localeCompare(b.courseName);
    });
  } catch {
    return [];
  }
}

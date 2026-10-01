import { ImportedGrade } from './GpaCalculator';

export function isLikelyGradePayload(text: string): boolean {
  return text.includes('"xscjcx"') && text.includes('"rows"');
}

export function parseGradeTranscript(payload: string): ImportedGrade[] {
  try {
    const root = JSON.parse(payload);
    if (!root || typeof root !== 'object') return [];

    const datas = (root as Record<string, unknown>)['datas'];
    if (!datas || typeof datas !== 'object') return [];

    const xscjcx = (datas as Record<string, unknown>)['xscjcx'];
    if (!xscjcx || typeof xscjcx !== 'object') return [];

    const rows = (xscjcx as Record<string, unknown>)['rows'];
    if (!Array.isArray(rows)) return [];

    const result: ImportedGrade[] = [];
    for (const row of rows) {
      if (!row || typeof row !== 'object') continue;
      const r = row as Record<string, unknown>;

      const rawCredit = r['XF'];
      const credit = Number(rawCredit);
      if (!Number.isFinite(credit) || credit <= 0.0) continue;

      const score =
        (r['ZCJ'] !== undefined && r['ZCJ'] !== null ? String(r['ZCJ']).trim() : '') ||
        (r['XSZCJMC'] !== undefined && r['XSZCJMC'] !== null ? String(r['XSZCJMC']).trim() : '') ||
        (r['DJCJMC'] !== undefined && r['DJCJMC'] !== null ? String(r['DJCJMC']).trim() : '');

      if (!score) continue;

      result.push({
        courseCode: String(r['KCH'] ?? '').trim(),
        name: String(r['KCM'] ?? '').trim(),
        credit,
        scoreText: score,
        semesterCode: String(r['XNXQDM'] ?? '').trim(),
        isCustom: false,
      });
    }

    return result;
  } catch {
    return [];
  }
}

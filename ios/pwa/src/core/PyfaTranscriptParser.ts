export interface AcademicRequirement {
  categoryName: string;
  requiredCredits: number;
  earnedCredits: number;
}

export interface AcademicProgressPlan {
  totalRequiredCredits: number;
  totalEarnedCredits: number;
  requirements: AcademicRequirement[];
  lastUpdated: number;
}

const NAME_KEYS = [
  'FAJDMC', 'JDMC', 'KCLBMC', 'KCXZMC', 'MKMC', 'PYFAMC', 'MC', 'NAME', 'TITLE', 'name', 'categoryName',
];
const REQ_KEYS = [
  'YQJDXF', 'ZSYQXF', 'YQXF', 'ZXF', 'REQUIRED_CREDITS', 'REQUIRED', 'requiredCredits', 'required', 'yqxf', 'zxf',
];
const EARNED_KEYS = [
  'YHDXF', 'YWCXF', 'WCXF', 'HDXF', 'YXXF', 'PASSED_CREDITS', 'EARNED', 'earnedCredits', 'earned', 'yhdxf', 'hdxf', 'yxxf',
];

function findString(obj: Record<string, unknown>, keys: string[]): string | null {
  for (const k of keys) {
    if (obj[k] !== undefined && obj[k] !== null && String(obj[k]).trim() !== '') {
      return String(obj[k]).trim();
    }
  }
  return null;
}

function findDouble(obj: Record<string, unknown>, keys: string[]): number | null {
  for (const k of keys) {
    if (obj[k] !== undefined && obj[k] !== null) {
      const n = Number(obj[k]);
      if (Number.isFinite(n)) return n;
    }
  }
  return null;
}

export function isLikelyPyfaPayload(text: string): boolean {
  if (!text.trim()) return false;
  if (text.includes('"pyfa_dom_extract"') || text.includes('"pyfa"') || text.includes('"pyfaTree"')) return true;
  if (text.includes('"FAJDMC"') || text.includes('"YQJDXF"') || text.includes('"YHDXF"')) return true;
  if (
    text.includes('"xsgzywcqk"') ||
    text.includes('"xywcqk"') ||
    text.includes('"wdpyfa"') ||
    text.includes('"grpyfacx"') ||
    text.includes('"cxxsscfa"')
  ) {
    return true;
  }
  return (
    text.includes('"datas"') &&
    (text.includes('"YQXF"') ||
      text.includes('"ZSYQXF"') ||
      text.includes('"YWCXF"') ||
      text.includes('"WCXF"') ||
      text.includes('"HDXF"') ||
      text.includes('"YXXF"'))
  );
}

export function parseAcademicProgress(payload: string): AcademicProgressPlan | null {
  if (!isLikelyPyfaPayload(payload)) return null;
  try {
    const root = JSON.parse(payload) as Record<string, unknown>;
    if (!root || typeof root !== 'object') return null;

    let rows: unknown[] = [];
    if (Array.isArray(root['modules'])) {
      rows = root['modules'] as unknown[];
    } else if (root['datas'] && typeof root['datas'] === 'object') {
      const datas = root['datas'] as Record<string, unknown>;
      for (const val of Object.values(datas)) {
        if (val && typeof val === 'object') {
          const valObj = val as Record<string, unknown>;
          if (Array.isArray(valObj['rows'])) {
            rows = valObj['rows'] as unknown[];
            break;
          }
        }
      }
    }

    if (rows.length === 0) return null;

    const reqs: AcademicRequirement[] = [];
    let sumReq = 0;
    let sumEarned = 0;

    for (const item of rows) {
      if (!item || typeof item !== 'object') continue;
      const itemObj = item as Record<string, unknown>;
      const name = findString(itemObj, NAME_KEYS);
      if (!name) continue;

      const reqCredit = findDouble(itemObj, REQ_KEYS) ?? 0;
      const earnedCredit = findDouble(itemObj, EARNED_KEYS) ?? 0;
      if (reqCredit <= 0 && earnedCredit <= 0) continue;

      reqs.push({
        categoryName: name,
        requiredCredits: Math.round(reqCredit * 10) / 10,
        earnedCredits: Math.round(earnedCredit * 10) / 10,
      });
      sumReq += reqCredit;
      sumEarned += earnedCredit;
    }

    if (reqs.length === 0) return null;

    const explicitTotalReq = findDouble(root, ['totalRequired', 'totalRequiredCredits']);
    const explicitTotalEarned = findDouble(root, ['totalEarned', 'totalEarnedCredits']);

    return {
      totalRequiredCredits: Math.round((explicitTotalReq ?? sumReq) * 10) / 10,
      totalEarnedCredits: Math.round((explicitTotalEarned ?? sumEarned) * 10) / 10,
      requirements: reqs,
      lastUpdated: Date.now(),
    };
  } catch {
    return null;
  }
}

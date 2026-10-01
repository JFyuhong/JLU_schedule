export type GpaGradeType = 'PERCENT' | 'LEVEL5';

export interface GpaCourse {
  id: string;
  name: string;
  gradeType: GpaGradeType;
  /** 百分制分数（0-100），gradeType 为 PERCENT 时有效 */
  score: number;
  /** 五级制等级原文，gradeType 为 LEVEL5 时有效 */
  level: string;
  credit: number;
  /** 是否参与计算（用户可逐门排除） */
  included: boolean;
}

export interface ImportedGrade {
  courseCode: string;
  name: string;
  credit: number;
  scoreText: string;
  semesterCode: string;
  isCustom?: boolean;
}

export interface CourseBreakdown {
  course: GpaCourse;
  effectiveScore: number;
  gradePoint: number;
  included: boolean;
}

export interface GpaSummary {
  recommendationGpa: number | null;
  weightedAverage: number | null;
  arithmeticAverage: number | null;
  includedCount: number;
  includedCredits: number;
  breakdowns: CourseBreakdown[];
}

export const GRADE_POINT_BANDS: [number, number][] = [
  [90.0, 4.0],
  [87.0, 3.7],
  [84.0, 3.3],
  [80.0, 3.0],
  [77.0, 2.7],
  [74.0, 2.3],
  [70.0, 2.0],
  [67.0, 1.7],
  [64.0, 1.3],
  [60.0, 1.0],
];

export const LEVEL_SCORES: Record<string, number> = {
  '优秀': 95.0,
  '良好': 85.0,
  '中等': 75.0,
  '及格': 65.0,
  '不及格': 0.0,
};

export const LEVEL_ORDER = ['优秀', '良好', '中等', '及格', '不及格'];
export const MAX_SCORE = 100.0;
export const MIN_SCORE = 0.0;

export function gradePointFor(score: number): number {
  if (score < 60.0) return 0.0;
  for (const [minScore, gp] of GRADE_POINT_BANDS) {
    if (score >= minScore) return gp;
  }
  return 4.0;
}

export function effectiveScore(gradeType: GpaGradeType, score: number, level: string): number {
  if (gradeType === 'LEVEL5') {
    return LEVEL_SCORES[level] ?? 0.0;
  }
  return score;
}

export function isValidCourse(course: GpaCourse): boolean {
  if (course.credit <= 0.0 || !Number.isFinite(course.credit)) return false;
  if (course.gradeType === 'PERCENT') {
    return Number.isFinite(course.score) && course.score >= MIN_SCORE && course.score <= MAX_SCORE;
  }
  return course.level in LEVEL_SCORES;
}

export function calculateGpa(courses: GpaCourse[]): GpaSummary {
  const breakdowns: CourseBreakdown[] = courses.map((course) => {
    const valid = isValidCourse(course);
    const included = course.included && valid;
    const effective = valid ? effectiveScore(course.gradeType, course.score, course.level) : 0.0;
    return {
      course,
      effectiveScore: effective,
      gradePoint: gradePointFor(effective),
      included,
    };
  });

  const includedItems = breakdowns.filter((b) => b.included);
  if (includedItems.length === 0) {
    return {
      recommendationGpa: null,
      weightedAverage: null,
      arithmeticAverage: null,
      includedCount: 0,
      includedCredits: 0.0,
      breakdowns,
    };
  }

  const creditSum = includedItems.reduce((acc, cur) => acc + cur.course.credit, 0);
  const totalGpaWeighted = includedItems.reduce(
    (acc, cur) => acc + cur.gradePoint * cur.course.credit,
    0
  );
  const totalScoreWeighted = includedItems.reduce(
    (acc, cur) => acc + cur.effectiveScore * cur.course.credit,
    0
  );
  const totalScoreArithmetic = includedItems.reduce((acc, cur) => acc + cur.effectiveScore, 0);

  const gpa = creditSum > 0 ? totalGpaWeighted / creditSum : null;
  const weighted = creditSum > 0 ? totalScoreWeighted / creditSum : null;
  const arithmetic = totalScoreArithmetic / includedItems.length;

  return {
    recommendationGpa: gpa !== null ? Math.round(gpa * 1000) / 1000 : null,
    weightedAverage: weighted !== null ? Math.round(weighted * 100) / 100 : null,
    arithmeticAverage: Math.round(arithmetic * 100) / 100,
    includedCount: includedItems.length,
    includedCredits: Math.round(creditSum * 10) / 10,
    breakdowns,
  };
}

export function importedEffectiveScore(grade: ImportedGrade): number {
  const asNumber = Number(grade.scoreText);
  if (Number.isFinite(asNumber) && asNumber >= MIN_SCORE && asNumber <= MAX_SCORE) {
    return asNumber;
  }
  return LEVEL_SCORES[grade.scoreText] ?? -1.0;
}

export function mergeImportedGrades(grades: ImportedGrade[]): GpaCourse[] {
  const best = new Map<string, ImportedGrade>();
  for (const grade of grades) {
    const key = grade.courseCode.trim() || `${grade.name.trim()}|${grade.semesterCode.trim()}`;
    const existing = best.get(key);
    if (!existing || importedEffectiveScore(grade) > importedEffectiveScore(existing)) {
      best.set(key, grade);
    }
  }

  const result: GpaCourse[] = [];
  for (const grade of best.values()) {
    const asNumber = Number(grade.scoreText);
    const id = grade.courseCode.trim()
      ? `jw-${grade.courseCode.trim()}-${grade.semesterCode.trim()}`
      : `jw-${grade.name.trim()}-${grade.semesterCode.trim()}`;

    if (Number.isFinite(asNumber) && asNumber >= MIN_SCORE && asNumber <= MAX_SCORE) {
      result.push({
        id,
        name: grade.name.trim() || '未命名课程',
        gradeType: 'PERCENT',
        score: asNumber,
        level: '',
        credit: grade.credit,
        included: true,
      });
    } else if (grade.scoreText.trim() in LEVEL_SCORES) {
      result.push({
        id,
        name: grade.name.trim() || '未命名课程',
        gradeType: 'LEVEL5',
        score: 0,
        level: grade.scoreText.trim(),
        credit: grade.credit,
        included: true,
      });
    }
  }
  return result;
}

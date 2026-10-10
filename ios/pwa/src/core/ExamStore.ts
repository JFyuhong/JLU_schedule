import { ExamItem, parseExamSchedule } from './ExamScheduleParser';

const EXAM_STORAGE_KEY = 'jlu_schedule_exams';

export class ExamStore {
  private static instance: ExamStore | null = null;

  static getInstance(): ExamStore {
    if (!ExamStore.instance) {
      ExamStore.instance = new ExamStore();
    }
    return ExamStore.instance;
  }

  private constructor() {}

  getExams(): ExamItem[] {
    try {
      const raw = localStorage.getItem(EXAM_STORAGE_KEY);
      if (raw) {
        return JSON.parse(raw) as ExamItem[];
      }
    } catch {
      // Ignore
    }
    return [];
  }

  saveExams(exams: ExamItem[]): void {
    localStorage.setItem(EXAM_STORAGE_KEY, JSON.stringify(exams));
  }

  importFromJson(payload: string): number {
    const parsed = parseExamSchedule(payload);
    if (parsed.length === 0) return 0;
    const existing = this.getExams();
    const customExams = existing.filter((e) => e.isCustom);
    const combined = [...parsed, ...customExams];
    combined.sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0));
    this.saveExams(combined);
    return parsed.length;
  }

  addExam(exam: ExamItem): void {
    const exams = this.getExams();
    exams.push(exam);
    exams.sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0));
    this.saveExams(exams);
  }

  updateExam(id: string, updated: Partial<ExamItem>): void {
    const exams = this.getExams();
    const idx = exams.findIndex((e) => e.id === id);
    if (idx !== -1) {
      exams[idx] = { ...exams[idx], ...updated };
      exams.sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0));
      this.saveExams(exams);
    }
  }

  deleteExam(id: string): void {
    const exams = this.getExams().filter((e) => e.id !== id);
    this.saveExams(exams);
  }

  clearExams(): void {
    localStorage.removeItem(EXAM_STORAGE_KEY);
  }
}

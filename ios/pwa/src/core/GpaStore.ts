import { GpaCourse, mergeImportedGrades } from './GpaCalculator';
import { parseGradeTranscript } from './GradeTranscriptParser';

const GPA_STORAGE_KEY = 'jlu_schedule_gpa_courses';

export class GpaStore {
  private static instance: GpaStore | null = null;

  static getInstance(): GpaStore {
    if (!GpaStore.instance) {
      GpaStore.instance = new GpaStore();
    }
    return GpaStore.instance;
  }

  private constructor() {}

  getCourses(): GpaCourse[] {
    try {
      const raw = localStorage.getItem(GPA_STORAGE_KEY);
      if (raw) {
        return JSON.parse(raw) as GpaCourse[];
      }
    } catch {
      // Ignore
    }
    return [];
  }

  saveCourses(courses: GpaCourse[]): void {
    localStorage.setItem(GPA_STORAGE_KEY, JSON.stringify(courses));
  }

  importFromTranscript(payload: string): number {
    const rawGrades = parseGradeTranscript(payload);
    if (rawGrades.length === 0) return 0;
    const imported = mergeImportedGrades(rawGrades);
    const existing = this.getCourses();
    const customCourses = existing.filter((c) => !c.id.startsWith('jw-'));
    const combined = [...imported, ...customCourses];
    this.saveCourses(combined);
    return imported.length;
  }

  addCourse(course: GpaCourse): void {
    const courses = this.getCourses();
    courses.unshift(course);
    this.saveCourses(courses);
  }

  updateCourse(id: string, updated: Partial<GpaCourse>): void {
    const courses = this.getCourses();
    const idx = courses.findIndex((c) => c.id === id);
    if (idx !== -1) {
      courses[idx] = { ...courses[idx], ...updated };
      this.saveCourses(courses);
    }
  }

  deleteCourse(id: string): void {
    const courses = this.getCourses().filter((c) => c.id !== id);
    this.saveCourses(courses);
  }

  clearCourses(): void {
    localStorage.removeItem(GPA_STORAGE_KEY);
  }

  toggleAll(included: boolean): void {
    const courses = this.getCourses().map((c) => ({ ...c, included }));
    this.saveCourses(courses);
  }
}


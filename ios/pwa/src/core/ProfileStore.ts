import { SimpleDate } from './SimpleDate';
import { parseSchedule } from './DoScheduleParser';
import { defaultForToday, inferFromCourses, normalizeToWeekStart } from './SemesterStartDatePolicy';
import { backupToCourse, courseToBackup, decodeBackup, encodeBackup } from './ScheduleBackupCodec';
import {
  AppPreferences,
  BackupProfile,
  CourseSchedule,
  ProfileInfo,
  StoredMeta,
  StoredProfileMeta,
} from './types';

const META_STORAGE_KEY = 'jlu_schedule_meta';
const COURSES_STORAGE_PREFIX = 'jlu_schedule_courses_';
const PREFS_STORAGE_KEY = 'jlu_schedule_prefs';
const DEFAULT_PROFILE_NAME = '默认课表';

const DEFAULT_PREFERENCES: AppPreferences = {
  theme: 'warm',
  darkMode: 'system',
  showNonCurrentWeek: true,
  totalWeeks: 20,
  fontSize: 'normal',
  defaultTab: 'timetable',
};

export class ProfileStore {
  private static instance: ProfileStore | null = null;

  static getInstance(): ProfileStore {
    if (!ProfileStore.instance) {
      ProfileStore.instance = new ProfileStore();
    }
    return ProfileStore.instance;
  }

  private constructor() {
    this.ensureInitialized();
  }

  private ensureInitialized(): void {
    const metaStr = localStorage.getItem(META_STORAGE_KEY);
    if (!metaStr) {
      const defaultId = 'profile_' + Date.now().toString();
      const defaultStart = defaultForToday().isoString();
      const initialMeta: StoredMeta = {
        activeId: defaultId,
        profiles: [
          {
            id: defaultId,
            name: DEFAULT_PROFILE_NAME,
            createdAt: Date.now(),
            updatedAt: Date.now(),
            semesterStartDate: defaultStart,
          },
        ],
      };
      localStorage.setItem(META_STORAGE_KEY, JSON.stringify(initialMeta));
      localStorage.setItem(COURSES_STORAGE_PREFIX + defaultId, JSON.stringify([]));
    }
  }

  getMeta(): StoredMeta {
    this.ensureInitialized();
    try {
      const meta = JSON.parse(localStorage.getItem(META_STORAGE_KEY) || '{}');
      if (meta && meta.activeId && Array.isArray(meta.profiles)) {
        return meta as StoredMeta;
      }
    } catch {
      // Fallback
    }
    localStorage.removeItem(META_STORAGE_KEY);
    this.ensureInitialized();
    return JSON.parse(localStorage.getItem(META_STORAGE_KEY)!) as StoredMeta;
  }

  private saveMeta(meta: StoredMeta): void {
    localStorage.setItem(META_STORAGE_KEY, JSON.stringify(meta));
  }

  listProfiles(): ProfileInfo[] {
    const meta = this.getMeta();
    return meta.profiles.map((p) => {
      const courses = this.getCourses(p.id);
      return {
        id: p.id,
        name: p.name,
        isActive: p.id === meta.activeId,
        updatedAt: p.updatedAt,
        semesterStartDate: p.semesterStartDate ?? null,
        courseCount: courses.length,
      };
    });
  }

  getActiveProfileId(): string {
    return this.getMeta().activeId;
  }

  getActiveProfile(): { meta: StoredProfileMeta; courses: CourseSchedule[] } {
    const meta = this.getMeta();
    let current = meta.profiles.find((p) => p.id === meta.activeId);
    if (!current) {
      current = meta.profiles[0];
      if (current) {
        meta.activeId = current.id;
        this.saveMeta(meta);
      }
    }
    const courses = current ? this.getCourses(current.id) : [];
    return {
      meta: current ?? {
        id: 'default',
        name: DEFAULT_PROFILE_NAME,
        createdAt: Date.now(),
        updatedAt: Date.now(),
        semesterStartDate: defaultForToday().isoString(),
      },
      courses,
    };
  }

  getCourses(profileId: string): CourseSchedule[] {
    try {
      const raw = localStorage.getItem(COURSES_STORAGE_PREFIX + profileId);
      if (raw) {
        return JSON.parse(raw) as CourseSchedule[];
      }
    } catch {
      // Ignore
    }
    return [];
  }

  saveCourses(profileId: string, courses: CourseSchedule[]): void {
    localStorage.setItem(COURSES_STORAGE_PREFIX + profileId, JSON.stringify(courses));
    const meta = this.getMeta();
    const target = meta.profiles.find((p) => p.id === profileId);
    if (target) {
      target.updatedAt = Date.now();
      this.saveMeta(meta);
    }
  }

  switchProfile(profileId: string): boolean {
    const meta = this.getMeta();
    if (meta.profiles.some((p) => p.id === profileId)) {
      meta.activeId = profileId;
      this.saveMeta(meta);
      return true;
    }
    return false;
  }

  createProfile(name: string, courses: CourseSchedule[] = [], startDate?: string): string {
    const meta = this.getMeta();
    const id = 'profile_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7);
    const start = startDate ?? (courses.length > 0 ? inferFromCourses(courses).isoString() : defaultForToday().isoString());
    meta.profiles.push({
      id,
      name: name.trim() || DEFAULT_PROFILE_NAME,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      semesterStartDate: start,
    });
    meta.activeId = id;
    this.saveMeta(meta);
    this.saveCourses(id, courses);
    return id;
  }

  renameProfile(profileId: string, newName: string): boolean {
    const meta = this.getMeta();
    const p = meta.profiles.find((item) => item.id === profileId);
    if (!p) return false;
    p.name = newName.trim() || p.name;
    p.updatedAt = Date.now();
    this.saveMeta(meta);
    return true;
  }

  deleteProfile(profileId: string): boolean {
    const meta = this.getMeta();
    if (meta.profiles.length <= 1) {
      return false; // 保留至少一个课表
    }
    const index = meta.profiles.findIndex((p) => p.id === profileId);
    if (index === -1) return false;

    meta.profiles.splice(index, 1);
    localStorage.removeItem(COURSES_STORAGE_PREFIX + profileId);

    if (meta.activeId === profileId) {
      meta.activeId = meta.profiles[0].id;
    }
    this.saveMeta(meta);
    return true;
  }

  setSemesterStartDate(profileId: string, dateStr: string): void {
    const meta = this.getMeta();
    const p = meta.profiles.find((item) => item.id === profileId);
    if (!p) return;
    const parsed = SimpleDate.parse(dateStr);
    p.semesterStartDate = parsed ? normalizeToWeekStart(parsed).isoString() : dateStr;
    p.updatedAt = Date.now();
    this.saveMeta(meta);
  }

  addCourse(profileId: string, course: CourseSchedule): void {
    const courses = this.getCourses(profileId);
    courses.push(course);
    this.saveCourses(profileId, courses);
  }

  updateCourse(profileId: string, index: number, course: CourseSchedule): void {
    const courses = this.getCourses(profileId);
    if (index >= 0 && index < courses.length) {
      courses[index] = course;
      this.saveCourses(profileId, courses);
    }
  }

  deleteCourse(profileId: string, index: number): void {
    const courses = this.getCourses(profileId);
    if (index >= 0 && index < courses.length) {
      courses.splice(index, 1);
      this.saveCourses(profileId, courses);
    }
  }

  importSchedule(
    rawJson: string,
    mode: 'overwrite' | 'new',
    newProfileName?: string
  ): { profileId: string; courseCount: number; isNewProfile: boolean } {
    const courses = parseSchedule(rawJson);
    if (courses.length === 0) {
      throw new Error('未在数据中解析到任何有效课程');
    }
    const inferred = inferFromCourses(courses).isoString();

    if (mode === 'new') {
      const name = newProfileName?.trim() || `${courses[0].semester || '导入'}课表`;
      const id = this.createProfile(name, courses, inferred);
      return { profileId: id, courseCount: courses.length, isNewProfile: true };
    } else {
      const activeId = this.getActiveProfileId();
      this.saveCourses(activeId, courses);
      this.setSemesterStartDate(activeId, inferred);
      return { profileId: activeId, courseCount: courses.length, isNewProfile: false };
    }
  }

  exportBackup(): string {
    const meta = this.getMeta();
    const backupProfiles: BackupProfile[] = meta.profiles.map((p) => {
      const courses = this.getCourses(p.id);
      return {
        name: p.name,
        semesterStartDate: p.semesterStartDate ?? null,
        courses: courses.map(courseToBackup),
      };
    });
    return encodeBackup(new Date().toISOString(), backupProfiles);
  }

  importBackup(content: string): number {
    const profiles = decodeBackup(content);
    if (!profiles || profiles.length === 0) {
      throw new Error('无效的备份文件或没有包含任何课表');
    }
    let importedCount = 0;
    for (const bp of profiles) {
      const courses = (bp.courses ?? []).map(backupToCourse);
      const startDate = bp.semesterStartDate ?? (courses.length > 0 ? inferFromCourses(courses).isoString() : defaultForToday().isoString());
      this.createProfile(bp.name || '已恢复课表', courses, startDate);
      importedCount++;
    }
    return importedCount;
  }

  getPreferences(): AppPreferences {
    try {
      const raw = localStorage.getItem(PREFS_STORAGE_KEY);
      if (raw) {
        return { ...DEFAULT_PREFERENCES, ...JSON.parse(raw) };
      }
    } catch {
      // Ignore
    }
    return DEFAULT_PREFERENCES;
  }

  savePreferences(prefs: Partial<AppPreferences>): AppPreferences {
    const current = this.getPreferences();
    const updated = { ...current, ...prefs };
    localStorage.setItem(PREFS_STORAGE_KEY, JSON.stringify(updated));
    return updated;
  }
}

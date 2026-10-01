export class SimpleDate {
  readonly year: number;
  readonly month: number;
  readonly day: number;

  constructor(year: number, month: number, day: number) {
    this.year = year;
    this.month = month;
    this.day = day;
  }

  static today(): SimpleDate {
    const now = new Date();
    return new SimpleDate(now.getFullYear(), now.getMonth() + 1, now.getDate());
  }

  static parse(text: string): SimpleDate | null {
    const parts = text.split('-');
    if (parts.length !== 3) return null;
    const y = Number(parts[0]);
    const m = Number(parts[1]);
    const d = Number(parts[2]);
    if (!Number.isInteger(y) || !Number.isInteger(m) || !Number.isInteger(d)) return null;
    if (y <= 0 || m <= 0 || m > 12 || d <= 0 || d > 31) return null;
    return new SimpleDate(y, m, d);
  }

  private utc(): number {
    return Date.UTC(this.year, this.month - 1, this.day, 12, 0, 0);
  }

  /** 周一=1 ... 周日=7 */
  weekdayMonBased(): number {
    const wd = new Date(this.utc()).getUTCDay(); // 0=Sun..6=Sat
    return wd === 0 ? 7 : wd;
  }

  weekday(): number {
    return this.weekdayMonBased();
  }

  days(since: SimpleDate): number {
    return Math.round((this.utc() - since.utc()) / 86400000);
  }

  adding(days: number): SimpleDate {
    const d = new Date(this.utc() + days * 86400000);
    return new SimpleDate(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate());
  }

  previousOrSameMonday(): SimpleDate {
    return this.adding(-(this.weekdayMonBased() - 1));
  }

  isoString(): string {
    const mm = String(this.month).padStart(2, '0');
    const dd = String(this.day).padStart(2, '0');
    return `${this.year}-${mm}-${dd}`;
  }

  displayText(): string {
    return `${this.year}/${this.month}/${this.day}`;
  }

  equals(other: SimpleDate | null | undefined): boolean {
    if (!other) return false;
    return this.year === other.year && this.month === other.month && this.day === other.day;
  }
}

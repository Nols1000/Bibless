export type TimingMode = 'TOKENS' | 'SCAN_ORDER';

export interface Run {
  readonly id: string;
  readonly name: string;
  readonly date: string | null | undefined;
  readonly format: string;
  readonly createdAt: number;
}

/** A QR code as one SVG path over a size × size grid of modules, without the quiet zone. */
export interface Qr {
  readonly size: number;
  readonly path: string;
}

/** A timer or scanner session imported into a run. `run` is the run its volunteer joined. */
export interface Session {
  readonly id: string;
  readonly source: string | null | undefined;
  readonly kind: string | null | undefined;
  readonly startedAt: number;
  readonly run: string | null | undefined;
  readonly count: number;
  readonly included: boolean;
}

export interface Finisher {
  readonly eventId: string;
  readonly athleteId: string | null | undefined;
  readonly name: string | null | undefined;
  readonly position: number | null | undefined;
}

export interface Row {
  readonly position: number;
  readonly elapsedMs: number | null | undefined;
  readonly time: string | null | undefined;
  readonly athleteId: string | null | undefined;
  readonly name: string | null | undefined;
  readonly timeEventId: string | null | undefined;
  readonly athleteEventId: string | null | undefined;
}

export type IssueKind =
  | 'MISSING_TIME'
  | 'MISSING_ATHLETE'
  | 'DUPLICATE_ATHLETE'
  | 'DUPLICATE_POSITION'
  | 'MISSING_POSITION'
  | 'COUNT_MISMATCH'
  | 'SEVERAL_TIMERS'
  | 'SEVERAL_SCANNERS';

/** Something to check before sharing results; only the fields its kind needs are set. */
export interface Issue {
  readonly kind: string;
  readonly positions: Array<number>;
  readonly athleteId: string | null | undefined;
  readonly finishers: Array<Finisher>;
  readonly times: number;
  readonly athletes: number;
}

export interface Review {
  readonly run: Run;
  readonly sessions: Array<Session>;
  /** The session whose times are used. */
  readonly timer: string | null | undefined;
  readonly rows: Array<Row>;
  readonly issues: Array<Issue>;
  /** Entries left out because their signature or chain doesn't check out. */
  readonly unverified: number;
}

/** What the organiser screens need; `RunOrganiser` from sharedLogic implements it. */
export interface Organiser {
  listRuns(): Promise<Array<Run>>;
  getRun(id: string): Promise<Run | null | undefined>;
  createRun(name: string, date: string | null, format: TimingMode): Promise<Run>;
  deleteRun(id: string): void;
  runQr(id: string): Promise<Qr | null | undefined>;
  getReview(id: string): Promise<Review | null | undefined>;
  /** Resolves to the number of new sessions; rejects if the file isn't a Bibless session export. */
  importLog(id: string, json: string): Promise<number>;
  includeSession(id: string, session: string): Promise<void>;
  chooseTimer(id: string, session: string): void;
  addTime(id: string, elapsedMs: number): Promise<void>;
  removeTime(id: string, timeEventId: string): Promise<void>;
  addFinisher(id: string, position: number | null, athleteId: string | null, name: string | null): Promise<void>;
  editFinisher(
    id: string,
    athleteEventId: string,
    position: number | null,
    athleteId: string | null,
    name: string | null,
  ): Promise<void>;
  removeFinisher(id: string, athleteEventId: string): Promise<void>;
  resultsCsv(id: string): Promise<string | null | undefined>;
}

export const modeNames: Record<TimingMode, string> = {
  TOKENS: 'Finish tokens',
  SCAN_ORDER: 'Scan order',
};

export function modeName(format: string): string {
  return modeNames[format as TimingMode] ?? format;
}

/** Formats an ISO yyyy-MM-dd date for people, in their locale. */
export function formatDate(date: string): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(year, month - 1, day).toLocaleDateString(undefined, { dateStyle: 'full' });
}

/** Reads `m:ss` or `h:mm:ss` into milliseconds, or null if it isn't a time. */
export function parseElapsed(text: string): number | null {
  const match = text.trim().match(/^(?:(\d+):)?(\d{1,2}):(\d{2})$/);
  if (!match) return null;
  const [, hours, minutes, seconds] = match;
  if (Number(seconds) > 59 || (hours !== undefined && Number(minutes) > 59)) return null;
  return ((Number(hours ?? 0) * 60 + Number(minutes)) * 60 + Number(seconds)) * 1000;
}

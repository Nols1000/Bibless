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

/** What the organiser screens need; `RunOrganiser` from sharedLogic implements it. */
export interface Organiser {
  listRuns(): Promise<Array<Run>>;
  getRun(id: string): Promise<Run | null | undefined>;
  createRun(name: string, date: string | null, format: TimingMode): Promise<Run>;
  deleteRun(id: string): void;
  runQr(id: string): Promise<Qr | null | undefined>;
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

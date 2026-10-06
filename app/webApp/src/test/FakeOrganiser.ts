import type { Organiser, Qr, Review, Run, TimingMode } from '../runs/organiser.ts';

/** An in-memory organiser, so screens can be tested without the Kotlin library. */
export class FakeOrganiser implements Organiser {
  runs: Run[] = [];
  /** What `getReview` returns per run; tests set it up. */
  reviews: Record<string, Review> = {};
  /** Every change the screens asked for, as [method, ...arguments]. */
  calls: unknown[][] = [];
  /** Changes that reject, by method name. */
  failing = new Set<string>();
  /** What `importLog` resolves to: the number of new sessions. */
  imported = 1;
  csv = 'position,time,elapsed_ms,athlete_id,name\n';
  private nextId = 1;

  async listRuns() {
    return [...this.runs].sort((a, b) => b.createdAt - a.createdAt);
  }

  async getRun(id: string) {
    return this.runs.find((run) => run.id === id) ?? null;
  }

  async createRun(name: string, date: string | null, format: TimingMode) {
    const run: Run = { id: `run-${this.nextId}`, name, date, format, createdAt: this.nextId++ };
    this.runs.push(run);
    return run;
  }

  deleteRun(id: string) {
    this.runs = this.runs.filter((run) => run.id !== id);
  }

  async runQr(id: string): Promise<Qr | null> {
    return this.runs.some((run) => run.id === id) ? { size: 21, path: 'M0 0h1v1h-1z' } : null;
  }

  async getReview(id: string) {
    return this.reviews[id] ?? null;
  }

  async importLog(id: string, json: string) {
    await this.record('importLog', id, json);
    JSON.parse(json);
    return this.imported;
  }

  includeSession = (...args: [string, string]) => this.record('includeSession', ...args);

  chooseTimer(id: string, session: string) {
    this.calls.push(['chooseTimer', id, session]);
  }

  addTime = (...args: [string, number]) => this.record('addTime', ...args);
  removeTime = (...args: [string, string]) => this.record('removeTime', ...args);
  addFinisher = (...args: [string, number | null, string | null, string | null]) => this.record('addFinisher', ...args);
  editFinisher = (...args: [string, string, number | null, string | null, string | null]) =>
    this.record('editFinisher', ...args);
  removeFinisher = (...args: [string, string]) => this.record('removeFinisher', ...args);

  async resultsCsv(id: string) {
    return this.reviews[id] ? this.csv : null;
  }

  private async record(method: string, ...args: unknown[]) {
    this.calls.push([method, ...args]);
    if (this.failing.has(method)) throw new Error(`${method} failed`);
  }
}

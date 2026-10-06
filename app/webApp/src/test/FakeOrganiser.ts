import type { Organiser, Qr, Run, TimingMode } from '../runs/organiser.ts';

/** An in-memory organiser, so screens can be tested without the Kotlin library. */
export class FakeOrganiser implements Organiser {
  runs: Run[] = [];
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
}

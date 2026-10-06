import { useEffect, useState } from 'react';
import { formatDate, modeName, type Organiser, type Run } from './organiser.ts';

export function RunList({ organiser }: { organiser: Organiser }) {
  const [runs, setRuns] = useState<Run[] | null>(null);

  useEffect(() => {
    let active = true;
    organiser.listRuns().then((list) => active && setRuns(list));
    return () => {
      active = false;
    };
  }, [organiser]);

  return (
    <section>
      <div className="row">
        <h1>Your runs</h1>
        <a href="#/new" className="button primary">New run</a>
      </div>
      {runs === null ? null : runs.length === 0 ? (
        <div className="empty">
          <p>
            Organise a free, timed community run. Create a run here, then print its run sheet:
            your timekeepers and scanners join by scanning the QR code with the Bibless app.
          </p>
          <p className="muted">Runs are kept in this browser. No account needed.</p>
        </div>
      ) : (
        <ul className="run-list">
          {runs.map((run) => (
            <li key={run.id}>
              <a href={`#/runs/${encodeURIComponent(run.id)}`}>
                <span className="run-name">{run.name}</span>
                <span className="muted">
                  {[run.date ? formatDate(run.date) : null, modeName(run.format)].filter(Boolean).join(' · ')}
                </span>
              </a>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

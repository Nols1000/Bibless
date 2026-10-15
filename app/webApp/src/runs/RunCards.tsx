import { Icon } from '../Icon.tsx';
import { formatDate, modeName, type Run } from './organiser.ts';

export function RunCards({ runs, recent = false }: { runs: Run[]; recent?: boolean }) {
  return (
    <ul className={`run-list${recent ? ' recent-runs' : ''}`}>
      {runs.map((run) => (
        <li key={run.id}>
          <a href={`#/runs/${encodeURIComponent(run.id)}`}>
            <div className="run-card-top"><span className="badge">{modeName(run.format)}</span><Icon name="arrow" /></div>
            <span className="run-name">{run.name}</span>
            {run.date && <span className="run-date"><Icon name="calendar" />{formatDate(run.date)}</span>}
          </a>
        </li>
      ))}
    </ul>
  );
}

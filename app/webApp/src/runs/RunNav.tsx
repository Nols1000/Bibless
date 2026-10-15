import { modeName, type Run } from './organiser.ts';

export function RunNav({ run, page }: { run: Run; page: 'run' | 'results' | 'tokens' }) {
  const path = `#/runs/${encodeURIComponent(run.id)}`;
  return (
    <div className="run-navigation no-print">
      <div className="run-breadcrumb">
        <a href="#/runs">Your runs</a><span aria-hidden="true">/</span>
        <span className="breadcrumb-name">{run.name}</span>
        <span className="badge">{modeName(run.format)}</span>
      </div>
      <nav className="run-tabs" aria-label="Run">
        <a href={path} aria-current={page === 'run' ? 'page' : undefined}>Run sheet</a>
        <a href={`${path}/results`} aria-current={page === 'results' ? 'page' : undefined}>Results</a>
        {run.format === 'TOKENS' && (
          <a href={`${path}/tokens`} aria-current={page === 'tokens' ? 'page' : undefined}>Finish tokens</a>
        )}
      </nav>
    </div>
  );
}

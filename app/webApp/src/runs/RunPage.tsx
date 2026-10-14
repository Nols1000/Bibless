import { useEffect, useState } from 'react';
import { navigate } from '../useHashRoute.ts';
import type { Organiser, Qr, Run } from './organiser.ts';
import { RunSheet } from './RunSheet.tsx';

type Loaded = { run: Run; qr: Qr } | 'missing' | null;

export function RunPage({ organiser, id }: { organiser: Organiser; id: string }) {
  const [loaded, setLoaded] = useState<Loaded>(null);

  useEffect(() => {
    let active = true;
    Promise.all([organiser.getRun(id), organiser.runQr(id)]).then(([run, qr]) => {
      if (active) setLoaded(run && qr ? { run, qr } : 'missing');
    });
    return () => {
      active = false;
    };
  }, [organiser, id]);

  if (loaded === null) return null;
  if (loaded === 'missing') {
    return (
      <section>
        <h1>Run not found</h1>
        <p>This run isn't stored in this browser.</p>
        <a href="#/" className="button">All runs</a>
      </section>
    );
  }

  const { run, qr } = loaded;

  function remove() {
    if (window.confirm(`Delete "${run.name}"? This can't be undone.`)) {
      organiser.deleteRun(id);
      navigate('/');
    }
  }

  return (
    <section>
      <div className="row no-print">
        <a href="#/">← All runs</a>
        <div className="row">
          <button type="button" className="button danger" onClick={remove}>Delete run</button>
          <button type="button" className="button" onClick={() => window.print()}>Print run sheet</button>
          {run.format === 'TOKENS' && (
            <a href={`#/runs/${encodeURIComponent(id)}/tokens`} className="button">Print tokens</a>
          )}
          <a href={`#/runs/${encodeURIComponent(id)}/results`} className="button primary">Results</a>
        </div>
      </div>
      <RunSheet run={run} qr={qr} />
      <p className="muted no-print">
        This run and the key that signs its results are stored only in this browser. Organise it
        from this browser on race day.
      </p>
    </section>
  );
}

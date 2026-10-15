import { useCallback } from 'react';
import { navigate } from '../useHashRoute.ts';
import { useLoad } from '../useLoad.ts';
import type { Organiser, Qr, Run } from './organiser.ts';
import { RunSheet } from './RunSheet.tsx';
import { RunNav } from './RunNav.tsx';
import { MissingRun, PageLoad } from './PageState.tsx';

type Loaded = { run: Run; qr: Qr } | 'missing';

export function RunPage({ organiser, id }: { organiser: Organiser; id: string }) {
  const load = useCallback(async (): Promise<Loaded> => {
    const [run, qr] = await Promise.all([organiser.getRun(id), organiser.runQr(id)]);
    return run && qr ? { run, qr } : 'missing';
  }, [organiser, id]);
  const { state, retry } = useLoad(load);

  if (state.status !== 'ready') return <PageLoad error={state.status === 'error'} retry={retry} />;
  if (state.data === 'missing') return <MissingRun />;
  const { run, qr } = state.data;

  function remove() {
    if (window.confirm(`Delete "${run.name}"? This can't be undone.`)) {
      organiser.deleteRun(id);
      navigate('/runs');
    }
  }

  return (
    <section>
      <RunNav run={run} page="run" />
      <div className="row page-actions no-print">
        <p className="muted" style={{ margin: 0 }}>A shared starting point for your volunteers.</p>
        <button type="button" className="button primary" onClick={() => window.print()}>Print run sheet</button>
      </div>
      <RunSheet run={run} qr={qr} />
      <div className="row no-print">
        <p className="storage-note">
          This run and the key that signs its results are stored only in this browser. Organise it
          from this browser on race day.
        </p>
        <button type="button" className="button danger" onClick={remove}>Delete run</button>
      </div>
    </section>
  );
}

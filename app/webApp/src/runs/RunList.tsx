import { useCallback } from 'react';
import { Icon } from '../Icon.tsx';
import { useLoad } from '../useLoad.ts';
import { PageLoad } from './PageState.tsx';
import { RunCards } from './RunCards.tsx';
import type { Organiser } from './organiser.ts';

export function RunList({ organiser }: { organiser: Organiser }) {
  const load = useCallback(() => organiser.listRuns(), [organiser]);
  const { state, retry } = useLoad(load);

  return (
    <section aria-labelledby="runs-heading">
      <div className="row page-heading">
        <div><p className="eyebrow">Community runs</p><h1 id="runs-heading">Your runs</h1><p>A starting line. A finish time. A community.</p></div>
        <a href="#/new" className="button primary"><Icon name="plus" />New run</a>
      </div>
      {state.status !== 'ready' ? <PageLoad error={state.status === 'error'} retry={retry} label="Loading your runs…" /> : state.data.length === 0 ? (
        <div className="empty">
          <h2>Your next run starts here.</h2>
          <p>
            Organise a free, timed community run. Create a run here, then print its run sheet:
            your timekeepers and scanners join by scanning the QR code with the Bibless app.
          </p>
          <a className="text-link" href="#/new">Create your first run<Icon name="arrow" /></a>
        </div>
      ) : (
        <RunCards runs={state.data} />
      )}
      <p className="storage-note">Runs are kept in this browser. No account needed.</p>
    </section>
  );
}

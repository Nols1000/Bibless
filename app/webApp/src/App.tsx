import './App.css';

import type { Organiser } from './runs/organiser.ts';
import { RunForm } from './runs/RunForm.tsx';
import { RunList } from './runs/RunList.tsx';
import { RunPage } from './runs/RunPage.tsx';
import { useHashRoute } from './useHashRoute.ts';

export function App({ organiser }: { organiser: Organiser }) {
  const route = useHashRoute();
  return (
    <div className="app">
      <header className="app-header no-print">
        <a href="#/" className="app-title">Bibless</a>
        <span className="app-subtitle">Community runs</span>
      </header>
      <main>
        {route.page === 'runs' && <RunList organiser={organiser} />}
        {route.page === 'new' && <RunForm organiser={organiser} />}
        {route.page === 'run' && <RunPage key={route.id} organiser={organiser} id={route.id} />}
      </main>
    </div>
  );
}

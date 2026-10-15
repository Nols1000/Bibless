import './App.css';

import { useEffect, useRef } from 'react';
import { OrganiserProvider, useOrganiser } from './OrganiserContext.tsx';
import { HomePage } from './landing/HomePage.tsx';
import type { Organiser } from './runs/organiser.ts';
import { RunForm } from './runs/RunForm.tsx';
import { RunList } from './runs/RunList.tsx';
import { ResultsPage } from './runs/ResultsPage.tsx';
import { RunPage } from './runs/RunPage.tsx';
import { TokensPage } from './runs/TokensPage.tsx';
import { PageLoad } from './runs/PageState.tsx';
import { useHashRoute, type Route } from './useHashRoute.ts';

function Management({ route }: { route: Exclude<Route, { page: 'home' }> }) {
  const { organiser, error, retry } = useOrganiser();
  if (!organiser) return <PageLoad error={error} retry={retry} label="Opening your runs…" />;
  switch (route.page) {
    case 'runs': return <RunList organiser={organiser} />;
    case 'new': return <RunForm organiser={organiser} />;
    case 'run': return <RunPage key={route.id} organiser={organiser} id={route.id} />;
    case 'results': return <ResultsPage key={route.id} organiser={organiser} id={route.id} />;
    case 'tokens': return <TokensPage key={route.id} organiser={organiser} id={route.id} />;
  }
}

export function App({ organiser }: { organiser?: Organiser }) {
  const route = useHashRoute();
  const main = useRef<HTMLElement>(null);
  const home = route.page === 'home';
  const base = import.meta.env.BASE_URL;

  useEffect(() => {
    const content = main.current;
    if (!content) return;
    const section = route.page === 'home' ? route.section : undefined;
    if (!section) window.scrollTo({ top: 0, behavior: 'instant' });

    const focus = () => {
      const target = section ? content.querySelector<HTMLElement>(`#${section}`) : content;
      const heading = target?.querySelector<HTMLElement>('h1, h2');
      if (!heading) return false;
      heading.tabIndex = -1;
      heading.focus({ preventScroll: true });
      if (section) target?.scrollIntoView({ block: 'start' });
      document.title = home ? 'Bibless — your barcode, your community run' : `${heading.textContent} · Bibless`;
      return true;
    };
    if (focus()) return;
    const observer = new MutationObserver(() => { if (focus()) observer.disconnect(); });
    observer.observe(content, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, [route, home]);

  return (
    <OrganiserProvider organiser={organiser}>
      <div className="app">
        <a href="#main-content" className="skip no-print" onClick={(event) => {
          event.preventDefault();
          main.current?.focus();
          main.current?.scrollIntoView({ block: 'start' });
        }}>Skip to content</a>
        <header className="app-header no-print">
          <div className="header-inner container">
            <a href="#/" className="brand" aria-label="Bibless home">
              <img src={`${base}favicon.svg`} width="32" height="32" alt="" />Bibless
            </a>
            <nav className="site-nav" aria-label="Main navigation">
              <a href="#/" aria-current={home ? 'page' : undefined}>Home</a>
              <a href="#/runs" aria-current={!home ? 'page' : undefined}>Your runs</a>
            </nav>
            <a href="#top" className="button small header-cta">Get the app</a>
          </div>
        </header>
        <main id="main-content" tabIndex={-1} ref={main} className={`app-main container ${home ? 'home-content' : 'management'}`}>
          {home ? <HomePage /> : <Management route={route} />}
        </main>
        <footer className="app-footer container no-print">
          <a href="#/" className="brand"><img src={`${base}favicon.svg`} width="24" height="24" alt="" />Bibless</a>
          <nav aria-label="Footer">
            <a href={`${base}privacy/android/`}>Privacy (Android)</a>
            <a href={`${base}privacy/apple/`}>Privacy (Apple)</a>
            <a href="https://github.com/Nols1000/Bibless">Source on GitHub</a>
          </nav>
          <p>Bibless is an independent app and is not affiliated with or endorsed by parkrun.</p>
        </footer>
      </div>
    </OrganiserProvider>
  );
}

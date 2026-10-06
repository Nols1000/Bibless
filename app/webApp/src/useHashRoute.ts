import { useEffect, useState } from 'react';

export type Route =
  | { page: 'runs' }
  | { page: 'new' }
  | { page: 'run'; id: string }
  | { page: 'results'; id: string };

/** Routes live in the URL hash, so the app works from any path on GitHub Pages. */
export function parseRoute(hash: string): Route {
  const path = hash.replace(/^#/, '');
  if (path === '/new') return { page: 'new' };
  const run = path.match(/^\/runs\/([^/]+)(\/results)?$/);
  if (run) return { page: run[2] ? 'results' : 'run', id: decodeURIComponent(run[1]) };
  return { page: 'runs' };
}

export function navigate(path: string) {
  window.location.hash = path;
}

export function useHashRoute(): Route {
  const [route, setRoute] = useState(() => parseRoute(window.location.hash));
  useEffect(() => {
    const update = () => setRoute(parseRoute(window.location.hash));
    window.addEventListener('hashchange', update);
    return () => window.removeEventListener('hashchange', update);
  }, []);
  return route;
}

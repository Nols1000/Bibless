import { useEffect, useState } from 'react';

export type Route =
  | { page: 'home'; section?: 'top' | 'features' | 'runs' | 'beta' }
  | { page: 'runs' }
  | { page: 'new' }
  | { page: 'run'; id: string }
  | { page: 'results'; id: string }
  | { page: 'tokens'; id: string };

/** Routes live in the URL hash, so the app works from any path on GitHub Pages. */
export function parseRoute(hash: string): Route {
  const path = hash.replace(/^#/, '');
  if (path === '/runs') return { page: 'runs' };
  if (path === '/new') return { page: 'new' };
  const run = path.match(/^\/runs\/([^/]+)(?:\/(results|tokens))?$/);
  if (run) {
    try {
      return { page: (run[2] as 'results' | 'tokens' | undefined) ?? 'run', id: decodeURIComponent(run[1]) };
    } catch {
      return { page: 'home' };
    }
  }
  if (path === 'apple') return { page: 'home', section: 'top' };
  if (path === 'top' || path === 'features' || path === 'runs' || path === 'beta') {
    return { page: 'home', section: path };
  }
  return { page: 'home' };
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

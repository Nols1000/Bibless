import { useCallback, useEffect, useRef, useState } from 'react';

type LoadState<T> = { status: 'loading' } | { status: 'error' } | { status: 'ready'; data: T };

/** Ignore old requests on navigation, and keep the current view visible during an action's reload. */
export function useLoad<T>(load: () => Promise<T>) {
  const [state, setState] = useState<LoadState<T>>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const request = useRef(0);
  const active = useRef(false);

  const reload = useCallback(async () => {
    if (!active.current) return;
    const current = ++request.current;
    try {
      const data = await load();
      if (active.current && request.current === current) setState({ status: 'ready', data });
    } catch {
      if (active.current && request.current === current) setState({ status: 'error' });
    }
  }, [load]);

  useEffect(() => {
    active.current = true;
    setState({ status: 'loading' });
    void reload();
    return () => {
      active.current = false;
      request.current++;
    };
  }, [reload, attempt]);

  const retry = useCallback(() => setAttempt((value) => value + 1), []);
  return { state, retry, reload };
}

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { Organiser } from './runs/organiser.ts';

let pendingOrganiser: Promise<Organiser> | undefined;

/** Load the Kotlin library after the page can render, and keep one repository for the app. */
function loadOrganiser(): Promise<Organiser> {
  pendingOrganiser ??= import('sharedLogic')
    .then(({ RunOrganiser }) => RunOrganiser.create())
    .catch((error: unknown) => {
      pendingOrganiser = undefined;
      throw error;
    });
  return pendingOrganiser;
}

interface OrganiserState {
  organiser: Organiser | null;
  error: boolean;
  retry(): void;
}

const OrganiserContext = createContext<OrganiserState | null>(null);

export function OrganiserProvider({ organiser, children }: { organiser?: Organiser; children: ReactNode }) {
  const [loaded, setLoaded] = useState<Organiser | null>(organiser ?? null);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const retry = useCallback(() => setAttempt((value) => value + 1), []);

  useEffect(() => {
    let active = true;
    setError(false);
    setLoaded(organiser ?? null);
    if (!organiser) {
      loadOrganiser().then(
        (value) => { if (active) setLoaded(value); },
        () => { if (active) setError(true); },
      );
    }
    return () => { active = false; };
  }, [organiser, attempt]);

  const value = useMemo(() => ({ organiser: loaded, error, retry }), [loaded, error, retry]);
  return <OrganiserContext.Provider value={value}>{children}</OrganiserContext.Provider>;
}

export function useOrganiser(): OrganiserState {
  const state = useContext(OrganiserContext);
  if (!state) throw new Error('An OrganiserProvider is required.');
  return state;
}

import { useEffect, useMemo, useState } from 'react';
import type { Organiser, Run, Token } from './organiser.ts';

/** The most tokens one print shows, so the page stays quick to lay out. */
const MAX_PRINT = 1000;
const PER_PAGE = 15;
const QUIET_ZONE = 10;

type Paper = 'A4' | 'letter';

/** Prints a range of numbered finish tokens as cut-out cards. */
export function TokensPage({ organiser, id }: { organiser: Organiser; id: string }) {
  const [run, setRun] = useState<Run | 'missing' | null>(null);
  const [from, setFrom] = useState('1');
  const [to, setTo] = useState('100');
  const [paper, setPaper] = useState<Paper>('A4');

  useEffect(() => {
    let active = true;
    organiser.getRun(id).then((loaded) => active && setRun(loaded ?? 'missing'));
    return () => {
      active = false;
    };
  }, [organiser, id]);

  const range = parseRange(from, to);
  const [first, last] = typeof range === 'string' ? [0, -1] : [range.from, range.to];
  const tokens = useMemo(() => (first > 0 ? organiser.tokens(first, last) : []), [organiser, first, last]);

  if (run === null) return null;
  if (run === 'missing') {
    return (
      <section>
        <h1>Run not found</h1>
        <p>This run isn't stored in this browser.</p>
        <a href="#/" className="button">All runs</a>
      </section>
    );
  }

  const back = <a href={`#/runs/${encodeURIComponent(id)}`}>← {run.name}</a>;
  if (run.format !== 'TOKENS') {
    return (
      <section>
        {back}
        <h1>Finish tokens</h1>
        <p>This run is timed in scan order, so it needs no tokens.</p>
      </section>
    );
  }

  return (
    <section>
      <style>{`@page { size: ${paper}; margin: 10mm; }`}</style>
      <div className="row no-print">
        {back}
        <button type="button" className="button primary" disabled={tokens.length === 0} onClick={() => window.print()}>
          Print tokens
        </button>
      </div>
      <div className="no-print">
        <h1>Finish tokens</h1>
        <p className="muted">
          Hand one out to each finisher in order. Scanners scan it with the finisher's barcode. Cut along the dashed
          lines; laminated tokens last for many runs.
        </p>
        <form className="token-options" onSubmit={(e) => e.preventDefault()} noValidate>
          <label className="field">
            <span>From</span>
            <input type="number" min={1} value={from} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="field">
            <span>To</span>
            <input type="number" min={1} value={to} onChange={(e) => setTo(e.target.value)} />
          </label>
          <label className="field">
            <span>Paper</span>
            <select value={paper} onChange={(e) => setPaper(e.target.value as Paper)}>
              <option value="A4">A4</option>
              <option value="letter">US Letter</option>
            </select>
          </label>
          {typeof range === 'string' ? (
            <p role="alert" className="error">{range}</p>
          ) : (
            <p className="muted">
              {tokens.length} {tokens.length === 1 ? 'token' : 'tokens'} on {Math.ceil(tokens.length / PER_PAGE)}{' '}
              {Math.ceil(tokens.length / PER_PAGE) === 1 ? 'page' : 'pages'}
            </p>
          )}
        </form>
      </div>
      <div className="token-sheet">
        {tokens.map((token) => (
          <TokenCard key={token.position} token={token} />
        ))}
      </div>
    </section>
  );
}

function TokenCard({ token }: { token: Token }) {
  const width = token.width + QUIET_ZONE * 2;
  return (
    <div className="token-card">
      <span className="token-number">{token.position}</span>
      <svg
        className="token-bars"
        role="img"
        aria-label={`Barcode ${token.text}`}
        viewBox={`${-QUIET_ZONE} 0 ${width} 1`}
        preserveAspectRatio="none"
        shapeRendering="crispEdges"
      >
        <rect x={-QUIET_ZONE} y={0} width={width} height={1} fill="#fff" />
        <path d={token.path} fill="#000" />
      </svg>
      <span className="token-text">{token.text}</span>
    </div>
  );
}

/** The range to print, or why it can't be printed. */
export function parseRange(from: string, to: string): { from: number; to: number } | string {
  const first = Number(from);
  const last = Number(to);
  if (!Number.isInteger(first) || !Number.isInteger(last) || from.trim() === '' || to.trim() === '') {
    return 'Enter whole numbers.';
  }
  if (first < 1 || last > 99_999) return 'Tokens run from 1 to 99999.';
  if (last < first) return '"To" must not be below "From".';
  if (last - first + 1 > MAX_PRINT) return `Print at most ${MAX_PRINT} tokens at a time.`;
  return { from: first, to: last };
}

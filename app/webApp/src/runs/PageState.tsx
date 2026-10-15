export function PageLoad({ error, retry, label = 'Loading your run…' }: {
  error: boolean;
  retry(): void;
  label?: string;
}) {
  return (
    <div className="load-state no-print">
      {error ? (
        <>
          <p role="alert">We couldn’t load your runs from this browser.</p>
          <button type="button" className="button" onClick={retry}>Try again</button>
        </>
      ) : <p role="status"><span className="loading-dot" aria-hidden="true" />{label}</p>}
    </div>
  );
}

export function MissingRun() {
  return (
    <section className="empty missing-run">
      <p className="eyebrow">Community runs</p>
      <h1>Run not found</h1>
      <p>This run isn’t stored in this browser.</p>
      <a href="#/runs" className="button primary">All runs</a>
    </section>
  );
}

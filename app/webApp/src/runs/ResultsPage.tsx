import { useCallback, useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react';
import { useLoad } from '../useLoad.ts';
import { RunNav } from './RunNav.tsx';
import { MissingRun, PageLoad } from './PageState.tsx';
import {
  parseElapsed,
  type Finisher,
  type Issue,
  type IssueKind,
  type Organiser,
  type Review,
  type Row,
  type Session,
} from './organiser.ts';

type Notice = { text: string; error: boolean } | null;
type Editing = { kind: 'row'; position: number } | { kind: 'finisher'; eventId: string } | null;

/** Combines the volunteers' sessions into results, shows what to check, and lets the organiser fix it. */
export function ResultsPage({ organiser, id }: { organiser: Organiser; id: string }) {
  const [notice, setNotice] = useState<Notice>(null);
  const [editing, setEditing] = useState<Editing>(null);

  const load = useCallback(() => organiser.getReview(id), [organiser, id]);
  const { state, retry, reload } = useLoad(load);
  if (state.status !== 'ready') return <PageLoad error={state.status === 'error'} retry={retry} label="Loading your results…" />;
  if (!state.data) return <MissingRun />;
  const review = state.data;

  const { run } = review;
  const tokens = run.format === 'TOKENS';

  /**
   * Runs a change and shows the new state either way. If it is rejected, [failure] is shown at the
   * top; forms pass null and show their own error.
   */
  async function change(action: () => Promise<unknown>, failure: string | null): Promise<boolean> {
    let ok = true;
    try {
      await action();
      setNotice(null);
    } catch {
      if (failure) setNotice({ text: failure, error: true });
      ok = false;
    }
    await reload();
    return ok;
  }

  async function importFiles(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []);
    event.target.value = '';
    let added = 0;
    const failed: string[] = [];
    for (const file of files) {
      try {
        added += await organiser.importLog(id, await file.text());
      } catch {
        failed.push(file.name);
      }
    }
    if (failed.length > 0) {
      setNotice({
        text: `${failed.join(', ')} ${failed.length === 1 ? "isn't a" : "aren't"} Bibless session ${failed.length === 1 ? 'file' : 'files'}. ` +
          'Import the .json file shared from "Recorded sessions" in the app.',
        error: true,
      });
    } else if (files.length > 0) {
      setNotice({
        text: added === 0 ? 'Nothing new: these sessions were imported already.' : `Imported ${plural(added, 'new session')}.`,
        error: false,
      });
    }
    await reload();
  }

  async function downloadCsv() {
    const csv = await organiser.resultsCsv(id);
    if (csv == null) return;
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    link.download = `${slug(run.name)}-results.csv`;
    link.click();
    URL.revokeObjectURL(link.href);
  }

  const flagged = new Set(
    review.issues
      .filter((issue) => ['MISSING_TIME', 'MISSING_ATHLETE', 'DUPLICATE_ATHLETE', 'DUPLICATE_POSITION'].includes(issue.kind))
      .flatMap((issue) => issue.positions),
  );

  return (
    <section className="results-page">
      <RunNav run={run} page="results" />
      <div className="row page-heading">
        <div><h1>Results</h1><p>Bring the finish times and scans together.</p></div>
        <button type="button" className="button primary" onClick={downloadCsv} disabled={review.rows.length === 0}>
          Download CSV
        </button>
      </div>
      {notice && (
        <p role={notice.error ? 'alert' : 'status'} className={notice.error ? 'error' : 'muted'}>{notice.text}</p>
      )}

      <dl className="review-summary">
        <div><dt>Finish positions</dt><dd>{review.rows.length}</dd></div>
        <div><dt>Included sessions</dt><dd>{review.sessions.filter((session) => session.included).length}</dd></div>
        <div><dt>To check</dt><dd>{review.issues.length}</dd></div>
      </dl>

      <section className="panel" aria-labelledby="sessions-heading">
        <div className="row">
          <h2 id="sessions-heading">Sessions</h2>
          <label className="button">
            Import sessions
            <input type="file" accept=".json,application/json" multiple onChange={importFiles} className="visually-hidden" />
          </label>
        </div>
        {review.sessions.length === 0 ? (
          <p className="muted">
            Ask your timekeepers and scanners to share their sessions from "Recorded sessions" in the Bibless app,
            then import the .json files here.
          </p>
        ) : (
          <Sessions
            review={review}
            onInclude={(session) => change(() => organiser.includeSession(id, session), 'The session could not be included.')}
            onChooseTimer={(session) => {
              organiser.chooseTimer(id, session);
              reload();
            }}
          />
        )}
        {review.unverified > 0 && (
          <p className="error">
            {plural(review.unverified, 'entry', 'entries')} could not be verified and {review.unverified === 1 ? 'was' : 'were'} left
            out. {review.unverified === 1 ? 'It' : 'They'} may have been changed after recording.
          </p>
        )}
      </section>

      {review.issues.length > 0 && (
        <section className="panel issues-panel" aria-labelledby="issues-heading">
          <h2 id="issues-heading">To check</h2>
          <ul className="issues">
            {review.issues.map((issue, index) => (
              <li key={index}>
                {describe(issue, tokens)}
                {issue.finishers.map((finisher) =>
                  editing?.kind === 'finisher' && editing.eventId === finisher.eventId ? (
                    <FinisherForm
                      key={finisher.eventId}
                      initial={finisher}
                      tokens={tokens}
                      submitLabel="Save finisher"
                      onSubmit={(position, athleteId, name) =>
                        change(
                          () => organiser.editFinisher(id, finisher.eventId, position, athleteId, name),
                          null,
                        )
                      }
                      onRemove={() => change(() => organiser.removeFinisher(id, finisher.eventId), 'The finisher could not be removed.')}
                      onClose={() => setEditing(null)}
                    />
                  ) : (
                    <button
                      key={finisher.eventId}
                      type="button"
                      className="link"
                      onClick={() => setEditing({ kind: 'finisher', eventId: finisher.eventId })}
                    >
                      Fix {finisherLabel(finisher)}
                    </button>
                  ),
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="table-wrap" tabIndex={0} role="region" aria-label="Finish results">
        <table className="results">
          <thead>
            <tr>
              <th scope="col">Pos</th>
              <th scope="col">Time</th>
              <th scope="col">Athlete</th>
              <th scope="col"><span className="visually-hidden">Fix</span></th>
            </tr>
          </thead>
          <tbody>
            {review.rows.map((row) => (
              <ResultRow
                key={row.position}
                row={row}
                flagged={flagged.has(row.position)}
                editing={editing?.kind === 'row' && editing.position === row.position}
                onEdit={() => setEditing({ kind: 'row', position: row.position })}
              >
                <RowEditor organiser={organiser} id={id} row={row} tokens={tokens} change={change} onClose={() => setEditing(null)} />
              </ResultRow>
            ))}
          </tbody>
        </table>
      </div>
      {review.rows.length === 0 && <p className="muted">No results yet.</p>}

      <div className="additions">
        <AddTime
          disabled={!review.timer}
          onAdd={(elapsedMs) => change(() => organiser.addTime(id, elapsedMs), null)}
        />
        <section className="panel" aria-labelledby="add-finisher-heading">
          <h2 id="add-finisher-heading">Add a finisher</h2>
          <FinisherForm
            tokens={tokens}
            submitLabel="Add finisher"
            onSubmit={(position, athleteId, name) =>
              change(() => organiser.addFinisher(id, position, athleteId, name), null)
            }
          />
        </section>
      </div>
    </section>
  );
}

function Sessions({
  review,
  onInclude,
  onChooseTimer,
}: {
  review: Review;
  onInclude: (session: string) => void;
  onChooseTimer: (session: string) => void;
}) {
  const timers = review.sessions.filter((session) => session.included && session.kind === 'TIMER');
  return (
    <ul className="sessions">
      {review.sessions.map((session) => (
        <li key={session.id}>
          <div>
            <strong>{session.source ?? 'Unnamed device'}</strong>
            <span className="muted"> · {sessionSummary(session)} · started {formatTime(session.startedAt)}</span>
          </div>
          {!session.included ? (
            <div className="row">
              <span className="muted">
                {session.run && session.run !== review.run.id ? 'Joined another run' : "Didn't join this run"}
              </span>
              <button type="button" className="button" onClick={() => onInclude(session.id)}>
                Include
              </button>
            </div>
          ) : timers.length > 1 && session.kind === 'TIMER' ? (
            <label className="row">
              <input
                type="radio"
                name="timer"
                checked={review.timer === session.id}
                onChange={() => onChooseTimer(session.id)}
              />
              Use these times
            </label>
          ) : (
            <span className="muted">Included</span>
          )}
        </li>
      ))}
    </ul>
  );
}

function ResultRow({
  row,
  flagged,
  editing,
  onEdit,
  children,
}: {
  row: Row;
  flagged: boolean;
  editing: boolean;
  onEdit: () => void;
  children: ReactNode;
}) {
  return (
    <>
      <tr className={flagged ? 'flagged' : undefined}>
        <td>{row.position}</td>
        <td>{row.time ?? '—'}</td>
        <td>{athleteLabel(row)}</td>
        <td>
          <button type="button" className="link" aria-label={`Fix position ${row.position}`} onClick={onEdit}>
            Fix
          </button>
        </td>
      </tr>
      {editing && (
        <tr className="editor">
          <td colSpan={4}>{children}</td>
        </tr>
      )}
    </>
  );
}

function RowEditor({
  organiser,
  id,
  row,
  tokens,
  change,
  onClose,
}: {
  organiser: Organiser;
  id: string;
  row: Row;
  tokens: boolean;
  change: (action: () => Promise<unknown>, failure: string | null) => Promise<boolean>;
  onClose: () => void;
}) {
  const finisher = row.athleteEventId;
  return (
    <div className="row-editor">
      <FinisherForm
        initial={{ athleteId: row.athleteId, name: row.name, position: row.position }}
        tokens={tokens}
        submitLabel={finisher ? 'Save finisher' : 'Add finisher'}
        onSubmit={(position, athleteId, name) =>
          change(
            () =>
              finisher
                ? organiser.editFinisher(id, finisher, position, athleteId, name)
                : organiser.addFinisher(id, position, athleteId, name),
            null,
          )
        }
        onRemove={finisher ? () => change(() => organiser.removeFinisher(id, finisher), 'The finisher could not be removed.') : undefined}
        onClose={onClose}
      />
      <div className="row">
        {!tokens && finisher && (
          <button
            type="button"
            className="button"
            onClick={() =>
              change(() => organiser.addFinisher(id, row.position, null, null), 'The finisher could not be added.').then(onClose)
            }
          >
            Insert unknown finisher before
          </button>
        )}
        {row.timeEventId && (
          <button
            type="button"
            className="button danger"
            onClick={() => change(() => organiser.removeTime(id, row.timeEventId!), 'The time could not be removed.').then(onClose)}
          >
            Remove time {row.time}
          </button>
        )}
      </div>
    </div>
  );
}

/** Edits a finisher: athlete ID or name, and the token or position. */
function FinisherForm({
  initial,
  tokens,
  submitLabel,
  onSubmit,
  onRemove,
  onClose,
}: {
  initial?: Partial<Pick<Finisher, 'athleteId' | 'name' | 'position'>>;
  tokens: boolean;
  submitLabel: string;
  onSubmit: (position: number | null, athleteId: string | null, name: string | null) => Promise<boolean>;
  onRemove?: () => Promise<boolean>;
  onClose?: () => void;
}) {
  const [athleteId, setAthleteId] = useState(initial?.athleteId ?? '');
  const [name, setName] = useState(initial?.name ?? '');
  const [position, setPosition] = useState(initial?.position?.toString() ?? '');
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const parsed = position.trim() === '' ? null : Number(position);
    if (parsed !== null && !(Number.isInteger(parsed) && parsed > 0)) {
      setError(`The ${tokens ? 'token' : 'position'} is a whole number from 1.`);
      return;
    }
    setError(null);
    if (!(await onSubmit(parsed, athleteId.trim() || null, name.trim() || null))) {
      setError("That isn't an athlete ID.");
      return;
    }
    if (initial) {
      onClose?.();
    } else {
      setAthleteId('');
      setName('');
      setPosition('');
    }
  }

  return (
    <form className="finisher-form" onSubmit={submit} noValidate>
      <label className="field">
        <span>Athlete ID</span>
        <input value={athleteId} onChange={(e) => setAthleteId(e.target.value)} placeholder="A1234567" />
      </label>
      <label className="field">
        <span>Name <span className="muted">(without an ID)</span></span>
        <input value={name} onChange={(e) => setName(e.target.value)} />
      </label>
      <label className="field">
        <span>{tokens ? 'Token' : 'Position'}</span>
        <input value={position} onChange={(e) => setPosition(e.target.value)} inputMode="numeric" />
      </label>
      {error && <p role="alert" className="error">{error}</p>}
      <div className="row">
        <button type="submit" className="button primary">{submitLabel}</button>
        {onRemove && (
          <button type="button" className="button danger" onClick={() => onRemove().then((ok) => ok && onClose?.())}>
            Remove finisher
          </button>
        )}
        {onClose && (
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
        )}
      </div>
    </form>
  );
}

function AddTime({ disabled, onAdd }: { disabled: boolean; onAdd: (elapsedMs: number) => Promise<boolean> }) {
  const [time, setTime] = useState('');
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const elapsed = parseElapsed(time);
    if (elapsed === null) {
      setError('Enter the time as m:ss or h:mm:ss.');
      return;
    }
    setError(null);
    if (await onAdd(elapsed)) setTime('');
    else setError('The time could not be added.');
  }

  return (
    <section className="panel" aria-labelledby="add-time-heading">
      <h2 id="add-time-heading">Add a missed time</h2>
      {disabled ? (
        <p className="muted">Import a timer session first: added times count from its start.</p>
      ) : (
        <form className="finisher-form" onSubmit={submit} noValidate>
          <label className="field">
            <span>Time</span>
            <input value={time} onChange={(e) => setTime(e.target.value)} placeholder="24:13" />
          </label>
          {error && <p role="alert" className="error">{error}</p>}
          <div className="row">
            <button type="submit" className="button primary">Add time</button>
          </div>
        </form>
      )}
    </section>
  );
}

function describe(issue: Issue, tokens: boolean): string {
  const [position] = issue.positions;
  switch (issue.kind as IssueKind) {
    case 'MISSING_TIME':
      return `Position ${position} has no time.`;
    case 'MISSING_ATHLETE':
      return `Nobody was recorded at position ${position}.`;
    case 'DUPLICATE_ATHLETE':
      return `${issue.athleteId} is at positions ${issue.positions.join(' and ')}.`;
    case 'DUPLICATE_POSITION':
      return `Token ${position} was recorded for ${issue.finishers.map(finisherLabel).join(' and ')}; the first is used.`;
    case 'MISSING_POSITION':
      return `${issue.finishers.map(finisherLabel).join(', ')} was recorded without a token.`;
    case 'COUNT_MISMATCH':
      return `${plural(issue.times, 'time')} but ${plural(issue.athletes, 'finisher')}. ` +
        (tokens ? '' : 'A missed or extra scan shifts everyone behind it.');
    case 'SEVERAL_TIMERS':
      return 'Several timers recorded times. Choose whose times to use under Sessions.';
    case 'SEVERAL_SCANNERS':
      return 'Several scanners recorded in scan order; their scans are merged by the time they were made.';
    default:
      return issue.kind;
  }
}

function sessionSummary(session: Session): string {
  if (session.kind === 'TIMER') return `Timer, ${plural(session.count, 'time')}`;
  if (session.kind === 'SCANNER') return `Scanner, ${plural(session.count, 'finisher')}`;
  return 'Nothing recorded';
}

function athleteLabel(row: Pick<Row, 'athleteId' | 'name' | 'athleteEventId'>): string {
  if (row.athleteId) return row.athleteId;
  if (!row.athleteEventId) return '—';
  return row.name ? `${row.name} (no barcode)` : 'Unknown';
}

function finisherLabel(finisher: Finisher): string {
  return finisher.athleteId ?? (finisher.name ? `${finisher.name} (no barcode)` : 'an unknown finisher');
}

function plural(count: number, one: string, many = `${one}s`): string {
  return `${count} ${count === 1 ? one : many}`;
}

function formatTime(millis: number): string {
  return new Date(millis).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

function slug(name: string): string {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'run';
}

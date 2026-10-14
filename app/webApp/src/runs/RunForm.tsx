import { useState, type FormEvent } from 'react';
import { navigate } from '../useHashRoute.ts';
import { modeNames, type Organiser, type TimingMode } from './organiser.ts';

const modeDescriptions: Record<TimingMode, string> = {
  TOKENS:
    'Each finisher gets a numbered token at the line; scanners pair it with their barcode. ' +
    'The most robust option, but you need printed tokens.',
  SCAN_ORDER:
    'Scanners scan finishers in the order they leave the funnel. Nothing to print, but a ' +
    'missed or swapped scan shifts everyone behind it until it is fixed.',
};

export function RunForm({ organiser }: { organiser: Organiser }) {
  const [name, setName] = useState('');
  const [date, setDate] = useState('');
  const [mode, setMode] = useState<TimingMode>('TOKENS');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!name.trim()) {
      setError('Give the run a name.');
      return;
    }
    setSaving(true);
    try {
      const run = await organiser.createRun(name.trim(), date || null, mode);
      navigate(`/runs/${encodeURIComponent(run.id)}`);
    } catch {
      setError('The run could not be created.');
      setSaving(false);
    }
  }

  return (
    <section>
      <h1>New run</h1>
      <form onSubmit={submit} noValidate>
        <label className="field">
          <span>Name</span>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Riverside 5k"
            aria-invalid={error !== null && !name.trim()}
          />
        </label>
        <label className="field">
          <span>Date <span className="muted">(optional)</span></span>
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <fieldset className="field">
          <legend>How is it timed?</legend>
          {(Object.keys(modeNames) as TimingMode[]).map((option) => (
            <label key={option} className={`mode${mode === option ? ' selected' : ''}`}>
              <input
                type="radio"
                name="mode"
                value={option}
                checked={mode === option}
                onChange={() => setMode(option)}
              />
              <span>
                <strong>{modeNames[option]}</strong>
                <span className="muted">{modeDescriptions[option]}</span>
              </span>
            </label>
          ))}
        </fieldset>
        {error && <p role="alert" className="error">{error}</p>}
        <div className="row">
          <a href="#/" className="button">Cancel</a>
          <button type="submit" className="button primary" disabled={saving}>Create run</button>
        </div>
      </form>
    </section>
  );
}

import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App.tsx';
import { FakeOrganiser } from '../test/FakeOrganiser.ts';
import { parseElapsed, type Issue, type Review, type Row, type Run, type Session } from './organiser.ts';

let organiser: FakeOrganiser;
let run: Run;

beforeEach(async () => {
  organiser = new FakeOrganiser();
  run = await organiser.createRun('Riverside 5k', null, 'TOKENS');
});

function session(overrides: Partial<Session> = {}): Session {
  return { id: 's1', source: 'Pixel 9', kind: 'TIMER', startedAt: 0, run: run.id, count: 2, included: true, ...overrides };
}

function row(position: number, overrides: Partial<Row> = {}): Row {
  return {
    position,
    elapsedMs: position * 60_000,
    time: `${position}:00`,
    athleteId: `A${position}`,
    name: null,
    timeEventId: `t${position}`,
    athleteEventId: `a${position}`,
    ...overrides,
  };
}

function issue(kind: string, overrides: Partial<Issue> = {}): Issue {
  return { kind, positions: [], athleteId: null, finishers: [], times: 0, athletes: 0, ...overrides };
}

function review(overrides: Partial<Review> = {}): Review {
  return { run, sessions: [], timer: null, rows: [], issues: [], unverified: 0, ...overrides };
}

async function open(setup: Partial<Review> = {}) {
  organiser.reviews[run.id] = review(setup);
  act(() => {
    window.location.hash = `#/runs/${run.id}/results`;
    window.dispatchEvent(new HashChangeEvent('hashchange'));
  });
  render(<App organiser={organiser} />);
  await screen.findByRole('heading', { name: 'Results' });
}

describe('results page', () => {
  it('is linked from the run page', async () => {
    window.location.hash = `#/runs/${run.id}`;
    render(<App organiser={organiser} />);
    expect(await screen.findByRole('link', { name: 'Results' })).toHaveAttribute('href', `#/runs/${run.id}/results`);
  });

  it('explains how to get sessions when there are none', async () => {
    await open();
    expect(screen.getByText(/share their sessions from "Recorded sessions"/)).toBeInTheDocument();
    expect(screen.getByText('No results yet.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Download CSV' })).toBeDisabled();
    expect(screen.getByText(/Import a timer session first/)).toBeInTheDocument();
  });

  it('imports session files and says what was new', async () => {
    await open();
    const file = new File(['{"version":1}'], 'bibless-timer.json', { type: 'application/json' });
    await userEvent.upload(screen.getByLabelText('Import sessions'), file);

    expect(organiser.calls).toEqual([['importLog', run.id, '{"version":1}']]);
    expect(await screen.findByRole('status')).toHaveTextContent('Imported 1 new session.');
  });

  it('names files that are not session exports', async () => {
    await open();
    const csv = new File(['position,time'], 'bibless-timer.csv', { type: 'text/csv' });
    await userEvent.upload(screen.getByLabelText('Import sessions'), csv, { applyAccept: false });

    expect(await screen.findByRole('alert')).toHaveTextContent("bibless-timer.csv isn't a Bibless session file.");
  });

  it('lists sessions and includes one that did not join', async () => {
    await open({
      sessions: [
        session(),
        session({ id: 's2', source: 'iPhone', kind: 'SCANNER', count: 1, run: null, included: false }),
      ],
    });
    const items = within(screen.getByRole('region', { name: 'Sessions' })).getAllByRole('listitem');

    expect(items[0]).toHaveTextContent('Pixel 9 · Timer, 2 times');
    expect(items[0]).toHaveTextContent('Included');
    expect(items[1]).toHaveTextContent('iPhone · Scanner, 1 finisher');
    expect(items[1]).toHaveTextContent("Didn't join this run");
    await userEvent.click(within(items[1]).getByRole('button', { name: 'Include' }));
    expect(organiser.calls).toEqual([['includeSession', run.id, 's2']]);
  });

  it('lets the organiser choose between timers', async () => {
    await open({
      sessions: [session(), session({ id: 's2', source: 'iPhone', count: 1 })],
      timer: 's1',
      issues: [issue('SEVERAL_TIMERS')],
    });
    const radios = screen.getAllByRole('radio', { name: 'Use these times' });

    expect(radios[0]).toBeChecked();
    expect(screen.getByText(/Choose whose times to use/)).toBeInTheDocument();
    await userEvent.click(radios[1]);
    expect(organiser.calls).toEqual([['chooseTimer', run.id, 's2']]);
  });

  it('warns about entries that could not be verified', async () => {
    await open({ unverified: 2 });
    expect(screen.getByText(/2 entries could not be verified and were left out/)).toBeInTheDocument();
  });
});

describe('results table', () => {
  it('shows times and finishers and flags rows with issues', async () => {
    await open({
      rows: [row(1), row(2, { athleteId: null, athleteEventId: null }), row(3, { athleteId: null, name: 'Sam' })],
      issues: [issue('MISSING_ATHLETE', { positions: [2] })],
    });
    const rows = screen.getAllByRole('row').slice(1);

    expect(rows.map((r) => r.textContent)).toEqual(['11:00A1Fix', '22:00—Fix', '33:00Sam (no barcode)Fix']);
    expect(rows[1]).toHaveClass('flagged');
    expect(rows[0]).not.toHaveClass('flagged');
    expect(screen.getByText('Nobody was recorded at position 2.')).toBeInTheDocument();
  });

  it('edits the finisher at a position', async () => {
    await open({ rows: [row(1), row(2)] });
    await userEvent.click(screen.getByRole('button', { name: 'Fix position 2' }));
    const [id] = screen.getAllByLabelText('Athlete ID');
    expect(id).toHaveValue('A2');
    expect(screen.getAllByLabelText('Token')[0]).toHaveValue('2');

    await userEvent.clear(id);
    await userEvent.type(id, 'A5');
    await userEvent.click(screen.getByRole('button', { name: 'Save finisher' }));
    expect(organiser.calls).toEqual([['editFinisher', run.id, 'a2', 2, 'A5', null]]);
    expect(screen.queryByLabelText('Athlete ID', { selector: '.row-editor input' })).not.toBeInTheDocument();
  });

  it('keeps the form open when the athlete ID is rejected', async () => {
    organiser.failing.add('editFinisher');
    await open({ rows: [row(1)] });
    await userEvent.click(screen.getByRole('button', { name: 'Fix position 1' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save finisher' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent("That isn't an athlete ID.");
    expect(alert.closest('.row-editor')).not.toBeNull();
    expect(screen.getByRole('button', { name: 'Save finisher' })).toBeInTheDocument();
  });

  it('removes a time or a finisher', async () => {
    await open({ rows: [row(1), row(2)] });
    await userEvent.click(screen.getByRole('button', { name: 'Fix position 2' }));
    await userEvent.click(screen.getByRole('button', { name: 'Remove time 2:00' }));
    expect(screen.queryByRole('button', { name: 'Remove finisher' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Fix position 2' }));
    await userEvent.click(screen.getByRole('button', { name: 'Remove finisher' }));

    expect(organiser.calls).toEqual([['removeTime', run.id, 't2'], ['removeFinisher', run.id, 'a2']]);
  });

  it('inserts an unknown finisher in scan order', async () => {
    run = await organiser.createRun('Park 2k', null, 'SCAN_ORDER');
    await open({ rows: [row(1), row(2)] });
    await userEvent.click(screen.getByRole('button', { name: 'Fix position 2' }));

    expect(screen.getAllByLabelText('Position')[0]).toHaveValue('2');
    await userEvent.click(screen.getByRole('button', { name: 'Insert unknown finisher before' }));
    expect(organiser.calls).toEqual([['addFinisher', run.id, 2, null, null]]);
  });

  it('fixes a finisher recorded without a token', async () => {
    await open({
      rows: [row(1)],
      issues: [issue('MISSING_POSITION', { finishers: [{ eventId: 'a9', athleteId: 'A9', name: null, position: null }] })],
    });
    expect(screen.getByText(/A9 was recorded without a token/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Fix A9' }));
    await userEvent.type(screen.getAllByLabelText('Token')[0], '4');
    await userEvent.click(screen.getByRole('button', { name: 'Save finisher' }));
    expect(organiser.calls).toEqual([['editFinisher', run.id, 'a9', 4, 'A9', null]]);
  });

  it('adds a missed time and a finisher', async () => {
    await open({ sessions: [session()], timer: 's1', rows: [row(1)] });
    const time = screen.getByLabelText('Time');

    await userEvent.type(time, '24');
    await userEvent.click(screen.getByRole('button', { name: 'Add time' }));
    expect(screen.getByRole('alert')).toHaveTextContent('m:ss or h:mm:ss');

    await userEvent.clear(time);
    await userEvent.type(time, '24:13');
    await userEvent.click(screen.getByRole('button', { name: 'Add time' }));
    expect(time).toHaveValue('');

    const form = screen.getByRole('region', { name: 'Add a finisher' });
    await userEvent.type(within(form).getByLabelText(/Name/), 'Sam');
    await userEvent.type(within(form).getByLabelText('Token'), '2');
    await userEvent.click(within(form).getByRole('button', { name: 'Add finisher' }));
    expect(organiser.calls).toEqual([['addTime', run.id, 1_453_000], ['addFinisher', run.id, 2, null, 'Sam']]);
  });

  it('downloads the results as CSV', async () => {
    const createObjectURL = vi.fn(() => 'blob:results');
    Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.download).toBe('riverside-5k-results.csv');
    });
    await open({ rows: [row(1)] });

    await userEvent.click(screen.getByRole('button', { name: 'Download CSV' }));
    expect(click).toHaveBeenCalled();
    expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
  });
});

describe('times', () => {
  it('parses m:ss and h:mm:ss', () => {
    expect(parseElapsed('24:13')).toBe(1_453_000);
    expect(parseElapsed(' 1:02:05 ')).toBe(3_725_000);
    expect(parseElapsed('0:59')).toBe(59_000);
    expect(parseElapsed('24')).toBeNull();
    expect(parseElapsed('1:60')).toBeNull();
    expect(parseElapsed('1:75:00')).toBeNull();
  });
});

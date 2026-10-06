import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App.tsx';
import { FakeOrganiser } from './test/FakeOrganiser.ts';
import { parseRoute } from './useHashRoute.ts';

let organiser: FakeOrganiser;

beforeEach(() => {
  organiser = new FakeOrganiser();
});

function go(hash: string) {
  act(() => {
    window.location.hash = hash;
    window.dispatchEvent(new HashChangeEvent('hashchange'));
  });
}

describe('run list', () => {
  it('explains runs when there are none', async () => {
    render(<App organiser={organiser} />);
    expect(await screen.findByText(/Organise a free, timed community run/)).toBeInTheDocument();
    expect(screen.getByText(/kept in this browser/)).toBeInTheDocument();
  });

  it('lists runs newest first with their mode', async () => {
    await organiser.createRun('Riverside 5k', '2026-10-10', 'TOKENS');
    await organiser.createRun('Park 2k', null, 'SCAN_ORDER');
    render(<App organiser={organiser} />);

    const items = await screen.findAllByRole('listitem');
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining('Park 2k'),
      expect.stringContaining('Riverside 5k'),
    ]);
    expect(items[0]).toHaveTextContent('Scan order');
    expect(items[1]).toHaveTextContent('Finish tokens');
    expect(items[1]).toHaveTextContent('2026');
  });
});

describe('new run', () => {
  it('explains both timing modes', () => {
    go('#/new');
    render(<App organiser={organiser} />);
    expect(screen.getByRole('radio', { name: /Finish tokens/ })).toBeChecked();
    expect(screen.getByText(/most robust option, but you need printed tokens/)).toBeInTheDocument();
    expect(screen.getByText(/missed or swapped scan shifts everyone behind it/)).toBeInTheDocument();
  });

  it('needs a name', async () => {
    go('#/new');
    render(<App organiser={organiser} />);
    await userEvent.click(screen.getByRole('button', { name: 'Create run' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Give the run a name.');
    expect(organiser.runs).toEqual([]);
  });

  it('creates a run with the chosen date and mode, then opens it', async () => {
    go('#/new');
    render(<App organiser={organiser} />);
    await userEvent.type(screen.getByLabelText('Name'), '  Riverside 5k ');
    await userEvent.type(screen.getByLabelText(/Date/), '2026-10-10');
    await userEvent.click(screen.getByRole('radio', { name: /Scan order/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Create run' }));

    expect(organiser.runs).toMatchObject([{ name: 'Riverside 5k', date: '2026-10-10', format: 'SCAN_ORDER' }]);
    expect(window.location.hash).toBe('#/runs/run-1');
  });
});

describe('run page', () => {
  it('shows the join code and the scan-order roles', async () => {
    const run = await organiser.createRun('Riverside 5k', null, 'SCAN_ORDER');
    go(`#/runs/${run.id}`);
    render(<App organiser={organiser} />);

    const qr = await screen.findByRole('img', { name: 'Join code for Riverside 5k' });
    expect(qr.querySelector('path')).toHaveAttribute('d', 'M0 0h1v1h-1z');
    expect(qr).toHaveAttribute('viewBox', '-4 -4 29 29');
    expect(screen.getByRole('heading', { name: 'Funnel marshal' })).toBeInTheDocument();
    expect(screen.getByText(/in the order they leave the funnel/)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Token handout' })).not.toBeInTheDocument();
  });

  it('shows the token roles in tokens mode', async () => {
    const run = await organiser.createRun('Riverside 5k', null, 'TOKENS');
    go(`#/runs/${run.id}`);
    render(<App organiser={organiser} />);

    expect(await screen.findByRole('heading', { name: 'Token handout' })).toBeInTheDocument();
    expect(screen.getByText(/then their finish token/)).toBeInTheDocument();
  });

  it('prints only the run sheet', async () => {
    const run = await organiser.createRun('Riverside 5k', null, 'TOKENS');
    go(`#/runs/${run.id}`);
    const print = vi.spyOn(window, 'print').mockImplementation(() => {});
    const { container } = render(<App organiser={organiser} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Print run sheet' }));
    expect(print).toHaveBeenCalled();
    expect(container.querySelector('.app-header')).toHaveClass('no-print');
    expect(screen.getByRole('button', { name: 'Print run sheet' }).closest('.no-print')).not.toBeNull();
    expect(container.querySelector('.run-sheet')?.closest('.no-print')).toBeNull();
  });

  it('deletes only after confirming', async () => {
    const run = await organiser.createRun('Riverside 5k', null, 'TOKENS');
    go(`#/runs/${run.id}`);
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    render(<App organiser={organiser} />);
    const remove = await screen.findByRole('button', { name: 'Delete run' });

    await userEvent.click(remove);
    expect(organiser.runs).toHaveLength(1);

    await userEvent.click(remove);
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('Riverside 5k'));
    expect(organiser.runs).toEqual([]);
    expect(window.location.hash).toBe('#/');
  });

  it('says when a run is not stored here', async () => {
    go('#/runs/elsewhere');
    render(<App organiser={organiser} />);
    expect(await screen.findByRole('heading', { name: 'Run not found' })).toBeInTheDocument();
  });
});

describe('routes', () => {
  it('parses hashes', () => {
    expect(parseRoute('')).toEqual({ page: 'runs' });
    expect(parseRoute('#/new')).toEqual({ page: 'new' });
    expect(parseRoute('#/runs/a%20b')).toEqual({ page: 'run', id: 'a b' });
    expect(parseRoute('#/runs/a%20b/results')).toEqual({ page: 'results', id: 'a b' });
    expect(parseRoute('#/runs/a/tokens')).toEqual({ page: 'tokens', id: 'a' });
    expect(parseRoute('#/unknown')).toEqual({ page: 'runs' });
  });
});

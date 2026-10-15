import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App.tsx';
import { FakeOrganiser } from '../test/FakeOrganiser.ts';
import { parseRange } from './TokensPage.tsx';

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

describe('finish tokens', () => {
  it('are linked from token runs only', async () => {
    const tokens = await organiser.createRun('Riverside 5k', null, 'TOKENS');
    const scanOrder = await organiser.createRun('Park 2k', null, 'SCAN_ORDER');
    go(`#/runs/${tokens.id}`);
    const { unmount } = render(<App organiser={organiser} />);
    expect(await screen.findByRole('link', { name: 'Finish tokens' })).toHaveAttribute('href', `#/runs/${tokens.id}/tokens`);
    unmount();

    go(`#/runs/${scanOrder.id}`);
    render(<App organiser={organiser} />);
    await screen.findByRole('link', { name: 'Results' });
    expect(screen.queryByRole('link', { name: 'Finish tokens' })).not.toBeInTheDocument();
  });

  it('prints cards with the number and barcode for the chosen range', async () => {
    const run = await organiser.createRun('Riverside 5k', null, 'TOKENS');
    go(`#/runs/${run.id}/tokens`);
    const print = vi.spyOn(window, 'print').mockImplementation(() => {});
    const { container } = render(<App organiser={organiser} />);

    expect(await screen.findByText('100 tokens on 7 pages')).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('From'));
    await userEvent.type(screen.getByLabelText('From'), '41');
    await userEvent.clear(screen.getByLabelText('To'));
    await userEvent.type(screen.getByLabelText('To'), '42');

    const cards = container.querySelectorAll('.token-card');
    expect([...cards].map((card) => card.textContent)).toEqual(['41P0041', '42P0042']);
    expect(screen.getByRole('img', { name: 'Barcode P0042' }).querySelector('path')).toHaveAttribute('d', 'M0 0h2v1h-2z');
    expect(container.querySelector('.token-sheet')?.closest('.no-print')).toBeNull();
    expect(screen.getByLabelText('From').closest('.no-print')).not.toBeNull();

    await userEvent.click(screen.getByRole('button', { name: 'Print tokens' }));
    expect(print).toHaveBeenCalled();
  });

  it('sets the paper size for printing', async () => {
    const run = await organiser.createRun('Riverside 5k', null, 'TOKENS');
    go(`#/runs/${run.id}/tokens`);
    const { container } = render(<App organiser={organiser} />);

    await screen.findByLabelText('Paper');
    expect(container.querySelector('style')).toHaveTextContent('size: A4');
    await userEvent.selectOptions(screen.getByLabelText('Paper'), 'US Letter');
    expect(container.querySelector('style')).toHaveTextContent('size: letter');
  });

  it('explains a range it cannot print', async () => {
    const run = await organiser.createRun('Riverside 5k', null, 'TOKENS');
    go(`#/runs/${run.id}/tokens`);
    const { container } = render(<App organiser={organiser} />);
    await userEvent.clear(await screen.findByLabelText('To'));
    await userEvent.type(screen.getByLabelText('To'), '2000');

    expect(screen.getByRole('alert')).toHaveTextContent('Print at most 1000 tokens at a time.');
    expect(container.querySelectorAll('.token-card')).toHaveLength(0);
    expect(screen.getByRole('button', { name: 'Print tokens' })).toBeDisabled();
  });

  it('says scan-order runs need no tokens', async () => {
    const run = await organiser.createRun('Park 2k', null, 'SCAN_ORDER');
    go(`#/runs/${run.id}/tokens`);
    render(<App organiser={organiser} />);
    expect(await screen.findByText(/needs no tokens/)).toBeInTheDocument();
  });

  it('checks ranges', () => {
    expect(parseRange('1', '300')).toEqual({ from: 1, to: 300 });
    expect(parseRange('', '3')).toBe('Enter whole numbers.');
    expect(parseRange('1.5', '3')).toBe('Enter whole numbers.');
    expect(parseRange('0', '3')).toBe('Tokens run from 1 to 99999.');
    expect(parseRange('99999', '100000')).toBe('Tokens run from 1 to 99999.');
    expect(parseRange('5', '4')).toBe('"To" must not be below "From".');
  });
});

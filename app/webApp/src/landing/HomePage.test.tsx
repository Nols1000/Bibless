import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../App.tsx';
import { FakeOrganiser } from '../test/FakeOrganiser.ts';

let organiser: FakeOrganiser;
beforeEach(() => { organiser = new FakeOrganiser(); });

function go(hash: string) {
  act(() => {
    window.location.hash = hash;
    window.dispatchEvent(new HashChangeEvent('hashchange'));
  });
}

describe('shared Home page', () => {
  it('introduces the wallet and organiser and keeps the existing beta links', async () => {
    render(<App organiser={organiser} />);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Your barcode.Your whole run.');
    expect(screen.getByRole('link', { name: 'Join the Android beta' })).toHaveAttribute('href', 'https://groups.google.com/g/bibless-testers');
    expect(screen.getByRole('link', { name: 'Create a run' })).toHaveAttribute('href', '#/new');
    expect(screen.getByRole('link', { name: 'Become a tester' })).toHaveAttribute('href', 'https://play.google.com/apps/testing/com.github.nols1000.bibless');
    expect(screen.getByRole('link', { name: 'Join the TestFlight beta' })).toHaveAttribute('href', 'https://testflight.apple.com/join/hd8FDfMY');
    expect(screen.getByRole('img', { name: /QR code on a phone/ }).querySelector('image')).toHaveAttribute('href', '/screens/phone/barcode.png');
    expect(await screen.findByRole('link', { name: 'Create your first run' })).toBeInTheDocument();
  });

  it('shows the three newest saved runs with their dates and timing modes', async () => {
    await organiser.createRun('Oldest run', null, 'TOKENS');
    await organiser.createRun('Park 2k', null, 'SCAN_ORDER');
    await organiser.createRun('Riverside 5k', '2026-10-10', 'TOKENS');
    await organiser.createRun('Newest run', null, 'TOKENS');
    render(<App organiser={organiser} />);
    const region = within(screen.getByRole('region', { name: 'Back to your runs.' }));
    const runs = await region.findAllByRole('listitem');
    expect(runs.map((item) => item.textContent)).toEqual([
      expect.stringContaining('Newest run'), expect.stringContaining('Riverside 5k'), expect.stringContaining('Park 2k'),
    ]);
    expect(runs[1]).toHaveTextContent('2026');
    expect(runs[2]).toHaveTextContent('Scan order');
    expect(region.queryByText('Oldest run')).not.toBeInTheDocument();
  });

  it('shows both platform previews and beta links in one hero without a toggle', async () => {
    render(<App organiser={organiser} />);
    const hero = within(screen.getByRole('region', { name: /Your barcode/ }));
    expect(hero.getByRole('img', { name: /QR code on a phone/ })).toHaveAttribute('data-device', 'android');
    expect(hero.getByRole('img', { name: /QR code on a Wear OS watch/ })).toHaveAttribute('data-device', 'wearos');
    expect(hero.getByRole('img', { name: /QR code on iPhone/ })).toHaveAttribute('data-device', 'ios');
    expect(hero.getByRole('img', { name: /QR code on Apple Watch Series 11/ })).toHaveAttribute('data-device', 'watchos');
    expect(hero.getByRole('img', { name: /QR code on Apple Watch Ultra 3/ })).toHaveAttribute('data-device', 'watchos-ultra');
    expect(hero.getByRole('link', { name: 'Join the TestFlight beta' })).toHaveAttribute('href', 'https://testflight.apple.com/join/hd8FDfMY');
    expect(hero.getByRole('link', { name: 'Join the Android beta' })).toHaveAttribute('href', 'https://groups.google.com/g/bibless-testers');
    expect(screen.queryByRole('group', { name: 'Choose a device preview' })).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Apple Watch model' })).not.toBeInTheDocument();
    await screen.findByRole('link', { name: 'Create your first run' });
  });

  it('keeps Home available while recent runs are loading or fail, and supports retry', async () => {
    vi.spyOn(organiser, 'listRuns').mockRejectedValueOnce(new Error('Storage unavailable'));
    render(<App organiser={organiser} />);
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Loading your recent runs');
    expect(await screen.findByRole('alert')).toHaveTextContent('couldn’t load');
    expect(screen.getByRole('link', { name: 'Join the Android beta' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByRole('link', { name: 'Create your first run' })).toBeInTheDocument();
  });

  it('refreshes Home after creating and deleting a run without reloading the app', async () => {
    render(<App organiser={organiser} />);
    await userEvent.click(screen.getByRole('link', { name: 'Create a run' }));
    await screen.findByRole('heading', { name: 'New run' });
    await userEvent.type(screen.getByLabelText('Name'), 'Riverside 5k');
    await userEvent.click(screen.getByRole('button', { name: 'Create run' }));
    await screen.findByRole('heading', { name: 'Riverside 5k' });
    await userEvent.click(screen.getByRole('link', { name: 'Home' }));
    const run = await screen.findByRole('link', { name: /Finish tokens Riverside 5k/ });
    await userEvent.click(run);
    await screen.findByRole('heading', { name: 'Riverside 5k' });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    await userEvent.click(screen.getByRole('button', { name: 'Delete run' }));
    await screen.findByRole('heading', { name: 'Your runs' });
    await userEvent.click(screen.getByRole('link', { name: 'Home' }));
    expect(await screen.findByRole('link', { name: 'Create your first run' })).toBeInTheDocument();
  });

  it('opens and focuses a Home section from the management UI', async () => {
    go('#/runs');
    render(<App organiser={organiser} />);
    await screen.findByRole('heading', { name: 'Your runs' });
    await userEvent.click(screen.getByRole('link', { name: 'Get the app' }));
    const heading = await screen.findByRole('heading', { level: 1 });
    expect(heading).toHaveFocus();
    expect(Element.prototype.scrollIntoView).toHaveBeenCalled();
    expect(screen.getByRole('link', { name: 'Home' })).toHaveAttribute('aria-current', 'page');
    await screen.findByRole('link', { name: 'Create your first run' });
    go('#/runs');
    await screen.findByRole('heading', { name: 'Your runs' });
    go('#apple');
    expect(await screen.findByRole('heading', { level: 1 })).toHaveFocus();
    go('#beta');
    expect(await screen.findByRole('heading', { name: /See you at/ })).toHaveFocus();
  });
});

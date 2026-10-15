import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it, vi } from 'vitest';
import { RunOrganiser } from 'sharedLogic';
import { App } from './App.tsx';
import { FakeOrganiser } from './test/FakeOrganiser.ts';

vi.mock('sharedLogic', () => ({ RunOrganiser: { create: vi.fn() } }));

it('renders Home before loading the organiser, retries failure and reuses the same instance', async () => {
  const organiser = new FakeOrganiser();
  vi.mocked(RunOrganiser.create).mockImplementationOnce(() => { throw new Error('Storage unavailable'); })
    .mockReturnValue(organiser as unknown as ReturnType<typeof RunOrganiser.create>);
  const { unmount } = render(<App />);
  expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument();
  expect(await screen.findByRole('alert')).toHaveTextContent('couldn’t load');
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
  await screen.findByRole('link', { name: 'Create your first run' });
  expect(RunOrganiser.create).toHaveBeenCalledTimes(2);
  unmount();
  render(<App />);
  await screen.findByRole('link', { name: 'Create your first run' });
  expect(RunOrganiser.create).toHaveBeenCalledTimes(2);
});

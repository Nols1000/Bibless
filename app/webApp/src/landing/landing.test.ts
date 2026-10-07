import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import html from '../../index.html?raw';
import { initLanding, type Landing } from './landing.ts';

function activeScreen(selector: string) {
  return document.querySelector<HTMLImageElement>(`${selector} img.screen.is-active`)?.dataset.screen;
}

function pick(name: string, value: string) {
  document.querySelector<HTMLInputElement>(`input[name="${name}"][value="${value}"]`)!.click();
}

describe('landing page', () => {
  let landing: Landing;

  function load(reducedMotion = false) {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: reducedMotion && query.includes('reduced-motion'),
    })) as unknown as typeof window.matchMedia;
    document.body.innerHTML = new DOMParser().parseFromString(html, 'text/html').body.innerHTML;
    // jsdom lays nothing out; put every step below the reading line, so scrolling keeps the first
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({ top: 10_000 } as DOMRect);
    landing = initLanding(document.body, '/base/');
    vi.advanceTimersByTime(20); // the first scroll check
  }

  beforeEach(() => vi.useFakeTimers());

  afterEach(() => {
    landing.destroy();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('opens on the code on both devices, from the base path', () => {
    load();
    expect(activeScreen('.captures')).toBe('barcode');
    expect(activeScreen('.watch-screen')).toBe('qr');
    expect(document.querySelector<HTMLImageElement>('.captures img')!.getAttribute('src')).toMatch(/^\/base\/screens\/phone\//);
  });

  it('sets the format per device', () => {
    load();
    landing.setStep('formats');
    pick('watchFormat', 'code128');
    expect(activeScreen('.watch-screen')).toBe('barcode');
    expect(activeScreen('.captures')).toBe('barcode');
    pick('phoneFormat', 'code128');
    expect(activeScreen('.captures')).toBe('barcode-code128');
  });

  it('flips the formats by itself until one is picked', () => {
    load();
    landing.setStep('formats');
    vi.advanceTimersByTime(2200);
    expect(activeScreen('.captures')).toBe('barcode-code128');
    expect(document.querySelector<HTMLInputElement>('input[name="phoneFormat"][value="code128"]')!.checked).toBe(true);

    pick('phoneFormat', 'qr');
    vi.advanceTimersByTime(10_000);
    expect(activeScreen('.captures')).toBe('barcode');
    expect(activeScreen('.watch-screen')).toBe('qr');
  });

  it('keeps still with reduced motion', () => {
    load(true);
    landing.setStep('formats');
    vi.advanceTimersByTime(10_000);
    expect(activeScreen('.captures')).toBe('barcode');
  });

  it('shows the dark theme with the code still on white', () => {
    load();
    landing.setStep('themes');
    pick('theme', 'dark');
    expect(activeScreen('.captures')).toBe('barcode-dark');
    expect(document.querySelector('.phone')!.classList.contains('is-dark')).toBe(true);
  });

  it('opens a barcode from the list and goes back', () => {
    load();
    landing.setStep('family');
    expect(activeScreen('.captures')).toBe('list');
    expect(document.querySelector('.watch')!.classList.contains('is-scroll')).toBe(true);

    document.querySelector<HTMLButtonElement>('.hotspot[data-action="open"]')!.click();
    expect(activeScreen('.captures')).toBe('barcode');
    expect(document.querySelector('[data-family]')!.textContent).toBe('Back to the list');

    document.querySelector<HTMLButtonElement>('.hotspot[data-action="back"]')!.click();
    expect(activeScreen('.captures')).toBe('list');
  });

  it('syncs the new barcode to the phone after the pulse', () => {
    load();
    landing.setStep('sync');
    expect(activeScreen('.watch-screen')).toBe('add');
    expect(activeScreen('.captures')).toBe('list');
    vi.advanceTimersByTime(1400);
    expect(activeScreen('.captures')).toBe('list-synced');
  });

  it('switches the community run screens and links to the organiser', () => {
    load();
    landing.setStep('runs');
    expect(activeScreen('.watch-screen')).toBe('timer');
    pick('runs', 'scanner');
    expect(activeScreen('.captures')).toBe('scanner');
    expect(document.querySelector('a[href="./organise/"]')).not.toBeNull();
  });

  it('hides the inactive screens from assistive tech', () => {
    load();
    const hidden = [...document.querySelectorAll('.captures img')].filter((img) => img.getAttribute('aria-hidden') === 'false');
    expect(hidden.map((img) => (img as HTMLElement).dataset.screen)).toEqual(['barcode']);
  });
});

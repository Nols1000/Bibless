import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import html from '../../index.html?raw';
import { initLanding, nextParkrun, type Landing } from './landing.ts';

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

  it('sends people to the Android testers group, not the closed Play listing', () => {
    load();
    const hero = document.querySelector<HTMLAnchorElement>('.step-hero .cta')!;
    expect(hero.href).toBe('https://groups.google.com/g/bibless-testers');
    expect(document.querySelector('.topbar .cta')!.getAttribute('href')).toBe('#beta');
    expect(document.querySelector('#beta')!.textContent).toContain('iPhone and Apple Watch');
  });

  it('scans the watch and adds Me to the finish on a loop', () => {
    load();
    const finish = document.querySelector('.finish')!;
    const stage = document.querySelector('.stage')!;
    expect(document.querySelector('.step-hero h1')!.textContent).toBe('No bibs. No printouts. Just your wrist.');
    expect(finish.classList.contains('is-scanned')).toBe(false);

    vi.advanceTimersByTime(1750);
    expect(stage.classList.contains('is-scanning')).toBe(true);
    vi.advanceTimersByTime(1000);
    expect(stage.classList.contains('is-scanning')).toBe(false);
    expect(finish.classList.contains('is-scanned')).toBe(true);
    expect(finish.querySelector('.is-me')!.textContent).toBe('P42Me24:26');
    expect(document.querySelector('[data-finish-clock]')!.textContent).toBe('24:26');

    vi.advanceTimersByTime(4500);
    expect(finish.classList.contains('is-scanned')).toBe(false);
    expect(document.querySelector('[data-finish-clock]')!.textContent).toBe('24:24');
  });

  it('shows the scanned finish without moving, with reduced motion', () => {
    load(true);
    vi.advanceTimersByTime(10_000);
    expect(document.querySelector('.finish')!.classList.contains('is-scanned')).toBe(true);
    expect(document.querySelector('.stage')!.classList.contains('is-scanning')).toBe(false);
  });

  it('counts down to Saturday 9am', () => {
    vi.setSystemTime(new Date(2026, 9, 8, 18, 30)); // a Thursday
    load();
    expect(document.querySelector('[data-countdown]')!.textContent).toBe('Next parkrun in 1d 14h 30m');
  });

  it('hides the inactive screens from assistive tech', () => {
    load();
    const hidden = [...document.querySelectorAll('.captures img')].filter((img) => img.getAttribute('aria-hidden') === 'false');
    expect(hidden.map((img) => (img as HTMLElement).dataset.screen)).toEqual(['barcode']);
  });
});

describe('nextParkrun', () => {
  const at = (day: number, h: number, m = 0) => new Date(2026, 9, day, h, m); // October 2026: the 10th is a Saturday

  it('counts down in days, hours and minutes', () => {
    expect(nextParkrun(at(8, 18, 30))).toBe('Next parkrun in 1d 14h 30m');
    expect(nextParkrun(at(10, 8, 0))).toBe('Next parkrun in 1h 00m');
    expect(nextParkrun(at(10, 8, 59))).toBe('Next parkrun in 01m');
  });

  it('is on from 9am until half past ten', () => {
    expect(nextParkrun(at(10, 9, 0))).toBe('parkrun is on now');
    expect(nextParkrun(at(10, 10, 29))).toBe('parkrun is on now');
  });

  it('moves on to next week once it is over', () => {
    expect(nextParkrun(at(10, 10, 30))).toBe('Next parkrun in 6d 22h 30m');
    expect(nextParkrun(at(11, 9, 0))).toBe('Next parkrun in 6d 0h 00m');
  });
});

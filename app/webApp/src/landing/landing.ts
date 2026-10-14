import { PHONE, watchFrame } from './frames.ts';

/** Phone screens, as captured by SiteScreenshotTest in app/androidApp (1080x2400). */
export const PHONE_SCREENS = {
  list: { alt: 'The barcode list: Me, Sam and Jamie (junior)', dark: false },
  'list-synced': { alt: 'The barcode list with Alex, added on the watch', dark: false },
  barcode: { alt: 'The QR code for athlete A0123456', dark: false },
  'barcode-code128': { alt: 'The barcode for athlete A0123456', dark: false },
  'barcode-dark': { alt: 'The barcode for athlete A0123456 in dark mode, still black on white', dark: true },
  settings: { alt: 'Settings: the format per device and what the app opens on', dark: false },
  timer: { alt: 'The finish timer at 17:31 with three finishers', dark: false },
  scanner: { alt: 'The finish scanner pairing barcodes with finish tokens', dark: false },
  sessions: { alt: 'Recorded sessions, ready to share with the organiser', dark: false },
} as const;

/** Watch screens, as captured by SiteScreenshotTest in app/wearApp (454x454, list-tall 336x752). */
export const WATCH_SCREENS = {
  list: 'The barcode list on the watch',
  qr: 'The QR code on the watch',
  barcode: 'The barcode on the watch',
  add: 'Adding a barcode for Alex on the watch',
  settings: 'Watch setting: open on the first barcode',
  timer: 'The watch timer with its big Finish button',
} as const;

export type PhoneScreen = keyof typeof PHONE_SCREENS;
export type WatchScreen = keyof typeof WATCH_SCREENS;

interface View {
  phone: PhoneScreen;
  watch: WatchScreen | 'scroll';
}

/** What each step shows, given the choices made on the page. */
export interface Choices {
  phoneFormat: 'qr' | 'code128';
  watchFormat: 'qr' | 'code128';
  theme: 'light' | 'dark';
  familyOpen: boolean;
  synced: boolean;
  runs: 'timer' | 'scanner' | 'sessions';
}

export const STEPS: Record<string, (c: Choices) => View> = {
  hero: () => ({ phone: 'barcode', watch: 'qr' }),
  formats: (c) => ({
    phone: c.phoneFormat === 'qr' ? 'barcode' : 'barcode-code128',
    watch: c.watchFormat === 'qr' ? 'qr' : 'barcode',
  }),
  themes: (c) => ({ phone: c.theme === 'dark' ? 'barcode-dark' : 'barcode-code128', watch: 'barcode' }),
  family: (c) => ({ phone: c.familyOpen ? 'barcode' : 'list', watch: 'scroll' }),
  sync: (c) => ({ phone: c.synced ? 'list-synced' : 'list', watch: 'add' }),
  faster: () => ({ phone: 'settings', watch: 'settings' }),
  privacy: () => ({ phone: 'barcode', watch: 'qr' }),
  runs: (c) => ({ phone: c.runs, watch: 'timer' }),
};

/** Where to tap on a phone screen, in fractions of the 1080x2400 capture. */
const HOTSPOTS: Partial<Record<PhoneScreen, { label: string; box: [number, number, number, number]; action: string }[]>> = {
  list: [{ label: 'Open the barcode for Me', box: [0, 0.07, 0.78, 0.065], action: 'open' }],
  barcode: [{ label: 'Back to the list', box: [0, 0, 0.14, 0.06], action: 'back' }],
};

/** The scanner's camera area in the capture, which the page animates. */
const CAMERA: [number, number, number, number] = [0.037, 0.0833, 0.926, 0.664];

function pct(n: number) {
  return `${(n * 100).toFixed(4)}%`;
}

/** Fractions of the capture to the phone screen box, which adds the status and gesture bars. */
function captureBox([x, y, w, h]: [number, number, number, number]) {
  const p = PHONE;
  return {
    left: pct(x),
    top: pct((p.statusBar + y * p.capture) / p.screenHeight),
    width: pct(w),
    height: pct((h * p.capture) / p.screenHeight),
  };
}

function el<K extends keyof HTMLElementTagNameMap>(tag: K, className: string, html = ''): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  node.className = className;
  if (html) node.innerHTML = html;
  return node;
}

function screenImage(src: string, alt: string, width: number, height: number, name: string) {
  const img = el('img', 'screen');
  img.src = src;
  img.alt = alt;
  img.width = width;
  img.height = height;
  img.decoding = 'async';
  img.dataset.screen = name;
  img.setAttribute('aria-hidden', 'true');
  return img;
}

function buildPhone(base: string, onHotspot: (action: string) => void) {
  const p = PHONE;
  const phone = el('div', 'phone');
  phone.style.setProperty('--phone-aspect', `${p.width} / ${p.screenHeight}`);
  // The bare screen, without a drawn phone around it
  const screen = el('div', 'phone-screen');
  Object.assign(screen.style, {
    inset: '0',
    borderRadius: `${pct(p.screenRadius / p.width)} / ${pct(p.screenRadius / p.screenHeight)}`,
  });
  screen.append(
    el('div', 'status-bar', '<span class="clock">8:59</span><span class="icons" aria-hidden="true">' +
      '<svg viewBox="0 0 24 24"><path d="M12 21 1 8a16 16 0 0 1 22 0z"/></svg>' +
      '<svg viewBox="0 0 24 24"><path d="M2 22 22 2v20z"/></svg>' +
      '<svg viewBox="0 0 24 24"><rect x="7" y="2" width="10" height="20" rx="2"/></svg></span>'),
  );
  const captures = el('div', 'captures');
  Object.assign(captures.style, {
    top: pct(p.statusBar / p.screenHeight),
    height: pct(p.capture / p.screenHeight),
  });
  for (const [name, { alt }] of Object.entries(PHONE_SCREENS)) {
    captures.append(screenImage(`${base}screens/phone/${name}.png`, alt, 1080, 2400, name));
  }
  screen.append(captures);

  const camera = el('div', 'camera-scan');
  Object.assign(camera.style, captureBox(CAMERA));
  screen.append(camera);
  screen.append(el('div', 'flash'));
  screen.append(el('div', 'gesture-bar'));

  for (const [name, spots] of Object.entries(HOTSPOTS)) {
    for (const spot of spots ?? []) {
      const button = el('button', 'hotspot');
      button.type = 'button';
      button.dataset.on = name;
      button.dataset.action = spot.action;
      button.setAttribute('aria-label', spot.label);
      Object.assign(button.style, captureBox(spot.box));
      button.addEventListener('click', () => onHotspot(spot.action));
      screen.append(button);
    }
  }
  phone.append(screen);
  return phone;
}

function buildWatch(base: string) {
  const watch = el('div', 'watch');
  const round = el('div', 'watch-round');
  const face = el('div', 'watch-screen');
  for (const [name, alt] of Object.entries(WATCH_SCREENS)) {
    face.append(screenImage(`${base}screens/watch/${name}.png`, alt, 454, 454, name));
  }
  round.append(face);
  round.insertAdjacentHTML('beforeend', watchFrame('round'));

  const scroll = el('div', 'watch-scroll');
  const strip = el('div', 'watch-strip');
  strip.append(screenImage(`${base}screens/watch/list-tall.png`, 'The barcode list scrolling on the watch', 336, 752, 'scroll'));
  scroll.append(strip);
  scroll.insertAdjacentHTML('beforeend', watchFrame('scroll'));

  watch.append(round, scroll);
  return watch;
}

/** The pulse that carries a new barcode from the watch to the phone. */
const SYNC_ARC = `<svg class="sync-arc" viewBox="0 0 100 125" preserveAspectRatio="none" aria-hidden="true">
  <path class="track" d="M76 70 C 74 34, 52 26, 40 36" vector-effect="non-scaling-stroke"/>
  <path class="pulse" d="M76 70 C 74 34, 52 26, 40 36" pathLength="100" vector-effect="non-scaling-stroke"/>
</svg>`;

/**
 * The countdown in the hero: parkruns start at 9am on Saturdays, local time, and the finish funnel
 * stays busy until about half past ten.
 */
export function nextParkrun(now: Date): string {
  const start = new Date(now);
  start.setHours(9, 0, 0, 0);
  start.setDate(start.getDate() + ((6 - start.getDay() + 7) % 7));
  const end = start.getTime() + 90 * 60_000;
  if (now.getTime() >= start.getTime() && now.getTime() < end) return 'parkrun is on now';
  if (now.getTime() >= end) start.setDate(start.getDate() + 7);
  const minutes = Math.ceil((start.getTime() - now.getTime()) / 60_000);
  const d = Math.floor(minutes / 1440);
  const h = Math.floor((minutes % 1440) / 60);
  const m = String(minutes % 60).padStart(2, '0');
  return `Next parkrun in ${d ? `${d}d ` : ''}${d || h ? `${h}h ` : ''}${m}m`;
}

/**
 * The hero's finish: the race clock runs, the volunteer's scanner frames the watch's QR code, and
 * Me lands in the results behind Jamie and Sam. One loop is [FINISH_LOOP] ms long.
 */
const FINISH_LOOP = 7000;
const FINISH_START = 24 * 60 + 24;
const SCAN_AT = 1500;
const SCANNED_AT = 2600;
const FADE_AT = 6400;

function formatClock(seconds: number) {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

function buildFinish() {
  const row = (pos: string, name: string, time: string, extra = '') =>
    `<li class="${extra}"><span class="finish-pos">${pos}</span><span class="finish-name">${name}</span><span class="finish-time">${time}</span></li>`;
  return el('div', 'finish', `<div class="finish-head"><span class="finish-label">Finish clock</span>` +
    `<span class="finish-clock" data-finish-clock>${formatClock(FINISH_START)}</span></div>` +
    `<ol class="finish-rows">${row('P40', 'Jamie (junior)', '24:05')}${row('P41', 'Sam', '24:19')}` +
    row('P42', 'Me', formatClock(FINISH_START + Math.floor(SCANNED_AT / 1000)), 'is-me') + '</ol>');
}

export interface Landing {
  choices: Choices;
  step: string;
  setStep(step: string): void;
  update(): void;
  destroy(): void;
}

export function initLanding(root: HTMLElement, base = import.meta.env.BASE_URL): Landing {
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  const stage = root.querySelector<HTMLElement>('.stage')!;
  const devices = stage.querySelector<HTMLElement>('.devices')!;
  const steps = [...root.querySelectorAll<HTMLElement>('.step[data-step]')];
  const timers: number[] = [];

  const choices: Choices = {
    phoneFormat: 'qr',
    watchFormat: 'qr',
    theme: 'light',
    familyOpen: false,
    synced: false,
    runs: 'timer',
  };

  const phone = buildPhone(base, (action) => {
    choices.familyOpen = action === 'open';
    syncControls();
    update();
  });
  const watch = buildWatch(base);
  devices.append(phone, watch);
  devices.insertAdjacentHTML('beforeend', SYNC_ARC);
  const finish = buildFinish();
  finish.setAttribute('aria-hidden', 'true');
  devices.append(finish);
  // A viewfinder that closes in around the watch face, without ever covering the code
  watch.querySelector('.watch-round')!.append(el('div', 'watch-scan'));

  const landing: Landing = {
    choices,
    step: steps[0]?.dataset.step ?? 'hero',
    setStep(step) {
      if (step === landing.step) return;
      landing.step = step;
      if (step === 'sync') replaySync();
      update();
    },
    update,
    destroy() {
      timers.forEach((t) => window.clearInterval(t));
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onScroll);
    },
  };

  function show(container: Element, name: string) {
    for (const img of container.querySelectorAll<HTMLImageElement>(':scope > img.screen')) {
      const active = img.dataset.screen === name;
      img.classList.toggle('is-active', active);
      img.setAttribute('aria-hidden', String(!active));
    }
  }

  function update() {
    const view = (STEPS[landing.step] ?? STEPS.hero)(choices);
    stage.dataset.step = landing.step;
    phone.dataset.screen = view.phone;
    phone.classList.toggle('is-dark', PHONE_SCREENS[view.phone].dark);
    show(phone.querySelector('.captures')!, view.phone);
    watch.classList.toggle('is-scroll', view.watch === 'scroll');
    if (view.watch !== 'scroll') show(watch.querySelector('.watch-screen')!, view.watch);
    for (const step of steps) step.classList.toggle('is-active', step.dataset.step === landing.step);
  }

  // The sync step plays once on arrival and again from its button: the pulse, then the new row
  function replaySync() {
    choices.synced = false;
    stage.classList.remove('is-syncing');
    void stage.offsetWidth; // restart the CSS animation
    stage.classList.add('is-syncing');
    update();
    window.setTimeout(() => {
      choices.synced = true;
      update();
    }, reducedMotion ? 0 : 1400);
  }

  function syncControls() {
    for (const input of root.querySelectorAll<HTMLInputElement>('input[data-choice]')) {
      input.checked = String(choices[input.dataset.choice as keyof Choices]) === input.value;
    }
    for (const button of root.querySelectorAll<HTMLButtonElement>('button[data-family]')) {
      button.textContent = choices.familyOpen ? 'Back to the list' : 'Open “Me”';
    }
  }

  // Segmented controls: radios named after the choice they set
  root.addEventListener('change', (event) => {
    const input = event.target as HTMLInputElement;
    const key = input.dataset.choice as keyof Choices | undefined;
    if (!key) return;
    (choices as unknown as Record<string, string>)[key] = input.value;
    if (key === 'phoneFormat' || key === 'watchFormat') stopCycling();
    if (key === 'theme' && input.value === 'light') {
      phone.classList.remove('is-flashing');
      void phone.offsetWidth;
      phone.classList.add('is-flashing');
    }
    update();
  });
  root.addEventListener('click', (event) => {
    const target = (event.target as HTMLElement).closest<HTMLElement>('[data-family], [data-replay-sync]');
    if (!target) return;
    if (target.matches('[data-family]')) {
      choices.familyOpen = !choices.familyOpen;
      syncControls();
      update();
    } else {
      replaySync();
    }
  });

  // The formats step flips the codes by itself until someone picks one
  let cycle = 0;
  if (!reducedMotion) {
    cycle = window.setInterval(() => {
      if (landing.step !== 'formats') return;
      if (choices.phoneFormat === choices.watchFormat) {
        choices.phoneFormat = choices.phoneFormat === 'qr' ? 'code128' : 'qr';
      } else {
        choices.watchFormat = choices.watchFormat === 'qr' ? 'code128' : 'qr';
      }
      syncControls();
      update();
    }, 2200);
    timers.push(cycle);
  }
  function stopCycling() {
    window.clearInterval(cycle);
  }

  // The hero's finish scan, on a loop while the hero is on stage; with reduced motion, its end state
  const clock = finish.querySelector<HTMLElement>('[data-finish-clock]')!;
  let loopStart = Date.now();
  function finishTick() {
    if (landing.step !== 'hero') {
      loopStart = Date.now();
      return;
    }
    let t = Date.now() - loopStart;
    if (t >= FINISH_LOOP) {
      loopStart += FINISH_LOOP * Math.floor(t / FINISH_LOOP);
      t = Date.now() - loopStart;
    }
    clock.textContent = formatClock(FINISH_START + Math.floor(t / 1000));
    stage.classList.toggle('is-scanning', t >= SCAN_AT && t < SCANNED_AT);
    finish.classList.toggle('is-scanned', t >= SCANNED_AT && t < FADE_AT);
  }
  if (reducedMotion) {
    clock.textContent = formatClock(FINISH_START + Math.floor(SCANNED_AT / 1000));
    finish.classList.add('is-scanned');
  } else {
    timers.push(window.setInterval(finishTick, 250));
  }

  const countdowns = [...root.querySelectorAll<HTMLElement>('[data-countdown]')];
  function countdown() {
    const text = nextParkrun(new Date());
    countdowns.forEach((node) => (node.textContent = text));
  }
  countdown();
  timers.push(window.setInterval(countdown, 30_000));

  // The step whose top has passed the reading line is the one on stage. On narrow screens the
  // stage sits on top, so the line is lower down.
  let frame = 0;
  function onScroll() {
    if (frame) return;
    frame = window.requestAnimationFrame(() => {
      frame = 0;
      const narrow = window.matchMedia?.('(max-width: 799px)').matches ?? false;
      const line = window.innerHeight * (narrow ? 0.72 : 0.55);
      let current = steps[0];
      for (const step of steps) if (step.getBoundingClientRect().top <= line) current = step;
      if (current?.dataset.step) landing.setStep(current.dataset.step);
    });
  }
  window.addEventListener('scroll', onScroll, { passive: true });
  window.addEventListener('resize', onScroll);

  syncControls();
  update();
  onScroll();
  root.classList.add('is-ready');
  return landing;
}

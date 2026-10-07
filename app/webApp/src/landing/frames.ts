import watchRound from './frames/watch-frame.svg?raw';
import watchScroll from './frames/watch-frame-scroll.svg?raw';

/**
 * The phone's geometry in screen pixels: the 1080x2400 captures (SiteScreenshotTest) between a
 * drawn status bar and gesture bar, behind a thin even bezel. The page places the screen with the
 * same numbers, as percentages, so frame and screen line up at any size.
 */
export const PHONE = {
  statusBar: 100,
  capture: 2400,
  navBar: 64,
  width: 1080,
  bezel: 34,
  rim: 12,
  buttons: 10,
  screenRadius: 120,
  get screenHeight() {
    return this.statusBar + this.capture + this.navBar;
  },
  get inset() {
    return this.bezel + this.rim;
  },
  get bodyWidth() {
    return this.width + 2 * this.inset;
  },
  get bodyHeight() {
    return this.screenHeight + 2 * this.inset;
  },
  get viewWidth() {
    return this.bodyWidth + this.buttons;
  },
};

function roundedRect(x: number, y: number, w: number, h: number, r: number): string {
  return `M${x + r} ${y}H${x + w - r}A${r} ${r} 0 0 1 ${x + w} ${y + r}V${y + h - r}A${r} ${r} 0 0 1 ${x + w - r} ${y + h}` +
    `H${x + r}A${r} ${r} 0 0 1 ${x} ${y + h - r}V${y + r}A${r} ${r} 0 0 1 ${x + r} ${y}Z`;
}

let frames = 0;

/**
 * A current-generation Android phone: flat sides, large even corners, a metal rim, power and volume
 * keys on the right and a centred punch-hole camera. The screen is a hole, so the page's screen
 * shows through from behind.
 */
export function phoneFrame(): string {
  const id = `phone${frames++}`;
  const p = PHONE;
  const outerRadius = p.screenRadius + p.inset;
  const body = roundedRect(0, 0, p.bodyWidth, p.bodyHeight, outerRadius);
  const glass = roundedRect(p.rim, p.rim, p.bodyWidth - 2 * p.rim, p.bodyHeight - 2 * p.rim, outerRadius - p.rim);
  const screen = roundedRect(p.inset, p.inset, p.width, p.screenHeight, p.screenRadius);
  const key = (y: number, h: number) =>
    `<rect x="${p.bodyWidth - 4}" y="${y}" width="${p.buttons + 4}" height="${h}" rx="6" fill="url(#${id}-key)"/>`;
  return `<svg class="frame" viewBox="0 0 ${p.viewWidth} ${p.bodyHeight}" aria-hidden="true" focusable="false">
  <defs>
    <linearGradient id="${id}-rim" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#4a4e55"/>
      <stop offset="0.35" stop-color="#1b1d21"/>
      <stop offset="0.65" stop-color="#2a2d32"/>
      <stop offset="1" stop-color="#55595f"/>
    </linearGradient>
    <linearGradient id="${id}-key" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="#1b1d21"/>
      <stop offset="1" stop-color="#4a4e55"/>
    </linearGradient>
    <linearGradient id="${id}-shine" x1="0" y1="0" x2="1" y2="0.6">
      <stop offset="0" stop-color="#fff" stop-opacity="0.10"/>
      <stop offset="0.4" stop-color="#fff" stop-opacity="0"/>
    </linearGradient>
  </defs>
  ${key(560, 170)}
  ${key(820, 330)}
  <path d="${body}${screen}" fill-rule="evenodd" fill="url(#${id}-rim)"/>
  <path d="${glass}${screen}" fill-rule="evenodd" fill="#050506"/>
  <path d="${glass}" fill="none" stroke="#fff" stroke-opacity="0.08" stroke-width="2"/>
  <path d="${screen}" fill="url(#${id}-shine)"/>
  <circle cx="${p.inset + p.width / 2}" cy="${p.inset + p.statusBar / 2}" r="21" fill="#050506"/>
  <circle cx="${p.inset + p.width / 2}" cy="${p.inset + p.statusBar / 2}" r="9" fill="#14161a"/>
</svg>`;
}

/**
 * The Wear OS frames, with their ids made unique per copy: the page shows each frame once per
 * stage, and an id clash would let one copy's gradients and masks follow another's.
 */
export function watchFrame(kind: 'round' | 'scroll'): string {
  const n = frames++;
  const svg = kind === 'round' ? watchRound : watchScroll;
  return svg
    .replace(/(id="|url\(#)([A-Za-z0-9]+_\d+_\d+)/g, `$1$2_${n}`)
    .replace('<svg ', '<svg class="frame" aria-hidden="true" focusable="false" ');
}

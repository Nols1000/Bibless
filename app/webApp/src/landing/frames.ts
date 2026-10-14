import watchRound from './frames/watch-frame.svg?raw';
import watchScroll from './frames/watch-frame-scroll.svg?raw';

/**
 * The phone screen in pixels: the 1080x2400 captures (SiteScreenshotTest) between a drawn status
 * bar and gesture bar. The page places everything on it with these numbers, as percentages.
 */
export const PHONE = {
  statusBar: 100,
  capture: 2400,
  navBar: 64,
  width: 1080,
  screenRadius: 120,
  get screenHeight() {
    return this.statusBar + this.capture + this.navBar;
  },
};

let frames = 0;

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

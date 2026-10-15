import watchRound from './frames/watch-frame.svg?raw';
import watchScroll from './frames/watch-frame-scroll.svg?raw';
import androidPhone from './frames/official/pixel-10.webp';
import iphone from './frames/official/iphone-17-pro-max.png';
import appleWatch from './frames/official/apple-watch-series-11.png';
import appleWatchUltra3 from './frames/official/apple-watch-ultra-3.png';
import officialGeometry from './frames/official/geometry.json';
import appleCatalogue from './frames/official/apple-catalogue.json';
import googleCatalogue from './frames/official/google-catalogue.json';

export type Device = 'android' | 'ios' | 'watchos' | 'watchos-ultra' | 'wearos';

interface Frame {
  svg: string;
  artwork?: { url: string; filename: string; source: string };
  viewBox: string;
  width: number;
  height: number;
  screen: { x: number; y: number; width: number; height: number };
}

type FrameGeometry = Pick<Frame, 'width' | 'height' | 'screen'> & { clip: string };

export interface FrameVariant {
  id: string;
  name: string;
  group: string;
  frame: Frame;
}

/** Read the image bounds from the same shape that cuts the opening in the SVG. */
function readFrame(svg: string): Frame {
  const viewBox = svg.match(/viewBox="([^"]+)"/)?.[1];
  const shape = svg.match(/<(rect|circle|path)\b([^>]*\bid="screen-shape"[^>]*)\/?\s*>/);
  if (!viewBox || !shape) throw new Error('A device frame needs a viewBox and screen-shape.');
  const attributes = Object.fromEntries([...shape[2].matchAll(/([\w-]+)="([^"]+)"/g)].map((match) => [match[1], match[2]]));
  const [, , width, height] = viewBox.split(/\s+/).map(Number);
  const bounds = attributes['data-screen-bounds']?.split(/\s+/).map(Number);
  if (shape[1] === 'path' && (!bounds || bounds.length !== 4 || !bounds.every(Number.isFinite))) {
    throw new Error('A continuous screen path needs data-screen-bounds.');
  }
  const screen = shape[1] === 'path' ? {
    x: bounds![0], y: bounds![1], width: bounds![2], height: bounds![3],
  } : shape[1] === 'circle' ? {
    x: Number(attributes.cx) - Number(attributes.r), y: Number(attributes.cy) - Number(attributes.r),
    width: Number(attributes.r) * 2, height: Number(attributes.r) * 2,
  } : {
    x: Number(attributes.x), y: Number(attributes.y), width: Number(attributes.width), height: Number(attributes.height),
  };
  return { svg, viewBox, width, height, screen };
}

/** The SVG supplies screen geometry; the manufacturer's original image supplies hardware. */
function officialFrame(url: string, geometry: FrameGeometry, filename: string, source: string): Frame {
  const { width, height, screen, clip } = geometry;
  const svg = `<svg viewBox="0 0 ${width} ${height}"><defs>
    <path id="screen-shape" data-screen-bounds="${screen.x} ${screen.y} ${screen.width} ${screen.height}" d="${clip}"/>
    <clipPath id="screen-clip"><use href="#screen-shape"/></clipPath>
  </defs></svg>`;
  return { ...readFrame(svg), artwork: { url, filename, source } };
}

export const deviceFrames: Record<Device, Frame> = {
  android: officialFrame(androidPhone, officialGeometry.android, 'pixel-10.webp', 'https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/mirror-goog-studio-main/artwork/resources/device-art-resources/pixel_10/'),
  ios: officialFrame(iphone, officialGeometry.ios, 'iphone-17-pro-max.png', 'https://devimages-cdn.apple.com/design/resources/download/Bezel-iPhone-17.dmg'),
  watchos: officialFrame(appleWatch, officialGeometry.watchos, 'apple-watch-series-11.png', 'https://devimages-cdn.apple.com/design/resources/download/Bezel-Apple-Watch-Series-11-2025.dmg'),
  'watchos-ultra': officialFrame(appleWatchUltra3, officialGeometry['watchos-ultra'], 'apple-watch-ultra-3.png', 'https://devimages-cdn.apple.com/design/resources/download/Bezel-Apple-Watch-Ultra-3-2025.dmg'),
  wearos: readFrame(watchRound),
};

const appleArtwork = import.meta.glob('./frames/official/apple/**/*.png', {
  eager: true, import: 'default', query: '?url',
}) as Record<string, string>;
const googleArtwork = import.meta.glob('./frames/official/google/**/back.webp', {
  eager: true, import: 'default', query: '?url',
}) as Record<string, string>;

export const deviceVariants: Partial<Record<Device, FrameVariant[]>> = {};
export const defaultDeviceVariants: Partial<Record<Device, string>> = {};

for (const entry of appleCatalogue) {
  const device = entry.device as 'ios' | 'watchos' | 'watchos-ultra';
  const url = appleArtwork[`./frames/official/apple/${entry.path}`];
  if (!url) throw new Error(`Missing official Apple artwork: ${entry.path}`);
  const variants = deviceVariants[device] ??= [];
  variants.push({ id: entry.id, name: entry.name, group: entry.group,
    frame: officialFrame(url, entry, entry.sourceFile, entry.sourcePack) });
  if (entry.sha256 === officialGeometry[device].sha256) defaultDeviceVariants[device] = entry.id;
}

for (const entry of googleCatalogue) {
  const url = googleArtwork[`./frames/official/google/${entry.path}`];
  if (!url) throw new Error(`Missing official Google artwork: ${entry.path}`);
  const variants = deviceVariants.android ??= [];
  variants.push({ id: entry.id, name: entry.name, group: entry.group,
    frame: officialFrame(url, entry, entry.sourceFile, entry.sourcePack) });
  if (entry.sha256 === officialGeometry.android.sha256) defaultDeviceVariants.android = entry.id;
}

export function getDeviceFrame(device: Device, variant?: string): Frame {
  return deviceVariants[device]?.find((option) => option.id === variant)?.frame ?? deviceFrames[device];
}

/** Gradients, masks and clips must stay local when several copies share a page. */
export function namespaceFrame(svg: string, prefix: string): string {
  return svg
    .replace(/\bid="([^"]+)"/g, `id="$1-${prefix}"`)
    .replace(/url\(#([^)]*)\)/g, `url(#$1-${prefix})`)
    .replace(/href="#([^"]+)"/g, `href="#$1-${prefix}"`);
}

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
  return namespaceFrame(svg, `watch-${n}`)
    .replace('<svg ', '<svg class="frame" aria-hidden="true" focusable="false" ');
}

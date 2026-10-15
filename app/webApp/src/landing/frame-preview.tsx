import { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource-variable/inter';
import '../App.css';
import './frame-preview.css';
import { DeviceFrame } from './DeviceFrame.tsx';
import { getDeviceFrame, deviceVariants, defaultDeviceVariants, type Device } from './frames.ts';

const devices: { device: Device; title: string; file?: string }[] = [
  { device: 'android', title: 'Google Pixel' },
  { device: 'ios', title: 'iPhone' },
  { device: 'watchos', title: 'Apple Watch Series 11' },
  { device: 'watchos-ultra', title: 'Apple Watch Ultra 3' },
  { device: 'wearos', title: 'Pixel Watch illustration', file: 'watch-frame.svg' },
];

function FrameCard({ device, title, file }: typeof devices[number]) {
  const [src, setSrc] = useState<string>();
  const [variant, setVariant] = useState(defaultDeviceVariants[device]);
  const variants = deviceVariants[device] ?? [];
  const groups = [...new Set(variants.map((option) => option.group))];
  const artwork = getDeviceFrame(device, variant).artwork;
  useEffect(() => () => { if (src) URL.revokeObjectURL(src); }, [src]);

  return (
    <section className="frame-card" aria-label={title}>
      <h2>{title}</h2>
      {variants.length > 0 && <label className="field frame-style-picker">
        <span>{device === 'android' ? 'Device and display' : device === 'ios' ? 'Device and colour' : 'Case and band'} <small>({variants.length} frames)</small></span>
        <select value={variant} onChange={(event) => setVariant(event.target.value)} aria-label={`${title} frame style`}>
          {groups.map((group) => <optgroup key={group} label={group}>
            {variants.filter((option) => option.group === group).map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
          </optgroup>)}
        </select>
      </label>}
      <div className="frame-stage"><DeviceFrame device={device} variant={variant} src={src} alt={`${title} device frame`} /></div>
      <div className="frame-actions">
        <label className="button small">Choose screenshot
          <input type="file" accept="image/*" className="visually-hidden" onChange={(event) => {
            const image = event.target.files?.[0];
            if (image) setSrc(URL.createObjectURL(image));
            event.target.value = '';
          }} />
        </label>
        {src && <button className="link" type="button" onClick={() => setSrc(undefined)}>Clear</button>}
        {file && <a className="text-link" href={`./${file}`} download={file}>Download SVG</a>}
        {artwork && <a className="text-link" href={artwork.source}>Official resources</a>}
      </div>
    </section>
  );
}

function FramePreview() {
  return (
    <main className="container frame-gallery">
      <p className="eyebrow">Bibless · device assets</p>
      <h1>Device frames</h1>
      <p className="muted">The checkerboard shows transparent areas. Choose a screenshot to preview its fit; images stay in this browser.</p>
      <div className="frame-grid">{devices.map((device) => <FrameCard key={device.device} {...device} />)}</div>
      <p className="muted frame-credit">
        Pixel phone artwork from <a href="https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/mirror-goog-studio-main/artwork/resources/device-art-resources/">Android Studio</a>,
        {' '}used under <a href="https://www.apache.org/licenses/LICENSE-2.0">Apache 2.0</a>.
      </p>
    </main>
  );
}

const root = document.getElementById('root');
if (root) createRoot(root).render(<FramePreview />);

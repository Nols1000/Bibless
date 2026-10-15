import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DeviceFrame } from './DeviceFrame.tsx';
import { deviceVariants, defaultDeviceVariants, type Device } from './frames.ts';

describe('device frames', () => {
  it('keeps clips, masks and gradients local across different and repeated devices', () => {
    const { container } = render(<>
      <DeviceFrame device="android" src="/android.png" alt="Android" />
      <DeviceFrame device="ios" src="/iphone.png" alt="iPhone" />
      <DeviceFrame device="watchos" src="/apple-watch.png" alt="Apple Watch" />
      <DeviceFrame device="watchos-ultra" src="/apple-watch-ultra.png" alt="Apple Watch Ultra 3" />
      <DeviceFrame device="wearos" src="/pixel-watch.png" alt="Pixel Watch" />
      <DeviceFrame device="wearos" src="/another-pixel-watch.png" alt="Second Pixel Watch" />
      <DeviceFrame device="ios" src="/another-iphone.png" alt="Second iPhone" />
      <DeviceFrame device="watchos-ultra" src="/another-apple-watch-ultra.png" alt="Second Apple Watch Ultra 3" />
    </>);
    const ids = [...container.querySelectorAll('[id]')].map((element) => element.id);
    expect(new Set(ids).size).toBe(ids.length);
    for (const frame of container.querySelectorAll('svg.device-frame')) {
      for (const node of frame.querySelectorAll('*')) {
        for (const attribute of node.attributes) {
          const reference = attribute.value.match(/^url\(#([^)]*)\)$/)?.[1]
            ?? (attribute.name === 'href' ? attribute.value.match(/^#(.+)$/)?.[1] : undefined);
          if (reference && ['clip-path', 'mask', 'fill', 'stroke', 'filter', 'href'].includes(attribute.name)) {
            expect([...frame.querySelectorAll('[id]')].some((element) => element.id === reference)).toBe(true);
          }
        }
      }
    }
  });

  it.each<Device>(['android', 'ios', 'watchos', 'watchos-ultra'])('uses official artwork for %s with an empty screenshot opening', (device) => {
    render(<DeviceFrame device={device} alt="Apple Watch frame" />);
    const frame = screen.getByRole('img', { name: 'Apple Watch frame' });
    expect(frame).toHaveAttribute('data-official', 'true');
    expect(frame.querySelector('image[data-screenshot]')).toBeNull();
    expect(frame.querySelector('image[data-frame-artwork]')?.getAttribute('href')).toMatch(/\.(png|webp)$/);
    expect(frame.querySelector('clipPath use')?.getAttribute('href')).toMatch(/^#screen-shape-device-/);
  });

  it('adds system bars only for Android app-content captures', () => {
    const { rerender } = render(<DeviceFrame device="android" src="/capture.png" contentOnly alt="Android capture" />);
    expect(screen.getByText('8:59')).toBeInTheDocument();
    rerender(<DeviceFrame device="ios" src="/capture.png" contentOnly alt="iPhone capture" />);
    expect(screen.queryByText('8:59')).not.toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'iPhone capture' }).querySelector('image[data-screenshot]')).toHaveAttribute('href', '/capture.png');
  });

  it('keeps the uploaded screenshot when switching to a landscape Apple variant', () => {
    const landscape = deviceVariants.ios!.find((option) => option.frame.width > option.frame.height)!;
    const { rerender } = render(<DeviceFrame device="ios" variant={defaultDeviceVariants.ios} src="/capture.png" alt="iPhone capture" />);
    const originalArtwork = screen.getByRole('img').querySelector('image[data-frame-artwork]')?.getAttribute('href');
    rerender(<DeviceFrame device="ios" variant={landscape.id} src="/capture.png" alt="iPhone capture" />);
    const frame = screen.getByRole('img');
    expect(frame).toHaveAttribute('data-landscape', 'true');
    expect(frame.querySelector('image[data-screenshot]')).toHaveAttribute('href', '/capture.png');
    expect(frame.querySelector('image[data-frame-artwork]')).toHaveAttribute('href', landscape.frame.artwork!.url);
    expect(landscape.frame.artwork!.url).not.toBe(originalArtwork);
    expect(frame.querySelector('image[data-screenshot]')).toHaveAttribute('width', String(landscape.frame.screen.width));
  });
});

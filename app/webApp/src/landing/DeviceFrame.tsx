import { useId, useMemo } from 'react';
import { getDeviceFrame, namespaceFrame, PHONE, type Device } from './frames.ts';
import './DeviceFrame.css';

interface DeviceFrameProps {
  device: Device;
  variant?: string;
  src?: string;
  alt: string;
  /** Android SiteScreenshotTest captures app content without the system bars. */
  contentOnly?: boolean;
  className?: string;
}

/** The screenshot and hardware share one SVG coordinate system and one screen clip. */
export function DeviceFrame({ device, variant, src, alt, contentOnly = false, className = '' }: DeviceFrameProps) {
  const prefix = `device-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  const frame = getDeviceFrame(device, variant);
  const body = useMemo(() => namespaceFrame(frame.svg, prefix)
    .replace(/^[\s\S]*?<svg\b[^>]*>/, '').replace(/<\/svg>\s*$/, ''), [frame, prefix]);
  const { screen } = frame;
  const appOnly = device === 'android' && contentOnly;
  const statusHeight = appOnly ? screen.height * PHONE.statusBar / PHONE.screenHeight : 0;
  const navigationHeight = appOnly ? screen.height * PHONE.navBar / PHONE.screenHeight : 0;
  const barsScale = screen.width / 332;
  const clip = `url(#screen-clip-${prefix})`;

  return (
    <svg className={`device-frame ${className}`} viewBox={frame.viewBox} width={frame.width} height={frame.height}
      fill="none" role="img" aria-label={alt} data-device={device} data-variant={variant}
      data-landscape={frame.width > frame.height ? 'true' : undefined} data-official={frame.artwork ? 'true' : undefined}>
      {src && (
        <g clipPath={clip}>
          <use href={`#screen-shape-${prefix}`} fill={appOnly ? '#fff' : '#000'} />
          <image data-screenshot href={src} x={screen.x} y={screen.y + statusHeight} width={screen.width}
            height={screen.height - statusHeight - navigationHeight} preserveAspectRatio="xMidYMid meet" />
          {appOnly && (
            <g transform={`translate(${screen.x} ${screen.y}) scale(${barsScale})`}
              fill="#111" fontFamily="system-ui, sans-serif" fontSize="13" fontWeight="600" aria-hidden="true">
              <text x="18" y="21">8:59</text>
              <path d="M272 12l7 9 7-9a11 11 0 0 0-14 0" />
              <rect x="297" y="10" width="16" height="10" rx="2" />
              <rect x="126" y={screen.height / barsScale - 9} width="80" height="3" rx="1.5" />
            </g>
          )}
        </g>
      )}
      <g aria-hidden="true" dangerouslySetInnerHTML={{ __html: body }} />
      {frame.artwork && <image data-frame-artwork href={frame.artwork.url} width={frame.width} height={frame.height} aria-hidden="true" />}
    </svg>
  );
}

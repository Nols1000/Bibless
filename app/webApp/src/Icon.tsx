import type { SVGProps } from 'react';

const paths = {
  arrow: 'M5 12h14m-6-6 6 6-6 6',
  plus: 'M12 5v14M5 12h14',
  clock: 'M12 8v4l3 2M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
  barcode: 'M4 5v14M7 5v14M11 5v14M13 5v14M17 5v14M20 5v14',
  check: 'm5 12 4 4L19 6',
  devices: 'M5 3h8v18H5zM17 8h5v8h-5zM19 5v3m0 8v3',
  people: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M17 4a4 4 0 0 1 0 8m5 9v-2a4 4 0 0 0-3-3.87M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  calendar: 'M4 5h16v16H4zM8 3v4m8-4v4M4 11h16',
} as const;

export function Icon({ name, ...props }: SVGProps<SVGSVGElement> & { name: keyof typeof paths }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round"
      strokeLinejoin="round" aria-hidden="true" focusable="false" {...props}>
      <path d={paths[name]} />
    </svg>
  );
}

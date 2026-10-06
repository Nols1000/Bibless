import { formatDate, modeName, type Qr, type Run } from './organiser.ts';

const QUIET_ZONE = 4;

export function QrCode({ qr, label }: { qr: Qr; label: string }) {
  const size = qr.size + QUIET_ZONE * 2;
  return (
    <svg
      className="qr"
      role="img"
      aria-label={label}
      viewBox={`${-QUIET_ZONE} ${-QUIET_ZONE} ${size} ${size}`}
      shapeRendering="crispEdges"
    >
      <rect x={-QUIET_ZONE} y={-QUIET_ZONE} width={size} height={size} fill="#fff" />
      <path d={qr.path} fill="#000" />
    </svg>
  );
}

interface Role {
  title: string;
  steps: string[];
}

function roles(format: string): Role[] {
  const join = (tool: string) => `Open the ${tool} in Bibless, tap "Join run" and scan this code.`;
  const timekeeper: Role = {
    title: 'Timekeeper',
    steps: [join('Timer'), 'Start the stopwatch on the start signal.', 'Tap once for every finisher as they cross the line.'],
  };
  if (format === 'SCAN_ORDER') {
    return [
      timekeeper,
      {
        title: 'Scanner',
        steps: [
          join('Scanner'),
          'Scan each finisher\'s barcode in the order they leave the funnel.',
          'Someone without a barcode? Record an unknown finisher so nobody behind them moves up.',
        ],
      },
      {
        title: 'Funnel marshal',
        steps: ['Keep finishers in the order they crossed the line until they are scanned.'],
      },
    ];
  }
  return [
    timekeeper,
    {
      title: 'Token handout',
      steps: ['Hand out finish tokens in order, one per finisher, as they leave the funnel.'],
    },
    {
      title: 'Scanner',
      steps: [
        join('Scanner'),
        'Scan the finisher\'s barcode, then their finish token.',
        'No barcode? Scan the token and record an unknown finisher.',
      ],
    },
  ];
}

/** The printable page: the run, its join code, and what each volunteer does. */
export function RunSheet({ run, qr }: { run: Run; qr: Qr }) {
  return (
    <article className="run-sheet">
      <header>
        <h1>{run.name}</h1>
        <p className="muted">
          {[run.date ? formatDate(run.date) : null, `Timed with ${modeName(run.format).toLowerCase()}`]
            .filter(Boolean)
            .join(' · ')}
        </p>
      </header>
      <div className="sheet-body">
        <figure>
          <QrCode qr={qr} label={`Join code for ${run.name}`} />
          <figcaption>Scan with the Bibless app to join this run</figcaption>
        </figure>
        <div className="roles">
          {roles(run.format).map((role) => (
            <section key={role.title}>
              <h2>{role.title}</h2>
              <ol>
                {role.steps.map((step) => (
                  <li key={step}>{step}</li>
                ))}
              </ol>
            </section>
          ))}
        </div>
      </div>
    </article>
  );
}

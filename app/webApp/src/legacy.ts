/** The old organiser entry is a bridge to the same app, on both root and repository hosting. */
export function legacyTarget(hash: string, base: string): string {
  return `${base}${!hash || hash === '#' || hash === '#/' ? '#/runs' : hash}`;
}

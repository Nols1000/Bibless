import { describe, expect, it } from 'vitest';
import { legacyTarget } from './legacy.ts';

describe('legacy organiser links', () => {
  it.each(['/', '/Bibless/'])('redirects the run list and keeps detail hashes under %s', (base) => {
    expect(legacyTarget('', base)).toBe(`${base}#/runs`);
    expect(legacyTarget('#/', base)).toBe(`${base}#/runs`);
    expect(legacyTarget('#/new', base)).toBe(`${base}#/new`);
    expect(legacyTarget('#/runs/a%20b', base)).toBe(`${base}#/runs/a%20b`);
    expect(legacyTarget('#/runs/a%20b/results', base)).toBe(`${base}#/runs/a%20b/results`);
    expect(legacyTarget('#/runs/a/tokens', base)).toBe(`${base}#/runs/a/tokens`);
  });
});

import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';

beforeEach(() => {
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
  Element.prototype.scrollIntoView = vi.fn();
});

afterEach(() => {
  cleanup();
  window.location.hash = '';
});

import { legacyTarget } from './legacy.ts';

window.location.replace(legacyTarget(window.location.hash, import.meta.env.BASE_URL));

import { installNameReplacerRuntime } from './runtime';
import type { NameReplacerRuntime } from './types';

declare global {
  interface Window {
    __NAME_REPLACER__: NameReplacerRuntime;
  }
}

window.__NAME_REPLACER__ = installNameReplacerRuntime(document);

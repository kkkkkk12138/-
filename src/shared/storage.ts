import { normalizeRules } from './rules';
import type { ReplaceRule } from './types';

const STORAGE_KEY = 'globalRules';

export async function loadRules(): Promise<ReplaceRule[]> {
  const result = await chrome.storage.local.get(STORAGE_KEY);
  return normalizeRules((result[STORAGE_KEY] as ReplaceRule[] | undefined) ?? []);
}

export async function saveRules(rules: ReplaceRule[]): Promise<ReplaceRule[]> {
  const normalized = normalizeRules(rules);
  await chrome.storage.local.set({ [STORAGE_KEY]: normalized });
  return normalized;
}

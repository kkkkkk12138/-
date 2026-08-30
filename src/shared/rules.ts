import type { ReplaceRule } from './types';

export function createEmptyRule(): ReplaceRule {
  return {
    id: crypto.randomUUID(),
    source: '',
    target: ''
  };
}

export function normalizeRules(input: ReplaceRule[]): ReplaceRule[] {
  const seen = new Set<string>();

  return input
    .map((rule) => ({
      ...rule,
      source: rule.source.trim(),
      target: rule.target.trim()
    }))
    .filter((rule) => rule.source && rule.target && rule.source !== rule.target)
    .filter((rule) => {
      const key = `${rule.source}::${rule.target}`;

      if (seen.has(key)) {
        return false;
      }

      seen.add(key);
      return true;
    })
    .sort((a, b) => b.source.length - a.source.length);
}

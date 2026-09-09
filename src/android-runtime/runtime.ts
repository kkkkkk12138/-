import { createTextEngine } from '../content/textEngine';
import type { ReplaceRule } from '../shared/types';
import type { NameReplacerRuntime, OrderedReplaceRule } from './types';

export type { ApplyResult, NameReplacerRuntime, OrderedReplaceRule } from './types';

function isValidRules(rules: unknown): rules is OrderedReplaceRule[] {
  return (
    Array.isArray(rules) &&
    rules.every(
      (rule) =>
        typeof rule === 'object' &&
        rule !== null &&
        typeof rule.id === 'string' &&
        typeof rule.source === 'string' &&
        rule.source.length > 0 &&
        typeof rule.target === 'string' &&
        typeof rule.order === 'number' &&
        Number.isFinite(rule.order)
    )
  );
}

function orderRules(rules: OrderedReplaceRule[]): ReplaceRule[] {
  return [...rules]
    .sort((left, right) => right.source.length - left.source.length || left.order - right.order)
    .map(({ id, source, target }) => ({ id, source, target }));
}

function collectTextNodes(doc: Document): Text[] {
  if (!doc.body) {
    return [];
  }

  const nodes: Text[] = [];
  const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT);
  let current = walker.nextNode();

  while (current) {
    nodes.push(current as Text);
    current = walker.nextNode();
  }

  return nodes;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export function installNameReplacerRuntime(doc: Document): NameReplacerRuntime {
  const engine = createTextEngine();
  let installed = false;

  const runtime: NameReplacerRuntime = {
    install() {
      installed = true;
    },

    applyRules(rules) {
      if (!installed) {
        return {
          ok: false,
          code: 'NOT_INSTALLED',
          message: 'Name replacer runtime is not installed'
        };
      }

      if (!isValidRules(rules)) {
        return {
          ok: false,
          code: 'INVALID_RULES',
          message: 'Rules must contain a non-empty source and a finite order'
        };
      }

      try {
        const textNodes = collectTextNodes(doc);
        const previousValues = textNodes.map((node) => node.nodeValue);
        engine.applyToDocument(doc, orderRules(rules));
        const changedTextNodeCount = textNodes.reduce(
          (count, node, index) => count + (node.nodeValue === previousValues[index] ? 0 : 1),
          0
        );

        return {
          ok: true,
          activeRuleCount: rules.length,
          changedTextNodeCount
        };
      } catch (error) {
        return {
          ok: false,
          code: 'RUNTIME_ERROR',
          message: errorMessage(error)
        };
      }
    },

    restoreOriginalText() {
      if (installed) {
        engine.applyToDocument(doc, []);
      }
    },

    dispose() {
      runtime.restoreOriginalText();
      installed = false;
    }
  };

  runtime.install();
  return runtime;
}

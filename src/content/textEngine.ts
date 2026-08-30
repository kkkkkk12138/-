import type { ReplaceRule } from '../shared/types';
import { shouldProcessTextNode } from './domFilter';

type TextEngine = {
  applyToDocument(doc: Document, rules: ReplaceRule[]): void;
  applyToNode(root: Node, rules: ReplaceRule[]): void;
  refreshOriginal(node: Text): void;
};

function replaceText(baseText: string, rules: ReplaceRule[]): string {
  return rules.reduce((current, rule) => current.split(rule.source).join(rule.target), baseText);
}

function getWalkerDocument(root: Node): Document | null {
  if (root.nodeType === Node.DOCUMENT_NODE) {
    return root as Document;
  }

  return root.ownerDocument;
}

export function createTextEngine(): TextEngine {
  const originals = new WeakMap<Text, string>();

  function applyToTextNode(node: Text, rules: ReplaceRule[]): void {
    if (!shouldProcessTextNode(node)) {
      return;
    }

    const baseText = originals.get(node) ?? (node.nodeValue ?? '');

    if (!originals.has(node)) {
      originals.set(node, baseText);
    }

    const nextText = replaceText(baseText, rules);

    if (nextText !== node.nodeValue) {
      node.nodeValue = nextText;
    }
  }

  function visitNode(root: Node, rules: ReplaceRule[]): void {
    if (root.nodeType === Node.TEXT_NODE) {
      applyToTextNode(root as Text, rules);
      return;
    }

    const doc = getWalkerDocument(root);

    if (!doc) {
      return;
    }

    const walker = doc.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let current = walker.nextNode();

    while (current) {
      applyToTextNode(current as Text, rules);
      current = walker.nextNode();
    }
  }

  return {
    applyToDocument(doc, rules) {
      if (!doc.body) {
        return;
      }

      visitNode(doc.body, rules);
    },
    applyToNode(root, rules) {
      visitNode(root, rules);
    },
    refreshOriginal(node) {
      originals.set(node, node.nodeValue ?? '');
    }
  };
}

const BLOCKED_TAGS = new Set([
  'SCRIPT',
  'STYLE',
  'NOSCRIPT',
  'TEXTAREA',
  'INPUT',
  'BUTTON',
  'SELECT',
  'OPTION',
  'CODE',
  'PRE'
]);

export function shouldProcessTextNode(node: Text): boolean {
  if (!node.nodeValue?.trim()) {
    return false;
  }

  let current: Node | null = node.parentNode;

  while (current) {
    if (current.nodeType !== Node.ELEMENT_NODE) {
      current = current.parentNode;
      continue;
    }

    const element = current as HTMLElement;

    if (BLOCKED_TAGS.has(element.tagName)) {
      return false;
    }

    if (
      element.isContentEditable ||
      element.getAttribute('contenteditable') === 'true' ||
      element.getAttribute('contenteditable') === ''
    ) {
      return false;
    }

    if (element.dataset.nameReplacementIgnore === 'true') {
      return false;
    }

    current = current.parentNode;
  }

  return true;
}

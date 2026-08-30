# Name Replacement Browser Extension Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Manifest V3 browser extension that lets users edit simple `原名 -> 新名` rules in a popup and apply them to the current webpage with style-preserving, low-latency text replacement.

**Architecture:** Use a popup page for rule editing, a content script for DOM scanning and dynamic re-application, and local extension storage for persistent global rules. The content script must cache original text node values in a `WeakMap` so rule edits can re-render the current page from original text without injecting wrapper elements or breaking layout behavior.

**Tech Stack:** Manifest V3, TypeScript, esbuild, Vitest, JSDOM, vanilla HTML/CSS

---

## File map

- `package.json`: dependencies and npm scripts
- `tsconfig.json`: TypeScript compiler settings
- `scripts/build.mjs`: bundles popup and content script into `dist/`
- `manifest.json`: extension manifest
- `popup.html`: popup shell that loads bundled popup script and CSS
- `src/shared/types.ts`: shared message and state types
- `src/shared/rules.ts`: rule normalization and sort helpers
- `src/shared/storage.ts`: `chrome.storage.local` read/write wrapper
- `src/content/domFilter.ts`: DOM node filtering rules
- `src/content/textEngine.ts`: text-node snapshot cache and replacement engine
- `src/content/index.ts`: message handling, observer lifecycle, page-state orchestration
- `src/popup/index.ts`: popup rendering, rule editing, apply/disable flow
- `src/popup/styles.css`: popup styles
- `tests/setup.ts`: test globals and `chrome` mocks
- `tests/rules.test.ts`: rule normalization tests
- `tests/textEngine.test.ts`: DOM replacement engine tests
- `tests/content-script.test.ts`: content-script lifecycle tests
- `tests/popup.test.ts`: popup behavior tests
- `README.md`: local development, loading, testing, and smoke-check instructions

## Task 1: Scaffold the extension workspace

**Files:**
- Create: `.gitignore`
- Create: `package.json`
- Create: `tsconfig.json`
- Create: `scripts/build.mjs`
- Create: `manifest.json`
- Create: `popup.html`
- Create: `tests/setup.ts`
- Create: `vitest.config.ts`

- [ ] **Step 1: Initialize git and write the minimal project scaffold**

Run:

```bash
git init
mkdir -p scripts src/shared src/content src/popup tests
```

Create `.gitignore`:

```gitignore
node_modules
dist
.DS_Store
coverage
```

Create `package.json`:

```json
{
  "name": "xiaoshuo-yijian-huanming",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "build": "node scripts/build.mjs",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "devDependencies": {
    "esbuild": "^0.25.9",
    "jsdom": "^26.1.0",
    "typescript": "^5.9.2",
    "vitest": "^2.1.9"
  }
}
```

Create `tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "lib": ["DOM", "ES2022"],
    "strict": true,
    "noEmit": true,
    "types": ["vitest/globals"]
  },
  "include": ["src", "tests", "scripts"]
}
```

- [ ] **Step 2: Write a failing build smoke test and config**

Create `vitest.config.ts`:

```ts
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts']
  }
});
```

Create `tests/setup.ts`:

```ts
import { vi } from 'vitest';

const storageState: Record<string, unknown> = {};

globalThis.chrome = {
  storage: {
    local: {
      async get(keys?: string | string[]) {
        if (!keys) return storageState;
        if (typeof keys === 'string') return { [keys]: storageState[keys] };
        return Object.fromEntries(keys.map((key) => [key, storageState[key]]));
      },
      async set(items: Record<string, unknown>) {
        Object.assign(storageState, items);
      }
    }
  },
  tabs: {
    query: vi.fn(),
    sendMessage: vi.fn()
  }
} as unknown as typeof chrome;
```

Create `scripts/build.mjs`:

```js
import { build } from 'esbuild';
import { cp, mkdir } from 'node:fs/promises';

await mkdir('dist', { recursive: true });

await Promise.all([
  build({
    entryPoints: ['src/popup/index.ts'],
    bundle: true,
    outfile: 'dist/popup.js',
    format: 'iife',
    platform: 'browser'
  }),
  build({
    entryPoints: ['src/content/index.ts'],
    bundle: true,
    outfile: 'dist/content.js',
    format: 'iife',
    platform: 'browser'
  }),
  cp('manifest.json', 'dist/manifest.json'),
  cp('popup.html', 'dist/popup.html')
]);
```

Create `manifest.json`:

```json
{
  "manifest_version": 3,
  "name": "Name Replacement Browser Extension",
  "version": "0.1.0",
  "description": "Replace names on the current page without changing visual style.",
  "permissions": ["storage", "tabs"],
  "host_permissions": ["<all_urls>"],
  "action": {
    "default_popup": "popup.html"
  },
  "content_scripts": [
    {
      "matches": ["<all_urls>"],
      "js": ["content.js"],
      "run_at": "document_idle"
    }
  ]
}
```

Create `popup.html`:

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>名字替换</title>
    <link rel="stylesheet" href="./styles.css" />
  </head>
  <body>
    <div id="app"></div>
    <script src="./popup.js"></script>
  </body>
</html>
```

Run:

```bash
npm install
npm run build
```

Expected: `dist/manifest.json`, `dist/popup.html`, `dist/popup.js`, `dist/content.js` exist and build exits `0`.

- [ ] **Step 3: Add missing CSS copy support so the build becomes usable**

Update `scripts/build.mjs`:

```js
import { build } from 'esbuild';
import { cp, mkdir } from 'node:fs/promises';

await mkdir('dist', { recursive: true });

await Promise.all([
  build({
    entryPoints: ['src/popup/index.ts'],
    bundle: true,
    outfile: 'dist/popup.js',
    format: 'iife',
    platform: 'browser'
  }),
  build({
    entryPoints: ['src/content/index.ts'],
    bundle: true,
    outfile: 'dist/content.js',
    format: 'iife',
    platform: 'browser'
  }),
  cp('manifest.json', 'dist/manifest.json'),
  cp('popup.html', 'dist/popup.html'),
  cp('src/popup/styles.css', 'dist/styles.css')
]);
```

Create `src/popup/styles.css`:

```css
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}
```

Create placeholder entry files:

```ts
// src/popup/index.ts
document.getElementById('app')!.textContent = '加载中...';
```

```ts
// src/content/index.ts
console.debug('name replacement content script loaded');
```

- [ ] **Step 4: Run build and test the scaffold**

Run:

```bash
npm run build
npm test
```

Expected:
- `npm run build` exits `0`
- `npm test` exits `0` with `0 failed`

- [ ] **Step 5: Commit the scaffold**

Run:

```bash
git add .gitignore package.json tsconfig.json scripts/build.mjs manifest.json popup.html src/popup/styles.css src/popup/index.ts src/content/index.ts tests/setup.ts vitest.config.ts
git commit -m "chore: scaffold browser extension workspace"
```

## Task 2: Implement shared rules and storage helpers

**Files:**
- Create: `src/shared/types.ts`
- Create: `src/shared/rules.ts`
- Create: `src/shared/storage.ts`
- Create: `tests/rules.test.ts`

- [ ] **Step 1: Write failing tests for rule cleanup and storage defaults**

Create `tests/rules.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { normalizeRules } from '../src/shared/rules';
import { loadRules, saveRules } from '../src/shared/storage';

describe('normalizeRules', () => {
  it('trims, removes blanks, removes duplicates, and sorts by source length desc', () => {
    expect(
      normalizeRules([
        { id: '1', source: ' 清辞 ', target: '惊鹤' },
        { id: '2', source: '', target: '空' },
        { id: '3', source: '沈清辞', target: '林惊鹤' },
        { id: '4', source: '清辞', target: '惊鹤' }
      ])
    ).toEqual([
      { id: '3', source: '沈清辞', target: '林惊鹤' },
      { id: '1', source: '清辞', target: '惊鹤' }
    ]);
  });
});

describe('storage', () => {
  it('returns empty rules by default and persists normalized rules', async () => {
    expect(await loadRules()).toEqual([]);

    await saveRules([
      { id: '1', source: ' 沈清辞 ', target: '林惊鹤' },
      { id: '2', source: '', target: '' }
    ]);

    expect(await loadRules()).toEqual([
      { id: '1', source: '沈清辞', target: '林惊鹤' }
    ]);
  });
});
```

Run:

```bash
npm test -- tests/rules.test.ts
```

Expected: FAIL with import or implementation errors for missing modules.

- [ ] **Step 2: Implement shared types and normalized rule logic**

Create `src/shared/types.ts`:

```ts
export type ReplaceRule = {
  id: string;
  source: string;
  target: string;
};

export type PageRuntimeState = {
  enabled: boolean;
  activeRuleCount: number;
};

export type ContentMessage =
  | { type: 'GET_PAGE_STATE' }
  | { type: 'APPLY_RULES'; rules: ReplaceRule[] }
  | { type: 'DISABLE_PAGE' };
```

Create `src/shared/rules.ts`:

```ts
import type { ReplaceRule } from './types';

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
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .sort((a, b) => b.source.length - a.source.length);
}
```

Create `src/shared/storage.ts`:

```ts
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
```

- [ ] **Step 3: Add small helper APIs the popup will need**

Update `src/shared/rules.ts`:

```ts
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
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .sort((a, b) => b.source.length - a.source.length);
}
```

Update `tests/setup.ts` to support deterministic ids:

```ts
import { vi } from 'vitest';

const storageState: Record<string, unknown> = {};

vi.stubGlobal('crypto', {
  randomUUID: () => 'test-uuid'
});

globalThis.chrome = {
  storage: {
    local: {
      async get(keys?: string | string[]) {
        if (!keys) return storageState;
        if (typeof keys === 'string') return { [keys]: storageState[keys] };
        return Object.fromEntries(keys.map((key) => [key, storageState[key]]));
      },
      async set(items: Record<string, unknown>) {
        Object.assign(storageState, items);
      }
    }
  },
  tabs: {
    query: vi.fn(),
    sendMessage: vi.fn()
  }
} as unknown as typeof chrome;
```

- [ ] **Step 4: Run the shared-layer tests**

Run:

```bash
npm test -- tests/rules.test.ts
```

Expected: PASS with `2 passed`.

- [ ] **Step 5: Commit the shared helpers**

Run:

```bash
git add src/shared/types.ts src/shared/rules.ts src/shared/storage.ts tests/rules.test.ts tests/setup.ts
git commit -m "feat: add rule normalization and storage helpers"
```

## Task 3: Build the DOM replacement engine

**Files:**
- Create: `src/content/domFilter.ts`
- Create: `src/content/textEngine.ts`
- Create: `tests/textEngine.test.ts`

- [ ] **Step 1: Write failing tests for text-node filtering, style preservation, and reapply-from-original behavior**

Create `tests/textEngine.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { createTextEngine } from '../src/content/textEngine';

describe('text engine', () => {
  it('replaces readable text nodes without injecting wrapper elements', () => {
    document.body.innerHTML = `
      <article><p id="line">沈清辞看着清辞笑了。</p></article>
      <input value="沈清辞" />
      <pre>沈清辞</pre>
    `;

    const engine = createTextEngine();
    engine.applyToDocument(document, [
      { id: '1', source: '沈清辞', target: '林惊鹤' },
      { id: '2', source: '清辞', target: '惊鹤' }
    ]);

    expect(document.getElementById('line')!.textContent).toBe('林惊鹤看着惊鹤笑了。');
    expect(document.querySelector('#line span')).toBeNull();
    expect((document.querySelector('input') as HTMLInputElement).value).toBe('沈清辞');
    expect(document.querySelector('pre')!.textContent).toBe('沈清辞');
  });

  it('reapplies from original text when rules change', () => {
    document.body.innerHTML = `<p id="line">沈清辞看着清辞笑了。</p>`;

    const engine = createTextEngine();
    engine.applyToDocument(document, [{ id: '1', source: '沈清辞', target: '林惊鹤' }]);
    engine.applyToDocument(document, [{ id: '1', source: '沈清辞', target: '顾云深' }]);

    expect(document.getElementById('line')!.textContent).toBe('顾云深看着清辞笑了。');
  });
});
```

Run:

```bash
npm test -- tests/textEngine.test.ts
```

Expected: FAIL because `createTextEngine` does not exist.

- [ ] **Step 2: Implement DOM filtering**

Create `src/content/domFilter.ts`:

```ts
const BLOCKED_TAGS = new Set([
  'SCRIPT',
  'STYLE',
  'NOSCRIPT',
  'TEXTAREA',
  'INPUT',
  'BUTTON',
  'CODE',
  'PRE'
]);

export function shouldProcessTextNode(node: Text): boolean {
  if (!node.nodeValue?.trim()) return false;

  let current: Node | null = node.parentNode;
  while (current && current.nodeType === Node.ELEMENT_NODE) {
    const element = current as HTMLElement;
    if (BLOCKED_TAGS.has(element.tagName)) return false;
    if (element.isContentEditable) return false;
    if (element.dataset.nameReplacementIgnore === 'true') return false;
    current = current.parentNode;
  }

  return true;
}
```

- [ ] **Step 3: Implement the replacement engine with original-text snapshots**

Create `src/content/textEngine.ts`:

```ts
import type { ReplaceRule } from '../shared/types';
import { shouldProcessTextNode } from './domFilter';

export function createTextEngine() {
  const originals = new WeakMap<Text, string>();

  function replaceText(baseText: string, rules: ReplaceRule[]): string {
    return rules.reduce(
      (current, rule) => current.split(rule.source).join(rule.target),
      baseText
    );
  }

  function applyToTextNode(node: Text, rules: ReplaceRule[]) {
    if (!shouldProcessTextNode(node)) return;

    const baseText = originals.get(node) ?? node.nodeValue ?? '';
    if (!originals.has(node)) originals.set(node, baseText);

    const nextText = replaceText(baseText, rules);
    if (nextText !== node.nodeValue) {
      node.nodeValue = nextText;
    }
  }

  function visitNode(root: Node, rules: ReplaceRule[]) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let current = walker.nextNode();
    while (current) {
      applyToTextNode(current as Text, rules);
      current = walker.nextNode();
    }
  }

  return {
    applyToDocument(doc: Document, rules: ReplaceRule[]) {
      if (doc.body) visitNode(doc.body, rules);
    },
    applyToNode(root: Node, rules: ReplaceRule[]) {
      visitNode(root, rules);
    },
    refreshOriginal(node: Text) {
      originals.set(node, node.nodeValue ?? '');
    }
  };
}
```

- [ ] **Step 4: Run the engine tests**

Run:

```bash
npm test -- tests/textEngine.test.ts
```

Expected: PASS with `2 passed`.

- [ ] **Step 5: Commit the DOM engine**

Run:

```bash
git add src/content/domFilter.ts src/content/textEngine.ts tests/textEngine.test.ts
git commit -m "feat: add DOM text replacement engine"
```

## Task 4: Wire the content script lifecycle and dynamic updates

**Files:**
- Modify: `src/content/index.ts`
- Create: `tests/content-script.test.ts`

- [ ] **Step 1: Write failing tests for apply, disable, and dynamic-node replacement**

Create `tests/content-script.test.ts`:

```ts
import { beforeEach, describe, expect, it } from 'vitest';
import { mountContentScript } from '../src/content/index';

describe('content script', () => {
  beforeEach(() => {
    document.body.innerHTML = `<article id="root"><p>沈清辞来了。</p></article>`;
  });

  it('applies rules to the current page and reports enabled state', async () => {
    const api = mountContentScript(document);

    await api.handleMessage({
      type: 'APPLY_RULES',
      rules: [{ id: '1', source: '沈清辞', target: '林惊鹤' }]
    });

    expect(document.body.textContent).toContain('林惊鹤来了。');
    expect(await api.handleMessage({ type: 'GET_PAGE_STATE' })).toEqual({
      enabled: true,
      activeRuleCount: 1
    });
  });

  it('stops processing after disable', async () => {
    const api = mountContentScript(document);

    await api.handleMessage({
      type: 'APPLY_RULES',
      rules: [{ id: '1', source: '沈清辞', target: '林惊鹤' }]
    });
    await api.handleMessage({ type: 'DISABLE_PAGE' });

    const next = document.createElement('p');
    next.textContent = '沈清辞又来了。';
    document.getElementById('root')!.appendChild(next);

    expect(next.textContent).toBe('沈清辞又来了。');
  });
});
```

Run:

```bash
npm test -- tests/content-script.test.ts
```

Expected: FAIL because `mountContentScript` does not exist.

- [ ] **Step 2: Implement message handling and observer lifecycle**

Replace `src/content/index.ts` with:

```ts
import type { ContentMessage, PageRuntimeState, ReplaceRule } from '../shared/types';
import { normalizeRules } from '../shared/rules';
import { createTextEngine } from './textEngine';

export function mountContentScript(doc: Document = document) {
  const engine = createTextEngine();
  let enabled = false;
  let rules: ReplaceRule[] = [];
  let observer: MutationObserver | null = null;
  let isApplying = false;

  function stopObserver() {
    observer?.disconnect();
    observer = null;
  }

  function applyToDocument() {
    isApplying = true;
    engine.applyToDocument(doc, rules);
    isApplying = false;
  }

  function startObserver() {
    stopObserver();

    observer = new MutationObserver((mutations) => {
      if (!enabled || isApplying) return;

      for (const mutation of mutations) {
        if (mutation.type === 'childList') {
          mutation.addedNodes.forEach((node) => {
            if (node.nodeType === Node.TEXT_NODE) {
              engine.refreshOriginal(node as Text);
              engine.applyToNode(node, rules);
              return;
            }

            if (node.nodeType === Node.ELEMENT_NODE) {
              engine.applyToNode(node, rules);
            }
          });
        }

        if (mutation.type === 'characterData') {
          const text = mutation.target as Text;
          engine.refreshOriginal(text);
          engine.applyToNode(text, rules);
        }
      }
    });

    observer.observe(doc.body, {
      childList: true,
      subtree: true,
      characterData: true
    });
  }

  async function handleMessage(message: ContentMessage): Promise<PageRuntimeState | void> {
    if (message.type === 'GET_PAGE_STATE') {
      return {
        enabled,
        activeRuleCount: rules.length
      };
    }

    if (message.type === 'APPLY_RULES') {
      rules = normalizeRules(message.rules);
      enabled = true;
      applyToDocument();
      startObserver();
      return;
    }

    if (message.type === 'DISABLE_PAGE') {
      enabled = false;
      stopObserver();
    }
  }

  return { handleMessage, stopObserver };
}

const runtime = mountContentScript(document);

chrome.runtime?.onMessage?.addListener((message, _sender, sendResponse) => {
  runtime.handleMessage(message).then(sendResponse);
  return true;
});
```

- [ ] **Step 3: Expand the test setup to mock runtime listeners**

Update `tests/setup.ts`:

```ts
import { vi } from 'vitest';

const storageState: Record<string, unknown> = {};

vi.stubGlobal('crypto', {
  randomUUID: () => 'test-uuid'
});

globalThis.chrome = {
  runtime: {
    onMessage: {
      addListener: vi.fn()
    }
  },
  storage: {
    local: {
      async get(keys?: string | string[]) {
        if (!keys) return storageState;
        if (typeof keys === 'string') return { [keys]: storageState[keys] };
        return Object.fromEntries(keys.map((key) => [key, storageState[key]]));
      },
      async set(items: Record<string, unknown>) {
        Object.assign(storageState, items);
      }
    }
  },
  tabs: {
    query: vi.fn(),
    sendMessage: vi.fn()
  }
} as unknown as typeof chrome;
```

- [ ] **Step 4: Run the content-script tests**

Run:

```bash
npm test -- tests/content-script.test.ts
```

Expected: PASS with `2 passed`.

- [ ] **Step 5: Commit the content-script wiring**

Run:

```bash
git add src/content/index.ts tests/content-script.test.ts tests/setup.ts
git commit -m "feat: wire content script apply and observe lifecycle"
```

## Task 5: Build the popup UI and current-tab apply flow

**Files:**
- Modify: `src/popup/index.ts`
- Modify: `src/popup/styles.css`
- Create: `tests/popup.test.ts`

- [ ] **Step 1: Write failing popup tests for render, add rule, apply, and disable**

Create `tests/popup.test.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mountPopup } from '../src/popup/index';

describe('popup', () => {
  beforeEach(() => {
    document.body.innerHTML = '<div id="app"></div>';
    vi.mocked(chrome.tabs.query).mockResolvedValue([{ id: 7 }] as chrome.tabs.Tab[]);
    vi.mocked(chrome.tabs.sendMessage).mockResolvedValue({ enabled: false, activeRuleCount: 0 });
  });

  it('renders stored rules and adds a new row', async () => {
    await chrome.storage.local.set({
      globalRules: [{ id: '1', source: '沈清辞', target: '林惊鹤' }]
    });

    const api = await mountPopup(document.getElementById('app')!);
    api.addRule();

    expect(document.querySelectorAll('[data-rule-row]').length).toBe(2);
  });

  it('saves normalized rules and sends apply to the active tab', async () => {
    const api = await mountPopup(document.getElementById('app')!);

    api.updateRule('test-uuid', 'source', '沈清辞');
    api.updateRule('test-uuid', 'target', '林惊鹤');
    await api.apply();

    expect(chrome.tabs.sendMessage).toHaveBeenCalledWith(7, {
      type: 'APPLY_RULES',
      rules: [{ id: 'test-uuid', source: '沈清辞', target: '林惊鹤' }]
    });
  });
});
```

Run:

```bash
npm test -- tests/popup.test.ts
```

Expected: FAIL because `mountPopup` does not exist.

- [ ] **Step 2: Implement popup state, rendering, and active-tab messaging**

Replace `src/popup/index.ts` with:

```ts
import { createEmptyRule, normalizeRules } from '../shared/rules';
import { loadRules, saveRules } from '../shared/storage';
import type { ReplaceRule } from '../shared/types';

type PopupState = {
  rules: ReplaceRule[];
  enabled: boolean;
  tabId: number | null;
};

export async function mountPopup(root: HTMLElement) {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  const pageState = tab?.id
    ? await chrome.tabs.sendMessage(tab.id, { type: 'GET_PAGE_STATE' }).catch(() => ({
        enabled: false,
        activeRuleCount: 0
      }))
    : { enabled: false, activeRuleCount: 0 };

  const storedRules = await loadRules();
  const state: PopupState = {
    rules: storedRules.length ? storedRules : [createEmptyRule()],
    enabled: Boolean(pageState.enabled),
    tabId: tab?.id ?? null
  };

  function render() {
    root.innerHTML = `
      <section class="popup">
        <header class="popup__header">
          <h1>名字替换</h1>
          <label class="popup__toggle">
            <input type="checkbox" data-role="enabled" ${state.enabled ? 'checked' : ''}>
            <span>本页启用</span>
          </label>
        </header>
        <div class="popup__list">
          ${state.rules
            .map(
              (rule) => `
                <div class="rule-row" data-rule-row data-id="${rule.id}">
                  <input data-field="source" data-id="${rule.id}" value="${rule.source}" placeholder="原名">
                  <input data-field="target" data-id="${rule.id}" value="${rule.target}" placeholder="替换成">
                  <button data-remove="${rule.id}" type="button">删</button>
                </div>
              `
            )
            .join('')}
        </div>
        <footer class="popup__footer">
          <button data-role="add" type="button">新增一条</button>
          <button data-role="apply" type="button">立即生效</button>
        </footer>
      </section>
    `;

    root.querySelector('[data-role="add"]')!.addEventListener('click', addRule);
    root.querySelector('[data-role="apply"]')!.addEventListener('click', () => void apply());
    root.querySelector('[data-role="enabled"]')!.addEventListener('change', toggleEnabled);
    root.querySelectorAll<HTMLInputElement>('[data-field]').forEach((input) => {
      input.addEventListener('input', (event) => {
        const target = event.currentTarget as HTMLInputElement;
        updateRule(target.dataset.id!, target.dataset.field as 'source' | 'target', target.value);
      });
    });
    root.querySelectorAll<HTMLButtonElement>('[data-remove]').forEach((button) => {
      button.addEventListener('click', () => removeRule(button.dataset.remove!));
    });
  }

  function addRule() {
    state.rules.push(createEmptyRule());
    render();
  }

  function removeRule(id: string) {
    state.rules = state.rules.filter((rule) => rule.id !== id);
    if (state.rules.length === 0) state.rules = [createEmptyRule()];
    render();
  }

  function updateRule(id: string, field: 'source' | 'target', value: string) {
    state.rules = state.rules.map((rule) => (rule.id === id ? { ...rule, [field]: value } : rule));
  }

  async function apply() {
    const normalized = await saveRules(state.rules);
    state.rules = normalized.length ? normalized : [createEmptyRule()];

    if (state.tabId) {
      await chrome.tabs.sendMessage(state.tabId, {
        type: 'APPLY_RULES',
        rules: normalized
      });
    }

    state.enabled = true;
    render();
  }

  async function toggleEnabled(event: Event) {
    state.enabled = (event.currentTarget as HTMLInputElement).checked;
    if (!state.tabId) return;

    if (state.enabled) {
      await chrome.tabs.sendMessage(state.tabId, {
        type: 'APPLY_RULES',
        rules: normalizeRules(state.rules)
      });
      return;
    }

    await chrome.tabs.sendMessage(state.tabId, { type: 'DISABLE_PAGE' });
  }

  render();

  return { addRule, updateRule, apply };
}

void mountPopup(document.getElementById('app')!);
```

- [ ] **Step 3: Add lightweight popup styling that stays compact**

Replace `src/popup/styles.css` with:

```css
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  color: #0f172a;
}

.popup {
  width: 360px;
  padding: 12px;
  box-sizing: border-box;
  display: grid;
  gap: 12px;
}

.popup__header,
.popup__footer,
.rule-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.popup__header {
  justify-content: space-between;
}

.popup__list {
  display: grid;
  gap: 8px;
}

.rule-row input {
  flex: 1;
  min-width: 0;
  border: 1px solid #d0d7de;
  border-radius: 8px;
  padding: 8px 10px;
}

button {
  border: 1px solid #d0d7de;
  border-radius: 8px;
  background: #fff;
  padding: 8px 10px;
  cursor: pointer;
}
```

- [ ] **Step 4: Run popup and full test suite**

Run:

```bash
npm test -- tests/popup.test.ts
npm test
npm run build
```

Expected:
- `tests/popup.test.ts` passes
- full suite has `0 failed`
- build exits `0`

- [ ] **Step 5: Commit the popup UI**

Run:

```bash
git add src/popup/index.ts src/popup/styles.css tests/popup.test.ts
git commit -m "feat: add popup rule editor and apply flow"
```

## Task 6: Add README and perform manual smoke validation

**Files:**
- Create: `README.md`

- [ ] **Step 1: Write a failing documentation check by listing the required operator steps**

Required README sections:

```md
- 安装依赖
- 本地构建
- Chrome 加载扩展
- 基本使用
- 手工烟测清单
```

Manual smoke checklist to verify after implementation:

```md
1. 在任意普通网页打开插件，新增“沈清辞 -> 林惊鹤”
2. 点击“立即生效”，确认正文替换、输入框和代码块不替换
3. 再把目标改成“顾云深”，确认当前页已替换文本能重新计算
4. 关闭“本页启用”，确认后续新增内容不再替换
5. 在支持无限滚动的页面继续滚动，确认新内容按规则替换
```

Expected before writing README: these instructions do not exist anywhere in the repo.

- [ ] **Step 2: Create the README**

Create `README.md`:

```md
# 名字替换浏览器插件

## 安装依赖

```bash
npm install
```

## 本地构建

```bash
npm run build
```

构建产物输出到 `dist/`。

## Chrome 加载扩展

1. 打开 Chrome 扩展管理页
2. 开启“开发者模式”
3. 选择“加载已解压的扩展程序”
4. 选择当前项目下的 `dist/` 目录

## 基本使用

1. 打开任意网页
2. 点击工具栏中的插件图标
3. 输入一条或多条 `原名 -> 替换成` 规则
4. 点击 `立即生效`
5. 如需暂停，关闭 `本页启用`

## 手工烟测清单

1. 在普通正文页确认替换即时生效
2. 确认输入框、按钮、代码块、预格式内容不被误替换
3. 修改规则后确认当前页已替换内容会重新计算
4. 关闭本页启用后确认后续动态内容不再替换
5. 在动态加载页面确认新增内容会自动替换
```
```

- [ ] **Step 3: Build and execute the manual smoke pass**

Run:

```bash
npm run build
open -a "Google Chrome" chrome://extensions/
```

Then load `dist/` manually and run this checklist:

```md
1. 在一篇普通长文页面添加规则“沈清辞 -> 林惊鹤”
2. 观察正文替换后字体、字号、颜色保持原样
3. 继续把规则改成“沈清辞 -> 顾云深”，确认当前页文本重新渲染为“顾云深”
4. 在页面追加或滚动加载新内容，确认新内容同步替换
5. 关闭“本页启用”，确认新增内容停止替换
```

- [ ] **Step 4: Run final automated checks**

Run:

```bash
npm test
npm run build
```

Expected:
- all tests pass
- build exits `0`
- extension is loadable from `dist/`

- [ ] **Step 5: Commit docs and final validation**

Run:

```bash
git add README.md
git commit -m "docs: add extension usage and smoke test guide"
```

## Self-review

- Spec coverage checked:
  - 极简规则弹窗：Task 5
  - 当前页立即生效：Task 4 + Task 5
  - 外观无感：Task 3
  - 动态内容持续生效：Task 4
  - 全局规则持久化：Task 2 + Task 5
  - 误替换基础防护：Task 2 + Task 3
  - 本页启用开关：Task 4 + Task 5
- Placeholder scan: no `TBD`/`TODO`/“implement later” placeholders remain.
- Type consistency checked:
  - `ReplaceRule`, `PageRuntimeState`, and `ContentMessage` are introduced in Task 2 and reused consistently later.
  - `mountContentScript` and `mountPopup` names stay stable across tests and implementation.

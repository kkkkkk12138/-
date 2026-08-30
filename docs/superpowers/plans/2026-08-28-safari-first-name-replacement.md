# Safari First Name Replacement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure `小说一键换名` into a Safari-first WebExtension project that preserves the existing text-replacement core, introduces a browser API adapter, and emits Safari-oriented packaging artifacts while keeping Chromium desktop as a secondary output.

**Architecture:** Keep the existing replacement engine, popup editor, and shared rule model as the product core. Add a thin platform adapter to isolate browser APIs, replace Chrome-specific build outputs with a neutral `build/webextension` source artifact plus derived `build/chromium-load` and `build/safari-upload` bundles, and update the popup/readme to reflect Safari permission and onboarding flows.

**Tech Stack:** TypeScript, Manifest V3/WebExtension APIs, esbuild, Node.js build scripts, Vitest, JSDOM, vanilla HTML/CSS

---

## File map

- Create: `src/platform/browserApi.ts` — browser API adapter for tabs/page messaging/storage/runtime capability detection
- Create: `src/platform/types.ts` — shared adapter result types used by popup and tests
- Create: `src/popup/onboarding.ts` — Safari-first copy helpers for permission and entry-state messaging
- Modify: `src/popup/index.ts` — switch popup logic to platform adapter and render Safari onboarding state
- Modify: `src/shared/types.ts` — extend runtime state and message types for permission/onboarding status
- Modify: `src/content/index.ts` — report richer page state without hard dependency on Chrome-specific assumptions
- Modify: `manifest.json` — reduce Safari-hostile assumptions and centralize metadata for shared build
- Modify: `popup.html` — update title and layout shell if needed for onboarding text
- Modify: `src/popup/styles.css` — support Safari-first status panel and mobile-friendly compact layout
- Modify: `scripts/build.mjs` — emit `build/webextension`, `build/chromium-load`, `build/safari-upload`
- Modify: `scripts/prepare-manual-load.mjs` — point Chromium manual-load output at `build/chromium-load`
- Modify: `package.json` — replace Chrome-centric scripts with neutral bundle scripts and Safari upload prep
- Modify: `README.md` — rewrite setup and testing around Safari-first flow and secondary Chromium flow
- Create: `docs/safari/APP_STORE_CONNECT_UPLOAD.md` — operational guide for Safari packager/TestFlight/App Store path
- Test: `tests/browser-api.test.ts` — adapter tests
- Test: `tests/popup.test.ts` — updated popup flow tests
- Test: `tests/content-script.test.ts` — richer runtime state tests
- Test: `tests/manual-load.test.ts` — update build output assertions

## Task 1: Introduce a browser API adapter

**Files:**
- Create: `src/platform/types.ts`
- Create: `src/platform/browserApi.ts`
- Modify: `tests/setup.ts`
- Create: `tests/browser-api.test.ts`

- [ ] **Step 1: Write the failing adapter tests**

Create `tests/browser-api.test.ts`:

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createBrowserApi,
  getBrowserFamily
} from '../src/platform/browserApi';

describe('browser adapter', () => {
  beforeEach(() => {
    vi.mocked(chrome.tabs.query).mockResolvedValue([{ id: 9 }] as chrome.tabs.Tab[]);
    vi.mocked(chrome.tabs.sendMessage).mockResolvedValue({
      enabled: true,
      activeRuleCount: 2,
      permissionState: 'granted'
    });
  });

  it('reads the active page state through one adapter entrypoint', async () => {
    const api = createBrowserApi();

    await expect(api.getActiveContext()).resolves.toEqual({
      tabId: 9,
      browserFamily: 'chromium',
      pageState: {
        enabled: true,
        activeRuleCount: 2,
        permissionState: 'granted'
      }
    });
  });

  it('falls back to safari when chrome.tabs is unavailable', () => {
    // @ts-expect-error test override
    globalThis.chrome = {
      storage: globalThis.chrome.storage,
      runtime: globalThis.chrome.runtime
    };

    expect(getBrowserFamily()).toBe('safari');
  });
});
```

Run:

```bash
npm test -- tests/browser-api.test.ts
```

Expected: FAIL with `Cannot find module '../src/platform/browserApi'`.

- [ ] **Step 2: Define adapter-facing types**

Create `src/platform/types.ts`:

```ts
import type { PageRuntimeState, ReplaceRule } from '../shared/types';

export type BrowserFamily = 'chromium' | 'safari';

export type ActiveContext = {
  tabId: number | null;
  browserFamily: BrowserFamily;
  pageState: PageRuntimeState;
};

export type ApplyRulesPayload = {
  rules: ReplaceRule[];
};
```

- [ ] **Step 3: Implement the browser adapter**

Create `src/platform/browserApi.ts`:

```ts
import type { ActiveContext, BrowserFamily } from './types';
import type { ContentMessage, PageRuntimeState, ReplaceRule } from '../shared/types';

const DEFAULT_PAGE_STATE: PageRuntimeState = {
  enabled: false,
  activeRuleCount: 0,
  permissionState: 'unknown'
};

export function getBrowserFamily(): BrowserFamily {
  if (typeof chrome?.tabs?.query === 'function' && typeof chrome?.tabs?.sendMessage === 'function') {
    return 'chromium';
  }

  return 'safari';
}

async function getChromiumActiveContext(): Promise<ActiveContext> {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  const pageState = tab?.id
    ? await chrome.tabs
        .sendMessage(tab.id, { type: 'GET_PAGE_STATE' } as ContentMessage)
        .catch(() => DEFAULT_PAGE_STATE)
    : DEFAULT_PAGE_STATE;

  return {
    tabId: tab?.id ?? null,
    browserFamily: 'chromium',
    pageState
  };
}

function getSafariActiveContext(): ActiveContext {
  return {
    tabId: null,
    browserFamily: 'safari',
    pageState: {
      ...DEFAULT_PAGE_STATE,
      permissionState: 'unknown'
    }
  };
}

export function createBrowserApi() {
  return {
    async getActiveContext(): Promise<ActiveContext> {
      return getBrowserFamily() === 'chromium'
        ? getChromiumActiveContext()
        : getSafariActiveContext();
    },

    async applyRulesToActivePage(tabId: number | null, rules: ReplaceRule[]): Promise<void> {
      if (getBrowserFamily() !== 'chromium' || tabId === null) return;

      await chrome.tabs.sendMessage(tabId, {
        type: 'APPLY_RULES',
        rules
      } satisfies ContentMessage);
    },

    async disableActivePage(tabId: number | null): Promise<void> {
      if (getBrowserFamily() !== 'chromium' || tabId === null) return;

      await chrome.tabs.sendMessage(tabId, {
        type: 'DISABLE_PAGE'
      } satisfies ContentMessage);
    }
  };
}
```

- [ ] **Step 4: Update test setup for richer runtime state**

Update `tests/setup.ts`:

```ts
import { beforeEach, vi } from 'vitest';

const storageState: Record<string, unknown> = {};

vi.stubGlobal('crypto', {
  randomUUID: () => 'test-uuid'
});

beforeEach(() => {
  for (const key of Object.keys(storageState)) {
    delete storageState[key];
  }
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

- [ ] **Step 5: Run the adapter tests**

Run:

```bash
npm test -- tests/browser-api.test.ts
```

Expected: PASS with `2 passed`.

- [ ] **Step 6: Commit**

```bash
git add src/platform/types.ts src/platform/browserApi.ts tests/setup.ts tests/browser-api.test.ts
git commit -m "refactor: add browser api adapter"
```

## Task 2: Extend runtime state for Safari-first permission messaging

**Files:**
- Modify: `src/shared/types.ts`
- Modify: `src/content/index.ts`
- Modify: `tests/content-script.test.ts`

- [ ] **Step 1: Write the failing runtime-state test**

Update `tests/content-script.test.ts` to add:

```ts
  it('returns permission-aware page state', async () => {
    const api = mountContentScript(document);

    await expect(api.handleMessage({ type: 'GET_PAGE_STATE' })).resolves.toEqual({
      enabled: false,
      activeRuleCount: 0,
      permissionState: 'granted'
    });
  });
```

Run:

```bash
npm test -- tests/content-script.test.ts
```

Expected: FAIL because `permissionState` is missing from the returned state.

- [ ] **Step 2: Extend shared types**

Update `src/shared/types.ts`:

```ts
export type ReplaceRule = {
  id: string;
  source: string;
  target: string;
};

export type PermissionState = 'granted' | 'needs-user-action' | 'unknown';

export type PageRuntimeState = {
  enabled: boolean;
  activeRuleCount: number;
  permissionState: PermissionState;
};

export type ContentMessage =
  | { type: 'GET_PAGE_STATE' }
  | { type: 'APPLY_RULES'; rules: ReplaceRule[] }
  | { type: 'DISABLE_PAGE' };
```

- [ ] **Step 3: Return richer runtime state from content script**

Update the `GET_PAGE_STATE` branch in `src/content/index.ts`:

```ts
    if (message.type === 'GET_PAGE_STATE') {
      return {
        enabled,
        activeRuleCount: rules.length,
        permissionState: 'granted'
      };
    }
```

- [ ] **Step 4: Run the content-script test file**

Run:

```bash
npm test -- tests/content-script.test.ts
```

Expected: PASS with all tests green.

- [ ] **Step 5: Commit**

```bash
git add src/shared/types.ts src/content/index.ts tests/content-script.test.ts
git commit -m "feat: add permission-aware runtime state"
```

## Task 3: Refactor the popup into a Safari-first UI shell

**Files:**
- Create: `src/popup/onboarding.ts`
- Modify: `src/popup/index.ts`
- Modify: `src/popup/styles.css`
- Modify: `tests/popup.test.ts`

- [ ] **Step 1: Write the failing popup test for Safari onboarding**

Append this case to `tests/popup.test.ts`:

```ts
  it('renders safari-first onboarding copy when active tabs api is unavailable', async () => {
    // @ts-expect-error test override
    globalThis.chrome = {
      runtime: chrome.runtime,
      storage: chrome.storage
    };

    await mountPopup(document.getElementById('app')!);

    expect(document.body.textContent).toContain('请先在 Safari 中为当前网站开启扩展权限');
  });
```

Run:

```bash
npm test -- tests/popup.test.ts
```

Expected: FAIL because the popup renders only Chromium-specific controls with no Safari copy.

- [ ] **Step 2: Add Safari-first status copy helpers**

Create `src/popup/onboarding.ts`:

```ts
import type { BrowserFamily } from '../platform/types';
import type { PermissionState } from '../shared/types';

export function getStatusCopy(browserFamily: BrowserFamily, permissionState: PermissionState): string {
  if (browserFamily === 'safari') {
    return '请先在 Safari 中为当前网站开启扩展权限，然后回到页面继续换名。';
  }

  if (permissionState === 'needs-user-action') {
    return '当前网站还没有授权，请先允许扩展访问此网站。';
  }

  return '当前页可直接应用规则。';
}
```

- [ ] **Step 3: Rewrite popup state around the adapter**

Replace `src/popup/index.ts` with:

```ts
import { createBrowserApi } from '../platform/browserApi';
import type { BrowserFamily } from '../platform/types';
import { createEmptyRule, normalizeRules } from '../shared/rules';
import { loadRules, saveRules } from '../shared/storage';
import type { PermissionState, ReplaceRule } from '../shared/types';
import { getStatusCopy } from './onboarding';

type PopupState = {
  rules: ReplaceRule[];
  enabled: boolean;
  tabId: number | null;
  browserFamily: BrowserFamily;
  permissionState: PermissionState;
};

export async function mountPopup(root: HTMLElement) {
  const browserApi = createBrowserApi();
  const activeContext = await browserApi.getActiveContext();
  const storedRules = await loadRules();

  const state: PopupState = {
    rules: storedRules.length ? storedRules : [createEmptyRule()],
    enabled: Boolean(activeContext.pageState.enabled),
    tabId: activeContext.tabId,
    browserFamily: activeContext.browserFamily,
    permissionState: activeContext.pageState.permissionState
  };

  function render() {
    const statusCopy = getStatusCopy(state.browserFamily, state.permissionState);
    const applyDisabled = state.browserFamily === 'safari';

    root.innerHTML = `
      <section class="popup">
        <header class="popup__header">
          <div>
            <h1>小说一键换名</h1>
            <p class="popup__subhead">Safari 首发，当前页触发</p>
          </div>
          <label class="popup__toggle">
            <input type="checkbox" data-role="enabled" ${state.enabled ? 'checked' : ''} ${
              state.browserFamily === 'safari' ? 'disabled' : ''
            }>
            <span>本页启用</span>
          </label>
        </header>

        <section class="popup__status" data-role="status">
          ${statusCopy}
        </section>

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
          <button data-role="apply" type="button" ${applyDisabled ? 'disabled' : ''}>立即生效</button>
        </footer>
      </section>
    `;

    root.querySelector('[data-role="add"]')!.addEventListener('click', addRule);
    root.querySelector('[data-role="apply"]')!.addEventListener('click', () => void apply());
    root.querySelector('[data-role="enabled"]')?.addEventListener('change', toggleEnabled);
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
    await browserApi.applyRulesToActivePage(state.tabId, normalized);
    state.enabled = true;
    render();
  }

  async function toggleEnabled(event: Event) {
    state.enabled = (event.currentTarget as HTMLInputElement).checked;

    if (state.enabled) {
      await browserApi.applyRulesToActivePage(state.tabId, normalizeRules(state.rules));
      return;
    }

    await browserApi.disableActivePage(state.tabId);
  }

  render();
  return { addRule, updateRule, apply };
}

void mountPopup(document.getElementById('app')!);
```

- [ ] **Step 4: Add status-panel styling**

Insert into `src/popup/styles.css`:

```css
.popup__subhead {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
}

.popup__status {
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.5;
  color: #334155;
}

button:disabled,
input:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
```

- [ ] **Step 5: Run the popup tests**

Run:

```bash
npm test -- tests/popup.test.ts
```

Expected: PASS with all popup cases green.

- [ ] **Step 6: Commit**

```bash
git add src/popup/onboarding.ts src/popup/index.ts src/popup/styles.css tests/popup.test.ts
git commit -m "feat: add safari-first popup shell"
```

## Task 4: Replace Chrome-specific build outputs with neutral packaging artifacts

**Files:**
- Modify: `scripts/build.mjs`
- Modify: `scripts/prepare-manual-load.mjs`
- Modify: `package.json`
- Modify: `tests/manual-load.test.ts`

- [ ] **Step 1: Write the failing build-output test**

Replace `tests/manual-load.test.ts` with:

```ts
import { describe, expect, it } from 'vitest';
import { existsSync } from 'node:fs';

describe('distribution bundle layout', () => {
  it('emits the safari-first directory contract', () => {
    expect(existsSync('build/webextension')).toBe(true);
    expect(existsSync('build/chromium-load')).toBe(true);
    expect(existsSync('build/safari-upload')).toBe(true);
  });
});
```

Run:

```bash
npm test -- tests/manual-load.test.ts
```

Expected: FAIL because the `build/` directories do not exist.

- [ ] **Step 2: Rewrite the build script**

Replace `scripts/build.mjs` with:

```js
import { build } from 'esbuild';
import { cp, mkdir, rm } from 'node:fs/promises';
import { generateIcons } from './generate-icons.mjs';
import { prepareManualLoadBundle } from './prepare-manual-load.mjs';

const ROOT = 'build';
const WEBEXT = `${ROOT}/webextension`;
const SAFARI = `${ROOT}/safari-upload`;

await generateIcons();
await rm(ROOT, { recursive: true, force: true });
await mkdir(WEBEXT, { recursive: true });

await Promise.all([
  build({
    entryPoints: ['src/popup/index.ts'],
    bundle: true,
    outfile: `${WEBEXT}/popup.js`,
    format: 'iife',
    platform: 'browser'
  }),
  build({
    entryPoints: ['src/content/index.ts'],
    bundle: true,
    outfile: `${WEBEXT}/content.js`,
    format: 'iife',
    platform: 'browser'
  }),
  cp('manifest.json', `${WEBEXT}/manifest.json`),
  cp('popup.html', `${WEBEXT}/popup.html`),
  cp('src/popup/styles.css', `${WEBEXT}/styles.css`),
  cp('assets/icons', `${WEBEXT}/icons`, { recursive: true })
]);

await mkdir(SAFARI, { recursive: true });
await cp(WEBEXT, SAFARI, { recursive: true });
await prepareManualLoadBundle(WEBEXT, `${ROOT}/chromium-load`);
```

- [ ] **Step 3: Update the Chromium manual-load helper**

Replace `scripts/prepare-manual-load.mjs` with:

```js
import { cp, mkdir, rm, writeFile } from 'node:fs/promises';

export async function prepareManualLoadBundle(sourceDir = 'build/webextension', targetDir = 'build/chromium-load') {
  await rm(targetDir, { recursive: true, force: true });
  await mkdir(targetDir, { recursive: true });
  await cp(sourceDir, targetDir, { recursive: true });

  await writeFile(
    `${targetDir}/LOAD_IN_CHROME.md`,
    [
      '# Chromium 手动加载',
      '',
      '1. 打开 chrome://extensions/',
      '2. 开启开发者模式',
      '3. 选择“加载已解压的扩展程序”',
      '4. 选择当前目录'
    ].join('\\n')
  );
}
```

- [ ] **Step 4: Rename npm scripts around the new distribution model**

Update the `scripts` block in `package.json`:

```json
  "scripts": {
    "build": "node scripts/build.mjs",
    "build:webextension": "node scripts/build.mjs",
    "chromium:open": "open -a \"Google Chrome\" \"chrome://extensions/\"",
    "chromium:ready": "npm run build && npm run chromium:open",
    "test": "vitest run",
    "test:watch": "vitest"
  },
```

- [ ] **Step 5: Run build and the distribution-layout test**

Run:

```bash
npm run build
npm test -- tests/manual-load.test.ts
```

Expected:
- `npm run build` exits `0`
- the manual-load test passes
- `build/webextension`, `build/chromium-load`, and `build/safari-upload` exist

- [ ] **Step 6: Commit**

```bash
git add scripts/build.mjs scripts/prepare-manual-load.mjs package.json tests/manual-load.test.ts
git commit -m "refactor: add safari-first distribution outputs"
```

## Task 5: Update manifest metadata and shared shell files for Safari-first positioning

**Files:**
- Modify: `manifest.json`
- Modify: `popup.html`
- Modify: `tests/branding.test.ts`

- [ ] **Step 1: Write the failing metadata test**

Append this case to `tests/branding.test.ts`:

```ts
import { readFileSync } from 'node:fs';

it('describes safari-first positioning in the manifest description', () => {
  const manifest = JSON.parse(readFileSync('manifest.json', 'utf8'));
  expect(manifest.description).toContain('Safari');
});
```

Run:

```bash
npm test -- tests/branding.test.ts
```

Expected: FAIL because the current manifest description is Chrome-era and does not mention Safari-first positioning.

- [ ] **Step 2: Update shared metadata**

Replace `manifest.json` with:

```json
{
  "manifest_version": 3,
  "name": "小说一键换名",
  "version": "0.2.0",
  "description": "Safari 首发的阅读换名扩展，在当前阅读网页里无感替换角色名字。",
  "permissions": ["storage", "activeTab"],
  "host_permissions": ["https://*/*", "http://*/*"],
  "icons": {
    "16": "icons/icon16.png",
    "32": "icons/icon32.png",
    "48": "icons/icon48.png",
    "128": "icons/icon128.png"
  },
  "action": {
    "default_popup": "popup.html",
    "default_title": "小说一键换名",
    "default_icon": {
      "16": "icons/icon16.png",
      "32": "icons/icon32.png"
    }
  },
  "content_scripts": [
    {
      "matches": ["https://*/*", "http://*/*"],
      "js": ["content.js"],
      "run_at": "document_idle"
    }
  ]
}
```

- [ ] **Step 3: Update popup shell title**

Replace the `<title>` block in `popup.html` with:

```html
    <title>小说一键换名 · Safari 首发</title>
    <link rel="stylesheet" href="./styles.css" />
```

- [ ] **Step 4: Run branding tests**

Run:

```bash
npm test -- tests/branding.test.ts
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add manifest.json popup.html tests/branding.test.ts
git commit -m "feat: update shared metadata for safari-first release"
```

## Task 6: Rewrite README and add Safari operational docs

**Files:**
- Modify: `README.md`
- Create: `docs/safari/APP_STORE_CONNECT_UPLOAD.md`

- [ ] **Step 1: Write the failing docs expectation**

Use this command to confirm the existing README is Chrome-centric:

```bash
grep -n "Chrome 加载扩展" README.md
```

Expected: output includes the old Chrome-only section, proving the README still needs replacement.

- [ ] **Step 2: Rewrite the README for Safari-first usage**

Replace `README.md` with:

```md
# 小说一键换名

Safari 首发的阅读换名扩展。目标是在当前阅读网页里无感替换角色名字，并尽量保持原网页的字体、字号、颜色与排版不变。

## 项目结构

- `build/webextension/`：标准 WebExtension 核心产物
- `build/safari-upload/`：上传到 Safari Web Extension Packager 的目录
- `build/chromium-load/`：Chromium 桌面手工加载目录

## 安装依赖

```bash
npm install
```

## 构建

```bash
npm run build
```

## Safari 首发测试

1. 执行 `npm run build`
2. 使用 `build/safari-upload/` 作为 Safari 打包上传目录
3. 在 App Store Connect 的 Safari Web Extension Packager 中上传完整扩展文件
4. 通过 TestFlight 在 iPhone、iPad、Mac 上测试

## Chromium 桌面兼容测试

1. 执行 `npm run chromium:ready`
2. 在 Chrome 扩展页选择 `build/chromium-load/`

## 核心使用

1. 打开阅读网页
2. 打开扩展入口
3. 输入 `原名 -> 替换成`
4. 对当前页生效

## 手工烟测

1. 验证普通正文节点会替换
2. 验证输入框、按钮、代码块不替换
3. 修改规则后确认当前页按原文重算
4. 验证动态新增内容继续替换
5. 在 Safari 里验证未授权网站时会出现明确提示
```

- [ ] **Step 3: Add the Safari upload runbook**

Create `docs/safari/APP_STORE_CONNECT_UPLOAD.md`:

```md
# Safari Web Extension 上传说明

## 目标

把 `build/safari-upload/` 作为 Safari Web Extension Packager 的输入目录，走 TestFlight，再进入 App Store 审核。

## 路径

1. 登录 App Store Connect
2. 创建 App 记录，平台选择 iOS 和 macOS，或至少 iOS
3. 打开 Xcode Cloud 标签页
4. 在 Safari Web Extension Packager 中点击 Upload
5. 上传 `build/safari-upload/` 中的完整扩展文件
6. 等待打包完成
7. 通过 TestFlight 分发测试
8. 测试通过后提交 App Store 审核

## 上传前检查

- `manifest.json` 存在
- `popup.html`、`popup.js`、`content.js`、`styles.css` 存在
- 图标目录完整
- 版本号已更新
- 权限说明与产品文案一致
```

- [ ] **Step 4: Run the full suite and build**

Run:

```bash
npm test
npm run build
```

Expected:
- full test suite passes
- Safari-first output directories are generated

- [ ] **Step 5: Commit**

```bash
git add README.md docs/safari/APP_STORE_CONNECT_UPLOAD.md
git commit -m "docs: rewrite project docs for safari-first release"
```

## Self-review

- Spec coverage checked:
  - Safari 首发平台修正：Task 5 + Task 6
  - 保留 WebExtension 内核：Task 1 + Task 2 + Task 3
  - 去 Chrome 化构建产物：Task 4
  - Safari 权限与当前页触发心智：Task 2 + Task 3 + Task 5
  - 移动端入口解释层：Task 3 + Task 6
  - App Store Connect / TestFlight 分发路线：Task 4 + Task 6
- Placeholder scan: no `TODO`, `TBD`, or vague “handle later” steps remain.
- Type consistency checked:
  - `PageRuntimeState.permissionState` is introduced in Task 2 and reused consistently in adapter and popup tasks.
  - `build/webextension`, `build/chromium-load`, and `build/safari-upload` naming stays stable across Task 4 and Task 6.

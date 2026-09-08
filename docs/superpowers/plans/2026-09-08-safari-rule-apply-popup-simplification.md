# Safari Rule Apply and Popup Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Safari rules apply to the active reading page and reduce the extension popup to one collapsed “添加规则” entry that expands only when editing.

**Architecture:** Safari and Chromium share one `tabs.sendMessage` path; browser-family detection no longer changes product behavior. The popup owns only local editor state (`expanded`, `errorMessage`, `isApplying`), while the content script remains responsible for applying rules, observing dynamic content, and restoring original text.

**Tech Stack:** TypeScript, DOM APIs, Safari WebExtension/Chromium WebExtension APIs, Vitest, jsdom, esbuild, Xcode

---

## File map

**Modify**

- `src/popup/index.ts` — remove Safari bypasses and implement collapsed editor state.
- `src/popup/styles.css` — replace dashboard styling with compact trigger/editor styling.
- `src/content/index.ts` — restore original text on disable.
- `tests/popup.test.ts` — cover Safari messaging and collapsed interaction.
- `tests/content-script.test.ts` — cover original-text restoration.
- `apple/小说一键换名/Shared (Extension)/Resources/` — synchronized build output.

**Delete**

- `src/popup/onboarding.ts` — tutorial content is no longer rendered.

## Task 1: Restore Safari active-page messaging

**Files**

- Modify: `src/popup/index.ts`
- Modify: `tests/popup.test.ts`

- [ ] **Step 1: Add a failing Safari message test**

Refactor `mountPopup` to accept an optional `BrowserApi` only after the failing test demonstrates the need. Add this test to `tests/popup.test.ts`:

```ts
it('Safari 也向当前标签页发送 APPLY_RULES', async () => {
  const sendMessage = vi.fn(async (_tabId: number, message: ContentMessage) => {
    if (message.type === 'GET_PAGE_STATE') {
      return {
        enabled: false,
        activeRuleCount: 0,
        permissionState: 'granted' as const
      };
    }
  });
  const safariApi: BrowserApi = {
    family: 'safari',
    namespace: 'browser',
    storage: {
      get: vi.fn(async () => ({ globalRules: [] })),
      set: vi.fn(async () => undefined)
    },
    tabs: {
      query: vi.fn(async () => [{ id: 7 }]),
      sendMessage
    },
    runtime: {
      addMessageListener: vi.fn()
    }
  };

  const popup = await mountPopup(document.getElementById('app')!, safariApi);
  popup.addRule();
  const ruleId = document.querySelector<HTMLElement>('[data-rule-row]')!.dataset.id!;
  popup.updateRule(ruleId, 'source', '沈清辞');
  popup.updateRule(ruleId, 'target', '林惊鹤');

  await popup.apply();

  expect(sendMessage).toHaveBeenCalledWith(7, {
    type: 'APPLY_RULES',
    rules: [{ id: ruleId, source: '沈清辞', target: '林惊鹤' }]
  });
});
```

Add imports:

```ts
import type { BrowserApi } from '../src/platform/types';
import type { ContentMessage } from '../src/shared/types';
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run:

```bash
npx vitest run tests/popup.test.ts
```

Expected: FAIL because `mountPopup` accepts one argument and Safari currently skips `APPLY_RULES`.

- [ ] **Step 3: Inject the browser API and remove Safari bypasses**

Change the mount signature:

```ts
export async function mountPopup(
  root: HTMLElement,
  browserApi: BrowserApi = createBrowserApi()
): Promise<PopupApi> {
```

Change `sendTabMessage`:

```ts
async function sendTabMessage(
  browserApi: BrowserApi,
  tabId: number | null,
  message: ContentMessage
): Promise<PageRuntimeState | void> {
  if (tabId === null) {
    throw new Error('当前页面不可用');
  }

  return browserApi.tabs.sendMessage(tabId, message);
}
```

Remove every condition that prevents `APPLY_RULES` or `DISABLE_PAGE` solely because `browserFamily === 'safari'`. Retain `permissionState === 'needs-user-action'` only as displayable error context, not as a pre-send return.

- [ ] **Step 4: Update all `sendTabMessage` calls**

Use:

```ts
await sendTabMessage(browserApi, state.tabId, {
  type: 'APPLY_RULES',
  rules: normalized
});
```

and:

```ts
await sendTabMessage(browserApi, state.tabId, { type: 'DISABLE_PAGE' });
```

- [ ] **Step 5: Run focused and full tests**

Run:

```bash
npx vitest run tests/popup.test.ts
npm test
```

Expected: Safari test passes and existing tests remain green.

- [ ] **Step 6: Commit**

```bash
git add src/popup/index.ts tests/popup.test.ts
git commit -m "fix: apply rules in Safari tabs"
```

## Task 2: Replace the popup dashboard with a collapsed rule editor

**Files**

- Modify: `src/popup/index.ts`
- Modify: `src/popup/styles.css`
- Modify: `tests/popup.test.ts`
- Delete: `src/popup/onboarding.ts`

- [ ] **Step 1: Add failing collapsed-interface tests**

Replace the old rendering assertions with:

```ts
it('初始只显示添加规则按钮', async () => {
  await mountPopup(document.getElementById('app')!);

  expect(document.querySelector('[data-role="open-editor"]')).not.toBeNull();
  expect(document.querySelector('[data-rule-editor]')).toBeNull();
  expect(document.querySelector('[data-role="enabled"]')).toBeNull();
  expect(document.body.textContent).not.toContain('Safari 首发');
});

it('点击添加规则后展开编辑器', async () => {
  await mountPopup(document.getElementById('app')!);

  (document.querySelector('[data-role="open-editor"]') as HTMLButtonElement).click();

  expect(document.querySelector('[data-rule-editor]')).not.toBeNull();
  expect(document.querySelectorAll('[data-rule-row]')).toHaveLength(1);
  expect(document.querySelector('[data-role="apply"]')).not.toBeNull();
});

it('成功应用后自动收起编辑器', async () => {
  const popup = await mountPopup(document.getElementById('app')!);
  popup.addRule();
  const ruleId = document.querySelector<HTMLElement>('[data-rule-row]')!.dataset.id!;
  popup.updateRule(ruleId, 'source', '沈清辞');
  popup.updateRule(ruleId, 'target', '林惊鹤');

  await popup.apply();

  expect(document.querySelector('[data-rule-editor]')).toBeNull();
  expect(document.querySelector('[data-role="open-editor"]')).not.toBeNull();
});

it('应用失败时保留编辑器与输入内容', async () => {
  vi.mocked(chrome.tabs.sendMessage).mockImplementation(async (_tabId, message) => {
    if ((message as ContentMessage).type === 'GET_PAGE_STATE') {
      return {
        enabled: false,
        activeRuleCount: 0,
        permissionState: 'granted'
      };
    }
    throw new Error('Missing host permission');
  });

  const popup = await mountPopup(document.getElementById('app')!);
  popup.addRule();
  const ruleId = document.querySelector<HTMLElement>('[data-rule-row]')!.dataset.id!;
  popup.updateRule(ruleId, 'source', '沈清辞');
  popup.updateRule(ruleId, 'target', '林惊鹤');

  await popup.apply();

  expect(document.querySelector('[data-rule-editor]')).not.toBeNull();
  expect(document.body.textContent).toContain('请允许扩展访问当前网站后重试');
  expect(
    (document.querySelector('[data-field="source"]') as HTMLInputElement).value
  ).toBe('沈清辞');
});
```

- [ ] **Step 2: Run popup tests and confirm RED**

Run:

```bash
npx vitest run tests/popup.test.ts
```

Expected: FAIL because the dashboard renders immediately and no `open-editor` trigger exists.

- [ ] **Step 3: Add minimal UI state**

Change `PopupState`:

```ts
type PopupState = {
  rules: ReplaceRule[];
  tabId: number | null;
  expanded: boolean;
  errorMessage: string;
  isApplying: boolean;
};
```

Initialize:

```ts
const state: PopupState = {
  rules: storedRules,
  tabId: activeContext.tabId,
  expanded: false,
  errorMessage: '',
  isApplying: false
};
```

- [ ] **Step 4: Render the collapsed and expanded states**

Collapsed HTML:

```ts
if (!state.expanded) {
  root.innerHTML = `
    <section class="popup popup--collapsed">
      <button class="popup__open" data-role="open-editor" type="button">
        <span aria-hidden="true">＋</span>
        添加规则
      </button>
    </section>
  `;

  root
    .querySelector<HTMLButtonElement>('[data-role="open-editor"]')
    ?.addEventListener('click', addRule);
  return;
}
```

Expanded HTML:

```ts
root.innerHTML = `
  <section class="popup" data-rule-editor>
    <div class="popup__list">
      ${state.rules.map(renderRuleRow).join('')}
    </div>
    ${
      state.errorMessage
        ? `<p class="popup__error" role="alert">${escapeHtml(state.errorMessage)}</p>`
        : ''
    }
    <footer class="popup__footer">
      <button data-role="add" type="button">再加一条</button>
      <button class="popup__primary" data-role="apply" type="button" ${
        state.isApplying ? 'disabled' : ''
      }>${state.isApplying ? '正在生效…' : '立即生效'}</button>
    </footer>
  </section>
`;
```

Extract the existing row template into:

```ts
function renderRuleRow(rule: ReplaceRule): string {
  return `
    <div class="rule-row" data-rule-row data-id="${rule.id}">
      <input class="rule-row__input" data-field="source" data-id="${rule.id}"
        value="${escapeHtml(rule.source)}" placeholder="原名" />
      <span class="rule-row__arrow" aria-hidden="true">→</span>
      <input class="rule-row__input" data-field="target" data-id="${rule.id}"
        value="${escapeHtml(rule.target)}" placeholder="替换成" />
      <button class="rule-row__remove" data-remove="${rule.id}" type="button"
        aria-label="删除规则">删除</button>
    </div>
  `;
}
```

- [ ] **Step 5: Implement expand, success, and error behavior**

Use:

```ts
function addRule(): void {
  state.rules = [...state.rules, createEmptyRule()];
  state.expanded = true;
  state.errorMessage = '';
  render();
}
```

At the start of `apply()`:

```ts
state.isApplying = true;
state.errorMessage = '';
render();
```

On successful `APPLY_RULES` or `DISABLE_PAGE`:

```ts
state.isApplying = false;
state.expanded = false;
render();
```

On failure:

```ts
state.isApplying = false;
state.expanded = true;
state.errorMessage = '请允许扩展访问当前网站后重试';
render();
```

Remove `setEnabled` from `PopupApi`, remove the toggle listener, and remove the old onboarding/meta/status markup.

- [ ] **Step 6: Replace popup CSS**

Keep the shared body/input/button reset, then use:

```css
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  background: #f8fafc;
  color: #0f172a;
}

#app,
.popup {
  width: min(100vw, 360px);
  box-sizing: border-box;
}

.popup {
  padding: 12px;
}

.popup--collapsed {
  min-width: 180px;
}

button,
input {
  font: inherit;
}

button {
  min-height: 40px;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  background: #fff;
  color: #0f172a;
  cursor: pointer;
}

.popup__open {
  width: 100%;
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
  font-weight: 600;
}

.popup__list {
  display: grid;
  gap: 10px;
}

.rule-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 16px minmax(0, 1fr) auto;
  gap: 6px;
  align-items: center;
}

.rule-row__input {
  min-width: 0;
  min-height: 38px;
  padding: 0 9px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
}

.rule-row__arrow {
  color: #64748b;
  text-align: center;
}

.rule-row__remove {
  min-height: 38px;
  padding: 0 8px;
}

.popup__error {
  margin: 10px 0 0;
  color: #b42318;
  font-size: 12px;
}

.popup__footer {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 12px;
}

.popup__primary {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}

button:disabled {
  cursor: wait;
  opacity: 0.65;
}
```

- [ ] **Step 7: Delete unused onboarding content**

Delete:

```text
src/popup/onboarding.ts
```

- [ ] **Step 8: Run popup and full tests**

Run:

```bash
npx vitest run tests/popup.test.ts
npm test
```

Expected: collapsed, expanded, success, and failure tests pass.

- [ ] **Step 9: Commit**

```bash
git add src/popup/index.ts src/popup/styles.css tests/popup.test.ts
git rm src/popup/onboarding.ts
git commit -m "feat: simplify the rule editor popup"
```

## Task 3: Restore original text when rules are cleared

**Files**

- Modify: `src/content/index.ts`
- Modify: `tests/content-script.test.ts`

- [ ] **Step 1: Add a failing restoration assertion**

Extend the `DISABLE_PAGE` test:

```ts
await runtime.handleMessage({ type: 'DISABLE_PAGE' });

expect(document.getElementById('line')?.textContent).toBe('沈清辞又回来了。');
```

- [ ] **Step 2: Run the test and confirm RED**

Run:

```bash
npx vitest run tests/content-script.test.ts
```

Expected: FAIL because the page still contains the replacement target after disable.

- [ ] **Step 3: Restore before clearing runtime state**

Change `stop()`:

```ts
function stop(): void {
  disconnectObserver();
  engine.applyToDocument(doc, []);
  enabled = false;
  rules = [];
  observer = null;
}
```

- [ ] **Step 4: Run focused and full tests**

Run:

```bash
npx vitest run tests/content-script.test.ts
npm test
```

Expected: original text returns and dynamic-content behavior remains green.

- [ ] **Step 5: Commit**

```bash
git add src/content/index.ts tests/content-script.test.ts
git commit -m "fix: restore page text when disabling rules"
```

## Task 4: Synchronize Safari resources and run full regression

**Files**

- Modify: `apple/小说一键换名/Shared (Extension)/Resources/content.js`
- Modify: `apple/小说一键换名/Shared (Extension)/Resources/popup.js`
- Modify: `apple/小说一键换名/Shared (Extension)/Resources/styles.css`

- [ ] **Step 1: Build and synchronize**

Run:

```bash
npm run apple:sync
```

Expected: WebExtension outputs are rebuilt and copied into the Apple project.

- [ ] **Step 2: Verify collapsed markup in the bundle**

Run:

```bash
node -e "
const fs=require('fs');
const popup=fs.readFileSync('apple/小说一键换名/Shared (Extension)/Resources/popup.js','utf8');
if(!popup.includes('data-role=\\\"open-editor\\\"')) process.exit(1);
if(popup.includes('popup__status-title')) process.exit(1);
"
```

Expected: exit code `0`.

- [ ] **Step 3: Run complete Apple verification**

Run:

```bash
npm run apple:verify
```

Expected:

- all Vitest tests pass
- WebExtension build succeeds
- iOS Simulator build succeeds
- macOS build succeeds

- [ ] **Step 4: Check repository scope**

Run:

```bash
git diff --check
git status --short
```

Expected: only synchronized Apple extension resources are uncommitted.

- [ ] **Step 5: Commit generated Safari resources**

```bash
git add "apple/小说一键换名/Shared (Extension)/Resources"
git commit -m "build: sync simplified Safari extension"
```

- [ ] **Step 6: Push**

```bash
git push origin main
```

Expected: local `main` and `origin/main` point to the same commit.

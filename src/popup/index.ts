import { createBrowserApi } from '../platform/browserApi';
import type { BrowserApi, BrowserFamily } from '../platform/types';
import { createEmptyRule, normalizeRules } from '../shared/rules';
import type { ContentMessage, PageRuntimeState, ReplaceRule } from '../shared/types';

type RuleField = 'source' | 'target';

type PopupState = {
  rules: ReplaceRule[];
  tabId: number | null;
  editingRuleId: string | null;
  hasPendingChanges: boolean;
  errorMessage: string;
  isApplying: boolean;
};

type PopupApi = {
  addRule: () => void;
  removeRule: (id: string) => void;
  updateRule: (id: string, field: RuleField, value: string) => void;
  apply: () => Promise<ReplaceRule[]>;
};

const DEFAULT_PAGE_STATE: PageRuntimeState = {
  enabled: false,
  activeRuleCount: 0,
  permissionState: 'unknown'
};

const STORAGE_KEY = 'globalRules';

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function renderRuleSummary(rule: ReplaceRule): string {
  return `
    <div class="rule-summary" data-rule-row data-rule-summary data-id="${rule.id}">
      <span class="rule-summary__name" title="${escapeHtml(rule.source)}">${escapeHtml(rule.source)}</span>
      <span class="rule-summary__arrow" aria-hidden="true">→</span>
      <span class="rule-summary__name" title="${escapeHtml(rule.target)}">${escapeHtml(rule.target)}</span>
      <button class="rule-summary__edit" data-edit="${rule.id}" type="button"
        aria-label="编辑 ${escapeHtml(rule.source)} 到 ${escapeHtml(rule.target)}">编辑</button>
    </div>
  `;
}

function renderRuleEditor(rule: ReplaceRule): string {
  return `
    <div class="rule-row rule-editor" data-rule-row data-rule-editor-row data-id="${rule.id}">
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

function renderRuleList(state: PopupState): string {
  return state.rules
    .map((rule) =>
      state.editingRuleId === rule.id ? renderRuleEditor(rule) : renderRuleSummary(rule)
    )
    .join('');
}

function isUnavailableBrowserMethod(error: unknown): boolean {
  return error instanceof Error && error.message.includes('Browser API method is unavailable');
}

async function loadRules(browserApi: BrowserApi): Promise<ReplaceRule[]> {
  const result = await browserApi.storage.get(STORAGE_KEY);
  return normalizeRules((result[STORAGE_KEY] as ReplaceRule[] | undefined) ?? []);
}

async function saveRules(browserApi: BrowserApi, rules: ReplaceRule[]): Promise<ReplaceRule[]> {
  const normalized = normalizeRules(rules);
  await browserApi.storage.set({ [STORAGE_KEY]: normalized });
  return normalized;
}

async function resolveActiveContext(browserApi: BrowserApi): Promise<{
  tabId: number | null;
  browserFamily: BrowserFamily;
  pageState: PageRuntimeState;
}> {
  let browserFamily: BrowserFamily = browserApi.family === 'unknown' ? 'chromium' : browserApi.family;

  try {
    const [activeTab] = await browserApi.tabs.query({
      active: true,
      currentWindow: true
    });
    const tabId = typeof activeTab?.id === 'number' ? activeTab.id : null;

    if (tabId === null) {
      return {
        tabId,
        browserFamily,
        pageState: DEFAULT_PAGE_STATE
      };
    }

    try {
      const pageState = await browserApi.tabs.sendMessage<ContentMessage, PageRuntimeState>(tabId, {
        type: 'GET_PAGE_STATE'
      });

      return {
        tabId,
        browserFamily,
        pageState: {
          ...DEFAULT_PAGE_STATE,
          ...pageState
        }
      };
    } catch {
      return {
        tabId,
        browserFamily,
        pageState: {
          ...DEFAULT_PAGE_STATE,
          permissionState: 'needs-user-action'
        }
      };
    }
  } catch (error) {
    if (isUnavailableBrowserMethod(error)) {
      browserFamily = 'safari';
    }

    return {
      tabId: null,
      browserFamily,
      pageState: {
        ...DEFAULT_PAGE_STATE,
        permissionState: browserFamily === 'safari' ? 'unknown' : 'needs-user-action'
      }
    };
  }
}

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

export async function mountPopup(
  root: HTMLElement,
  browserApi: BrowserApi = createBrowserApi()
): Promise<PopupApi> {
  const [activeContext, storedRules] = await Promise.all([resolveActiveContext(browserApi), loadRules(browserApi)]);

  const state: PopupState = {
    rules: storedRules,
    tabId: activeContext.tabId,
    editingRuleId: null,
    hasPendingChanges: false,
    errorMessage: '',
    isApplying: false
  };

  function render(): void {
    if (state.rules.length === 0 && !state.hasPendingChanges) {
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

    root.innerHTML = `
      <section class="popup" data-rule-editor>
        <header class="popup__header">
          <strong>换名规则</strong>
          <span class="popup__count">${state.rules.length} 条</span>
        </header>
        <div class="popup__list">
          ${
            state.rules.length > 0
              ? renderRuleList(state)
              : '<p class="popup__empty" data-empty-pending>暂无规则</p>'
          }
        </div>
        ${
          state.errorMessage
            ? `<p class="popup__error" role="alert">${escapeHtml(state.errorMessage)}</p>`
            : ''
        }
        <footer class="popup__footer">
          <button data-role="add" type="button">添加规则</button>
          <button class="popup__primary" data-role="apply" type="button" ${
            state.isApplying ? 'disabled' : ''
          }>${state.isApplying ? '正在生效…' : '全部生效'}</button>
        </footer>
      </section>
    `;

    root.querySelector<HTMLButtonElement>('[data-role="add"]')?.addEventListener('click', () => {
      addRule();
    });

    root
      .querySelector<HTMLButtonElement>('[data-role="apply"]')
      ?.addEventListener('click', () => void apply());

    root.querySelectorAll<HTMLButtonElement>('[data-edit]').forEach((button) => {
      button.addEventListener('click', () => {
        state.editingRuleId = button.dataset.edit ?? null;
        render();
      });
    });

    root.querySelectorAll<HTMLInputElement>('[data-field]').forEach((input) => {
      input.addEventListener('input', (event) => {
        const target = event.currentTarget as HTMLInputElement;
        updateRule(target.dataset.id ?? '', target.dataset.field as RuleField, target.value);
      });
    });

    root.querySelectorAll<HTMLButtonElement>('[data-remove]').forEach((button) => {
      button.addEventListener('click', () => {
        removeRule(button.dataset.remove ?? '');
      });
    });
  }

  function addRule(): void {
    const rule = createEmptyRule();
    state.rules = [...state.rules, rule];
    state.editingRuleId = rule.id;
    state.hasPendingChanges = true;
    state.errorMessage = '';
    render();
    root
      .querySelector<HTMLInputElement>(`[data-field="source"][data-id="${rule.id}"]`)
      ?.focus();
  }

  function removeRule(id: string): void {
    state.rules = state.rules.filter((rule) => rule.id !== id);
    state.editingRuleId = null;
    state.hasPendingChanges = true;
    state.errorMessage = '';
    render();
  }

  function updateRule(id: string, field: RuleField, value: string): void {
    state.rules = state.rules.map((rule) => (rule.id === id ? { ...rule, [field]: value } : rule));
    state.hasPendingChanges = true;
  }

  async function persistRules(): Promise<ReplaceRule[]> {
    const normalized = await saveRules(browserApi, state.rules);
    state.rules = normalized;
    return normalized;
  }

  async function apply(): Promise<ReplaceRule[]> {
    state.isApplying = true;
    state.errorMessage = '';
    render();

    try {
      const normalized = await persistRules();

      if (normalized.length === 0) {
        await sendTabMessage(browserApi, state.tabId, { type: 'DISABLE_PAGE' });
      } else {
        await sendTabMessage(browserApi, state.tabId, {
          type: 'APPLY_RULES',
          rules: normalized
        });
      }

      state.isApplying = false;
      state.editingRuleId = null;
      state.hasPendingChanges = false;
      render();
      return normalized;
    } catch {
      state.isApplying = false;
      state.errorMessage = '请允许扩展访问当前网站后重试';
      render();
      return normalizeRules(state.rules);
    }
  }

  render();

  return {
    addRule,
    removeRule,
    updateRule,
    apply
  };
}

const app = document.getElementById('app');

if (app instanceof HTMLElement) {
  void mountPopup(app);
}

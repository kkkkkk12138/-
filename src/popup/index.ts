import { createBrowserApi } from '../platform/browserApi';
import type { BrowserApi, BrowserFamily } from '../platform/types';
import { createEmptyRule, normalizeRules } from '../shared/rules';
import type { ContentMessage, PageRuntimeState, PermissionState, ReplaceRule } from '../shared/types';
import { getOnboardingContent } from './onboarding';

type RuleField = 'source' | 'target';

type PopupState = {
  rules: ReplaceRule[];
  enabled: boolean;
  tabId: number | null;
  browserFamily: BrowserFamily;
  permissionState: PermissionState;
};

type PopupApi = {
  addRule: () => void;
  removeRule: (id: string) => void;
  updateRule: (id: string, field: RuleField, value: string) => void;
  apply: () => Promise<ReplaceRule[]>;
  setEnabled: (enabled: boolean) => Promise<void>;
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
  browserFamily: BrowserFamily,
  tabId: number | null,
  message: ContentMessage
): Promise<PageRuntimeState | void> {
  if (tabId === null || browserFamily === 'safari') {
    return DEFAULT_PAGE_STATE;
  }

  try {
    return await browserApi.tabs.sendMessage(tabId, message);
  } catch (error) {
    if (message.type === 'GET_PAGE_STATE') {
      return {
        ...DEFAULT_PAGE_STATE,
        permissionState: 'needs-user-action'
      };
    }

    throw error;
  }
}

export async function mountPopup(root: HTMLElement): Promise<PopupApi> {
  const browserApi = createBrowserApi();
  const [activeContext, storedRules] = await Promise.all([resolveActiveContext(browserApi), loadRules(browserApi)]);

  const state: PopupState = {
    rules: storedRules.length > 0 ? storedRules : [createEmptyRule()],
    enabled: activeContext.pageState.enabled,
    tabId: activeContext.tabId,
    browserFamily: activeContext.browserFamily,
    permissionState: activeContext.pageState.permissionState
  };

  function getNormalizedRules(): ReplaceRule[] {
    return normalizeRules(state.rules);
  }

  function render(): void {
    const activeRuleCount = getNormalizedRules().length;
    const onboarding = getOnboardingContent(state.browserFamily, state.permissionState);
    const disableActiveControls = state.browserFamily === 'safari' || state.permissionState === 'needs-user-action';

    root.innerHTML = `
      <section class="popup">
        <header class="popup__header">
          <div class="popup__header-copy">
            <h1 class="popup__title">小说一键换名</h1>
            <p class="popup__subtitle">${escapeHtml(onboarding.subhead)}</p>
          </div>
          <label class="popup__toggle">
            <input
              type="checkbox"
              data-role="enabled"
              ${state.enabled ? 'checked' : ''}
              ${disableActiveControls ? 'disabled' : ''}
            />
            <span>本页启用</span>
          </label>
        </header>

        <div class="popup__meta">
          <span class="popup__badge">有效规则 ${activeRuleCount}</span>
          <span class="popup__status-text">${state.enabled ? '当前页已启用' : '当前页未启用'}</span>
        </div>

        <section class="popup__status" data-role="status">
          <p class="popup__status-title">${escapeHtml(onboarding.title)}</p>
          <p class="popup__status-body">${escapeHtml(onboarding.detail)}</p>
          <p class="popup__status-hint">${escapeHtml(onboarding.hint)}</p>
        </section>

        <section class="popup__section">
          <div class="popup__section-title">换名规则</div>
          <p class="popup__section-desc">规则会保存在本地，仅在你手动对当前页面生效时应用。</p>
        </section>

        <div class="popup__list">
          ${state.rules
            .map(
              (rule, index) => `
                <div class="rule-row" data-rule-row data-id="${rule.id}">
                  <span class="rule-row__index">${index + 1}</span>
                  <input
                    class="rule-row__input"
                    data-field="source"
                    data-id="${rule.id}"
                    value="${escapeHtml(rule.source)}"
                    placeholder="原名"
                  />
                  <input
                    class="rule-row__input"
                    data-field="target"
                    data-id="${rule.id}"
                    value="${escapeHtml(rule.target)}"
                    placeholder="替换成"
                  />
                  <button class="rule-row__remove" data-remove="${rule.id}" type="button">删</button>
                </div>
              `
            )
            .join('')}
        </div>

        <footer class="popup__footer">
          <button data-role="add" type="button">新增一条</button>
          <button class="popup__primary" data-role="apply" type="button" ${
            disableActiveControls ? 'disabled' : ''
          }>立即生效</button>
        </footer>
      </section>
    `;

    root.querySelector<HTMLButtonElement>('[data-role="add"]')?.addEventListener('click', () => {
      addRule();
    });

    root
      .querySelector<HTMLButtonElement>('[data-role="apply"]')
      ?.addEventListener('click', () => void apply());

    root
      .querySelector<HTMLInputElement>('[data-role="enabled"]')
      ?.addEventListener('change', (event) => {
        const target = event.currentTarget as HTMLInputElement;
        void setEnabled(target.checked);
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
    state.rules = [...state.rules, createEmptyRule()];
    render();
  }

  function removeRule(id: string): void {
    state.rules = state.rules.filter((rule) => rule.id !== id);

    if (state.rules.length === 0) {
      state.rules = [createEmptyRule()];
    }

    render();
  }

  function updateRule(id: string, field: RuleField, value: string): void {
    state.rules = state.rules.map((rule) => (rule.id === id ? { ...rule, [field]: value } : rule));
  }

  async function persistRules(): Promise<ReplaceRule[]> {
    const normalized = await saveRules(browserApi, state.rules);
    state.rules = normalized.length > 0 ? normalized : [createEmptyRule()];
    return normalized;
  }

  async function apply(): Promise<ReplaceRule[]> {
    const normalized = await persistRules();

    if (state.browserFamily === 'safari' || state.permissionState === 'needs-user-action') {
      state.enabled = false;
      render();
      return normalized;
    }

    if (normalized.length === 0) {
      state.enabled = false;
      await sendTabMessage(browserApi, state.browserFamily, state.tabId, { type: 'DISABLE_PAGE' });
      render();
      return normalized;
    }

    try {
      await sendTabMessage(browserApi, state.browserFamily, state.tabId, {
        type: 'APPLY_RULES',
        rules: normalized
      });
      state.enabled = true;
    } catch {
      state.enabled = false;
    }

    render();
    return normalized;
  }

  async function setEnabled(enabled: boolean): Promise<void> {
    state.enabled = enabled;

    if (!enabled) {
      try {
        await sendTabMessage(browserApi, state.browserFamily, state.tabId, { type: 'DISABLE_PAGE' });
      } finally {
        render();
      }
      return;
    }

    if (state.browserFamily === 'safari' || state.permissionState === 'needs-user-action') {
      state.enabled = false;
      render();
      return;
    }

    const normalized = await persistRules();

    if (normalized.length === 0) {
      state.enabled = false;
      render();
      return;
    }

    try {
      await sendTabMessage(browserApi, state.browserFamily, state.tabId, {
        type: 'APPLY_RULES',
        rules: normalized
      });
      state.enabled = true;
    } catch {
      state.enabled = false;
    }

    render();
  }

  render();

  return {
    addRule,
    removeRule,
    updateRule,
    apply,
    setEnabled
  };
}

const app = document.getElementById('app');

if (app instanceof HTMLElement) {
  void mountPopup(app);
}

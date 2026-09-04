"use strict";
(() => {
  // src/platform/browserApi.ts
  function isThenable(value) {
    return (typeof value === "object" || typeof value === "function") && value !== null && "then" in value && typeof value.then === "function";
  }
  function createCallbackBridge(invoke) {
    return new Promise((resolve, reject) => {
      let settled = false;
      const callback = (value) => {
        settled = true;
        resolve(value);
      };
      try {
        const result = invoke(callback);
        if (isThenable(result)) {
          Promise.resolve(result).then(resolve, reject);
          return;
        }
        if (result !== void 0 && !settled) {
          resolve(result);
        }
      } catch (error) {
        reject(error);
      }
    });
  }
  function getUserAgent() {
    return globalThis.navigator?.userAgent ?? "";
  }
  function getVendor() {
    return globalThis.navigator?.vendor ?? "";
  }
  function isSafariUserAgent(userAgent, vendor) {
    return /Safari/i.test(userAgent) && !/(Chrome|Chromium|CriOS|Edg|OPR|OPiOS|DuckDuckGo|SamsungBrowser|UCBrowser|YaBrowser)/i.test(
      userAgent
    ) && /Apple/i.test(vendor);
  }
  function isChromiumUserAgent(userAgent) {
    return /(Chrome|Chromium|CriOS|Edg|OPR|OPiOS)/i.test(userAgent);
  }
  function getBrowserFamily() {
    const userAgent = getUserAgent();
    const vendor = getVendor();
    const hasBrowserNamespace = typeof globalThis.browser !== "undefined";
    const hasChromeNamespace = typeof globalThis.chrome !== "undefined";
    if (isSafariUserAgent(userAgent, vendor)) {
      return "safari";
    }
    if (isChromiumUserAgent(userAgent)) {
      return "chromium";
    }
    if (hasBrowserNamespace && !hasChromeNamespace) {
      return "safari";
    }
    if (hasChromeNamespace) {
      return "chromium";
    }
    return "unknown";
  }
  function resolveExtensionApi() {
    const family = getBrowserFamily();
    if (family === "safari" && typeof globalThis.browser !== "undefined") {
      return {
        api: globalThis.browser,
        namespace: "browser"
      };
    }
    if (typeof globalThis.chrome !== "undefined") {
      return {
        api: globalThis.chrome,
        namespace: "chrome"
      };
    }
    if (typeof globalThis.browser !== "undefined") {
      return {
        api: globalThis.browser,
        namespace: "browser"
      };
    }
    return null;
  }
  function createMissingMethodError(methodName) {
    return new Error(`Browser API method is unavailable: ${methodName}`);
  }
  function createBrowserApi() {
    const resolved = resolveExtensionApi();
    if (!resolved) {
      throw new Error("No browser extension API is available on globalThis.");
    }
    const { api, namespace } = resolved;
    return {
      family: getBrowserFamily(),
      namespace,
      storage: {
        get(keys) {
          const get = api.storage?.local?.get;
          if (!get) {
            return Promise.reject(createMissingMethodError("storage.local.get"));
          }
          return createCallbackBridge((callback) => get.call(api.storage?.local, keys, callback));
        },
        set(items) {
          const set = api.storage?.local?.set;
          if (!set) {
            return Promise.reject(createMissingMethodError("storage.local.set"));
          }
          return createCallbackBridge((callback) => set.call(api.storage?.local, items, callback));
        }
      },
      tabs: {
        query(queryInfo) {
          const query = api.tabs?.query;
          if (!query) {
            return Promise.reject(createMissingMethodError("tabs.query"));
          }
          return createCallbackBridge((callback) => query.call(api.tabs, queryInfo, callback));
        },
        sendMessage(tabId, message) {
          const sendMessage = api.tabs?.sendMessage;
          if (!sendMessage) {
            return Promise.reject(createMissingMethodError("tabs.sendMessage"));
          }
          return createCallbackBridge(
            (callback) => sendMessage.call(api.tabs, tabId, message, callback)
          );
        }
      },
      runtime: {
        addMessageListener(listener) {
          const addListener = api.runtime?.onMessage?.addListener;
          if (!addListener) {
            throw createMissingMethodError("runtime.onMessage.addListener");
          }
          addListener.call(api.runtime?.onMessage, listener);
        }
      }
    };
  }

  // src/shared/rules.ts
  function createEmptyRule() {
    return {
      id: crypto.randomUUID(),
      source: "",
      target: ""
    };
  }
  function normalizeRules(input) {
    const seen = /* @__PURE__ */ new Set();
    return input.map((rule) => ({
      ...rule,
      source: rule.source.trim(),
      target: rule.target.trim()
    })).filter((rule) => rule.source && rule.target && rule.source !== rule.target).filter((rule) => {
      const key = `${rule.source}::${rule.target}`;
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    }).sort((a, b) => b.source.length - a.source.length);
  }

  // src/popup/onboarding.ts
  function getOnboardingContent(browserFamily, permissionState) {
    if (browserFamily === "safari") {
      return {
        subhead: "Safari \u9996\u53D1\uFF0C\u6309\u5F53\u524D\u7F51\u7AD9\u9010\u9875\u5F00\u542F",
        title: "\u8BF7\u5148\u5728 Safari \u4E2D\u4E3A\u5F53\u524D\u7F51\u7AD9\u5F00\u542F\u6269\u5C55\u6743\u9650",
        detail: "Safari \u66F4\u5F3A\u8C03\u6309\u7F51\u7AD9\u6388\u6743\u3002\u5148\u5728\u5F53\u524D\u9605\u8BFB\u9875\u5F00\u542F\u672C\u6269\u5C55\uFF0C\u518D\u56DE\u5230\u5F39\u7A97\u7EE7\u7EED\u5E94\u7528\u6362\u540D\u89C4\u5219\u3002",
        hint: "iPhone / iPad \u901A\u5E38\u5728\u5730\u5740\u680F\u7684\u62FC\u56FE\u83DC\u5355\u5F00\u542F\uFF0CMac \u53EF\u5728 Safari \u7684\u7F51\u7AD9\u8BBE\u7F6E\u91CC\u5141\u8BB8\u6B64\u6269\u5C55\u3002"
      };
    }
    if (permissionState === "needs-user-action") {
      return {
        subhead: "\u5148\u786E\u8BA4\u6743\u9650\uFF0C\u518D\u5BF9\u5F53\u524D\u9875\u751F\u6548",
        title: "\u5F53\u524D\u7F51\u7AD9\u8FD8\u6CA1\u6709\u6388\u6743",
        detail: "\u8BF7\u5148\u5141\u8BB8\u6269\u5C55\u8BBF\u95EE\u8FD9\u4E2A\u7F51\u7AD9\u3002\u6388\u6743\u540E\u518D\u6B21\u6253\u5F00\u5F39\u7A97\uFF0C\u5C31\u80FD\u628A\u89C4\u5219\u5E94\u7528\u5230\u5F53\u524D\u9875\u9762\u3002",
        hint: "\u5982\u679C\u4F60\u521A\u4FEE\u6539\u8FC7\u6D4F\u89C8\u5668\u6743\u9650\uFF0C\u5237\u65B0\u9875\u9762\u540E\u518D\u70B9\u4E00\u6B21\u201C\u7ACB\u5373\u751F\u6548\u201D\u4F1A\u66F4\u7A33\u3002"
      };
    }
    if (permissionState === "unknown") {
      return {
        subhead: "\u9762\u5411\u79FB\u52A8\u7AEF\u5165\u53E3\u7684\u5F53\u524D\u9875\u64CD\u4F5C",
        title: "\u5148\u6253\u5F00\u8981\u9605\u8BFB\u7684\u7F51\u9875\uFF0C\u518D\u5728\u8FD9\u91CC\u5904\u7406\u6362\u540D",
        detail: "\u5F39\u7A97\u53EA\u4F1A\u4F5C\u7528\u4E8E\u5F53\u524D\u6807\u7B7E\u9875\uFF0C\u89C4\u5219\u4ECD\u4F1A\u4FDD\u5B58\u5230\u672C\u5730\uFF0C\u65B9\u4FBF\u4F60\u5728\u4E0B\u4E00\u6B21\u9605\u8BFB\u65F6\u7EE7\u7EED\u4F7F\u7528\u3002",
        hint: "\u5982\u679C\u6CA1\u6709\u68C0\u6D4B\u5230\u9875\u9762\u72B6\u6001\uFF0C\u901A\u5E38\u662F\u5F53\u524D\u9875\u8FD8\u6CA1\u51C6\u5907\u597D\uFF0C\u91CD\u65B0\u6253\u5F00\u76EE\u6807\u7F51\u9875\u5373\u53EF\u3002"
      };
    }
    return {
      subhead: "\u5F53\u524D\u9875\u89E6\u53D1\uFF0C\u89C4\u5219\u53EA\u5728\u672C\u5730\u4FDD\u5B58",
      title: "\u5F53\u524D\u9875\u5DF2\u51C6\u5907\u597D\u5E94\u7528\u89C4\u5219",
      detail: "\u4FDD\u5B58\u540E\u7684\u89C4\u5219\u4F1A\u7ACB\u5373\u540C\u6B65\u5230\u5F53\u524D\u6807\u7B7E\u9875\uFF0C\u66F4\u7B26\u5408\u79FB\u52A8\u7AEF\u548C\u6309\u7AD9\u70B9\u6388\u6743\u7684\u4F7F\u7528\u5FC3\u667A\u3002",
      hint: "\u5EFA\u8BAE\u4E00\u9875\u4E00\u9875\u786E\u8BA4\u6548\u679C\uFF1B\u5173\u95ED\u201C\u672C\u9875\u542F\u7528\u201D\u540E\uFF0C\u53EA\u4F1A\u505C\u7528\u5F53\u524D\u6807\u7B7E\u9875\uFF0C\u4E0D\u4F1A\u5220\u9664\u5DF2\u4FDD\u5B58\u89C4\u5219\u3002"
    };
  }

  // src/popup/index.ts
  var DEFAULT_PAGE_STATE = {
    enabled: false,
    activeRuleCount: 0,
    permissionState: "unknown"
  };
  var STORAGE_KEY = "globalRules";
  function escapeHtml(value) {
    return value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;");
  }
  function isUnavailableBrowserMethod(error) {
    return error instanceof Error && error.message.includes("Browser API method is unavailable");
  }
  async function loadRules(browserApi) {
    const result = await browserApi.storage.get(STORAGE_KEY);
    return normalizeRules(result[STORAGE_KEY] ?? []);
  }
  async function saveRules(browserApi, rules) {
    const normalized = normalizeRules(rules);
    await browserApi.storage.set({ [STORAGE_KEY]: normalized });
    return normalized;
  }
  async function resolveActiveContext(browserApi) {
    let browserFamily = browserApi.family === "unknown" ? "chromium" : browserApi.family;
    try {
      const [activeTab] = await browserApi.tabs.query({
        active: true,
        currentWindow: true
      });
      const tabId = typeof activeTab?.id === "number" ? activeTab.id : null;
      if (tabId === null) {
        return {
          tabId,
          browserFamily,
          pageState: DEFAULT_PAGE_STATE
        };
      }
      try {
        const pageState = await browserApi.tabs.sendMessage(tabId, {
          type: "GET_PAGE_STATE"
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
            permissionState: "needs-user-action"
          }
        };
      }
    } catch (error) {
      if (isUnavailableBrowserMethod(error)) {
        browserFamily = "safari";
      }
      return {
        tabId: null,
        browserFamily,
        pageState: {
          ...DEFAULT_PAGE_STATE,
          permissionState: browserFamily === "safari" ? "unknown" : "needs-user-action"
        }
      };
    }
  }
  async function sendTabMessage(browserApi, browserFamily, tabId, message) {
    if (tabId === null || browserFamily === "safari") {
      return DEFAULT_PAGE_STATE;
    }
    try {
      return await browserApi.tabs.sendMessage(tabId, message);
    } catch (error) {
      if (message.type === "GET_PAGE_STATE") {
        return {
          ...DEFAULT_PAGE_STATE,
          permissionState: "needs-user-action"
        };
      }
      throw error;
    }
  }
  async function mountPopup(root) {
    const browserApi = createBrowserApi();
    const [activeContext, storedRules] = await Promise.all([resolveActiveContext(browserApi), loadRules(browserApi)]);
    const state = {
      rules: storedRules.length > 0 ? storedRules : [createEmptyRule()],
      enabled: activeContext.pageState.enabled,
      tabId: activeContext.tabId,
      browserFamily: activeContext.browserFamily,
      permissionState: activeContext.pageState.permissionState
    };
    function getNormalizedRules() {
      return normalizeRules(state.rules);
    }
    function render() {
      const activeRuleCount = getNormalizedRules().length;
      const onboarding = getOnboardingContent(state.browserFamily, state.permissionState);
      const disableActiveControls = state.browserFamily === "safari" || state.permissionState === "needs-user-action";
      root.innerHTML = `
      <section class="popup">
        <header class="popup__header">
          <div class="popup__header-copy">
            <h1 class="popup__title">\u5C0F\u8BF4\u4E00\u952E\u6362\u540D</h1>
            <p class="popup__subtitle">${escapeHtml(onboarding.subhead)}</p>
          </div>
          <label class="popup__toggle">
            <input
              type="checkbox"
              data-role="enabled"
              ${state.enabled ? "checked" : ""}
              ${disableActiveControls ? "disabled" : ""}
            />
            <span>\u672C\u9875\u542F\u7528</span>
          </label>
        </header>

        <div class="popup__meta">
          <span class="popup__badge">\u6709\u6548\u89C4\u5219 ${activeRuleCount}</span>
          <span class="popup__status-text">${state.enabled ? "\u5F53\u524D\u9875\u5DF2\u542F\u7528" : "\u5F53\u524D\u9875\u672A\u542F\u7528"}</span>
        </div>

        <section class="popup__status" data-role="status">
          <p class="popup__status-title">${escapeHtml(onboarding.title)}</p>
          <p class="popup__status-body">${escapeHtml(onboarding.detail)}</p>
          <p class="popup__status-hint">${escapeHtml(onboarding.hint)}</p>
        </section>

        <section class="popup__section">
          <div class="popup__section-title">\u6362\u540D\u89C4\u5219</div>
          <p class="popup__section-desc">\u89C4\u5219\u4F1A\u4FDD\u5B58\u5728\u672C\u5730\uFF0C\u4EC5\u5728\u4F60\u624B\u52A8\u5BF9\u5F53\u524D\u9875\u9762\u751F\u6548\u65F6\u5E94\u7528\u3002</p>
        </section>

        <div class="popup__list">
          ${state.rules.map(
        (rule, index) => `
                <div class="rule-row" data-rule-row data-id="${rule.id}">
                  <span class="rule-row__index">${index + 1}</span>
                  <input
                    class="rule-row__input"
                    data-field="source"
                    data-id="${rule.id}"
                    value="${escapeHtml(rule.source)}"
                    placeholder="\u539F\u540D"
                  />
                  <input
                    class="rule-row__input"
                    data-field="target"
                    data-id="${rule.id}"
                    value="${escapeHtml(rule.target)}"
                    placeholder="\u66FF\u6362\u6210"
                  />
                  <button class="rule-row__remove" data-remove="${rule.id}" type="button">\u5220</button>
                </div>
              `
      ).join("")}
        </div>

        <footer class="popup__footer">
          <button data-role="add" type="button">\u65B0\u589E\u4E00\u6761</button>
          <button class="popup__primary" data-role="apply" type="button" ${disableActiveControls ? "disabled" : ""}>\u7ACB\u5373\u751F\u6548</button>
        </footer>
      </section>
    `;
      root.querySelector('[data-role="add"]')?.addEventListener("click", () => {
        addRule();
      });
      root.querySelector('[data-role="apply"]')?.addEventListener("click", () => void apply());
      root.querySelector('[data-role="enabled"]')?.addEventListener("change", (event) => {
        const target = event.currentTarget;
        void setEnabled(target.checked);
      });
      root.querySelectorAll("[data-field]").forEach((input) => {
        input.addEventListener("input", (event) => {
          const target = event.currentTarget;
          updateRule(target.dataset.id ?? "", target.dataset.field, target.value);
        });
      });
      root.querySelectorAll("[data-remove]").forEach((button) => {
        button.addEventListener("click", () => {
          removeRule(button.dataset.remove ?? "");
        });
      });
    }
    function addRule() {
      state.rules = [...state.rules, createEmptyRule()];
      render();
    }
    function removeRule(id) {
      state.rules = state.rules.filter((rule) => rule.id !== id);
      if (state.rules.length === 0) {
        state.rules = [createEmptyRule()];
      }
      render();
    }
    function updateRule(id, field, value) {
      state.rules = state.rules.map((rule) => rule.id === id ? { ...rule, [field]: value } : rule);
    }
    async function persistRules() {
      const normalized = await saveRules(browserApi, state.rules);
      state.rules = normalized.length > 0 ? normalized : [createEmptyRule()];
      return normalized;
    }
    async function apply() {
      const normalized = await persistRules();
      if (state.browserFamily === "safari" || state.permissionState === "needs-user-action") {
        state.enabled = false;
        render();
        return normalized;
      }
      if (normalized.length === 0) {
        state.enabled = false;
        await sendTabMessage(browserApi, state.browserFamily, state.tabId, { type: "DISABLE_PAGE" });
        render();
        return normalized;
      }
      try {
        await sendTabMessage(browserApi, state.browserFamily, state.tabId, {
          type: "APPLY_RULES",
          rules: normalized
        });
        state.enabled = true;
      } catch {
        state.enabled = false;
      }
      render();
      return normalized;
    }
    async function setEnabled(enabled) {
      state.enabled = enabled;
      if (!enabled) {
        try {
          await sendTabMessage(browserApi, state.browserFamily, state.tabId, { type: "DISABLE_PAGE" });
        } finally {
          render();
        }
        return;
      }
      if (state.browserFamily === "safari" || state.permissionState === "needs-user-action") {
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
          type: "APPLY_RULES",
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
  var app = document.getElementById("app");
  if (app instanceof HTMLElement) {
    void mountPopup(app);
  }
})();

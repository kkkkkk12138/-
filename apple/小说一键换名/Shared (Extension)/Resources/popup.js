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
  function renderRuleRow(rule) {
    return `
    <div class="rule-row" data-rule-row data-id="${rule.id}">
      <input class="rule-row__input" data-field="source" data-id="${rule.id}"
        value="${escapeHtml(rule.source)}" placeholder="\u539F\u540D" />
      <span class="rule-row__arrow" aria-hidden="true">\u2192</span>
      <input class="rule-row__input" data-field="target" data-id="${rule.id}"
        value="${escapeHtml(rule.target)}" placeholder="\u66FF\u6362\u6210" />
      <button class="rule-row__remove" data-remove="${rule.id}" type="button"
        aria-label="\u5220\u9664\u89C4\u5219">\u5220\u9664</button>
    </div>
  `;
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
  async function sendTabMessage(browserApi, tabId, message) {
    if (tabId === null) {
      throw new Error("\u5F53\u524D\u9875\u9762\u4E0D\u53EF\u7528");
    }
    return browserApi.tabs.sendMessage(tabId, message);
  }
  async function mountPopup(root, browserApi = createBrowserApi()) {
    const [activeContext, storedRules] = await Promise.all([resolveActiveContext(browserApi), loadRules(browserApi)]);
    const state = {
      rules: storedRules,
      tabId: activeContext.tabId,
      expanded: false,
      errorMessage: "",
      isApplying: false
    };
    function render() {
      if (!state.expanded) {
        root.innerHTML = `
        <section class="popup popup--collapsed">
          <button class="popup__open" data-role="open-editor" type="button">
            <span aria-hidden="true">\uFF0B</span>
            \u6DFB\u52A0\u89C4\u5219
          </button>
        </section>
      `;
        root.querySelector('[data-role="open-editor"]')?.addEventListener("click", addRule);
        return;
      }
      root.innerHTML = `
      <section class="popup" data-rule-editor>
        <div class="popup__list">
          ${state.rules.map(renderRuleRow).join("")}
        </div>
        ${state.errorMessage ? `<p class="popup__error" role="alert">${escapeHtml(state.errorMessage)}</p>` : ""}
        <footer class="popup__footer">
          <button data-role="add" type="button">\u518D\u52A0\u4E00\u6761</button>
          <button class="popup__primary" data-role="apply" type="button" ${state.isApplying ? "disabled" : ""}>${state.isApplying ? "\u6B63\u5728\u751F\u6548\u2026" : "\u7ACB\u5373\u751F\u6548"}</button>
        </footer>
      </section>
    `;
      root.querySelector('[data-role="add"]')?.addEventListener("click", () => {
        addRule();
      });
      root.querySelector('[data-role="apply"]')?.addEventListener("click", () => void apply());
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
      state.expanded = true;
      state.errorMessage = "";
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
      state.rules = normalized;
      return normalized;
    }
    async function apply() {
      state.isApplying = true;
      state.errorMessage = "";
      render();
      try {
        const normalized = await persistRules();
        if (normalized.length === 0) {
          await sendTabMessage(browserApi, state.tabId, { type: "DISABLE_PAGE" });
        } else {
          await sendTabMessage(browserApi, state.tabId, {
            type: "APPLY_RULES",
            rules: normalized
          });
        }
        state.isApplying = false;
        state.expanded = false;
        render();
        return normalized;
      } catch {
        state.isApplying = false;
        state.expanded = true;
        state.errorMessage = "\u8BF7\u5141\u8BB8\u6269\u5C55\u8BBF\u95EE\u5F53\u524D\u7F51\u7AD9\u540E\u91CD\u8BD5";
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
  var app = document.getElementById("app");
  if (app instanceof HTMLElement) {
    void mountPopup(app);
  }
})();

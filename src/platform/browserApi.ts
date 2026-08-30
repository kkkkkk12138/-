import type {
  BrowserApi,
  BrowserFamily,
  BrowserRuntimeMessageListener,
  BrowserStorageItems,
  BrowserStorageKeys,
  BrowserTab,
  BrowserTabsQueryInfo,
  ExtensionApiLike
} from './types';

function isThenable<T>(value: unknown): value is PromiseLike<T> {
  return (
    (typeof value === 'object' || typeof value === 'function') &&
    value !== null &&
    'then' in value &&
    typeof (value as PromiseLike<T>).then === 'function'
  );
}

function createCallbackBridge<T>(
  invoke: (callback: (value: T) => void) => unknown
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    let settled = false;

    const callback = (value: T) => {
      settled = true;
      resolve(value);
    };

    try {
      const result = invoke(callback);

      if (isThenable<T>(result)) {
        Promise.resolve(result).then(resolve, reject);
        return;
      }

      if (result !== undefined && !settled) {
        resolve(result as T);
      }
    } catch (error) {
      reject(error);
    }
  });
}

function getUserAgent(): string {
  return globalThis.navigator?.userAgent ?? '';
}

function getVendor(): string {
  return globalThis.navigator?.vendor ?? '';
}

function isSafariUserAgent(userAgent: string, vendor: string): boolean {
  return (
    /Safari/i.test(userAgent) &&
    !/(Chrome|Chromium|CriOS|Edg|OPR|OPiOS|DuckDuckGo|SamsungBrowser|UCBrowser|YaBrowser)/i.test(
      userAgent
    ) &&
    /Apple/i.test(vendor)
  );
}

function isChromiumUserAgent(userAgent: string): boolean {
  return /(Chrome|Chromium|CriOS|Edg|OPR|OPiOS)/i.test(userAgent);
}

export function getBrowserFamily(): BrowserFamily {
  const userAgent = getUserAgent();
  const vendor = getVendor();
  const hasBrowserNamespace = typeof globalThis.browser !== 'undefined';
  const hasChromeNamespace = typeof globalThis.chrome !== 'undefined';

  if (isSafariUserAgent(userAgent, vendor)) {
    return 'safari';
  }

  if (isChromiumUserAgent(userAgent)) {
    return 'chromium';
  }

  if (hasBrowserNamespace && !hasChromeNamespace) {
    return 'safari';
  }

  if (hasChromeNamespace) {
    return 'chromium';
  }

  return 'unknown';
}

function resolveExtensionApi():
  | { api: ExtensionApiLike; namespace: 'browser' | 'chrome' }
  | null {
  const family = getBrowserFamily();

  if (family === 'safari' && typeof globalThis.browser !== 'undefined') {
    return {
      api: globalThis.browser as ExtensionApiLike,
      namespace: 'browser'
    };
  }

  if (typeof globalThis.chrome !== 'undefined') {
    return {
      api: globalThis.chrome as ExtensionApiLike,
      namespace: 'chrome'
    };
  }

  if (typeof globalThis.browser !== 'undefined') {
    return {
      api: globalThis.browser as ExtensionApiLike,
      namespace: 'browser'
    };
  }

  return null;
}

function createMissingMethodError(methodName: string): Error {
  return new Error(`Browser API method is unavailable: ${methodName}`);
}

export function createBrowserApi(): BrowserApi {
  const resolved = resolveExtensionApi();

  if (!resolved) {
    throw new Error('No browser extension API is available on globalThis.');
  }

  const { api, namespace } = resolved;

  return {
    family: getBrowserFamily(),
    namespace,
    storage: {
      get(keys?: BrowserStorageKeys): Promise<BrowserStorageItems> {
        const get = api.storage?.local?.get;

        if (!get) {
          return Promise.reject(createMissingMethodError('storage.local.get'));
        }

        return createCallbackBridge<BrowserStorageItems>((callback) => get.call(api.storage?.local, keys, callback));
      },
      set(items: BrowserStorageItems): Promise<void> {
        const set = api.storage?.local?.set;

        if (!set) {
          return Promise.reject(createMissingMethodError('storage.local.set'));
        }

        return createCallbackBridge<void>((callback) => set.call(api.storage?.local, items, callback));
      }
    },
    tabs: {
      query(queryInfo: BrowserTabsQueryInfo): Promise<BrowserTab[]> {
        const query = api.tabs?.query;

        if (!query) {
          return Promise.reject(createMissingMethodError('tabs.query'));
        }

        return createCallbackBridge<BrowserTab[]>((callback) => query.call(api.tabs, queryInfo, callback));
      },
      sendMessage<TMessage = unknown, TResult = unknown>(
        tabId: number,
        message: TMessage
      ): Promise<TResult> {
        const sendMessage = api.tabs?.sendMessage;

        if (!sendMessage) {
          return Promise.reject(createMissingMethodError('tabs.sendMessage'));
        }

        return createCallbackBridge<TResult>((callback) =>
          sendMessage.call(api.tabs, tabId, message, callback)
        );
      }
    },
    runtime: {
      addMessageListener(listener: BrowserRuntimeMessageListener): void {
        const addListener = api.runtime?.onMessage?.addListener;

        if (!addListener) {
          throw createMissingMethodError('runtime.onMessage.addListener');
        }

        addListener.call(api.runtime?.onMessage, listener);
      }
    }
  };
}

export type BrowserFamily = 'safari' | 'chromium' | 'unknown';

export type BrowserStorageItems = Record<string, unknown>;

export type BrowserStorageKeys = string | string[] | undefined;

export type BrowserTab = {
  id?: number;
};

export type BrowserTabsQueryInfo = {
  active?: boolean;
  currentWindow?: boolean;
};

export type BrowserRuntimeMessageListener = (
  message: unknown,
  sender: unknown,
  sendResponse: (response?: unknown) => void
) => unknown;

export type BrowserStorageAreaLike = {
  get?: (
    keys?: BrowserStorageKeys,
    callback?: (items: BrowserStorageItems) => void
  ) => Promise<BrowserStorageItems> | BrowserStorageItems | void;
  set?: (
    items: BrowserStorageItems,
    callback?: () => void
  ) => Promise<void> | void;
};

export type BrowserTabsApiLike = {
  query?: (
    queryInfo: BrowserTabsQueryInfo,
    callback?: (tabs: BrowserTab[]) => void
  ) => Promise<BrowserTab[]> | BrowserTab[] | void;
  sendMessage?: <TMessage = unknown, TResult = unknown>(
    tabId: number,
    message: TMessage,
    callback?: (response: TResult) => void
  ) => Promise<TResult> | TResult | void;
};

export type BrowserRuntimeApiLike = {
  onMessage?: {
    addListener?: (listener: BrowserRuntimeMessageListener) => void;
  };
};

export type ExtensionApiLike = {
  storage?: {
    local?: BrowserStorageAreaLike;
  };
  tabs?: BrowserTabsApiLike;
  runtime?: BrowserRuntimeApiLike;
};

export type BrowserApi = {
  family: BrowserFamily;
  namespace: 'browser' | 'chrome';
  storage: {
    get: (keys?: BrowserStorageKeys) => Promise<BrowserStorageItems>;
    set: (items: BrowserStorageItems) => Promise<void>;
  };
  tabs: {
    query: (queryInfo: BrowserTabsQueryInfo) => Promise<BrowserTab[]>;
    sendMessage: <TMessage = unknown, TResult = unknown>(
      tabId: number,
      message: TMessage
    ) => Promise<TResult>;
  };
  runtime: {
    addMessageListener: (listener: BrowserRuntimeMessageListener) => void;
  };
};

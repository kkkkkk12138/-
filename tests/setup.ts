import { beforeEach, vi } from 'vitest';

const storageState: Record<string, unknown> = {};

declare global {
  // eslint-disable-next-line no-var
  var browser: typeof chrome;
}

function defineNavigatorProperty<K extends 'userAgent' | 'vendor'>(key: K, value: string): void {
  Object.defineProperty(window.navigator, key, {
    configurable: true,
    value
  });
}

function createExtensionNamespace() {
  return {
    runtime: {
      onMessage: {
        addListener: vi.fn()
      }
    },
    storage: {
      local: {
        async get(keys?: string | string[]) {
          if (!keys) {
            return storageState;
          }

          if (typeof keys === 'string') {
            return { [keys]: storageState[keys] };
          }

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
}

vi.stubGlobal('crypto', {
  randomUUID: () => 'test-uuid'
});

vi.stubGlobal('chrome', createExtensionNamespace());
vi.stubGlobal('browser', createExtensionNamespace());

beforeEach(() => {
  for (const key of Object.keys(storageState)) {
    delete storageState[key];
  }

  vi.clearAllMocks();

  globalThis.chrome = createExtensionNamespace();
  globalThis.browser = createExtensionNamespace();

  defineNavigatorProperty(
    'userAgent',
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36'
  );
  defineNavigatorProperty('vendor', 'Google Inc.');
});

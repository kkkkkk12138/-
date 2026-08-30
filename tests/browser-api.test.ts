import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createBrowserApi, getBrowserFamily } from '../src/platform/browserApi';

function mockNavigator(userAgent: string, vendor: string): void {
  Object.defineProperty(window.navigator, 'userAgent', {
    configurable: true,
    value: userAgent
  });

  Object.defineProperty(window.navigator, 'vendor', {
    configurable: true,
    value: vendor
  });
}

describe('browser api', () => {
  beforeEach(() => {
    mockNavigator(
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36',
      'Google Inc.'
    );
  });

  it('能区分 Safari 与 Chromium', () => {
    expect(getBrowserFamily()).toBe('chromium');

    mockNavigator(
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15',
      'Apple Computer, Inc.'
    );

    expect(getBrowserFamily()).toBe('safari');
  });

  it('在 Safari 环境优先使用 browser namespace', async () => {
    mockNavigator(
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15',
      'Apple Computer, Inc.'
    );

    const browserGet = vi.fn().mockResolvedValue({ globalRules: [{ id: '1' }] });
    const browserSet = vi.fn().mockResolvedValue(undefined);
    const browserQuery = vi.fn().mockResolvedValue([{ id: 9 }]);
    const browserSendMessage = vi.fn().mockResolvedValue({ ok: true });
    const browserAddListener = vi.fn();

    globalThis.browser = {
      storage: {
        local: {
          get: browserGet,
          set: browserSet
        }
      },
      tabs: {
        query: browserQuery,
        sendMessage: browserSendMessage
      },
      runtime: {
        onMessage: {
          addListener: browserAddListener
        }
      }
    } as unknown as typeof chrome;

    globalThis.chrome = {
      storage: {
        local: {
          get: vi.fn(),
          set: vi.fn()
        }
      },
      tabs: {
        query: vi.fn(),
        sendMessage: vi.fn()
      },
      runtime: {
        onMessage: {
          addListener: vi.fn()
        }
      }
    } as unknown as typeof chrome;

    const api = createBrowserApi();
    const listener = vi.fn();

    await expect(api.storage.get('globalRules')).resolves.toEqual({ globalRules: [{ id: '1' }] });
    await expect(api.tabs.query({ active: true, currentWindow: true })).resolves.toEqual([{ id: 9 }]);
    await expect(api.tabs.sendMessage(9, { type: 'PING' })).resolves.toEqual({ ok: true });
    api.runtime.addMessageListener(listener);
    await expect(api.storage.set({ globalRules: [] })).resolves.toBeUndefined();

    expect(api.family).toBe('safari');
    expect(api.namespace).toBe('browser');
    expect(browserGet).toHaveBeenCalledWith('globalRules', expect.any(Function));
    expect(browserSet).toHaveBeenCalledWith({ globalRules: [] }, expect.any(Function));
    expect(browserQuery).toHaveBeenCalledWith({ active: true, currentWindow: true }, expect.any(Function));
    expect(browserSendMessage).toHaveBeenCalledWith(9, { type: 'PING' }, expect.any(Function));
    expect(browserAddListener).toHaveBeenCalledWith(listener);
  });

  it('在 Chromium 环境兼容 callback 风格的 chrome API', async () => {
    const chromeGet = vi.fn((keys: string | string[] | undefined, callback?: (items: Record<string, unknown>) => void) => {
      callback?.({ [String(keys)]: 'value-from-chrome' });
    });
    const chromeSet = vi.fn((_items: Record<string, unknown>, callback?: () => void) => {
      callback?.();
    });
    const chromeQuery = vi.fn(
      (_queryInfo: { active?: boolean; currentWindow?: boolean }, callback?: (tabs: Array<{ id?: number }>) => void) => {
        callback?.([{ id: 3 }]);
      }
    );
    const chromeSendMessage = vi.fn(
      (_tabId: number, _message: unknown, callback?: (response: unknown) => void) => {
        callback?.({ accepted: true });
      }
    );

    globalThis.chrome = {
      storage: {
        local: {
          get: chromeGet,
          set: chromeSet
        }
      },
      tabs: {
        query: chromeQuery,
        sendMessage: chromeSendMessage
      },
      runtime: {
        onMessage: {
          addListener: vi.fn()
        }
      }
    } as unknown as typeof chrome;

    globalThis.browser = undefined as unknown as typeof chrome;

    const api = createBrowserApi();

    await expect(api.storage.get('globalRules')).resolves.toEqual({
      globalRules: 'value-from-chrome'
    });
    await expect(api.tabs.query({ active: true, currentWindow: true })).resolves.toEqual([{ id: 3 }]);
    await expect(api.tabs.sendMessage(3, { type: 'PING' })).resolves.toEqual({ accepted: true });
    await expect(api.storage.set({ globalRules: [] })).resolves.toBeUndefined();

    expect(api.family).toBe('chromium');
    expect(api.namespace).toBe('chrome');
  });
});

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mountPopup } from '../src/popup/index';

describe('popup', () => {
  beforeEach(() => {
    document.body.innerHTML = '<div id="app"></div>';

    vi.mocked(chrome.tabs.query).mockResolvedValue([{ id: 7 }] as chrome.tabs.Tab[]);
    vi.mocked(chrome.tabs.sendMessage).mockImplementation(async (_tabId, message) => {
      if ((message as { type: string }).type === 'GET_PAGE_STATE') {
        return {
          enabled: false,
          activeRuleCount: 0,
          permissionState: 'granted'
        };
      }

      return undefined;
    });
  });

  it('渲染已保存规则并支持新增一行', async () => {
    await chrome.storage.local.set({
      globalRules: [{ id: '1', source: '沈清辞', target: '林惊鹤' }]
    });

    const popup = await mountPopup(document.getElementById('app')!);
    popup.addRule();

    expect(document.querySelectorAll('[data-rule-row]')).toHaveLength(2);
    expect((document.querySelector('[data-id="1"][data-field="source"]') as HTMLInputElement).value).toBe(
      '沈清辞'
    );
  });

  it('保存归一化规则并向活动标签页发送 APPLY_RULES', async () => {
    const popup = await mountPopup(document.getElementById('app')!);

    popup.updateRule('test-uuid', 'source', ' 沈清辞 ');
    popup.updateRule('test-uuid', 'target', ' 林惊鹤 ');

    await popup.apply();

    expect(chrome.tabs.sendMessage).toHaveBeenLastCalledWith(7, {
      type: 'APPLY_RULES',
      rules: [{ id: 'test-uuid', source: '沈清辞', target: '林惊鹤' }]
    }, expect.any(Function));

    await expect(chrome.storage.local.get('globalRules')).resolves.toEqual({
      globalRules: [{ id: 'test-uuid', source: '沈清辞', target: '林惊鹤' }]
    });
  });

  it('关闭本页启用开关时发送 DISABLE_PAGE', async () => {
    vi.mocked(chrome.tabs.sendMessage).mockImplementation(async (_tabId, message) => {
      if ((message as { type: string }).type === 'GET_PAGE_STATE') {
        return {
          enabled: true,
          activeRuleCount: 1,
          permissionState: 'granted'
        };
      }

      return undefined;
    });

    await mountPopup(document.getElementById('app')!);

    const toggle = document.querySelector('[data-role="enabled"]') as HTMLInputElement;
    toggle.checked = false;
    toggle.dispatchEvent(new Event('change', { bubbles: true }));

    await Promise.resolve();

    expect(chrome.tabs.sendMessage).toHaveBeenLastCalledWith(7, {
      type: 'DISABLE_PAGE'
    }, expect.any(Function));
  });

  it('在缺少活动标签页 API 时渲染 Safari 首发引导文案', async () => {
    // @ts-expect-error test override
    globalThis.chrome = {
      runtime: chrome.runtime,
      storage: chrome.storage
    };

    await mountPopup(document.getElementById('app')!);

    expect(document.body.textContent).toContain('请先在 Safari 中为当前网站开启扩展权限');
    expect((document.querySelector('[data-role="apply"]') as HTMLButtonElement).disabled).toBe(true);
    expect((document.querySelector('[data-role="enabled"]') as HTMLInputElement).disabled).toBe(true);
  });
});

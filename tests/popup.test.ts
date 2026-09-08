import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { BrowserApi } from '../src/platform/types';
import { mountPopup } from '../src/popup/index';
import type { ContentMessage } from '../src/shared/types';

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

  it('已有规则时默认完整显示摘要列表', async () => {
    await chrome.storage.local.set({
      globalRules: [
        { id: 'r1', source: '沈清辞', target: '林惊鹤' },
        { id: 'r2', source: '顾怀安', target: '江望舒' }
      ]
    });

    await mountPopup(document.getElementById('app')!);

    expect(document.querySelectorAll('[data-rule-summary]')).toHaveLength(2);
    expect(document.body.textContent).toContain('沈清辞');
    expect(document.body.textContent).toContain('江望舒');
    expect(document.querySelector('[data-role="open-editor"]')).toBeNull();
  });

  it('点击摘要后仅该行进入编辑状态', async () => {
    await chrome.storage.local.set({
      globalRules: [
        { id: 'r1', source: '沈清辞', target: '林惊鹤' },
        { id: 'r2', source: '顾怀安', target: '江望舒' }
      ]
    });

    await mountPopup(document.getElementById('app')!);
    (document.querySelector('[data-edit="r1"]') as HTMLButtonElement).click();

    expect(document.querySelector('[data-rule-editor-row][data-id="r1"]')).not.toBeNull();
    expect(document.querySelector('[data-rule-summary][data-id="r2"]')).not.toBeNull();
  });

  it('成功应用后保留摘要列表', async () => {
    const popup = await mountPopup(document.getElementById('app')!);
    popup.addRule();
    const ruleId = document.querySelector<HTMLElement>('[data-rule-row]')!.dataset.id!;
    popup.updateRule(ruleId, 'source', '沈清辞');
    popup.updateRule(ruleId, 'target', '林惊鹤');

    await popup.apply();

    expect(document.querySelector('[data-rule-summary]')).not.toBeNull();
    expect(document.querySelector('[data-role="open-editor"]')).toBeNull();
  });

  it('切换编辑规则时保留前一行输入', async () => {
    await chrome.storage.local.set({
      globalRules: [
        { id: 'r1', source: '沈清辞', target: '林惊鹤' },
        { id: 'r2', source: '顾怀安', target: '江望舒' }
      ]
    });

    const popup = await mountPopup(document.getElementById('app')!);
    (document.querySelector('[data-edit="r1"]') as HTMLButtonElement).click();
    popup.updateRule('r1', 'target', '周既明');
    (document.querySelector('[data-edit="r2"]') as HTMLButtonElement).click();

    expect(document.querySelector('[data-rule-summary][data-id="r1"]')?.textContent).toContain('周既明');
    expect(document.querySelector('[data-rule-editor-row][data-id="r2"]')).not.toBeNull();
  });

  it('新增规则后在列表末尾进入编辑状态', async () => {
    await chrome.storage.local.set({
      globalRules: [{ id: 'r1', source: '沈清辞', target: '林惊鹤' }]
    });
    const popup = await mountPopup(document.getElementById('app')!);

    popup.addRule();

    const rows = document.querySelectorAll('[data-rule-row]');
    expect(rows).toHaveLength(2);
    expect(rows[1].matches('[data-rule-editor-row]')).toBe(true);
  });

  it('删除最后一条规则后显示待应用空态', async () => {
    await chrome.storage.local.set({
      globalRules: [{ id: 'r1', source: '沈清辞', target: '林惊鹤' }]
    });
    const popup = await mountPopup(document.getElementById('app')!);

    popup.removeRule('r1');

    expect(document.querySelector('[data-empty-pending]')).not.toBeNull();
    expect(document.querySelector('[data-role="apply"]')).not.toBeNull();
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

  it('保存归一化规则并向活动标签页发送 APPLY_RULES', async () => {
    const popup = await mountPopup(document.getElementById('app')!);
    popup.addRule();
    const ruleId = document.querySelector<HTMLElement>('[data-rule-row]')!.dataset.id!;

    popup.updateRule(ruleId, 'source', ' 沈清辞 ');
    popup.updateRule(ruleId, 'target', ' 林惊鹤 ');

    await popup.apply();

    expect(chrome.tabs.sendMessage).toHaveBeenLastCalledWith(7, {
      type: 'APPLY_RULES',
      rules: [{ id: ruleId, source: '沈清辞', target: '林惊鹤' }]
    }, expect.any(Function));

    await expect(chrome.storage.local.get('globalRules')).resolves.toEqual({
      globalRules: [{ id: ruleId, source: '沈清辞', target: '林惊鹤' }]
    });
  });

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
    expect((document.querySelector('[data-role="apply"]') as HTMLButtonElement).disabled).toBe(false);
    popup.updateRule(ruleId, 'source', '沈清辞');
    popup.updateRule(ruleId, 'target', '林惊鹤');

    await popup.apply();

    expect(sendMessage).toHaveBeenCalledWith(7, {
      type: 'APPLY_RULES',
      rules: [{ id: ruleId, source: '沈清辞', target: '林惊鹤' }]
    });
  });

});

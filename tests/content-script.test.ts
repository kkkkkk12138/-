import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { mountContentScript } from '../src/content/index';

describe('content script', () => {
  let runtime: ReturnType<typeof mountContentScript> | null = null;

  beforeEach(() => {
    document.body.innerHTML = `
      <article id="root">
        <p id="line">沈清辞来了。</p>
      </article>
    `;
  });

  afterEach(() => {
    runtime?.stop();
    runtime = null;
  });

  it('返回带权限信息的页面状态', async () => {
    runtime = mountContentScript(document);

    await expect(runtime.handleMessage({ type: 'GET_PAGE_STATE' })).resolves.toEqual({
      enabled: false,
      activeRuleCount: 0,
      permissionState: 'granted'
    });
  });

  it('处理 APPLY_RULES 与 GET_PAGE_STATE，并替换动态新增节点', async () => {
    runtime = mountContentScript(document);

    await runtime.handleMessage({
      type: 'APPLY_RULES',
      rules: [{ id: '1', source: '沈清辞', target: '林惊鹤' }]
    });

    expect(document.getElementById('line')?.textContent).toBe('林惊鹤来了。');
    expect(await runtime.handleMessage({ type: 'GET_PAGE_STATE' })).toEqual({
      enabled: true,
      activeRuleCount: 1,
      permissionState: 'granted'
    });

    const next = document.createElement('p');
    next.textContent = '沈清辞又来了。';
    document.getElementById('root')?.appendChild(next);

    await Promise.resolve();

    expect(next.textContent).toBe('林惊鹤又来了。');
  });

  it('处理 characterData 变更并在 DISABLE_PAGE 后停止处理', async () => {
    runtime = mountContentScript(document);

    await runtime.handleMessage({
      type: 'APPLY_RULES',
      rules: [{ id: '1', source: '沈清辞', target: '林惊鹤' }]
    });

    const line = document.getElementById('line')!.firstChild as Text;
    line.nodeValue = '沈清辞又回来了。';

    await Promise.resolve();

    expect(document.getElementById('line')?.textContent).toBe('林惊鹤又回来了。');

    await runtime.handleMessage({ type: 'DISABLE_PAGE' });

    expect(await runtime.handleMessage({ type: 'GET_PAGE_STATE' })).toEqual({
      enabled: false,
      activeRuleCount: 0,
      permissionState: 'granted'
    });

    const next = document.createElement('p');
    next.textContent = '沈清辞不会再被替换。';
    document.getElementById('root')?.appendChild(next);

    await Promise.resolve();

    expect(next.textContent).toBe('沈清辞不会再被替换。');
  });
});

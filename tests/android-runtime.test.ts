import { readFile } from 'node:fs/promises';
import { describe, expect, it } from 'vitest';
import { installNameReplacerRuntime } from '../src/android-runtime/runtime';

describe('Android page runtime', () => {
  it('applies longest sources first and restores from original text', () => {
    document.body.innerHTML = '<p>沈清辞和沈清</p>';
    const runtime = installNameReplacerRuntime(document);
    runtime.applyRules([
      { id: 'short', source: '沈清', target: 'A', order: 0 },
      { id: 'long', source: '沈清辞', target: 'B', order: 1 }
    ]);
    expect(document.body.textContent).toBe('B和A');
    runtime.applyRules([]);
    expect(document.body.textContent).toBe('沈清辞和沈清');
  });

  it('does not expose browser or native capabilities', async () => {
    await import('../src/android-runtime/index');
    const source = await Promise.all(
      ['index.ts', 'runtime.ts', 'types.ts'].map((file) =>
        readFile(`src/android-runtime/${file}`, 'utf8')
      )
    );

    expect(window.__NAME_REPLACER__).toBeDefined();
    expect(source.join('\n')).not.toMatch(
      /chrome\.|browser\.|addJavascriptInterface|document\.cookie|fetch\(/
    );
  });
});

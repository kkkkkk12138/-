import { describe, expect, it } from 'vitest';
import { shouldProcessTextNode } from '../src/content/domFilter';
import { createTextEngine } from '../src/content/textEngine';

describe('domFilter', () => {
  it('skips blocked and editable regions', () => {
    document.body.innerHTML = `
      <p id="plain">沈清辞来了</p>
      <input value="沈清辞" />
      <pre>沈清辞</pre>
      <code>沈清辞</code>
      <div id="editable" contenteditable="true">沈清辞</div>
      <div data-name-replacement-ignore="true"><span id="ignored">沈清辞</span></div>
    `;

    expect(shouldProcessTextNode(document.getElementById('plain')!.firstChild as Text)).toBe(true);
    expect(shouldProcessTextNode(document.querySelector('pre')!.firstChild as Text)).toBe(false);
    expect(shouldProcessTextNode(document.querySelector('code')!.firstChild as Text)).toBe(false);
    expect(shouldProcessTextNode(document.getElementById('editable')!.firstChild as Text)).toBe(
      false
    );
    expect(shouldProcessTextNode(document.getElementById('ignored')!.firstChild as Text)).toBe(
      false
    );
  });
});

describe('text engine', () => {
  it('replaces readable text nodes without injecting wrapper elements', () => {
    document.body.innerHTML = `
      <article>
        <p id="line">沈清辞看着清辞笑了。</p>
      </article>
      <input value="沈清辞" />
      <pre>沈清辞</pre>
      <code>沈清辞</code>
    `;

    const engine = createTextEngine();
    engine.applyToDocument(document, [
      { id: '1', source: '沈清辞', target: '林惊鹤' },
      { id: '2', source: '清辞', target: '惊鹤' }
    ]);

    expect(document.getElementById('line')!.textContent).toBe('林惊鹤看着惊鹤笑了。');
    expect(document.querySelector('#line span')).toBeNull();
    expect((document.querySelector('input') as HTMLInputElement).value).toBe('沈清辞');
    expect(document.querySelector('pre')!.textContent).toBe('沈清辞');
    expect(document.querySelector('code')!.textContent).toBe('沈清辞');
  });

  it('reapplies from original text when rules change or shrink', () => {
    document.body.innerHTML = `<p id="line">沈清辞看着清辞笑了。</p>`;

    const engine = createTextEngine();

    engine.applyToDocument(document, [
      { id: '1', source: '沈清辞', target: '林惊鹤' },
      { id: '2', source: '清辞', target: '惊鹤' }
    ]);
    engine.applyToDocument(document, [{ id: '1', source: '沈清辞', target: '顾云深' }]);

    expect(document.getElementById('line')!.textContent).toBe('顾云深看着清辞笑了。');
  });

  it('can refresh the original baseline for newly changed text nodes', () => {
    document.body.innerHTML = `<div id="root"><p id="line">沈清辞来了。</p></div>`;

    const engine = createTextEngine();
    const textNode = document.getElementById('line')!.firstChild as Text;

    engine.applyToDocument(document, [{ id: '1', source: '沈清辞', target: '林惊鹤' }]);

    textNode.nodeValue = '沈清辞又来了。';
    engine.refreshOriginal(textNode);
    engine.applyToNode(textNode, [{ id: '1', source: '沈清辞', target: '顾云深' }]);

    expect(document.getElementById('line')!.textContent).toBe('顾云深又来了。');
  });
});

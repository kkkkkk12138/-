(() => {
  'use strict';

  const MAX_DOM_CHUNKS = 5;
  const params = new URLSearchParams(location.search);
  const session = params.get('session') || '';
  const chunkCount = Number(params.get('chunks') || 0);
  const starts = (params.get('starts') || '').split(',').filter(Boolean).map(Number);
  const requestedOffset = Number(params.get('offset') || 0);
  const reader = document.getElementById('reader');
  const status = document.getElementById('status');
  const loaded = new Map();
  let loading = false;

  function chunkIndexForOffset(offset) {
    let result = 0;
    for (let index = 0; index < starts.length && starts[index] <= offset; index += 1) {
      result = index;
    }
    return result;
  }

  let currentIndex = chunkIndexForOffset(requestedOffset);

  const viewportHeight = () => window.innerHeight;
  const chunkUrl = index =>
    `/txt/${encodeURIComponent(session)}/${index}`;

  async function fetchChunk(index) {
    if (index < 0 || index >= chunkCount || loaded.has(index)) return;
    const response = await fetch(chunkUrl(index), { credentials: 'omit', cache: 'no-store' });
    if (!response.ok) throw new Error(`TXT chunk ${index} unavailable`);
    const element = document.createElement('section');
    element.className = 'chunk';
    element.dataset.index = String(index);
    element.dataset.startCharacterOffset =
      response.headers.get('X-Character-Start') || String(starts[index] || 0);
    element.dataset.endCharacterOffset =
      response.headers.get('X-Character-End') || element.dataset.startCharacterOffset;
    element.innerHTML = await response.text();
    loaded.set(index, element);
  }

  function renderWindow(anchorIndex) {
    const anchor = loaded.get(anchorIndex);
    const previousTop = anchor?.getBoundingClientRect().top;
    const ordered = [...loaded.entries()].sort((left, right) => left[0] - right[0]);
    const keep = ordered
      .sort((left, right) =>
        Math.abs(left[0] - currentIndex) - Math.abs(right[0] - currentIndex))
      .slice(0, MAX_DOM_CHUNKS)
      .sort((left, right) => left[0] - right[0]);
    loaded.clear();
    keep.forEach(([index, element]) => loaded.set(index, element));
    reader.replaceChildren(...keep.map(([, element]) => element));
    if (anchor && previousTop !== undefined && loaded.has(anchorIndex)) {
      window.scrollBy(0, anchor.getBoundingClientRect().top - previousTop);
    }
    status.hidden = loaded.size >= chunkCount;
  }

  async function loadWindow(center, anchorIndex = center) {
    if (loading) return;
    loading = true;
    try {
      currentIndex = Math.max(0, Math.min(chunkCount - 1, center));
      const indexes = [];
      for (let index = currentIndex - 2; index <= currentIndex + 2; index += 1) {
        if (index >= 0 && index < chunkCount) indexes.push(index);
      }
      await Promise.all(indexes.map(fetchChunk));
      renderWindow(anchorIndex);
    } finally {
      loading = false;
    }
  }

  function visibleCharacterOffset() {
    const chunks = [...reader.querySelectorAll('.chunk')];
    const visible = chunks.find(element => element.getBoundingClientRect().bottom > 0);
    if (!visible) return requestedOffset;
    const startCharacterOffset = Number(visible.dataset.startCharacterOffset || 0);
    const endCharacterOffset = Number(visible.dataset.endCharacterOffset || startCharacterOffset);
    const rect = visible.getBoundingClientRect();
    const ratio = Math.max(0, Math.min(1, -rect.top / Math.max(1, rect.height)));
    return Math.round(startCharacterOffset + (endCharacterOffset - startCharacterOffset) * ratio);
  }

  window.__TXT_READER__ = {
    characterOffset: visibleCharacterOffset,
    restoreCharacterOffset: async offset => {
      const target = chunkIndexForOffset(Number(offset));
      await loadWindow(target);
      const element = loaded.get(target);
      if (!element) return;
      const start = Number(element.dataset.startCharacterOffset || 0);
      const end = Number(element.dataset.endCharacterOffset || start);
      const ratio = Math.max(0, Math.min(1, (Number(offset) - start) / Math.max(1, end - start)));
      window.scrollTo(0, element.offsetTop + element.scrollHeight * ratio);
    },
  };

  window.addEventListener('scroll', () => {
    const viewportHeight = window.innerHeight;
    const visible = [...reader.querySelectorAll('.chunk')]
      .find(element => element.getBoundingClientRect().bottom > 0);
    const anchorIndex = Number(visible?.dataset.index || currentIndex);
    const indexes = [...loaded.keys()];
    const firstIndex = Math.min(...indexes);
    const lastIndex = Math.max(...indexes);
    const distanceToBottom =
      document.documentElement.scrollHeight - window.scrollY - viewportHeight;
    if (distanceToBottom < viewportHeight * 2 && lastIndex + 1 < chunkCount) {
      loadWindow(lastIndex + 1, anchorIndex).catch(() => { status.textContent = '载入失败'; });
    } else if (window.scrollY < viewportHeight * 2 && firstIndex > 0) {
      loadWindow(firstIndex - 1, anchorIndex).catch(() => { status.textContent = '载入失败'; });
    }
  }, { passive: true });

  window.__TXT_READER__.restoreCharacterOffset(requestedOffset)
    .catch(() => { status.textContent = '无法载入 TXT'; });
})();

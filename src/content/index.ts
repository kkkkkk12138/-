import { normalizeRules } from '../shared/rules';
import type { ContentMessage, PageRuntimeState, ReplaceRule } from '../shared/types';
import { createTextEngine } from './textEngine';

type ContentScriptRuntime = {
  handleMessage(message: ContentMessage): Promise<PageRuntimeState | void>;
  stop(): void;
};

export function mountContentScript(doc: Document = document): ContentScriptRuntime {
  const engine = createTextEngine();

  let enabled = false;
  let rules: ReplaceRule[] = [];
  let observer: MutationObserver | null = null;
  let isObserving = false;

  function getState(): PageRuntimeState {
    return {
      enabled,
      activeRuleCount: rules.length,
      permissionState: 'granted'
    };
  }

  function disconnectObserver(): void {
    observer?.disconnect();
    isObserving = false;
  }

  function resumeObserver(): void {
    if (!enabled || !observer || !doc.body) {
      return;
    }

    observer.observe(doc.body, {
      childList: true,
      subtree: true,
      characterData: true
    });
    isObserving = true;
  }

  function stop(): void {
    enabled = false;
    rules = [];
    disconnectObserver();
    observer = null;
  }

  function runWithoutObserving(work: () => void): void {
    const shouldResume = isObserving;

    if (shouldResume) {
      disconnectObserver();
    }

    try {
      work();
    } finally {
      if (shouldResume) {
        resumeObserver();
      }
    }
  }

  function applyRulesToDocument(): void {
    runWithoutObserving(() => {
      engine.applyToDocument(doc, rules);
    });
  }

  function processAddedNode(node: Node): void {
    if (node.nodeType !== Node.TEXT_NODE && node.nodeType !== Node.ELEMENT_NODE) {
      return;
    }

    engine.applyToNode(node, rules);
  }

  function startObserver(): void {
    disconnectObserver();

    if (!doc.body) {
      observer = null;
      return;
    }

    observer = new MutationObserver((mutations) => {
      if (!enabled || rules.length === 0) {
        return;
      }

      runWithoutObserving(() => {
        for (const mutation of mutations) {
          if (mutation.type === 'childList') {
            mutation.addedNodes.forEach((node) => {
              processAddedNode(node);
            });
            continue;
          }

          if (mutation.type === 'characterData' && mutation.target.nodeType === Node.TEXT_NODE) {
            const textNode = mutation.target as Text;
            engine.refreshOriginal(textNode);
            engine.applyToNode(textNode, rules);
          }
        }
      });
    });

    resumeObserver();
  }

  async function handleMessage(message: ContentMessage): Promise<PageRuntimeState | void> {
    if (message.type === 'GET_PAGE_STATE') {
      return getState();
    }

    if (message.type === 'APPLY_RULES') {
      rules = normalizeRules(message.rules);
      enabled = rules.length > 0;

      if (!enabled) {
        disconnectObserver();
        return;
      }

      applyRulesToDocument();
      startObserver();
      return;
    }

    stop();
  }

  return {
    handleMessage,
    stop
  };
}

const runtime = mountContentScript(document);

chrome.runtime?.onMessage?.addListener((message, _sender, sendResponse) => {
  runtime.handleMessage(message as ContentMessage).then((result) => {
    sendResponse(result);
  });

  return true;
});

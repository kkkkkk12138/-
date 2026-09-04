"use strict";
(() => {
  // src/shared/rules.ts
  function normalizeRules(input) {
    const seen = /* @__PURE__ */ new Set();
    return input.map((rule) => ({
      ...rule,
      source: rule.source.trim(),
      target: rule.target.trim()
    })).filter((rule) => rule.source && rule.target && rule.source !== rule.target).filter((rule) => {
      const key = `${rule.source}::${rule.target}`;
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    }).sort((a, b) => b.source.length - a.source.length);
  }

  // src/content/domFilter.ts
  var BLOCKED_TAGS = /* @__PURE__ */ new Set([
    "SCRIPT",
    "STYLE",
    "NOSCRIPT",
    "TEXTAREA",
    "INPUT",
    "BUTTON",
    "SELECT",
    "OPTION",
    "CODE",
    "PRE"
  ]);
  function shouldProcessTextNode(node) {
    if (!node.nodeValue?.trim()) {
      return false;
    }
    let current = node.parentNode;
    while (current) {
      if (current.nodeType !== Node.ELEMENT_NODE) {
        current = current.parentNode;
        continue;
      }
      const element = current;
      if (BLOCKED_TAGS.has(element.tagName)) {
        return false;
      }
      if (element.isContentEditable || element.getAttribute("contenteditable") === "true" || element.getAttribute("contenteditable") === "") {
        return false;
      }
      if (element.dataset.nameReplacementIgnore === "true") {
        return false;
      }
      current = current.parentNode;
    }
    return true;
  }

  // src/content/textEngine.ts
  function replaceText(baseText, rules) {
    return rules.reduce((current, rule) => current.split(rule.source).join(rule.target), baseText);
  }
  function getWalkerDocument(root) {
    if (root.nodeType === Node.DOCUMENT_NODE) {
      return root;
    }
    return root.ownerDocument;
  }
  function createTextEngine() {
    const originals = /* @__PURE__ */ new WeakMap();
    function applyToTextNode(node, rules) {
      if (!shouldProcessTextNode(node)) {
        return;
      }
      const baseText = originals.get(node) ?? (node.nodeValue ?? "");
      if (!originals.has(node)) {
        originals.set(node, baseText);
      }
      const nextText = replaceText(baseText, rules);
      if (nextText !== node.nodeValue) {
        node.nodeValue = nextText;
      }
    }
    function visitNode(root, rules) {
      if (root.nodeType === Node.TEXT_NODE) {
        applyToTextNode(root, rules);
        return;
      }
      const doc = getWalkerDocument(root);
      if (!doc) {
        return;
      }
      const walker = doc.createTreeWalker(root, NodeFilter.SHOW_TEXT);
      let current = walker.nextNode();
      while (current) {
        applyToTextNode(current, rules);
        current = walker.nextNode();
      }
    }
    return {
      applyToDocument(doc, rules) {
        if (!doc.body) {
          return;
        }
        visitNode(doc.body, rules);
      },
      applyToNode(root, rules) {
        visitNode(root, rules);
      },
      refreshOriginal(node) {
        originals.set(node, node.nodeValue ?? "");
      }
    };
  }

  // src/content/index.ts
  function mountContentScript(doc = document) {
    const engine = createTextEngine();
    let enabled = false;
    let rules = [];
    let observer = null;
    let isObserving = false;
    function getState() {
      return {
        enabled,
        activeRuleCount: rules.length,
        permissionState: "granted"
      };
    }
    function disconnectObserver() {
      observer?.disconnect();
      isObserving = false;
    }
    function resumeObserver() {
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
    function stop() {
      enabled = false;
      rules = [];
      disconnectObserver();
      observer = null;
    }
    function runWithoutObserving(work) {
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
    function applyRulesToDocument() {
      runWithoutObserving(() => {
        engine.applyToDocument(doc, rules);
      });
    }
    function processAddedNode(node) {
      if (node.nodeType !== Node.TEXT_NODE && node.nodeType !== Node.ELEMENT_NODE) {
        return;
      }
      engine.applyToNode(node, rules);
    }
    function startObserver() {
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
            if (mutation.type === "childList") {
              mutation.addedNodes.forEach((node) => {
                processAddedNode(node);
              });
              continue;
            }
            if (mutation.type === "characterData" && mutation.target.nodeType === Node.TEXT_NODE) {
              const textNode = mutation.target;
              engine.refreshOriginal(textNode);
              engine.applyToNode(textNode, rules);
            }
          }
        });
      });
      resumeObserver();
    }
    async function handleMessage(message) {
      if (message.type === "GET_PAGE_STATE") {
        return getState();
      }
      if (message.type === "APPLY_RULES") {
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
  var runtime = mountContentScript(document);
  chrome.runtime?.onMessage?.addListener((message, _sender, sendResponse) => {
    runtime.handleMessage(message).then((result) => {
      sendResponse(result);
    });
    return true;
  });
})();

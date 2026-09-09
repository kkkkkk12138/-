package com.xiaoshuo.yijianhuanming.content.web

import java.net.URI

class LoginRiskPolicy {
    fun isLoginUrl(rawUrl: String): Boolean {
        val path = runCatching { URI(rawUrl).path }.getOrNull() ?: return false
        return path
            .split('/')
            .filter { it.isNotBlank() }
            .any { segment -> segment.lowercase() in LOGIN_PATH_SEGMENTS }
    }

    companion object {
        private val LOGIN_PATH_SEGMENTS = setOf(
            "login",
            "signin",
            "sign-in",
            "passport",
        )

        const val PASSWORD_FORM_GUARD_SCRIPT = """
            (() => {
              const password = document.querySelector('input[type="password"]');
              if (!password) return JSON.stringify({ loginRisk: false });
              document.querySelectorAll('form').forEach(form => {
                if (form.querySelector('input[type="password"]')) {
                  form.addEventListener('submit', event => event.preventDefault(), { capture: true });
                }
              });
              return JSON.stringify({ loginRisk: true });
            })()
        """

        fun resultHasLoginRisk(rawResult: String?): Boolean =
            rawResult
                ?.replace("\\\"", "\"")
                ?.contains("\"loginRisk\":true") == true
    }
}

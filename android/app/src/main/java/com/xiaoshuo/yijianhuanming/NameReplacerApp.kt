package com.xiaoshuo.yijianhuanming

import android.app.Application
import android.webkit.WebView
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NameReplacerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }
}

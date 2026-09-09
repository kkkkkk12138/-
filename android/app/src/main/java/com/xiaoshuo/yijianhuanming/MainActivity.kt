package com.xiaoshuo.yijianhuanming

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.xiaoshuo.yijianhuanming.reader.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeScreen(
                onOpenUrl = {},
                onOpenDocument = {},
            )
        }
    }
}

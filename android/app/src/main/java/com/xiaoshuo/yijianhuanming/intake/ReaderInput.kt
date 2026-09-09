package com.xiaoshuo.yijianhuanming.intake

import android.net.Uri

sealed interface ReaderInput {
    data class WebUrl(val uri: Uri) : ReaderInput

    data class TxtDocument(
        val uri: Uri,
        val persistedReadPermission: Boolean,
    ) : ReaderInput

    data class EpubDocument(
        val uri: Uri,
        val persistedReadPermission: Boolean,
    ) : ReaderInput
}

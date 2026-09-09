package com.xiaoshuo.yijianhuanming.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavHostTest {
    @Test
    fun process_recreation_restores_input_type_uri_and_reading_position() {
        val original = ReaderDestinationState(
            inputType = "EPUB",
            uri = "content://books/story.epub",
            chapterId = "chapter-12",
            scrollRatio = 0.64,
        )

        val restored = ReaderDestinationState.fromSavedState(original.toSavedState())

        assertEquals(original, restored)
    }

    @Test
    fun adaptive_policy_uses_bottom_sheet_on_phone_and_supporting_pane_on_wide_screen() {
        assertEquals(ReaderPaneMode.BottomSheet, ReaderPaneMode.forWidth(599))
        assertEquals(ReaderPaneMode.SupportingPane, ReaderPaneMode.forWidth(840))
    }

    @Test
    fun empty_process_state_stays_on_library() {
        assertEquals(null, ReaderDestinationState.fromSavedState(emptyMap()))
    }

    @Test
    fun material_experience_enables_dynamic_color_only_when_supported() {
        assertEquals(false, ReaderExperiencePolicy.usesDynamicColor(apiLevel = 30))
        assertEquals(true, ReaderExperiencePolicy.usesDynamicColor(apiLevel = 31))
        assertEquals(true, ReaderExperiencePolicy.edgeToEdge)
        assertEquals(true, ReaderExperiencePolicy.predictiveBack)
    }

    @Test
    fun back_dismisses_transient_ui_then_web_history_before_closing_reader() {
        assertEquals(
            ReaderBackTarget.DismissPanel,
            readerBackTarget(panelOpen = true, canNavigateWebHistory = true),
        )
        assertEquals(
            ReaderBackTarget.WebHistory,
            readerBackTarget(panelOpen = false, canNavigateWebHistory = true),
        )
        assertEquals(
            ReaderBackTarget.CloseReader,
            readerBackTarget(panelOpen = false, canNavigateWebHistory = false),
        )
    }
}

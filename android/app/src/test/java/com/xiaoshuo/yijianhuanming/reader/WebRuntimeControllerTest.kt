package com.xiaoshuo.yijianhuanming.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRuntimeControllerTest {
    @Test
    fun generation_token_rejects_callbacks_from_an_old_navigation() {
        val generations = NavigationGenerations()
        val first = generations.beginNavigation()
        val second = generations.beginNavigation()

        assertFalse(generations.isCurrent(first))
        assertTrue(generations.isCurrent(second))
    }

    @Test
    fun invalidation_rejects_the_current_pages_pending_callbacks() {
        val generations = NavigationGenerations()
        val current = generations.beginNavigation()

        generations.invalidate()

        assertFalse(generations.isCurrent(current))
    }
}

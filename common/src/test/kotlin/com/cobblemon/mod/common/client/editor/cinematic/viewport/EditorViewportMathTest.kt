package com.cobblemon.mod.common.client.editor.cinematic.viewport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditorViewportMathTest {

    @Test
    fun `isInside is true within the rect and false outside`() {
        assertTrue(EditorViewportMath.isInside(150f, 120f, 100, 100, 400, 300))
        assertFalse(EditorViewportMath.isInside(90f, 120f, 100, 100, 400, 300))
        assertFalse(EditorViewportMath.isInside(150f, 450f, 100, 100, 400, 300))
    }

    @Test
    fun `screenToViewport maps top-left origin and returns null when outside`() {
        val p = EditorViewportMath.screenToViewport(150f, 130f, 100, 100, 400, 300)
        assertNotNull(p)
        assertEquals(50f, p!!.first, 1e-3f)
        assertEquals(30f, p.second, 1e-3f)
        assertNull(EditorViewportMath.screenToViewport(10f, 10f, 100, 100, 400, 300))
    }

    @Test
    fun `blitFractions converts a rect to 0to1 window fractions`() {
        val f = EditorViewportMath.blitFractions(100, 150, 400, 300, 800, 600)
        assertEquals(0.125f, f[0], 1e-4f)
        assertEquals(0.25f, f[1], 1e-4f)
        assertEquals(0.625f, f[2], 1e-4f)
        assertEquals(0.75f, f[3], 1e-4f)
    }

    @Test
    fun `overrideSize returns rect size when valid, else default`() {
        assertEquals(400, EditorViewportMath.overrideSize(rectSize = 400, default = 1920))
        assertEquals(1920, EditorViewportMath.overrideSize(rectSize = 1, default = 1920))
    }
}

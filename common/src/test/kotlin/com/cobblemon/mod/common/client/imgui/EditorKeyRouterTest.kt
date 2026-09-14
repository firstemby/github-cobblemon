package com.cobblemon.mod.common.client.imgui

import com.cobblemon.mod.common.client.imgui.EditorKeyRouter.Target
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_KEY_A
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.lwjgl.glfw.GLFW.GLFW_KEY_TAB
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE
import org.lwjgl.glfw.GLFW.GLFW_REPEAT

class EditorKeyRouterTest {
    @Test
    fun `editor shortcuts work on remapped keys and Escape reaches the editor`() {
        val router = EditorKeyRouter()
        assertEquals(Target.GAME, router.route(GLFW_KEY_A, GLFW_PRESS, true, true, false, true, true))
        assertEquals(Target.GAME, router.route(GLFW_KEY_A, GLFW_RELEASE, false, false, false, false, true))
        assertEquals(Target.IMGUI, router.route(GLFW_KEY_ESCAPE, GLFW_PRESS, true, true, false, false, true))
        assertEquals(Target.IMGUI, router.route(GLFW_KEY_ESCAPE, GLFW_RELEASE, true, true, false, false, true))
    }

    @Test
    fun `typing a remapped key cannot close the editor and Tab remains available to text fields`() {
        val router = EditorKeyRouter()
        assertEquals(Target.IMGUI, router.route(GLFW_KEY_A, GLFW_PRESS, true, true, true, true, true))
        assertEquals(Target.IMGUI, router.route(GLFW_KEY_TAB, GLFW_PRESS, true, true, true, false, true))
        assertEquals(Target.IMGUI, router.route(GLFW_KEY_TAB, GLFW_RELEASE, false, true, false, false, true))
    }

    @Test
    fun `key release and repeat retain ownership across capture changes`() {
        val router = EditorKeyRouter()
        assertEquals(Target.GAME, router.route(GLFW_KEY_A, GLFW_PRESS, false, false, false, false, true))
        assertEquals(Target.GAME, router.route(GLFW_KEY_A, GLFW_REPEAT, true, true, false, false, true))
        assertEquals(Target.GAME, router.route(GLFW_KEY_A, GLFW_RELEASE, true, true, false, false, true))
        assertEquals(Target.IMGUI, router.route(GLFW_KEY_A, GLFW_PRESS, true, true, false, false, true))
        assertEquals(Target.IMGUI, router.route(GLFW_KEY_A, GLFW_RELEASE, false, false, false, false, true))
        assertEquals(Target.GAME, router.route(GLFW_KEY_A, GLFW_PRESS, false, false, false, false, true))
    }

    @Test
    fun `passthrough consumes the entire Tab stroke and yields to a remapped editor binding`() {
        val router = EditorKeyRouter()
        assertEquals(Target.PASSTHROUGH, router.route(GLFW_KEY_TAB, GLFW_PRESS, true, true, false, false, true))
        assertEquals(Target.PASSTHROUGH, router.route(GLFW_KEY_TAB, GLFW_REPEAT, false, true, false, false, true))
        assertEquals(Target.PASSTHROUGH, router.route(GLFW_KEY_TAB, GLFW_RELEASE, false, true, false, false, true))
        assertEquals(Target.GAME, router.route(GLFW_KEY_TAB, GLFW_PRESS, true, true, false, true, true))
    }

    @Test
    fun `unmatched releases and unknown keys remain available to the game`() {
        val router = EditorKeyRouter()
        assertEquals(Target.GAME, router.route(GLFW_KEY_A, GLFW_RELEASE, true, true, false, false, true))
        assertEquals(Target.GAME, router.route(-1, GLFW_PRESS, true, true, false, false, true))
    }

    @Test
    fun `losing focus clears held ownership and stale text focus cannot trap passthrough`() {
        val router = EditorKeyRouter()
        assertEquals(Target.IMGUI, router.route(GLFW_KEY_A, GLFW_PRESS, true, true, false, false, true))
        router.reset()
        assertEquals(Target.GAME, router.route(GLFW_KEY_A, GLFW_PRESS, false, true, false, false, true))
        assertEquals(Target.PASSTHROUGH, router.route(GLFW_KEY_TAB, GLFW_PRESS, false, true, true, false, true))
    }
}

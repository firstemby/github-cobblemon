package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.client.editor.cinematic.overlay.LetterboxHudRenderer
import com.cobblemon.mod.common.client.imgui.CobblemonImGui
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorManager
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import net.minecraft.client.gui.GuiGraphics
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class LetterboxHudRendererTest {
    @Test
    fun `closed editor and rendered feed lenses do not add a screen wide preview mask`() {
        val graphics = mockk<GuiGraphics>()
        mockkObject(CobblemonImGui)
        mockkStatic(CompositorManager::class)
        try {
            every { CobblemonImGui.isActive() } returns false
            LetterboxHudRenderer.render(graphics)
            every { CobblemonImGui.isActive() } returns true
            every { CompositorManager.isFullscreenActive() } returns true
            LetterboxHudRenderer.render(graphics)
            verify { graphics wasNot Called }
        } finally {
            unmockkStatic(CompositorManager::class)
            unmockkObject(CobblemonImGui)
        }
    }
}

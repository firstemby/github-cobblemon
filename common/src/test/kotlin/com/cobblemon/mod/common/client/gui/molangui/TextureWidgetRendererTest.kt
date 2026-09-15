/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.molangui.widget.WidgetDefinition
import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
import com.cobblemon.mod.common.api.molangui.widget.WidgetProperty
import com.cobblemon.mod.common.api.molangui.widget.WidgetType
import com.cobblemon.mod.common.client.gui.molangui.renderer.ImageRenderer
import com.cobblemon.mod.common.client.gui.molangui.renderer.PanelRenderer
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.mojang.blaze3d.systems.RenderSystem
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.lwjgl.opengl.ARBImaging.GL_BLEND_COLOR
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14

@BootstrapMinecraft
class TextureWidgetRendererTest {
    private val texture = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/test.png")
    private val runtime = MoLangRuntime()

    @BeforeEach
    fun setupBlendState() {
        mockkStatic(RenderSystem::class, GL11::class, GL14::class)
        every { GL11.glIsEnabled(GL11.GL_BLEND) } returns false
        every { GL11.glGetInteger(any<Int>()) } returns GL11.GL_ONE
        every { GL11.glGetFloatv(GL_BLEND_COLOR, any<FloatArray>()) } answers { secondArg<FloatArray>().fill(0f) }
        every { GL14.glBlendColor(any(), any(), any(), any()) } returns Unit
        every { RenderSystem.enableBlend() } returns Unit
        every { RenderSystem.disableBlend() } returns Unit
        every { RenderSystem.defaultBlendFunc() } returns Unit
        every { RenderSystem.blendFuncSeparate(any<Int>(), any<Int>(), any<Int>(), any<Int>()) } returns Unit
    }

    @AfterEach
    fun restoreBlendState() = unmockkStatic(RenderSystem::class, GL11::class, GL14::class)

    @Test
    fun `image drawing enables alpha blending and restores custom factors on failure`() {
        val factors = mapOf(GL14.GL_BLEND_SRC_RGB to GL11.GL_ONE, GL14.GL_BLEND_DST_RGB to GL11.GL_ONE,
            GL14.GL_BLEND_SRC_ALPHA to GL11.GL_ZERO, GL14.GL_BLEND_DST_ALPHA to GL11.GL_ONE)
        every { GL11.glGetInteger(any<Int>()) } answers { factors.getValue(firstArg()) }
        for (originalEnabled in listOf(false, true)) {
            var enabled = originalEnabled
            var standardBlend = false
            every { GL11.glIsEnabled(GL11.GL_BLEND) } answers { enabled }
            every { RenderSystem.enableBlend() } answers { enabled = true }
            every { RenderSystem.disableBlend() } answers { enabled = false }
            every { RenderSystem.defaultBlendFunc() } answers { standardBlend = true }
            val graphics = mockk<GuiGraphics>(relaxed = true)
            every { graphics.blit(texture, 10, 20, 96, 48, 0f, 0f, 96, 48, 96, 48) } answers {
                assertTrue(enabled && standardBlend)
                throw IllegalStateException("texture draw failed")
            }
            assertThrows(IllegalStateException::class.java) { ImageRenderer.render(widget(), graphics, runtime, 0f, 0f, 0f) }
            assertTrue(enabled == originalEnabled)
        }
        verify(exactly = 2) { RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE) }
    }

    @Test
    fun `an atlas region scales independently from its destination size`() {
        val widget = widget(mapOf("uv" to listOf(8.0, 12.0, 32.0, 16.0), "texture_size" to listOf(128.0, 64.0)))
        val graphics = mockk<GuiGraphics>(relaxed = true)

        ImageRenderer.render(widget, graphics, runtime, 0f, 0f, 0f)

        verify(exactly = 1) { graphics.blit(texture, 10, 20, 96, 48, 8f, 12f, 32, 16, 128, 64) }
    }

    @Test
    fun `omitting UV uses the whole declared texture`() {
        val widget = widget(mapOf("texture_size" to listOf(64, 128)))
        val graphics = mockk<GuiGraphics>(relaxed = true)

        ImageRenderer.render(widget, graphics, runtime, 0f, 0f, 0f)

        verify(exactly = 1) { graphics.blit(texture, 10, 20, 96, 48, 0f, 0f, 64, 128, 64, 128) }
    }

    @Test
    fun `UV without texture size uses the default atlas dimensions`() {
        val widget = widget(mapOf("uv" to listOf(8, 12)))
        val graphics = mockk<GuiGraphics>(relaxed = true)

        ImageRenderer.render(widget, graphics, runtime, 0f, 0f, 0f)

        verify(exactly = 1) { graphics.blit(texture, 10, 20, 96, 48, 8f, 12f, 96, 48, 256, 256) }
    }

    @Test
    fun `invalid dynamic texture does not prevent a panel background from rendering`() {
        val definition = WidgetDefinition("panel", WidgetType.PANEL, mapOf(
            "texture" to WidgetProperty.molang("'Invalid texture'"),
            "color" to WidgetProperty.literal("0xFF112233")
        ))
        val widget = widget().let { dimensions -> WidgetInstance(definition).apply {
            absoluteX = dimensions.absoluteX
            absoluteY = dimensions.absoluteY
            computedWidth = dimensions.computedWidth
            computedHeight = dimensions.computedHeight
        } }
        val graphics = mockk<GuiGraphics>(relaxed = true)

        PanelRenderer.render(widget, graphics, runtime, 0f, 0f, 0f)

        verify(exactly = 1) { graphics.fill(10, 20, 106, 68, 0xFF112233.toInt()) }
        verify(exactly = 0) { graphics.blit(any<ResourceLocation>(), any(), any(), any(), any(), any<Float>(), any<Float>(), any(), any(), any(), any()) }
    }

    @Test
    fun `invalid texture coordinates and dimensions never reach the graphics backend`() {
        val cases = listOf(
            widget(mapOf("texture" to "Invalid texture")),
            widget(mapOf("texture_size" to listOf(0, 64))),
            widget(mapOf("texture_size" to listOf(64, -1))),
            widget(mapOf("texture_size" to listOf(Double.MAX_VALUE, 64))),
            widget(mapOf("uv" to listOf(Double.NaN, 0, 16, 16))),
            widget(mapOf("uv" to listOf(0, 0, 0, 16))),
            widget(mapOf("uv" to "invalid")),
            widget().apply { computedWidth = 0f },
            widget().apply { computedHeight = Float.NaN },
            widget().apply { absoluteX = Float.POSITIVE_INFINITY }
        )
        for (widget in cases) {
            val graphics = mockk<GuiGraphics>(relaxed = true)
            ImageRenderer.render(widget, graphics, runtime, 0f, 0f, 0f)
            confirmVerified(graphics)
        }
    }

    private fun widget(properties: Map<String, Any> = emptyMap()) = WidgetInstance(
        WidgetDefinition("image", WidgetType.IMAGE,
            (mapOf("texture" to texture.toString()) + properties).mapValues { WidgetProperty.literal(it.value) })
    ).apply {
        absoluteX = 10f
        absoluteY = 20f
        computedWidth = 96f
        computedHeight = 48f
    }
}

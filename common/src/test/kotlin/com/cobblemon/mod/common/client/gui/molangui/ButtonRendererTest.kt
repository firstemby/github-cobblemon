/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.molangui.widget.WidgetDefinition
import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
import com.cobblemon.mod.common.api.molangui.widget.WidgetProperty
import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import com.cobblemon.mod.common.api.molangui.widget.WidgetType
import com.cobblemon.mod.common.client.gui.molangui.renderer.ButtonRenderer
import com.cobblemon.mod.common.client.gui.molangui.renderer.WidgetRendererRegistry
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.google.gson.JsonParser
import io.mockk.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class ButtonRendererTest {
    private val graphics = mockk<GuiGraphics>(relaxed = true)
    private lateinit var font: Font

    @BeforeEach
    fun setup() {
        mockkStatic(Minecraft::class)
        mockkObject(WidgetRendererRegistry)
        val client = mockk<Minecraft>(relaxed = true)
        font = mockk(relaxed = true)
        Minecraft::class.java.getDeclaredField("font").apply { isAccessible = true }.set(client, font)
        Font::class.java.getDeclaredField("lineHeight").apply { isAccessible = true }.setInt(font, 9)
        every { font.width(any<String>()) } answers { firstArg<String>().length * 5 }
        every { font.plainSubstrByWidth(any(), any()) } answers { firstArg<String>().take(secondArg<Int>() / 5) }
        every { WidgetRendererRegistry.get(any()) } returns null
        every { WidgetRendererRegistry.get(WidgetType.BUTTON) } returns ButtonRenderer
        every { Minecraft.getInstance() } returns client
    }

    @AfterEach
    fun cleanup() {
        unmockkObject(WidgetRendererRegistry)
        unmockkStatic(Minecraft::class)
    }

    @Test
    fun `buttons use the prepared enabled state including disabled ancestry`() {
        var reads = 0
        fun tree(json: String) = MolangUiWidgetTree(ResourceLocation.fromNamespaceAndPath("cobblemon", "button_test"), UUID.randomUUID(),
            WidgetTree.parseWidget("root", JsonParser.parseString(json).asJsonObject), emptyMap(), emptyMap()).also { tree ->
                tree.runtime.environment.query.addFunction("enabled") { reads++; DoubleValue(1.0) }
            }
        val active = tree("""{"type":"button","size":[40,20],"enabled":{"molang":"q.enabled"},"text":"hello"}""")
        active.render(graphics, 100, 100, 5.0, 5.0, 0f)
        assertEquals(1, reads)
        reads = 0
        val disabled = tree("""{"size":[40,20],"enabled":false,"controls":[{"button":{
            "type":"button","size":[40,20],"enabled":{"molang":"q.enabled"},"text":"hello"}}]}""")
        disabled.render(graphics, 100, 100, 5.0, 5.0, 0f)
        assertEquals(0, reads)
        assertFalse(disabled.root.children.single().enabled)
        assertFalse(disabled.onClick(5.0, 5.0, 0))
        verify { graphics.fill(0, 0, 40, 20, 0xFF333333.toInt()) }
        verify { graphics.drawString(font, "hello", any(), any(), 0xAAAAAA, true) }
    }

    @Test
    fun `long text fits the button and its clipping scope restores after font failure`() {
        val button = WidgetInstance(WidgetDefinition("button", WidgetType.BUTTON,
            mapOf("text" to WidgetProperty.literal("abcdefghij")))).apply {
                absoluteX = 10f; absoluteY = 20f; computedWidth = 24f; computedHeight = 12f
            }
        var clipDepth = 1
        every { graphics.enableScissor(10, 20, 34, 32) } answers { clipDepth++; Unit }
        every { graphics.disableScissor() } answers { clipDepth--; Unit }
        every { graphics.drawString(font, "abc?", 12, 21, 0xFFFFFF, true) } answers {
            assertEquals(2, clipDepth)
            throw IllegalStateException("font failure")
        }
        assertThrows(IllegalStateException::class.java) { ButtonRenderer.render(button, graphics, MoLangRuntime(), 0f, 0f, 0f) }
        assertEquals(1, clipDepth)
        verify(exactly = 1) { graphics.disableScissor() }
        button.computedHeight = 5f
        assertDoesNotThrow { ButtonRenderer.render(button, graphics, MoLangRuntime(), 0f, 0f, 0f) }
        verify(exactly = 1) { graphics.enableScissor(10, 20, 34, 32) }
    }
}

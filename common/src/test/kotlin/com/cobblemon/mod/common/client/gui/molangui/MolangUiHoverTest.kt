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
import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import com.cobblemon.mod.common.api.molangui.widget.WidgetType
import com.cobblemon.mod.common.client.gui.molangui.renderer.ButtonRenderer
import com.cobblemon.mod.common.client.gui.molangui.renderer.WidgetRenderer
import com.cobblemon.mod.common.client.gui.molangui.renderer.WidgetRendererRegistry
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolve
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class MolangUiHoverTest {
    private val graphics = mockk<GuiGraphics>(relaxed = true)
    private val events = mutableListOf<String>()
    private val draws = mutableListOf<String>()

    @BeforeEach
    fun renderers() {
        mockkObject(WidgetRendererRegistry)
        val renderer = object : WidgetRenderer {
            override fun render(widget: WidgetInstance, guiGraphics: GuiGraphics, runtime: MoLangRuntime, mouseX: Float, mouseY: Float, delta: Float) {
                draws += widget.definition.id
            }
        }
        every { WidgetRendererRegistry.get(any()) } returns renderer
    }

    @AfterEach
    fun cleanup() = unmockkObject(WidgetRendererRegistry)

    private fun tree(json: String) = MolangUiWidgetTree(
        ResourceLocation.fromNamespaceAndPath("cobblemon", "hover_test"), UUID.randomUUID(),
        WidgetTree.parseWidget("root", JsonParser.parseString(json).asJsonObject), emptyMap(), emptyMap()
    ).also { tree ->
        tree.runtime.environment.query.addFunction("record") {
            events += tree.runtime.resolve("q.event.type".asExpression()).asString() + ":" +
                tree.runtime.resolve("q.event.widget_id".asExpression()).asString()
            DoubleValue(0.0)
        }
    }
    private fun render(tree: MolangUiWidgetTree, x: Double, y: Double = 10.0) = tree.render(graphics, 200, 100, x, y, 0f)

    @Test
    fun `hover follows ancestry and crossing siblings does not repeat parent events`() {
        val tree = tree("""{"size":[100,40],"on_hover_enter_client":{"molang":"q.record;"},"on_hover_exit_client":{"molang":"q.record;"},
            "controls":[{"button":{"type":"button","size":[100,40],"hover":{"color":"0xFF123456"},
                "on_hover_enter_client":{"molang":"q.record;"},"on_hover_exit_client":{"molang":"q.record;"},
                "controls":[{"left":{"size":[40,40],"on_hover_enter_client":{"molang":"q.record;"},"on_hover_exit_client":{"molang":"q.record;"}}},
                            {"right":{"offset":[50,0],"size":[40,40],"on_hover_enter_client":{"molang":"q.record;"},"on_hover_exit_client":{"molang":"q.record;"}}}]}}]}""")
        every { WidgetRendererRegistry.get(WidgetType.BUTTON) } returns ButtonRenderer
        render(tree, 10.0)
        val button = tree.root.children.single()
        assertTrue(tree.root.hovered && button.hovered && button.children[0].hovered)
        verify { graphics.fill(0, 0, 100, 40, 0xFF123456.toInt()) }
        render(tree, 10.0)
        assertEquals(listOf("on_hover_enter:root", "on_hover_enter:button", "on_hover_enter:left"), events)
        render(tree, 60.0)
        assertTrue(button.hovered && !button.children[0].hovered && button.children[1].hovered)
        assertEquals(listOf("on_hover_exit:left", "on_hover_enter:right"), events.takeLast(2))
        render(tree, 150.0)
        assertEquals(listOf("on_hover_exit:right", "on_hover_exit:button", "on_hover_exit:root"), events.takeLast(3))
        assertFalse(tree.root.hovered || button.hovered || button.children.any { it.hovered })
    }

    @Test
    fun `list hover events retain each row context and restore queries`() {
        val tree = tree("""{"type":"list","size":[100,40],"layout":"stack_vertical","source_type":"count","source":2,
            "item_template":{"size":[100,20],"on_hover_enter_client":{"molang":"q.row;"},"on_hover_exit_client":{"molang":"q.row;"}}}""")
        tree.runtime.environment.query.addFunction("row") {
            events += tree.runtime.resolve("q.event.type".asExpression()).asString() + ":" +
                tree.runtime.resolve("q.item.index".asExpression()).asDouble().toInt()
            DoubleValue(0.0)
        }
        render(tree, 10.0, 10.0)
        render(tree, 10.0, 30.0)
        assertEquals(listOf("on_hover_enter:0", "on_hover_exit:0", "on_hover_enter:1"), events)
        assertFalse("item" in tree.runtime.environment.query.functions)
        assertFalse("event" in tree.runtime.environment.query.functions)
    }

    @Test
    fun `closing during hover exit clears flags and cancels further enter and draw callbacks`() {
        val tree = tree("""{"size":[100,40],"controls":[
            {"old":{"size":[40,40],"on_hover_exit_client":{"molang":"q.close;"}}},
            {"next":{"offset":[50,0],"size":[40,40],"on_hover_enter_client":{"molang":"q.record;"}}}]}""")
        tree.runtime.environment.query.addFunction("close") { tree.cleanup(); DoubleValue(0.0) }
        render(tree, 10.0)
        val children = tree.root.children.toList()
        draws.clear()
        render(tree, 60.0)
        assertTrue(events.isEmpty() && draws.isEmpty())
        assertFalse(tree.root.hovered || children.any { it.hovered })
        assertFalse(tree.prepare(200, 100))
    }

    @Test
    fun `hover mutations refresh visibility before drawing and disappearance emits one exit`() {
        val tree = tree("""{"size":[100,40],"controls":[{"child":{"size":[40,40],"visible":{"molang":"v.hidden == 0"},
            "on_hover_enter_client":{"molang":"v.hidden = 1; q.record;"},"on_hover_exit_client":{"molang":"q.record;"}}}]}""")
        render(tree, 10.0)
        assertFalse("child" in draws)
        render(tree, 10.0)
        render(tree, 10.0)
        assertEquals(listOf("on_hover_enter:child", "on_hover_exit:child"), events)
        assertFalse(tree.root.children.single().hovered)
    }

    @Test
    fun `scroll clipping excludes offscreen descendants from the hover path`() {
        val tree = tree("""{"size":[100,100],"controls":[{"scroll":{"type":"scroll_panel","size":[100,20],
            "controls":[{"offscreen":{"offset":[0,30],"size":[100,20],"on_hover_enter_client":{"molang":"q.record;"}}}]}}]}""")
        render(tree, 10.0, 35.0)
        assertTrue(events.isEmpty())
        assertFalse(tree.root.children.single().hovered)
        tree.root.children.single().scrollOffset = -30f
        render(tree, 10.0, 5.0)
        assertEquals(listOf("on_hover_enter:offscreen"), events)
        assertTrue(tree.root.children.single().children.single().hovered)
    }

    @Test
    fun `failed layout clears hover state without sending callbacks from the invalid view`() {
        val tree = tree("""{"size":[{"molang":"v.width"},40],"on_hover_exit_client":{"molang":"q.record;"}}""")
        tree.updateVariables(mapOf("width" to 100.0))
        render(tree, 10.0)
        assertTrue(tree.root.hovered)
        tree.runtime.environment.setSimpleVariable("width", DoubleValue(Double.NaN))
        assertFalse(tree.prepare(200, 100))
        assertFalse(tree.root.hovered)
        assertTrue(events.isEmpty())
        tree.updateVariables(mapOf("width" to 100.0))
        render(tree, 10.0)
        assertTrue(tree.root.hovered)
    }

    @Test
    fun `unsupported submit and slot change declarations are rejected`() {
        for (name in listOf("on_submit", "on_submit_client", "on_slot_change", "on_slot_change_client")) {
            assertThrows(IllegalArgumentException::class.java) { tree("""{"$name":{"molang":"v.changed = 1;"}}""") }
        }
    }
}

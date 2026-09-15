/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molangui.widget.WidgetExpansion
import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import com.cobblemon.mod.common.api.molangui.widget.WidgetType
import com.cobblemon.mod.common.client.gui.molangui.MolangUiWidgetTree
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
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class WidgetExpansionTest {
    private fun definition(json: String) = WidgetTree.parseWidget("root", JsonParser.parseString(json).asJsonObject)
    private fun tree(json: String, parameters: Map<String, String> = emptyMap(), variables: Map<String, Double> = emptyMap()) =
        MolangUiWidgetTree(ResourceLocation.fromNamespaceAndPath("cobblemon", "list_test"), UUID.randomUUID(),
            definition(json), parameters.mapValues { ParameterValue.StringValue(it.value) }, variables)
    private fun number(runtime: MoLangRuntime, source: String) = runtime.resolve(source.asExpression()).asDouble()

    @Test
    fun `expansion rejects huge fractional and nonfinite counts before allocating rows`() {
        val runtime = MoLangRuntime()
        val def = definition("""{"type":"list","source_type":"count","source":{"molang":"v.count"},"item_template":{"type":"label"}}""")
        for (count in listOf(-1.0, 1.5, Double.NaN, Double.POSITIVE_INFINITY, Int.MAX_VALUE.toDouble())) {
            runtime.environment.setSimpleVariable("count", DoubleValue(count))
            assertThrows(IllegalArgumentException::class.java) { WidgetExpansion.refresh(def, runtime) }
        }
    }

    @Test
    fun `nested lists share the same total instance budget and failed planning preserves the previous tree`() {
        val tree = tree("""{"type":"list","source_type":"count","source":{"molang":"v.count"},"item_template":{
            "type":"list","source_type":"count","source":50,"item_template":{"type":"label"}}}""", variables = mapOf("count" to 2.0))
        val original = tree.root.children.toList()
        tree.updateVariables(mapOf("count" to 50.0))
        assertFalse(tree.prepare(200, 100))
        assertEquals(original, tree.root.children)
        assertFalse(tree.onClick(0.0, 0.0, 0))
        tree.updateVariables(mapOf("count" to 3.0))
        assertTrue(tree.prepare(200, 100))
        assertEquals(3, tree.root.children.size)
        assertSame(original[0], tree.root.children[0])
    }

    @Test
    fun `list row context controls layout nested clicks and data types without leaking to siblings`() {
        val tree = tree("""{"type":"list","size":[200,100],"source_type":"data","source":{"molang":"q.rows"},"layout":"stack_vertical",
            "item_template":{"type":"panel","size":[200,20],"controls":[{"choose":{"type":"button",
              "size":[{"molang":"q.item.width"},20],"on_click_client":{"molang":"v.selected = q.item.index; v.name = q.item.name;"}}}]}}""",
            mapOf("rows" to """[{"name":"0012","width":30,"index":99},{"name":"NaN","width":60}]"""))
        assertTrue(tree.prepare(200, 100))
        assertEquals(30f, tree.root.children[0].children[0].computedWidth)
        assertEquals(60f, tree.root.children[1].children[0].computedWidth)
        assertTrue(tree.onClick(10.0, 25.0, 0))
        assertEquals(1.0, number(tree.runtime, "v.selected"))
        assertEquals("NaN", tree.runtime.resolve("v.name".asExpression()).asString())
        assertTrue(tree.onClick(10.0, 5.0, 0))
        assertEquals(0.0, number(tree.runtime, "v.selected"))
        assertEquals("0012", tree.runtime.resolve("v.name".asExpression()).asString())
        assertFalse("item" in tree.runtime.environment.query.functions)
    }

    @Test
    fun `nested data source sees its outer row and render restores context after a renderer throws`() {
        val tree = tree("""{"type":"list","source_type":"data","source":{"molang":"q.rows"},"item_template":{
            "type":"list","source_type":"data","source":{"molang":"q.item.children"},"item_template":{"type":"label","text":{"molang":"q.item.name"}}}}""",
            mapOf("rows" to """[{"children":[{"name":"inner"}]}]"""))
        val rendered = mutableListOf<String>()
        val renderer = object : WidgetRenderer {
            override fun render(widget: WidgetInstance, guiGraphics: GuiGraphics, runtime: MoLangRuntime, mouseX: Float, mouseY: Float, delta: Float) {
                rendered += widget.definition.getProperty("text")!!.resolveString(runtime)
                throw IllegalStateException("render failed")
            }
        }
        mockkObject(WidgetRendererRegistry)
        try {
            every { WidgetRendererRegistry.get(any()) } returns null
            every { WidgetRendererRegistry.get(WidgetType.LABEL) } returns renderer
            tree.runtime.environment.query.addFunction("item") { StringValue("outside") }
            assertThrows(IllegalStateException::class.java) { tree.render(mockk(relaxed = true), 200, 100, 0.0, 0.0, 0f) }
            assertEquals(listOf("inner"), rendered)
            assertEquals("outside", tree.runtime.resolve("q.item".asExpression()).asString())
        } finally {
            unmockkObject(WidgetRendererRegistry)
        }
    }

    @Test
    fun `variable and parameter changes refresh root list rows while retaining existing widget state`() {
        val tree = tree("""{"type":"list","source_type":"count","source":{"molang":"v.count"},"item_template":{"type":"label"}}""", variables = mapOf("count" to 1.0))
        val first = tree.root.children.single()
        val state = Any()
        first.rendererState = state
        tree.updateVariables(mapOf("count" to 3.0))
        assertTrue(tree.prepare(100, 100))
        assertEquals(3, tree.root.children.size)
        assertSame(first, tree.root.children[0])
        assertSame(state, tree.root.children[0].rendererState)
        tree.updateVariables(mapOf("count" to 0.0))
        assertTrue(tree.prepare(100, 100))
        assertTrue(tree.root.children.isEmpty())

        val data = tree("""{"type":"list","source":{"molang":"q.rows"},"item_template":{"type":"label"}}""", mapOf("rows" to "[]"))
        data.updateParameters(mapOf("rows" to ParameterValue.StringValue("""[{"name":"new"}]""")))
        assertTrue(data.prepare(100, 100))
        assertEquals(1, data.root.children.size)
    }

    @Test
    fun `invisible disabled and closed trees cannot dispatch a nested click`() {
        val tree = tree("""{"type":"panel","size":[100,100],"visible":{"molang":"v.visible"},"enabled":{"molang":"v.enabled"},
            "controls":[{"button":{"type":"button","size":[100,100],"on_click_client":{"molang":"v.clicked = 1;"}}}]}""",
            variables = mapOf("visible" to 1.0, "enabled" to 1.0))
        assertTrue(tree.prepare(100, 100))
        tree.updateVariables(mapOf("visible" to 0.0))
        assertFalse(tree.onClick(5.0, 5.0, 0))
        tree.updateVariables(mapOf("visible" to 1.0, "enabled" to 0.0))
        assertFalse(tree.onClick(5.0, 5.0, 0))
        assertEquals(0.0, number(tree.runtime, "v.clicked"))
        tree.cleanup()
        tree.updateVariables(mapOf("enabled" to 1.0))
        assertFalse(tree.prepare(100, 100))
        assertFalse(tree.onClick(5.0, 5.0, 0))
    }

    @Test
    fun `malformed and oversized list data is rejected before replacing displayed rows`() {
        val tree = tree("""{"type":"list","source":{"molang":"q.rows"},"item_template":{"type":"label"}}""",
            mapOf("rows" to "[{}]"))
        val row = tree.root.children.single()
        for (data in listOf("{}", "[1]", "[")) {
            tree.updateParameters(mapOf("rows" to ParameterValue.StringValue(data)))
            assertFalse(tree.prepare(100, 100))
            assertSame(row, tree.root.children.single())
        }
        assertThrows(IllegalArgumentException::class.java) {
            tree.updateParameters(mapOf("rows" to ParameterValue.StringValue(" ".repeat(MolangUiPacketLimits.MAX_VALUE_LENGTH + 1))))
        }
        assertSame(row, tree.root.children.single())
        tree.updateParameters(mapOf("rows" to ParameterValue.StringValue("[]")))
        assertTrue(tree.prepare(100, 100))
        assertTrue(tree.root.children.isEmpty())
    }
}

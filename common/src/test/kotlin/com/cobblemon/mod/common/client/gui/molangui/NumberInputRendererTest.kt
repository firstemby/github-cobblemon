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
import com.cobblemon.mod.common.client.gui.molangui.renderer.NumberInputRenderer
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolve
import com.google.gson.JsonParser
import io.mockk.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class NumberInputRendererTest {
    private fun widget(properties: Map<String, Any> = emptyMap()) = WidgetInstance(WidgetDefinition("amount", WidgetType.NUMBER_INPUT,
        (mapOf("variable" to "v.amount", "min" to Int.MIN_VALUE, "max" to Int.MAX_VALUE, "step" to 1) + properties)
            .mapValues { WidgetProperty.literal(it.value) } + ("value" to WidgetProperty.molang("v.amount")))).apply {
        absoluteX = 10f
        absoluteY = 20f
        computedWidth = 100f
        computedHeight = 20f
    }
    private fun value(runtime: MoLangRuntime) = runtime.resolve("v.amount".asExpression()).asDouble()

    @Test
    fun `integer extremes clamp without wrapping and bound clicks do not emit changes`() {
        val runtime = MoLangRuntime()
        val input = widget(mapOf("step" to Int.MAX_VALUE))
        val changes = mutableListOf<Int>()
        runtime.environment.setSimpleVariable("amount", DoubleValue(Int.MAX_VALUE.toDouble() - 1))
        assertTrue(NumberInputRenderer.handleClick(input, runtime, 105f, 25f, { changes += it }))
        assertEquals(Int.MAX_VALUE.toDouble(), value(runtime))
        assertTrue(NumberInputRenderer.handleClick(input, runtime, 105f, 25f, { changes += it }))
        assertEquals(listOf(Int.MAX_VALUE), changes)
        runtime.environment.setSimpleVariable("amount", DoubleValue(Int.MIN_VALUE.toDouble() + 1))
        assertTrue(NumberInputRenderer.handleClick(input, runtime, 15f, 25f, { changes += it }))
        assertEquals(Int.MIN_VALUE.toDouble(), value(runtime))
        assertEquals(listOf(Int.MAX_VALUE, Int.MIN_VALUE), changes)
        assertFalse("v.amount" in runtime.environment.variable.map)
    }

    @Test
    fun `invalid bounds steps values and variable names never mutate or emit events`() {
        val runtime = MoLangRuntime()
        val invalid = listOf(mapOf("min" to 10, "max" to 1), mapOf("step" to 0), mapOf("step" to -1),
            mapOf("step" to 0.5), mapOf("max" to Double.POSITIVE_INFINITY), mapOf("min" to Double.NaN),
            mapOf("variable" to "q.amount"), mapOf("variable" to "v.a.b"))
        for (properties in invalid) {
            runtime.environment.setSimpleVariable("amount", DoubleValue(5.0))
            assertFalse(NumberInputRenderer.handleClick(widget(properties), runtime, 105f, 25f) { fail("unexpected change") })
            assertEquals(5.0, value(runtime))
        }
        for (current in listOf(Double.NaN, Double.POSITIVE_INFINITY, 1e100, 0.5)) {
            runtime.environment.setSimpleVariable("amount", DoubleValue(current))
            assertFalse(NumberInputRenderer.handleClick(widget(), runtime, 105f, 25f) { fail("unexpected change") })
        }
    }

    @Test
    fun `variable binding supplies the value when no display expression is provided`() {
        val runtime = MoLangRuntime()
        val definition = widget().definition.let { it.copy(properties = it.properties - "value") }
        val input = WidgetInstance(definition).apply { computedWidth = 100f; computedHeight = 20f }
        runtime.environment.setSimpleVariable("amount", DoubleValue(5.0))
        repeat(2) { assertTrue(NumberInputRenderer.handleClick(input, runtime, 95f, 10f) {}) }
        assertEquals(7.0, value(runtime))
    }

    @Test
    fun `out of range values clamp against both limits`() {
        val runtime = MoLangRuntime()
        val input = widget(mapOf("min" to 10, "max" to 20))
        runtime.environment.setSimpleVariable("amount", DoubleValue(100.0))
        assertTrue(NumberInputRenderer.handleClick(input, runtime, 15f, 25f) {})
        assertEquals(20.0, value(runtime))
        runtime.environment.setSimpleVariable("amount", DoubleValue(-100.0))
        assertTrue(NumberInputRenderer.handleClick(input, runtime, 105f, 25f) {})
        assertEquals(10.0, value(runtime))
    }

    @Test
    fun `narrow controls draw and hit test the same separate buttons inside their bounds`() {
        val runtime = MoLangRuntime()
        runtime.environment.setSimpleVariable("amount", DoubleValue(5.0))
        val input = widget().apply { computedWidth = 15f }
        mockkStatic(Minecraft::class)
        try {
            val client = mockk<Minecraft>(relaxed = true)
            val font = mockk<Font>(relaxed = true)
            Minecraft::class.java.getDeclaredField("font").apply { isAccessible = true }.set(client, font)
            every { font.width(any<String>()) } returns 6
            every { Minecraft.getInstance() } returns client
            val graphics = mockk<GuiGraphics>(relaxed = true)
            NumberInputRenderer.render(input, graphics, runtime, 24f, 25f, 0f)
            verify(exactly = 1) { graphics.fill(10, 20, 15, 40, any()) }
            verify(exactly = 1) { graphics.fill(20, 20, 25, 40, any()) }
            verify(exactly = 0) { graphics.drawString(any<Font>(), any<String>(), any(), any(), any(), any()) }
            input.enabled = false
            NumberInputRenderer.render(input, graphics, runtime, 24f, 25f, 0f)
            verify(exactly = 1) { graphics.fill(20, 20, 25, 40, 0xFF555555.toInt()) }
            assertFalse(NumberInputRenderer.handleClick(input, runtime, 24f, 25f) {})
            input.enabled = true
            assertFalse(NumberInputRenderer.handleClick(input, runtime, 17f, 25f) {})
            assertFalse(NumberInputRenderer.handleClick(input, runtime, 25f, 25f) {})
            assertTrue(NumberInputRenderer.handleClick(input, runtime, 24f, 25f) {})
            assertEquals(6.0, value(runtime))
            assertTrue(NumberInputRenderer.handleClick(input, runtime, 10f, 25f) {})
            assertEquals(5.0, value(runtime))
            assertFalse(NumberInputRenderer.handleClick(input, runtime, Float.NaN, 25f) {})
            input.computedWidth = 2f
            assertFalse(NumberInputRenderer.handleClick(input, runtime, 10f, 25f) {})
        } finally { unmockkStatic(Minecraft::class) }
    }

    @Test
    fun `tree change events see the updated variable and fire only once per actual change`() {
        val definition = WidgetTree.parseWidget("root", JsonParser.parseString("""{
            "type":"number_input","size":[100,20],"variable":"v.amount","value":{"molang":"v.amount"},"min":1,"max":2,
            "on_change_client":{"molang":"v.changes = v.changes + 1; v.seen = q.event.value + v.amount;"}
        }""").asJsonObject)
        val tree = MolangUiWidgetTree(ResourceLocation.fromNamespaceAndPath("cobblemon", "number_test"), UUID.randomUUID(),
            definition, emptyMap(), mapOf("amount" to 1.0, "changes" to 0.0))
        assertTrue(tree.prepare(100, 100))
        assertTrue(tree.onClick(95.0, 10.0, 0))
        assertEquals(2.0, value(tree.runtime))
        assertEquals(4.0, tree.runtime.resolve("v.seen".asExpression()).asDouble())
        assertTrue(tree.onClick(95.0, 10.0, 0))
        assertEquals(1.0, tree.runtime.resolve("v.changes".asExpression()).asDouble())
        assertFalse("event" in tree.runtime.environment.query.functions)
    }
}

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
import com.cobblemon.mod.common.api.molangui.layout.LayoutEngine
import com.cobblemon.mod.common.api.molangui.layout.SizeExpression
import com.cobblemon.mod.common.api.molangui.widget.WidgetExpansion
import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import com.cobblemon.mod.common.client.gui.molangui.MolangUiWidgetTree
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolve
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class MolangUiLayoutTest {
    private fun definition(json: String) = WidgetTree.parseWidget("root", JsonParser.parseString(json).asJsonObject)

    @Test
    fun `size literals reject implicit code unsupported auto and float overflow`() {
        for (value in listOf("v.width", "v.changed = 1;", "auto", "NaN", "Infinity", "1e100", "9".repeat(100) + "%", true)) {
            assertThrows(IllegalArgumentException::class.java) { SizeExpression.parse(value) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            definition("""{"size":["v.width",20]}""")
        }
        val runtime = MoLangRuntime()
        assertEquals(90f, SizeExpression.parse("50% - 10").resolve(200f, runtime))
        assertEquals(-10f, SizeExpression.parse(-10).resolve(200f, runtime))
    }

    @Test
    fun `invalid dynamic layout prevents clicks and recovers when the expression becomes valid`() {
        val tree = MolangUiWidgetTree(ResourceLocation.fromNamespaceAndPath("cobblemon", "layout_test"), UUID.randomUUID(),
            definition("""{"type":"button","size":[{"molang":"v.width"},20],
                "on_click_client":{"molang":"v.clicked = v.clicked + 1;"}}"""),
            emptyMap(), mapOf("width" to 30.0, "clicked" to 0.0))
        assertTrue(tree.prepare(200, 100))
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, 1e100, -1.0, Int.MAX_VALUE.toDouble())) {
            tree.runtime.environment.setSimpleVariable("width", DoubleValue(value))
            assertFalse(tree.prepare(200, 100))
            assertFalse(tree.onClick(10.0, 10.0, 0))
        }
        tree.updateVariables(mapOf("width" to 30.0))
        assertTrue(tree.prepare(200, 100))
        assertTrue(tree.onClick(10.0, 10.0, 0))
        assertEquals(1.0, tree.runtime.resolve("v.clicked".asExpression()).asDouble())
    }

    @Test
    fun `nested and stacked coordinates cannot overflow integer draw endpoints`() {
        val runtime = MoLangRuntime()
        for (json in listOf(
            """{"size":[100,100],"offset":[2000000000,0],"controls":[{"child":{"size":[100,20],"offset":[2000000000,0]}}]}""",
            """{"size":[100,100],"layout":"stack_horizontal","controls":[{"a":{"size":[2000000000,20]}},{"b":{"size":[2000000000,20]}}]}""",
            """{"size":[100,100],"controls":[{"child":{"size":[{"molang":"v.invalid"},20]}}]}"""
        )) {
            runtime.environment.setSimpleVariable("invalid", DoubleValue(Double.NaN))
            val root = WidgetExpansion.refresh(definition(json), runtime)
            assertThrows(IllegalArgumentException::class.java) { LayoutEngine.layout(root, 200f, 100f, runtime) }
        }
    }

    @Test
    fun `fill occupies available space and exhausted grid or padded content has zero width`() {
        val runtime = MoLangRuntime()
        val root = WidgetExpansion.refresh(definition("""{"size":["fill","fill"],"layout":"stack_horizontal","spacing":10,
            "controls":[{"fixed":{"size":[30,20]}},{"flex":{"size":["fill",20]}}]}"""), runtime)
        LayoutEngine.layout(root, 200f, 100f, runtime)
        assertEquals(200f, root.computedWidth)
        assertEquals(160f, root.children[1].computedWidth)
        assertEquals(40f, root.children[1].absoluteX)
        for (extra in listOf("\"padding\":[0,60,0,60]", "\"layout\":\"grid\",\"columns\":2,\"spacing\":200")) {
            val exhausted = WidgetExpansion.refresh(definition("""{"size":[100,20],$extra,
                "controls":[{"child":{"size":["fill",20]}}]}"""), runtime)
            LayoutEngine.layout(exhausted, 200f, 100f, runtime)
            assertEquals(0f, exhausted.children.single().computedWidth)
        }
    }

    @Test
    fun `padding expressions retain row context and reject negative or nonfinite values`() {
        val runtime = MoLangRuntime()
        val root = WidgetExpansion.refresh(definition("""{"type":"list","size":[100,100],"source_type":"count","source":2,
            "layout":"stack_vertical","item_template":{"size":[100,30],"padding":[0,0,0,{"molang":"q.item.index * 10 + v.padding"}],
                "controls":[{"child":{"size":["100%",20]}}]}}"""), runtime)
        runtime.environment.setSimpleVariable("padding", DoubleValue(5.0))
        LayoutEngine.layout(root, 200f, 100f, runtime)
        assertEquals(5f, root.children[0].children.single().absoluteX)
        assertEquals(15f, root.children[1].children.single().absoluteX)
        assertEquals(85f, root.children[1].children.single().computedWidth)
        assertFalse("item" in runtime.environment.query.functions)
        for (value in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            runtime.environment.setSimpleVariable("padding", DoubleValue(value))
            assertThrows(IllegalArgumentException::class.java) { LayoutEngine.layout(root, 200f, 100f, runtime) }
        }
        for (padding in listOf("[1,2]", "[0,-1,0,0]", "[0,0,0,\"v.padding\"]")) {
            assertThrows(IllegalArgumentException::class.java) { definition("""{"padding":$padding}""") }
        }
    }

    @Test
    fun `nonfinite spacing rejects layout but hidden descendants do not evaluate geometry`() {
        val runtime = MoLangRuntime()
        runtime.environment.setSimpleVariable("invalid", DoubleValue(Double.NaN))
        val root = WidgetExpansion.refresh(definition("""{"size":[100,100],"spacing":{"molang":"v.spacing"},
            "controls":[{"hidden":{"size":[{"molang":"v.invalid"},20]}}]}"""), runtime)
        root.children.single().visible = false
        runtime.environment.setSimpleVariable("spacing", DoubleValue(Double.NaN))
        assertThrows(IllegalArgumentException::class.java) { LayoutEngine.layout(root, 200f, 100f, runtime) }
        runtime.environment.setSimpleVariable("spacing", DoubleValue(0.0))
        LayoutEngine.layout(root, 200f, 100f, runtime)
        runtime.environment.setSimpleVariable("invalid", DoubleValue(Double.NaN))
        root.visible = false
        LayoutEngine.layout(root, 200f, 100f, runtime)
    }
}

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui

import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolve
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class MolangUiScrollTest {
    private fun tree(json: String, variables: Map<String, Double> = emptyMap()) = MolangUiWidgetTree(
        ResourceLocation.fromNamespaceAndPath("cobblemon", "scroll_test"), UUID.randomUUID(),
        WidgetTree.parseWidget("root", JsonParser.parseString(json).asJsonObject), emptyMap(), variables
    ).also { assertTrue(it.prepare(200, 100)) }

    @Test
    fun `leading space remains scrollable and wheel events and clicks use the moved content`() {
        val tree = tree("""{"type":"scroll_panel","size":[100,20],
            "on_scroll_client":{"molang":"v.amount = q.event.amount; v.events = v.events + 1;"},
            "controls":[{"button":{"type":"button","offset":[0,100],"size":[100,20],
                "on_click_client":{"molang":"v.clicked = 1;"}}}]}""")
        assertTrue(tree.onScroll(5.0, 5.0, -Double.MAX_VALUE))
        assertEquals(-100f, tree.root.scrollOffset)
        assertEquals(-Double.MAX_VALUE, tree.runtime.resolve("v.amount".asExpression()).asDouble())
        assertEquals(1.0, tree.runtime.resolve("v.events".asExpression()).asDouble())
        assertTrue(tree.onClick(5.0, 5.0, 0))
        assertEquals(1.0, tree.runtime.resolve("v.clicked".asExpression()).asDouble())
        assertFalse(tree.onScroll(5.0, 5.0, Double.NaN))
        assertEquals(-100f, tree.root.scrollOffset)
    }

    @Test
    fun `hidden content stops extending the range and invalid offsets recover`() {
        val tree = tree("""{"type":"scroll_panel","size":[100,20],"controls":[
            {"far":{"offset":[0,100],"size":[100,20],"visible":{"molang":"v.show"}}},
            {"near":{"size":[100,20]}}]}""", mapOf("show" to 1.0))
        assertTrue(tree.onScroll(5.0, 5.0, -100.0))
        assertEquals(-100f, tree.root.scrollOffset)
        tree.updateVariables(mapOf("show" to 0.0))
        assertTrue(tree.prepare(200, 100))
        assertEquals(0f, tree.root.scrollOffset)
        tree.root.scrollOffset = Float.NaN
        assertTrue(tree.prepare(200, 100))
        assertEquals(0f, tree.root.scrollOffset)
    }

    @Test
    fun `removing list rows clamps the current scroll without another wheel event`() {
        val tree = tree("""{"type":"scroll_panel","size":[100,20],"controls":[{"rows":{
            "type":"list","layout":"stack_vertical","source_type":"count","source":{"molang":"v.count"},
            "item_template":{"size":[100,20]}}}]}""", mapOf("count" to 3.0))
        assertTrue(tree.onScroll(5.0, 5.0, -100.0))
        assertEquals(-40f, tree.root.scrollOffset)
        tree.updateVariables(mapOf("count" to 1.0))
        assertTrue(tree.prepare(200, 100))
        assertEquals(0f, tree.root.scrollOffset)
    }

    @Test
    fun `outer scrolling includes container bounds but stops at nested scroll viewports`() {
        val panel = tree("""{"type":"scroll_panel","size":[100,20],"controls":[{"panel":{"size":[100,100],
            "controls":[{"small":{"size":[100,10]}}]}}]}""")
        assertTrue(panel.onScroll(5.0, 5.0, -100.0))
        assertEquals(-80f, panel.root.scrollOffset)

        val tree = tree("""{"type":"scroll_panel","size":[100,40],"controls":[{"inner":{
            "type":"scroll_panel","offset":[0,30],"size":[100,20],"controls":[{"far":{
                "type":"button","offset":[0,1000],"size":[100,20],"on_click_client":{"molang":"v.clicked = 1;"}}}]}}]}""")
        assertTrue(tree.onScroll(5.0, 5.0, -1000.0))
        assertEquals(-10f, tree.root.scrollOffset)
        val inner = tree.root.children.single()
        assertTrue(tree.onScroll(5.0, 25.0, -1000.0))
        assertEquals(-1000f, inner.scrollOffset)
        assertEquals(-10f, tree.root.scrollOffset)
        assertTrue(tree.onClick(5.0, 25.0, 0))
        assertEquals(1.0, tree.runtime.resolve("v.clicked".asExpression()).asDouble())
    }
}

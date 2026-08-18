/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.cobblemon.mod.common.api.molangui.container.ShiftClickRule
import com.cobblemon.mod.common.api.molangui.template.TemplateDefinition
import com.cobblemon.mod.common.api.molangui.template.TemplateInput
import com.cobblemon.mod.common.api.molangui.template.TemplateInputType
import com.cobblemon.mod.common.api.molangui.template.TemplateResolver
import com.cobblemon.mod.common.api.molangui.widget.WidgetDefinition
import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import com.cobblemon.mod.common.api.molangui.widget.WidgetType
import com.cobblemon.mod.common.util.asExpressionLike
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MolangUiCodecTest {
    private val id = ResourceLocation.fromNamespaceAndPath("cobblemon", "test")

    @Test
    fun `view codec reads snake-case blocks_input and explicit expressions`() {
        val view = MolangUiRegistry.parse(id, json("""
            {"format_version":1,"contract_version":"1.2.3","mode":"screen","blocks_input":true,
             "parameters":{"title":"string"},"actions":[],"root":{"type":"panel","text":"q.title",
             "visible":{"molang":"v.enabled"}}}
        """))
        assertTrue(view.blocksInput)
        assertEquals("q.title", view.root.properties.getValue("text").raw)
        assertTrue(view.root.properties.getValue("text").isStatic)
        assertNotNull(view.root.properties.getValue("visible").expression)
    }

    @Test
    fun `view codec rejects unknown widgets duplicate ids and inferred event scripts`() {
        assertThrows(IllegalArgumentException::class.java) { MolangUiRegistry.parse(id, viewWithRoot("""{"type":"unknown"}""")) }
        assertThrows(IllegalArgumentException::class.java) { MolangUiRegistry.parse(id, viewWithRoot("""
            {"type":"panel","controls":[{"same":{"type":"label"}},{"same":{"type":"label"}}]}
        """)) }
        assertThrows(IllegalArgumentException::class.java) { MolangUiRegistry.parse(id, viewWithRoot("""
            {"type":"button","on_click_client":"v.x = 1"}
        """)) }
        assertThrows(IllegalArgumentException::class.java) { MolangUiRegistry.parse(id, viewWithRoot("""
            {"type":"button","on_click":{"action":"undeclared"}}
        """)) }
        assertThrows(IllegalArgumentException::class.java) { MolangUiRegistry.parse(id, viewWithRoot("""
            {"type":"slot_grid","grid_dimensions":[1,1]}
        """)) }
    }

    @Test
    fun `contract codec rejects version type and collection inconsistencies`() {
        assertThrows(IllegalArgumentException::class.java) { MolangUiContractRegistry.parse(id, json("""
            {"format_version":2,"contract_version":"1","mode":"screen"}
        """)) }
        assertThrows(IllegalArgumentException::class.java) { MolangUiContractRegistry.parse(id, json("""
            {"format_version":1,"contract_version":"1","mode":"screen","parameters":{"x":{"type":"wat"}}}
        """)) }
        assertThrows(IllegalArgumentException::class.java) { MolangUiContractRegistry.parse(id, json("""
            {"format_version":1,"contract_version":"1","mode":"screen","collections":{"x":{"size":2,
             "layout":{"grid_dimensions":[1,1]}}}}
        """)) }
    }

    @Test
    fun `contract codec preserves literal versus expression defaults`() {
        val contract = MolangUiContractRegistry.parse(id, json("""
            {"format_version":1,"contract_version":"1","mode":"overlay","parameters":{
              "literal":{"type":"string","default":"v.quantity + 1"},
              "expression":{"type":"number","default":{"molang":"v.quantity + 1"}}
            }}
        """))
        assertEquals(ParameterValue.StringValue("v.quantity + 1"), (contract.parameters.getValue("literal").default as ParameterDefault.Literal).value)
        assertEquals("v.quantity + 1", (contract.parameters.getValue("expression").default as ParameterDefault.Molang).expression)
    }

    @Test
    fun `templates substitute typed JSON values without text injection`() {
        val body = json("""{"inputs":{},"type":"label","text":"${'$'}text","metadata":"${'$'}metadata"}""")
        val template = TemplateDefinition("label", "", mapOf(
            "text" to TemplateInput(TemplateInputType.STRING),
            "metadata" to TemplateInput(TemplateInputType.OBJECT)
        ), null, body)
        val root = json("""
            {"type":"panel","controls":[{"child@label":{"text":"\"}; malicious() //","metadata":{"nested":{"safe":true}}}}]}
        """)
        val resolved = TemplateResolver.resolve(root, mapOf("label" to template))
        val child = resolved.getAsJsonArray("controls")[0].asJsonObject.getAsJsonObject("child")
        assertEquals("\"}; malicious() //", child.get("text").asString)
        assertTrue(child.getAsJsonObject("metadata").getAsJsonObject("nested").get("safe").asBoolean)
    }

    @Test
    fun `templates report missing references and inheritance cycles`() {
        assertThrows(IllegalArgumentException::class.java) {
            TemplateResolver.resolve(json("""{"type":"panel","controls":[{"x@missing":{}}]}"""), emptyMap())
        }
        val a = TemplateDefinition("a", "", emptyMap(), "b", json("""{"type":"panel"}"""))
        val b = TemplateDefinition("b", "", emptyMap(), "a", json("""{"type":"panel"}"""))
        assertThrows(IllegalArgumentException::class.java) {
            TemplateResolver.resolve(json("""{"type":"panel","controls":[{"x@a":{}}]}"""), mapOf("a" to a, "b" to b))
        }
    }

    @Test
    fun `tree limits depth and node count`() {
        var nested = "{\"type\":\"panel\"}"
        repeat(WidgetTree.MAX_DEPTH) { index -> nested = "{\"type\":\"panel\",\"controls\":[{\"n$index\":$nested}]}" }
        assertThrows(IllegalArgumentException::class.java) { MolangUiRegistry.parse(id, viewWithRoot(nested)) }

        val controls = (0..WidgetTree.MAX_NODES).joinToString(",") { index ->
            "{\"n$index\":{\"type\":\"label\"}}"
        }
        assertThrows(IllegalArgumentException::class.java) {
            MolangUiRegistry.parse(id, viewWithRoot("{\"type\":\"panel\",\"controls\":[$controls]}"))
        }
    }

    @Test
    fun `rate limiter allows burst then refills at twenty per second`() {
        val start = 10_000_000_000L
        val limiter = MolangUiRateLimiter(start)
        repeat(40) { assertTrue(limiter.tryAcquire(start)) }
        assertFalse(limiter.tryAcquire(start))
        repeat(20) { assertTrue(limiter.tryAcquire(start + 1_000_000_000L)) }
        assertFalse(limiter.tryAcquire(start + 1_000_000_000L))
    }

    @Test
    fun `action registry rejects duplicates and supports explicit unregister`() {
        val key = ActionKey("test_registration")
        MolangUiActionRegistry.unregister(key)
        val handler = MolangUiActionHandler { }
        MolangUiActionRegistry.register(key, handler)
        try {
            assertThrows(IllegalStateException::class.java) { MolangUiActionRegistry.register(key, handler) }
        } finally {
            assertTrue(MolangUiActionRegistry.unregister(key))
        }
        assertFalse(MolangUiActionRegistry.unregister(key))
    }

    @Test
    fun `shipped contracts and views stay in sync`() {
        listOf("donation_box", "example_furnace", "test_overlay", "vending_machine").forEach { name ->
            val contract = resource("/data/cobblemon/molang_ui/$name.json")
            val view = MolangUiRegistry.parse(
                ResourceLocation.fromNamespaceAndPath("cobblemon", name),
                resource("/assets/cobblemon/molang_ui/$name.json")
            )

            assertEquals(contract.get("contract_version").asString, view.contractVersion, name)
            assertEquals(GuiMode.fromString(contract.get("mode").asString), view.mode, name)

            val declared = contract.getAsJsonObject("actions")?.keySet().orEmpty()
            view.actions.forEach { assertTrue(it.value in declared, "$name view uses undeclared action '${it.value}'") }

            val collections = contract.getAsJsonObject("collections")
            slotGrids(view.root).forEach { grid ->
                val collectionName = grid.getStringProperty("collection_name")!!
                if (collectionName in ShiftClickRule.PLAYER_SIDE_NAMES) return@forEach
                val layout = collections?.getAsJsonObject(collectionName)?.getAsJsonObject("layout")
                assertNotNull(layout, "$name view binds unknown collection '$collectionName'")
                assertEquals(layout!!.ints("grid_dimensions"), grid.ints("grid_dimensions"), "$name.$collectionName dimensions")
                assertEquals(layout.ints("offset"), grid.ints("offset"), "$name.$collectionName offset")
            }
        }
    }

    @Test
    fun `shipped ui action scripts parse`() {
        listOf(
            "donation_box/donation_box_donate", "donation_box/donation_box_close",
            "example_furnace/example_furnace_smelt", "vending_machine/vending_machine_buy"
        ).forEach { path ->
            val source = javaClass.getResourceAsStream("/data/cobblemon/molang/ui/$path.molang")
                .let { requireNotNull(it) { "missing script $path" } }
                .reader().use { it.readText() }
            assertNotNull(source.asExpressionLike(), path)
        }
    }

    private fun slotGrids(node: WidgetDefinition): List<WidgetDefinition> =
        (if (node.type == WidgetType.SLOT_GRID) listOf(node) else emptyList()) + node.children.flatMap(::slotGrids)

    private fun JsonObject.ints(name: String) = getAsJsonArray(name).map { it.asInt }

    private fun WidgetDefinition.ints(name: String) = (getProperty(name)!!.raw as List<*>).map { (it as Number).toInt() }

    private fun resource(path: String) = javaClass.getResourceAsStream(path)
        .let { requireNotNull(it) { "missing resource $path" } }
        .reader().use { JsonParser.parseReader(it).asJsonObject }

    private fun viewWithRoot(root: String) = json("""
        {"format_version":1,"contract_version":"1","mode":"screen","root":$root}
    """)

    private fun json(value: String) = JsonParser.parseString(value.trimIndent()).asJsonObject
}

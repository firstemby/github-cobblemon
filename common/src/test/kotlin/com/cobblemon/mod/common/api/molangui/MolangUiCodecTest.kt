/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.cobblemon.mod.common.api.molangui.template.TemplateDefinition
import com.cobblemon.mod.common.api.molangui.template.TemplateInput
import com.cobblemon.mod.common.api.molangui.template.TemplateInputType
import com.cobblemon.mod.common.api.molangui.template.TemplateResolver
import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class MolangUiCodecTest {
    private val id = ResourceLocation.fromNamespaceAndPath("cobblemon", "test")

    @Test
    fun `view codec reads mode and explicit expressions`() {
        val view = MolangUiRegistry.parse(id, json("""
            {"format_version":1,"contract_version":"1.2.3","mode":"screen",
             "parameters":{"title":"string"},"actions":[],"root":{"type":"panel","text":"q.title",
             "visible":{"molang":"v.enabled"}}}
        """))
        assertEquals(GuiMode.SCREEN, view.mode)
        assertEquals("q.title", view.root.properties.getValue("text").raw)
        assertNull(view.root.properties.getValue("text").expression)
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
    fun `bundled examples are executable contract fixtures`() {
        listOf("donation_box", "example_furnace", "test_overlay", "battle_intro").forEach { name ->
            val resourceId = ResourceLocation.fromNamespaceAndPath("cobblemon", name)
            val view = MolangUiRegistry.parse(
                resourceId,
                resourceJson("assets/cobblemon/molang_ui/$name.json")
            )
            val contract = MolangUiContractRegistry.parse(
                resourceId,
                resourceJson("data/cobblemon/molang_ui/$name.json")
            )

            assertEquals(contract.contractVersion, view.contractVersion, name)
            assertEquals(contract.mode, view.mode, name)
            assertEquals(contract.parameters.keys, view.parameters.keys, name)
            assertEquals(contract.actions.keys, view.actions, name)
        }
    }

    private fun viewWithRoot(root: String) = json("""
        {"format_version":1,"contract_version":"1","mode":"screen","root":$root}
    """)

    @Test
    fun `format versions cannot be truncated or coerced by either codec`() {
        listOf("1.5", "4294967297", "1e100", "\"1\"", "true", "null").forEach { version ->
            val source = json("""{"format_version":$version,"contract_version":"1","mode":"screen","root":{"type":"panel"}}""")
            assertThrows(IllegalArgumentException::class.java, { MolangUiRegistry.parse(id, source) }, version)
            assertThrows(IllegalArgumentException::class.java, { MolangUiContractRegistry.parse(id, source) }, version)
        }
        listOf("1", "1.0", "1e0").forEach { version ->
            val source = json("""{"format_version":$version,"contract_version":"1","mode":"screen","root":{"type":"panel"}}""")
            assertEquals(1, MolangUiRegistry.parse(id, source).formatVersion)
            assertEquals(1, MolangUiContractRegistry.parse(id, source).formatVersion)
        }
    }

    @Test
    fun `contract defaults enforce declared JSON types and finite components`() {
        val invalid = mapOf(
            "string" to listOf("12", "true", "null", "[]"),
            "number" to listOf("\"12\"", "true", "null", "1e400"),
            "boolean" to listOf("\"false\"", "0", "null"),
            "vec3" to listOf("[0,\"1\",2]", "[0,true,2]", "[0,1e400,2]", "[0,1]", "null")
        )
        invalid.forEach { (type, values) ->
            values.forEach { value ->
                val source = json("""{"format_version":1,"contract_version":"1","mode":"screen",
                    "parameters":{"value":{"type":"$type","default":$value}}}""")
                assertThrows(IllegalArgumentException::class.java, { MolangUiContractRegistry.parse(id, source) }, "$type: $value")
            }
        }
        val source = json("""{"format_version":1,"contract_version":"1","mode":"screen","parameters":{
            "text":{"type":"string","default":"0012"},
            "number":{"type":"number","default":1.25},
            "flag":{"type":"boolean","default":false},
            "position":{"type":"vec3","default":[-1,2.5,3]}}}""")
        val defaults = MolangUiContractRegistry.parse(id, source).parameters.mapValues {
            (it.value.default as ParameterDefault.Literal).value
        }
        assertEquals(mapOf(
            "text" to ParameterValue.StringValue("0012"),
            "number" to ParameterValue.NumberValue(1.25),
            "flag" to ParameterValue.BooleanValue(false),
            "position" to ParameterValue.Vec3Value(-1.0, 2.5, 3.0)
        ), defaults)
        val text = source.getAsJsonObject("parameters").getAsJsonObject("text")
        text.addProperty("default", "a".repeat(MolangUiPacketLimits.MAX_VALUE_LENGTH))
        MolangUiContractRegistry.parse(id, source)
        text.addProperty("default", "a".repeat(MolangUiPacketLimits.MAX_VALUE_LENGTH + 1))
        assertThrows(IllegalArgumentException::class.java) { MolangUiContractRegistry.parse(id, source) }
    }

    @Test
    fun `both codecs reject unsupported or malformed flags and blank contract versions`() {
        listOf("blocks_input", "exclusive").forEach { flag ->
            val values = listOf("\"true\"", "\"false\"", "1", "null") +
                if (flag == "blocks_input") listOf("true", "false") else emptyList()
            values.forEach { value ->
                val source = viewWithRoot("""{"type":"panel"}""")
                source.add(flag, JsonParser.parseString(value))
                assertThrows(IllegalArgumentException::class.java) { MolangUiRegistry.parse(id, source) }
                assertThrows(IllegalArgumentException::class.java) { MolangUiContractRegistry.parse(id, source) }
            }
        }
        val source = viewWithRoot("""{"type":"panel"}""")
        source.addProperty("contract_version", " ")
        assertThrows(IllegalArgumentException::class.java) { MolangUiRegistry.parse(id, source) }
        assertThrows(IllegalArgumentException::class.java) { MolangUiContractRegistry.parse(id, source) }
        source.addProperty("contract_version", "1")
        source.add("parameters", json("""{"x":{"type":"number","required":"false"}}"""))
        assertThrows(IllegalArgumentException::class.java) { MolangUiContractRegistry.parse(id, source) }
    }

    @Test
    fun `parameter declarations must fit opening packet capacity`() {
        val view = viewWithRoot("""{"type":"panel"}""")
        val contract = view.deepCopy()
        val viewParameters = JsonObject()
        val contractParameters = JsonObject()
        repeat(MolangUiPacketLimits.MAX_ACTION_FIELDS) { index ->
            viewParameters.addProperty("p$index", "number")
            contractParameters.add("p$index", json("""{"type":"number"}"""))
        }
        view.add("parameters", viewParameters)
        contract.add("parameters", contractParameters)
        assertEquals(MolangUiPacketLimits.MAX_ACTION_FIELDS, MolangUiRegistry.parse(id, view).parameters.size)
        assertEquals(MolangUiPacketLimits.MAX_ACTION_FIELDS, MolangUiContractRegistry.parse(id, contract).parameters.size)
        viewParameters.addProperty("extra", "number")
        contractParameters.add("extra", json("""{"type":"number"}"""))
        assertThrows(IllegalArgumentException::class.java) { MolangUiRegistry.parse(id, view) }
        assertThrows(IllegalArgumentException::class.java) { MolangUiContractRegistry.parse(id, contract) }
    }

    @Test
    fun `view variables must be finite JSON numbers`() {
        val source = viewWithRoot("""{"type":"panel"}""")
        listOf("\"12\"", "\"NaN\"", "true", "null", "1e400").forEach { value ->
            source.add("variables", json("""{"x":$value}"""))
            assertThrows(IllegalArgumentException::class.java) { MolangUiRegistry.parse(id, source) }
        }
        source.add("variables", json("""{"x":-1.25}"""))
        assertEquals(-1.25, MolangUiRegistry.parse(id, source).variables.getValue("x"))
    }

    @Test
    fun `explicit expressions require strings in defaults properties geometry and events`() {
        listOf("12", "true", "null", "[]").forEach { value ->
            val contract = json("""{"format_version":1,"contract_version":"1","mode":"screen",
                "parameters":{"x":{"type":"number","default":{"molang":$value}}}}""")
            assertThrows(IllegalArgumentException::class.java) { MolangUiContractRegistry.parse(id, contract) }
            listOf(
                """{"type":"label","text":{"molang":$value}}""",
                """{"type":"panel","size":[{"molang":$value},20]}""",
                """{"type":"button","on_click_client":{"molang":$value}}"""
            ).forEach { root ->
                assertThrows(IllegalArgumentException::class.java) { MolangUiRegistry.parse(id, viewWithRoot(root)) }
            }
        }
    }

    @Test
    fun `slot grid dimensions cannot silently truncate fractional values`() {
        listOf("[1.5,1]", "[1,2.1]", "[1e100,1]", "[\"1\",1]", "[0,1]", "[17,1]").forEach { size ->
            assertThrows(IllegalArgumentException::class.java) {
                MolangUiRegistry.parse(id, viewWithRoot("""{"type":"slot_grid","collection_name":"items","grid_dimensions":$size}"""))
            }
        }
        MolangUiRegistry.parse(id, viewWithRoot("""{"type":"slot_grid","collection_name":"items","grid_dimensions":[16,16]}"""))
    }

    @Test
    fun `unsupported widget declarations fail validation while hover remains supported`() {
        for (type in listOf("toggle", "slider", "text_input", "move_display")) {
            assertThrows(IllegalArgumentException::class.java) {
                MolangUiRegistry.parse(id, viewWithRoot("""{"type":"$type"}"""))
            }
        }
        for (property in listOf("transition", "enter", "exit", "static")) {
            assertThrows(IllegalArgumentException::class.java) {
                MolangUiRegistry.parse(id, viewWithRoot("""{"type":"panel","$property":{}}"""))
            }
        }
        val view = MolangUiRegistry.parse(id, viewWithRoot("""{"type":"button","hover":{"color":"0xFF777777"}}"""))
        assertTrue("color" in view.root.hoverOverrides)
    }

    @Test
    fun `unused template import declarations are rejected`() {
        val view = viewWithRoot("""{"type":"panel"}""").apply { add("imports", JsonParser.parseString("[]")) }
        assertThrows(IllegalArgumentException::class.java) { MolangUiRegistry.parse(id, view) }
    }

    private fun json(value: String) = JsonParser.parseString(value.trimIndent()).asJsonObject

    private fun resourceJson(path: String) = MolangUiCodecTest::class.java.classLoader
        .getResourceAsStream(path)
        ?.bufferedReader()
        ?.use { JsonParser.parseReader(it).asJsonObject }
        ?: error("Missing test fixture resource: $path")
}

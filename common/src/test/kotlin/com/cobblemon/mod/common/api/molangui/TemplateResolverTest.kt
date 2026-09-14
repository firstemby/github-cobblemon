package com.cobblemon.mod.common.api.molangui

import com.cobblemon.mod.common.api.molangui.template.TemplateDefinition
import com.cobblemon.mod.common.api.molangui.template.TemplateInput
import com.cobblemon.mod.common.api.molangui.template.TemplateInputType
import com.cobblemon.mod.common.api.molangui.template.TemplateResolver
import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TemplateResolverTest {
    @Test
    fun `recursive controls and list item templates are rejected before expansion`() {
        for (body in listOf(
            """{"controls":[{"again@loop":{}}]}""",
            """{"type":"list","item_template":{"controls":[{"again@loop":{}}]}}"""
        )) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                TemplateResolver.resolve(root("loop"), mapOf("loop" to template("loop", body)))
            }
            assertTrue(error.message!!.contains("cycle"))
        }
    }

    @Test
    fun `siblings may use the same template without sharing mutable JSON`() {
        val source = json("""{"controls":[{"first@label":{}},{"second@label":{}}]}""")
        val body = """{"type":"label","metadata":{"inputs":{"a":1},"extends":"literal"}}"""
        val definition = template("label", body)
        val result = TemplateResolver.resolve(source, mapOf("label" to definition))
        val first = result.getAsJsonArray("controls")[0].asJsonObject.getAsJsonObject("first")
        val second = result.getAsJsonArray("controls")[1].asJsonObject.getAsJsonObject("second")
        first.getAsJsonObject("metadata").addProperty("extends", "changed")
        assertEquals("literal", second.getAsJsonObject("metadata").get("extends").asString)
        assertEquals(1, second.getAsJsonObject("metadata").getAsJsonObject("inputs").get("a").asInt)
        assertEquals(json(body), definition.body)
        assertTrue(source.getAsJsonArray("controls")[0].asJsonObject.has("first@label"))
    }

    @Test
    fun `inheritance preserves parent inputs and applies child defaults and overrides`() {
        val parent = template("parent", """{"type":"label","text":"${'$'}text","color":"${'$'}color"}""")
            .copy(inputs = mapOf(
                "text" to TemplateInput(TemplateInputType.STRING),
                "color" to TemplateInput(TemplateInputType.STRING, JsonPrimitive("white"))
            ))
        val child = template("child", """{"shadow":true}""").copy(
            extends = "parent",
            inputs = mapOf("color" to TemplateInput(TemplateInputType.STRING, JsonPrimitive("blue")))
        )
        val result = TemplateResolver.resolve(
            json("""{"controls":[{"instance@child":{"text":"hello"}}]}"""),
            mapOf("parent" to parent, "child" to child)
        ).getAsJsonArray("controls")[0].asJsonObject.getAsJsonObject("instance")
        assertEquals("hello", result.get("text").asString)
        assertEquals("blue", result.get("color").asString)
        assertTrue(result.get("shadow").asBoolean)
        assertFalse(result.has("inputs"))
    }

    @Test
    fun `acyclic exponential expansion shares a widget budget`() {
        val templates = (0..12).associate { index ->
            val body = if (index == 12) """{"type":"label"}"""
            else """{"controls":[{"left@t${index + 1}":{}},{"right@t${index + 1}":{}}]}"""
            "t$index" to template("t$index", body)
        }
        val error = assertThrows(IllegalArgumentException::class.java) { TemplateResolver.resolve(root("t0"), templates) }
        assertTrue(error.message!!.contains("widget count"))
    }

    @Test
    fun `deep inheritance and ordinary widget nesting are rejected`() {
        val templates = (0..WidgetTree.MAX_DEPTH).associate { index ->
            "t$index" to template("t$index", "{}").copy(extends = if (index < WidgetTree.MAX_DEPTH) "t${index + 1}" else null)
        }
        assertThrows(IllegalArgumentException::class.java) { TemplateResolver.resolve(root("t0"), templates) }
        var node = JsonObject()
        repeat(WidgetTree.MAX_DEPTH) { index ->
            node = JsonObject().apply {
                add("controls", JsonArray().apply { add(JsonObject().apply { add("n$index", node) }) })
            }
        }
        assertThrows(IllegalArgumentException::class.java) { TemplateResolver.resolve(node, emptyMap()) }
    }

    @Test
    fun `deep metadata and excessive JSON copies are bounded`() {
        var metadata = JsonObject()
        repeat(140) { metadata = JsonObject().apply { add("nested", metadata) } }
        val deep = JsonObject().apply { add("metadata", metadata) }
        assertThrows(IllegalArgumentException::class.java) { TemplateResolver.resolve(deep, emptyMap()) }

        val shared = JsonArray().apply { repeat(1024) { add(it) } }
        val many = JsonObject().apply { add("metadata", JsonArray().apply { repeat(1024) { add(shared) } }) }
        assertThrows(IllegalArgumentException::class.java) { TemplateResolver.resolve(many, emptyMap()) }
    }

    @Test
    fun `template inputs reject coerced expressions and nonfinite numbers`() {
        assertFalse(TemplateInputType.EXPRESSION.accepts(json("""{"molang":true}""")))
        assertFalse(TemplateInputType.EXPRESSION.accepts(json("""{"molang":12}""")))
        assertTrue(TemplateInputType.EXPRESSION.accepts(json("""{"molang":"v.count"}""")))
        assertFalse(TemplateInputType.NUMBER.accepts(JsonPrimitive(Double.POSITIVE_INFINITY)))
        assertThrows(IllegalArgumentException::class.java) {
            WidgetTree.parse(json("""{"templates":{"bad":{"inputs":{"value":{"type":"expression","default":{"molang":1}}}}},"root":{}}"""))
        }
    }

    private fun template(name: String, body: String) = TemplateDefinition(name, "", body = json(body))
    private fun root(template: String) = json("""{"controls":[{"instance@$template":{}}]}""")
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
}

package com.cobblemon.mod.common.api.molangui

import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.client.gui.molangui.MolangUiClientRuntimeFactory
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolve
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class MolangUiParametersTest {
    private val id = ResourceLocation.fromNamespaceAndPath("cobblemon", "parameters")

    @Test
    fun `client queries preserve declared types through updates`() {
        val types = mapOf("code" to ParameterType.STRING, "amount" to ParameterType.NUMBER,
            "enabled" to ParameterType.BOOLEAN, "position" to ParameterType.VEC3)
        val parameters = MolangUiParameters.decode(types,
            mapOf("code" to "001", "amount" to "12.5", "enabled" to "true", "position" to "1,2,3"))
        val runtime = MolangUiClientRuntimeFactory.create(id, parameters, emptyMap())
        assertInstanceOf(StringValue::class.java, runtime.resolve("q.code".asExpression()))
        assertEquals("001", runtime.resolve("q.code".asExpression()).asString())
        assertInstanceOf(DoubleValue::class.java, runtime.resolve("q.amount".asExpression()))
        assertEquals(12.5, runtime.resolve("q.amount".asExpression()).asDouble())
        assertEquals(1.0, runtime.resolve("q.enabled".asExpression()).asDouble())
        assertEquals("1.0,2.0,3.0", runtime.resolve("q.position".asExpression()).asString())
        MolangUiClientRuntimeFactory.updateParameters(runtime,
            MolangUiParameters.decode(types, mapOf("code" to "NaN", "enabled" to "false")))
        assertEquals("NaN", runtime.resolve("q.code".asExpression()).asString())
        assertEquals(0.0, runtime.resolve("q.enabled".asExpression()).asDouble())
    }

    @Test
    fun `invalid parameters cannot overwrite runtime queries or partially update values`() {
        val runtime = MolangUiClientRuntimeFactory.create(id,
            mapOf("code" to ParameterValue.StringValue("original")), emptyMap())
        assertThrows(IllegalArgumentException::class.java) {
            MolangUiClientRuntimeFactory.updateParameters(runtime, linkedMapOf(
                "code" to ParameterValue.StringValue("changed"), "gui_id" to ParameterValue.StringValue("wrong")))
        }
        assertEquals("original", runtime.resolve("q.code".asExpression()).asString())
        assertEquals(id.toString(), runtime.resolve("q.gui_id".asExpression()).asString())
        for (invalid in listOf("NaN", "Infinity", "1e400", "text")) {
            assertThrows(IllegalArgumentException::class.java) {
                MolangUiParameters.decode(mapOf("amount" to ParameterType.NUMBER), mapOf("amount" to invalid))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            MolangUiParameters.decode(emptyMap(), mapOf("undeclared" to "1"))
        }
    }

    @Test
    fun `both resource codecs reject names that cannot be safe parameter queries`() {
        for (name in listOf("gui_id", "format", "translate", "mouse_x", "item", "MixedCase", "v.nested", "bad-name")) {
            val view = JsonParser.parseString("""{"format_version":1,"contract_version":"1","mode":"screen",
                "parameters":{"$name":"string"},"root":{"type":"panel"}}""").asJsonObject
            val contract = JsonParser.parseString("""{"format_version":1,"contract_version":"1","mode":"screen",
                "parameters":{"$name":{"type":"string"}}}""").asJsonObject
            assertThrows(IllegalArgumentException::class.java, { MolangUiRegistry.parse(id, view) }, name)
            assertThrows(IllegalArgumentException::class.java, { MolangUiContractRegistry.parse(id, contract) }, name)
        }
    }

    @Test
    fun `variable updates reject invalid names aliases and nonfinite values atomically`() {
        val runtime = MolangUiClientRuntimeFactory.create(id, emptyMap(), mapOf("v.counter" to 1.0))
        val invalidUpdates = listOf(
            linkedMapOf("counter" to 2.0, "v.counter" to 3.0),
            linkedMapOf("counter" to 2.0, "nested.value" to 3.0),
            linkedMapOf("counter" to 2.0, "invalid" to Double.NaN),
            linkedMapOf("counter" to 2.0, "" to 3.0)
        )
        for (update in invalidUpdates) {
            assertThrows(IllegalArgumentException::class.java) {
                MolangUiClientRuntimeFactory.updateVariables(runtime, update)
            }
            assertEquals(1.0, runtime.resolve("v.counter".asExpression()).asDouble())
        }
        MolangUiClientRuntimeFactory.updateVariables(runtime, mapOf("counter" to 4.0))
        assertEquals(4.0, runtime.resolve("v.counter".asExpression()).asDouble())
    }

    @Test
    fun `empty string parameters preserve their type and native conditional comparison`() {
        val runtime = MolangUiClientRuntimeFactory.create(id,
            mapOf("name" to ParameterValue.StringValue("")), emptyMap())
        assertEquals("", runtime.resolve("q.name".asExpression()).asString())
        assertEquals(1.0, runtime.resolve("q.name == '' ? 1 : 0".asExpression()).asDouble())
        assertEquals(0.0, runtime.resolve("q.name == 'trainer' ? 1 : 0".asExpression()).asDouble())
    }
}

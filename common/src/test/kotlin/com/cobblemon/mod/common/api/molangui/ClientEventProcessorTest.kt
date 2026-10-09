/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.molangui.event.ClientEventProcessor
import com.cobblemon.mod.common.api.molangui.event.WidgetEvent
import com.cobblemon.mod.common.api.molangui.event.WidgetEventType
import com.cobblemon.mod.common.api.molangui.widget.ServerWidgetEvent
import com.cobblemon.mod.common.api.molangui.widget.WidgetDefinition
import com.cobblemon.mod.common.api.molangui.widget.WidgetProperty
import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import com.cobblemon.mod.common.api.molangui.widget.WidgetType
import com.cobblemon.mod.common.client.gui.molangui.MolangUiWidgetTree
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.server.molangui.MolangUiActionPacket
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolve
import com.google.gson.JsonParser
import io.mockk.*
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import java.util.UUID

@BootstrapMinecraft
class ClientEventProcessorTest {
    private val runtime = MoLangRuntime()
    private val session = UUID.randomUUID()
    private val sent = mutableListOf<MolangUiActionPacket>()

    @BeforeEach
    fun network() {
        mockkStatic(CobblemonNetwork::class)
        every { CobblemonNetwork.sendToServer(any()) } answers { sent += firstArg<MolangUiActionPacket>() }
    }

    @AfterEach
    fun cleanup() = unmockkStatic(CobblemonNetwork::class)

    private fun definition(client: String = "v.amount = q.event.button + 10;", parameters: Map<String, WidgetProperty> = mapOf(
        "value" to WidgetProperty.molang("v.amount"), "target" to WidgetProperty.molang("q.event.widget_id")
    )) = WidgetDefinition("parent", WidgetType.PANEL,
        clientEvents = mapOf("on_click_client" to client),
        events = mapOf("on_click" to ServerWidgetEvent(ActionKey("choose"), parameters)))

    @Test
    fun `client mutation and event context are available when server arguments are evaluated`() {
        runtime.environment.query.addFunction("event") { StringValue("outside") }
        ClientEventProcessor.processEvent(WidgetEvent(WidgetEventType.CLICK, "child", mapOf("button" to 2.0)), definition(), runtime, session)
        assertEquals(12.0, sent.single().data.getValue("value").toDouble())
        assertEquals("child", sent.single().data.getValue("target"))
        assertEquals(session, sent.single().sessionId)
        assertEquals("outside", runtime.resolve("q.event".asExpression()).asString())
    }

    @Test
    fun `nested events restore the outer context even when sending fails`() {
        runtime.environment.query.addFunction("nested") {
            ClientEventProcessor.processEvent(WidgetEvent(WidgetEventType.CLICK, "inner", mapOf("button" to 7.0)), definition(), runtime, session)
            runtime.resolve("q.event.widget_id".asExpression())
        }
        every { CobblemonNetwork.sendToServer(any()) } throws IllegalStateException("disconnected")
        assertDoesNotThrow {
            ClientEventProcessor.processEvent(WidgetEvent(WidgetEventType.CLICK, "outer", mapOf("button" to 3.0)),
                definition("v.outer = q.nested; v.after = q.event.button;"), runtime, session)
        }
        assertEquals("outer", runtime.resolve("v.outer".asExpression()).asString())
        assertEquals(3.0, runtime.resolve("v.after".asExpression()).asDouble())
        assertFalse("event" in runtime.environment.query.functions)
    }

    @Test
    fun `failed handlers arguments and oversized values do not dispatch partial actions`() {
        runtime.environment.query.addFunction("fail") { throw IllegalStateException("failed expression") }
        val failure = WidgetProperty.molang("q.fail")
        for (widget in listOf(definition("q.fail;"), definition(parameters = mapOf("value" to failure)),
            definition(parameters = mapOf("value" to WidgetProperty.literal("x".repeat(MolangUiPacketLimits.MAX_VALUE_LENGTH + 1)))))) {
            assertDoesNotThrow { ClientEventProcessor.processEvent(WidgetEvent(WidgetEventType.CLICK, "child"), widget, runtime, session) }
            assertFalse("event" in runtime.environment.query.functions)
        }
        assertTrue(sent.isEmpty())
        verify(exactly = 0) { CobblemonNetwork.sendToServer(any()) }
    }

    @Test
    fun `key handlers can inspect key data but cannot swallow escape`() {
        val tree = MolangUiWidgetTree(ResourceLocation.fromNamespaceAndPath("cobblemon", "key_test"), session,
            WidgetTree.parseWidget("root", JsonParser.parseString("""{"size":[100,100],
                "on_key_client":{"molang":"v.key = q.event.key; v.scan = q.event.scan; v.modifiers = q.event.modifiers;"}}""").asJsonObject),
            emptyMap(), emptyMap())
        assertTrue(tree.prepare(100, 100))
        assertTrue(tree.onKeyPress(65, 30, 2))
        assertEquals(65.0, tree.runtime.resolve("v.key".asExpression()).asDouble())
        assertEquals(30.0, tree.runtime.resolve("v.scan".asExpression()).asDouble())
        assertEquals(2.0, tree.runtime.resolve("v.modifiers".asExpression()).asDouble())
        assertFalse(tree.onKeyPress(GLFW_KEY_ESCAPE, 1, 0))
        assertEquals(65.0, tree.runtime.resolve("v.key".asExpression()).asDouble())
        assertThrows(IllegalArgumentException::class.java) { MolangUiParameters.requireName("event") }
    }
}

package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.stadium.StadiumCinematicPacket
import com.cobblemon.mod.common.net.messages.server.stadium.EditStadiumCinematicPacket
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class EditorStadiumBridgeTest {
    private lateinit var client: Minecraft
    private val sent = mutableListOf<EditStadiumCinematicPacket>()

    @BeforeEach fun setup() {
        mockkStatic(Minecraft::class, CobblemonNetwork::class)
        client = mockk(relaxed = true)
        client.level = null
        client.player = null
        every { client.connection } returns null
        every { Minecraft.getInstance() } returns client
        EditorStadiumBridge.tick(client)
        client.level = mockk(relaxed = true)
        every { client.connection } returns mockk(relaxed = true)
        captureSends()
    }

    private fun captureSends() {
        every { CobblemonNetwork.sendToServer(any()) } answers {
            (firstArg<Any>() as? EditStadiumCinematicPacket)?.let { sent += it }
            Unit
        }
    }

    @AfterEach fun cleanup() {
        client.level = null
        every { client.connection } returns null
        EditorStadiumBridge.tick(client)
        unmockkStatic(Minecraft::class, CobblemonNetwork::class)
    }

    private fun bind() = EditorScene.createDefault().also {
        it.markDirty()
        EditorStadiumBridge.bindEditor(it, BlockPos.ZERO, UUID.randomUUID(), client, "r1")
    }

    @Test fun `apply acknowledgement advances revision without clearing newer edits`() {
        val editor = bind()
        EditorStadiumBridge.apply(editor)
        val first = sent.single()
        editor.markDirty()
        EditorStadiumBridge.handle(StadiumCinematicPacket(first.requestId, true, Component.empty(), "r2"), client)
        assertTrue(editor.isDirty)
        EditorStadiumBridge.apply(editor)
        val second = sent.last()
        assertEquals("r2", second.revision)
        EditorStadiumBridge.handle(StadiumCinematicPacket(second.requestId, true, Component.empty(), "r3"), client)
        assertFalse(editor.isDirty)
    }

    @Test fun `timeout leaves edits dirty ignores late acknowledgement and permits retry`() {
        val editor = bind()
        val now = System.nanoTime()
        EditorStadiumBridge.apply(editor, now)
        val first = sent.single()
        EditorStadiumBridge.tick(client, now + 15_000_000_000L)
        EditorStadiumBridge.handle(StadiumCinematicPacket(first.requestId, true, Component.empty(), "late"), client)
        assertTrue(editor.isDirty)
        EditorStadiumBridge.apply(editor)
        assertEquals(2, sent.size)
        assertEquals("r1", sent.last().revision)
    }

    @Test fun `failed open send cannot keep apply disabled`() {
        val editor = bind()
        every { CobblemonNetwork.sendToServer(any()) } throws IllegalStateException("send failed")
        EditorStadiumBridge.open(BlockPos.ZERO, UUID.randomUUID())
        captureSends()
        EditorStadiumBridge.apply(editor)
        assertEquals(1, sent.size)
    }

    @Test fun `new connection or world invalidates pending acknowledgement and old stadium target`() {
        for (changeConnection in listOf(false, true)) {
            val editor = bind()
            EditorStadiumBridge.apply(editor)
            val request = sent.last()
            if (changeConnection) every { client.connection } returns mockk(relaxed = true)
            else client.level = mockk(relaxed = true)
            EditorStadiumBridge.handle(StadiumCinematicPacket(request.requestId, true, Component.empty(), "stale"), client)
            val count = sent.size
            EditorStadiumBridge.apply(editor)
            assertEquals(count, sent.size)
            assertTrue(editor.isDirty)
        }
    }
}

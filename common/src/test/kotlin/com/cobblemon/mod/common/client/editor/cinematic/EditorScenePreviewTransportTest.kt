package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.server.editor.EditorPreviewStopC2SPacket
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class EditorScenePreviewTransportTest {
    private val stopped = mutableListOf<UUID>()

    @BeforeEach fun setup() {
        mockkStatic(CobblemonNetwork::class)
        every { CobblemonNetwork.sendToServer(any()) } answers {
            (firstArg<Any>() as? EditorPreviewStopC2SPacket)?.let { stopped += it.previewId }
            Unit
        }
    }

    @AfterEach fun cleanup() = unmockkStatic(CobblemonNetwork::class)

    @Test fun `pending preview expires without overlay playback ticks and late success is stopped`() {
        val editor = EditorScene.createDefault()
        val play = EditorPlayState(EditorPlayMode.PLAY)
        val connection = Any()
        val level = Any()
        var now = 0L
        val preview = EditorScenePreview(play, { editor }, { now }, { connection }, { level })
        val id = UUID.randomUUID()
        assertTrue(preview.trackRequest(id, editor))
        now = 14_999_999_999L
        preview.tickTransport()
        assertEquals(EditorPlayMode.PLAY, play.mode)
        now++
        preview.tickTransport()
        assertEquals(EditorPlayMode.EDIT, play.mode)
        assertTrue(preview.paused)
        assertTrue(preview.error.isNotEmpty())
        assertEquals(listOf(id), stopped)
        preview.onAckSuccess(id, UUID.randomUUID())
        assertNull(preview.runningSceneInstanceIdOrNull())
        assertEquals(listOf(id, id), stopped)
    }

    @Test fun `accepted preview does not expire and duplicate success cannot stop it`() {
        val editor = EditorScene.createDefault()
        val play = EditorPlayState(EditorPlayMode.PLAY)
        val connection = Any()
        val level = Any()
        var now = 0L
        val preview = EditorScenePreview(play, { editor }, { now }, { connection }, { level })
        val id = UUID.randomUUID()
        val sceneId = UUID.randomUUID()
        preview.trackRequest(id, editor)
        preview.onAckSuccess(id, sceneId)
        now = 60_000_000_000L
        preview.tickTransport()
        preview.onAckSuccess(id, sceneId)
        assertEquals(sceneId, preview.runningSceneInstanceIdOrNull())
        assertTrue(stopped.isEmpty())
        play.setMode(EditorPlayMode.EDIT)
        assertEquals(listOf(id), stopped)
    }

    @Test fun `connection replacement clears the preview without sending stop to the replacement server`() {
        val editor = EditorScene.createDefault()
        val play = EditorPlayState(EditorPlayMode.PLAY)
        var connection: Any? = Any()
        val level = Any()
        val preview = EditorScenePreview(play, { editor }, { 0L }, { connection }, { level })
        val id = UUID.randomUUID()
        preview.trackRequest(id, editor)
        preview.onAckSuccess(id, UUID.randomUUID())
        connection = Any()
        preview.tickTransport()
        assertEquals(EditorPlayMode.EDIT, play.mode)
        assertNull(preview.runningSceneInstanceIdOrNull())
        assertTrue(stopped.isEmpty())
        connection = null
        assertFalse(preview.trackRequest(UUID.randomUUID(), editor))
    }

    @Test fun `scene replacement and failed stop sends still clear request ownership`() {
        var editor = EditorScene.createDefault()
        val play = EditorPlayState(EditorPlayMode.PLAY)
        val connection = Any()
        val level = Any()
        val preview = EditorScenePreview(play, { editor }, { 0L }, { connection }, { level })
        preview.trackRequest(UUID.randomUUID(), editor)
        every { CobblemonNetwork.sendToServer(any()) } throws IllegalStateException("disconnected")
        editor = EditorScene.createDefault()
        preview.tickTransport()
        assertEquals(EditorPlayMode.EDIT, play.mode)
        assertNull(preview.runningSceneInstanceIdOrNull())
        preview.tickTransport()
    }
}

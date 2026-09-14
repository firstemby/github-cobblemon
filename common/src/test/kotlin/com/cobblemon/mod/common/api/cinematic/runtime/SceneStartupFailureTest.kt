package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.registry.SceneRegistry
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.command.CinematicCommand
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.cinematic.BeatChangePacket
import com.cobblemon.mod.common.net.messages.client.cinematic.SceneEndPacket
import com.cobblemon.mod.common.net.messages.client.cinematic.SceneStartPacket
import com.cobblemon.mod.common.util.server
import com.mojang.brigadier.CommandDispatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class SceneStartupFailureTest {
    private val scene = Scene("startup_test", startBeatId = "main", beats = mapOf("main" to Beat("main")))
    private val players = linkedMapOf<UUID, ServerPlayer>()
    private val sent = mutableListOf<Pair<UUID, NetworkPacket<*>>>()
    private var failSend: (UUID, NetworkPacket<*>) -> Boolean = { _, _ -> false }
    private val failure = IllegalStateException("Injected packet failure")

    @BeforeEach
    fun setup() {
        mockkObject(CobblemonNetwork, SceneRegistry)
        mockkStatic(::server)
        val server = mockk<MinecraftServer>(relaxed = true)
        every { server() } returns server
        every { server.playerList.getPlayer(any()) } answers { players[firstArg()] }
        every { SceneRegistry.getScene(scene.id) } returns scene
        every { CobblemonNetwork.sendPacketToPlayer(any(), any()) } answers {
            val id = firstArg<ServerPlayer>().uuid
            val packet = secondArg<NetworkPacket<*>>()
            if (failSend(id, packet)) throw failure
            sent += id to packet
        }
        CinematicManager.clear()
    }

    @AfterEach
    fun cleanup() {
        failSend = { _, _ -> false }
        try {
            CinematicManager.clear()
        } finally {
            unmockkObject(CobblemonNetwork, SceneRegistry)
            unmockkStatic(::server)
        }
    }

    @Test
    fun `failed startup ends other viewers even when one end packet also fails`() {
        val first = player()
        val broken = player()
        val last = player()
        failSend = { id, _ -> id == broken.uuid }
        val id = CinematicManager.startScene(scene, Vec3.ZERO, initialViewers = players.keys.toSet())
        assertNull(id)
        assertEquals(0, CinematicManager.activeCount())
        val start = sent.map { it.second }.filterIsInstance<SceneStartPacket>().single()
        val ended = sent.filter { it.second is SceneEndPacket }
        assertEquals(listOf(first.uuid, last.uuid), ended.map { it.first })
        assertTrue(ended.all { (it.second as SceneEndPacket).sceneId == start.sceneId })
        assertNull(CinematicManager.getScene(start.sceneId))
    }

    @Test
    fun `failed late join rolls back only new viewers and permits retry`() {
        val existing = player()
        val joining = player()
        val id = requireNotNull(CinematicManager.startScene(scene, Vec3.ZERO))
        val instance = requireNotNull(CinematicManager.getScene(id))
        instance.addViewer(existing.uuid)
        failSend = { _, packet -> packet is BeatChangePacket }
        assertSame(failure, assertThrows(IllegalStateException::class.java) { instance.addViewerPlayer(joining) })
        assertEquals(setOf(existing.uuid), instance.networkSync.viewerIdsSnapshot())
        assertEquals(listOf(joining.uuid), sent.filter { it.second is SceneEndPacket }.map { it.first })
        assertTrue(instance.isActive())

        failSend = { _, _ -> false }
        instance.addViewerPlayer(joining)
        assertEquals(players.keys.toSet(), instance.networkSync.viewerIdsSnapshot())
        sent.clear()
        failSend = { _, packet -> packet is BeatChangePacket }
        assertSame(failure, assertThrows(IllegalStateException::class.java) { instance.addViewerPlayer(existing) })
        assertEquals(players.keys.toSet(), instance.networkSync.viewerIdsSnapshot())
        assertFalse(sent.any { it.second is SceneEndPacket })
    }

    @Test
    fun `command cleans its registered scene when viewer initialization fails`() {
        val player = player()
        val source = mockk<CommandSourceStack>(relaxed = true)
        every { source.hasPermission(2) } returns true
        every { source.playerOrException } returns player
        failSend = { _, packet -> packet is SceneStartPacket }
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        CinematicCommand.register(dispatcher)
        assertEquals(0, dispatcher.execute("cinematic start ${scene.id}", source))
        assertEquals(0, CinematicManager.activeCount())
        assertEquals(listOf(player.uuid), sent.filter { it.second is SceneEndPacket }.map { it.first })
        verify(exactly = 1) { source.sendFailure(any()) }
        verify(exactly = 0) { source.sendSuccess(any(), any()) }
    }

    private fun player(): ServerPlayer {
        val id = UUID.randomUUID()
        return mockk<ServerPlayer>(relaxed = true).also {
            every { it.uuid } returns id
            players[id] = it
        }
    }
}

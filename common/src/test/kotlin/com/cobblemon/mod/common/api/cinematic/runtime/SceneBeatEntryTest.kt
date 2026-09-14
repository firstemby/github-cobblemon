/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.BeatTransition
import com.cobblemon.mod.common.api.cinematic.model.CutEntry
import com.cobblemon.mod.common.api.cinematic.model.CutTrack
import com.cobblemon.mod.common.api.cinematic.model.CutTransition
import com.cobblemon.mod.common.api.cinematic.model.FeedDeclaration
import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.SceneGuiAction
import com.cobblemon.mod.common.api.cinematic.model.SceneGuiActionType
import com.cobblemon.mod.common.api.cinematic.model.SceneGuiTrack
import com.cobblemon.mod.common.api.cinematic.model.Shot
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedRotation
import com.cobblemon.mod.common.api.molangui.MolangUiContract
import com.cobblemon.mod.common.api.molangui.MolangUiContractRegistry
import com.cobblemon.mod.common.api.molangui.MolangUiOpenResult
import com.cobblemon.mod.common.api.molangui.MolangUiService
import com.cobblemon.mod.common.api.molangui.MolangUiSessionHandle
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.cinematic.BeatChangePacket
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedAssignmentPacket
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransformPacket
import com.cobblemon.mod.common.net.messages.client.cinematic.SceneEndPacket
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.server
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.UUID
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class SceneBeatEntryTest {
    private val packets = mutableListOf<NetworkPacket<*>>()
    private val instances = mutableListOf<SceneInstance>()
    private val viewerId = UUID.randomUUID()
    private val player = mockk<ServerPlayer>(relaxed = true)
    private val gui = mockk<MolangUiSessionHandle>(relaxed = true)
    private val guiId = ResourceLocation.fromNamespaceAndPath("cobblemon", "test_overlay")

    @BeforeEach
    fun setup() {
        mockkStatic(::server)
        mockkObject(CobblemonNetwork, MolangUiContractRegistry, MolangUiService)
        val minecraftServer = mockk<MinecraftServer>(relaxed = true)
        every { server() } returns minecraftServer
        every { minecraftServer.playerList.getPlayer(viewerId) } returns player
        every { player.uuid } returns viewerId
        every { CobblemonNetwork.sendPacketToPlayer(any(), any()) } answers { packets.add(secondArg()); Unit }
        val contract = mockk<MolangUiContract>()
        every { contract.parameters } returns emptyMap()
        every { contract.id } returns guiId
        every { MolangUiContractRegistry.getContract(guiId) } returns contract
        every { MolangUiService.open(player, any()) } returns MolangUiOpenResult.Opened(gui)
    }

    @AfterEach
    fun cleanup() {
        instances.forEach { it.stop() }
        unmockkObject(CobblemonNetwork, MolangUiContractRegistry, MolangUiService)
        unmockkStatic(::server)
    }

    @Test
    fun `feed returning after an inactive beat receives the same shot assignment again`() {
        val cut = mapOf("primary" to CutTrack(listOf(CutEntry(0f, "orbit"))))
        val first = Beat("first", cuts = cut)
        val hidden = Beat("hidden")
        val returning = Beat("returning", cuts = cut)
        val instance = start(listOf(first, hidden, returning))

        instance.tick(0f)
        assertEquals(1, packets.filterIsInstance<FeedAssignmentPacket>().size)
        instance.tick(0.5f)
        assertEquals(emptySet<String>(), packets.filterIsInstance<BeatChangePacket>().last().activeFeedIds)
        instance.tick(0.5f)

        val assignments = packets.filterIsInstance<FeedAssignmentPacket>()
        assertEquals(listOf("orbit", "orbit"), assignments.map { it.shotId })
        assertEquals(setOf("primary"), packets.filterIsInstance<BeatChangePacket>().last().activeFeedIds)
        instance.tick(0f)
        assertEquals(2, packets.filterIsInstance<FeedAssignmentPacket>().size)
    }

    @Test
    fun `cut assignments precede their first transforms on initial and subsequent beat entry`() {
        val cuts = mapOf("primary" to CutTrack(listOf(CutEntry(0f, "orbit"))))
        val instance = start(listOf(Beat("first", cuts = cuts), Beat("second", cuts = cuts)))
        for (delta in listOf(0f, 0.5f)) {
            packets.clear()
            instance.tick(delta)
            val assignmentIndex = packets.indexOfFirst { it is FeedAssignmentPacket }
            val transformIndex = packets.indexOfFirst { it is FeedTransformPacket }
            assertTrue(assignmentIndex >= 0)
            assertTrue(transformIndex > assignmentIndex)
        }
    }

    @Test
    fun `beat transition controls destination entry before later cuts take over`() {
        val first = Beat("first", cuts = mapOf("primary" to CutTrack(listOf(CutEntry(0f, "orbit")))))
        val second = Beat("second", cuts = mapOf("primary" to CutTrack(listOf(
            CutEntry(0f, "orbit", CutTransition.Instant),
            CutEntry(1f, "close", CutTransition.Blend(0.7f))
        ))))
        val instance = start(listOf(first, second), listOf(
            BeatTransition(
                "first",
                "second",
                "v.time_on_current_path >= 0.5".asExpressionLike(),
                CutTransition.Blend(0.25f)
            )
        ))

        instance.tick(0f)
        packets.clear()
        instance.tick(0.5f)

        val beatChange = packets.filterIsInstance<BeatChangePacket>().single()
        assertEquals(false, beatChange.instantTransition)
        val entryAssignment = packets.filterIsInstance<FeedAssignmentPacket>().single()
        assertEquals(FeedAssignmentPacket.TransitionType.BLEND, entryAssignment.transitionType)
        assertEquals(0.25f, entryAssignment.transitionDurationSeconds)

        packets.clear()
        instance.tick(0.5f)
        instance.tick(0.5f)
        val cutAssignment = packets.filterIsInstance<FeedAssignmentPacket>().single()
        assertEquals(FeedAssignmentPacket.TransitionType.BLEND, cutAssignment.transitionType)
        assertEquals(0.7f, cutAssignment.transitionDurationSeconds)
    }

    @Test
    fun `instant beat transition overrides a blended destination entry cut`() {
        val cuts = mapOf("primary" to CutTrack(listOf(
            CutEntry(0f, "orbit", CutTransition.Blend(0.7f))
        )))
        val instance = start(listOf(Beat("first", cuts = cuts), Beat("second", cuts = cuts)), listOf(
            BeatTransition(
                "first",
                "second",
                "v.time_on_current_path >= 0.5".asExpressionLike(),
                CutTransition.Instant
            )
        ))

        instance.tick(0f)
        packets.clear()
        instance.tick(0.5f)

        assertTrue(packets.filterIsInstance<BeatChangePacket>().single().instantTransition)
        val assignment = packets.filterIsInstance<FeedAssignmentPacket>().single()
        assertEquals(FeedAssignmentPacket.TransitionType.INSTANT, assignment.transitionType)
        assertEquals(0f, assignment.transitionDurationSeconds)
    }

    @Test
    fun `time zero GUI actions run once on initial and subsequent beat entry`() {
        val first = Beat("first", guiTrack = SceneGuiTrack(listOf(
            SceneGuiAction(0f, SceneGuiActionType.OPEN, guiId.toString())
        )))
        val second = Beat("second", guiTrack = SceneGuiTrack(listOf(
            SceneGuiAction(0f, SceneGuiActionType.CLOSE, guiId.toString())
        )))
        val instance = start(listOf(first, second))

        repeat(3) { instance.tick(0f) }
        verify(exactly = 1) { MolangUiService.open(player, any()) }
        verify(exactly = 0) { gui.close() }

        instance.tick(0.5f)
        repeat(3) { instance.tick(0f) }
        verify(exactly = 1) { gui.close() }
        verify(exactly = 1) { MolangUiService.open(player, any()) }
    }

    @Test
    fun `failed GUI close and end packet still run all stop listeners exactly once`() {
        val beat = Beat("main", guiTrack = SceneGuiTrack(listOf(
            SceneGuiAction(0f, SceneGuiActionType.OPEN, guiId.toString())
        )))
        val instance = start(listOf(beat))
        instance.tick(0f)
        every { gui.close() } throws IllegalStateException("GUI close failed")
        every { CobblemonNetwork.sendPacketToPlayer(any(), ofType<SceneEndPacket>()) } throws IllegalStateException("send failed")
        var callbacks = 0
        instance.onStop { throw IllegalStateException("listener failed") }
        instance.onStop { callbacks++ }
        repeat(2) { instance.stop() }
        assertEquals(1, callbacks)
        verify(exactly = 1) { gui.close() }
        verify(exactly = 1) { CobblemonNetwork.sendPacketToPlayer(any(), ofType<SceneEndPacket>()) }
    }

    @Test
    fun `early transitions do not run future GUI opens`() {
        val first = Beat("first", duration = 20f, guiTrack = SceneGuiTrack(listOf(
            SceneGuiAction(10f, SceneGuiActionType.OPEN, guiId.toString())
        )))
        val instance = start(listOf(first, Beat("second")))

        instance.tick(0.5f)

        assertEquals("second", instance.stateMachine.currentBeat?.id)
        verify(exactly = 0) { MolangUiService.open(player, any()) }
    }

    @Test
    fun `GUI close in the elapsed outgoing interval still runs before a transition`() {
        val first = Beat("first", guiTrack = SceneGuiTrack(listOf(
            SceneGuiAction(0f, SceneGuiActionType.OPEN, guiId.toString()),
            SceneGuiAction(0.4f, SceneGuiActionType.CLOSE, guiId.toString())
        )))
        val instance = start(listOf(first, Beat("second")))
        instance.tick(0f)

        instance.tick(0.5f)

        verify(exactly = 1) { MolangUiService.open(player, any()) }
        verify(exactly = 1) { gui.close() }
    }

    @Test
    fun `ending scene does not run future GUI opens`() {
        val beat = Beat("main", duration = 20f, guiTrack = SceneGuiTrack(listOf(
            SceneGuiAction(10f, SceneGuiActionType.OPEN, guiId.toString())
        )))
        val instance = start(listOf(beat), listOf(
            BeatTransition("main", "", "v.time_on_current_path >= 0.5".asExpressionLike(), endsScene = true)
        ))

        instance.tick(0.5f)

        assertEquals(false, instance.isActive())
        verify(exactly = 0) { MolangUiService.open(player, any()) }
    }

    @Test
    fun `loop boundary dispatches the final interval and resets GUI cursor`() {
        val beat = Beat("main", duration = 1f, isLooping = true, guiTrack = SceneGuiTrack(listOf(
            SceneGuiAction(0.2f, SceneGuiActionType.OPEN, guiId.toString()),
            SceneGuiAction(0.8f, SceneGuiActionType.CLOSE, guiId.toString())
        )))
        val instance = start(listOf(beat))

        repeat(4) { instance.tick(0.5f) }

        verify(exactly = 2) { MolangUiService.open(player, any()) }
        verify(exactly = 2) { gui.close() }
        assertEquals(0f, instance.stateMachine.beatTime)
    }

    @Test
    fun `self transition repeats time zero GUI and resends shot assignment once`() {
        val beat = Beat("main", cuts = mapOf("primary" to CutTrack(listOf(CutEntry(0f, "orbit")))),
            guiTrack = SceneGuiTrack(listOf(SceneGuiAction(0f, SceneGuiActionType.OPEN, guiId.toString()))))
        val instance = start(listOf(beat), listOf(
            BeatTransition("main", "main", "v.time_on_current_path >= 0.5".asExpressionLike())
        ))
        instance.tick(0f)

        instance.tick(0.5f)
        instance.tick(0f)

        verify(exactly = 2) { MolangUiService.open(player, any()) }
        assertEquals(2, packets.filterIsInstance<FeedAssignmentPacket>().size)
    }

    private fun start(beats: List<Beat>, transitions: List<BeatTransition> = beats.zipWithNext().map { (from, to) ->
        BeatTransition(from.id, to.id, "v.time_on_current_path >= 0.5".asExpressionLike())
    }): SceneInstance {
        val scene = Scene("entry_test", startBeatId = beats.first().id,
            beats = beats.associateBy { it.id },
            feeds = listOf(FeedDeclaration("primary", FeedKind.Fullscreen)),
            shots = mapOf("orbit" to Shot("orbit", FixedPosition(Vec3.ZERO), FixedRotation(CameraRotation(0f, 0f)))),
            transitions = transitions)
        return SceneInstance(UUID.randomUUID(), scene, Vec3.ZERO, emptyMap()).also {
            instances.add(it)
            it.addViewer(viewerId)
            assertTrue(it.start())
        }
    }
}

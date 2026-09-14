package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.CutEntry
import com.cobblemon.mod.common.api.cinematic.model.CutTrack
import com.cobblemon.mod.common.api.cinematic.model.FeedDeclaration
import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.Shot
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedRotation
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.cinematic.BeatChangePacket
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedAssignmentPacket
import com.cobblemon.mod.common.util.server
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class SceneViewerCatchUpTest {
    @Test fun `late viewer gets current cut without historical intro feeds`() {
        mockkObject(CobblemonNetwork)
        mockkStatic(::server)
        val packets = mutableListOf<NetworkPacket<*>>()
        every { server() } returns null
        every { CobblemonNetwork.sendPacketToPlayer(any(), any()) } answers { packets.add(secondArg()); Unit }
        try {
            val intro = Beat("intro", duration = 10f, cuts = listOf("intro_a", "intro_b").associateWith {
                CutTrack(listOf(CutEntry(0f, "trainer"))) })
            val battle = Beat("battle", cuts = mapOf("primary" to CutTrack(listOf(CutEntry(0f, "orbit"), CutEntry(10f, "closeup")))))
            val scene = Scene("stadium", startBeatId = "intro", beats = mapOf("intro" to intro, "battle" to battle),
                feeds = listOf("primary", "intro_a", "intro_b").map { FeedDeclaration(it, FeedKind.Fullscreen) },
                shots = listOf("trainer", "orbit", "closeup").associateWith {
                    Shot(it, FixedPosition(Vec3.ZERO), FixedRotation(CameraRotation(0f, 0f))) })
            val instance = SceneInstance(UUID.randomUUID(), scene, Vec3.ZERO, emptyMap())
            assertTrue(instance.start())
            instance.tick(0f)
            instance.stateMachine.samplePreview(battle, 15f)
            val player = mockk<ServerPlayer>(relaxed = true)
            every { player.uuid } returns UUID.randomUUID()
            instance.addViewerPlayer(player)
            val beatPacket = packets.filterIsInstance<BeatChangePacket>().single()
            assertEquals("battle", beatPacket.beatId)
            assertEquals(setOf("primary"), beatPacket.activeFeedIds)
            val assignment = packets.filterIsInstance<FeedAssignmentPacket>().single()
            assertEquals("primary", assignment.feedId)
            assertEquals("closeup", assignment.shotId)
        } finally {
            unmockkObject(CobblemonNetwork)
            unmockkStatic(::server)
        }
    }
}

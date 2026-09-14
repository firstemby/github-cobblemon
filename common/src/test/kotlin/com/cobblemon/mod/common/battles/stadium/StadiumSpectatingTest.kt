package com.cobblemon.mod.common.battles.stadium

import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.cinematic.runtime.CinematicManager
import com.cobblemon.mod.common.api.cinematic.runtime.SceneInstance
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket
import com.cobblemon.mod.common.net.messages.client.battle.BattleMusicPacket
import com.cobblemon.mod.common.net.messages.server.battle.SpectateBattlePacket
import com.cobblemon.mod.common.net.serverhandling.battle.SpectateBattleHandler
import com.cobblemon.mod.common.net.messages.client.cinematic.SceneEndPacket
import com.cobblemon.mod.common.net.messages.client.stadium.StadiumSpectateStatePacket
import com.cobblemon.mod.common.util.server
import io.mockk.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

@BootstrapMinecraft
class StadiumSpectatingTest {
    private val player = mockk<ServerPlayer>(relaxed = true)
    private val level = mockk<ServerLevel>(relaxed = true)
    private val gameServer = mockk<MinecraftServer>(relaxed = true)
    private val scene = mockk<SceneInstance>(relaxed = true)
    private val sceneId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()
    private val participant = UUID.randomUUID()
    private val packets = mutableListOf<NetworkPacket<*>>()
    private val eye = Vec3(0.0, 5.0, -20.0)
    private val config = mockk<StadiumBattleConfig>(relaxed = true)
    private lateinit var sessionId: UUID

    @BeforeEach fun setup() {
        mockkObject(Cobblemon, CobblemonNetwork, CinematicManager, BattleRegistry, StadiumBattleRegistry)
        mockkStatic(BattleRegistry::class)
        mockkStatic(::server)
        every { server() } returns gameServer
        every { gameServer.playerList.getPlayer(playerId) } returns player
        every { gameServer.playerList.players } returns listOf(player)
        every { Cobblemon.config } returns mockk(relaxed = true) { every { allowSpectating } returns true }
        every { CobblemonNetwork.sendPacketToPlayer(any(), any()) } answers { packets.add(secondArg()); Unit }
        every { BattleRegistry.getBattleByParticipatingPlayer(any()) } returns null
        every { BattleRegistry.getBattleBySpectatingPlayer(any()) } returns null
        every { CinematicManager.getActiveScenes() } returns emptyList()
        every { CinematicManager.getScene(sceneId) } returns scene
        every { scene.isActive() } returns true
        every { scene.removeViewer(playerId) } returns true
        every { player.uuid } returns playerId
        every { player.isAlive } returns true
        every { player.isSpectator } returns false
        every { player.isPassenger } returns false
        every { player.level() } returns level
        every { player.position() } returns eye.add(0.0, -1.6, 0.0)
        every { player.eyePosition } returns eye
        every { player.lookAngle } returns Vec3(0.0, 0.0, 1.0)
        every { level.dimension() } returns Level.OVERWORLD
        every { level.clip(any()) } returns BlockHitResult.miss(Vec3(0.0, 5.0, -6.0), Direction.NORTH, BlockPos.ZERO)
        every { config.id } returns "stadium"
        every { config.displayName } returns Component.literal("Arena")
        every { config.arena } returns StadiumArena(Vec3.ZERO, 16.0, 12.0)
        every { config.spectatorAreas } returns listOf(StadiumSpectatorArea(Vec3(-5.0, 0.0, -25.0), Vec3(5.0, 10.0, -15.0)))
        every { StadiumBattleRegistry.get("stadium") } returns config
        sessionId = StadiumSpectating.start(config, Level.OVERWORLD.location(), setOf(participant), sceneId)
    }

    @AfterEach fun cleanup() {
        StadiumSpectating.clear()
        unmockkStatic(::server)
        unmockkStatic(BattleRegistry::class)
        unmockkObject(Cobblemon, CobblemonNetwork, CinematicManager, BattleRegistry, StadiumBattleRegistry)
    }

    private fun battle(): PokemonBattle = mockk<PokemonBattle>(relaxed = true).also {
        every { it.battleId } returns UUID.randomUUID()
        every { it.ended } returns false
        every { it.spectators } returns mutableSetOf()
        every { it.onEndHandlers } returns mutableListOf()
        every { it.format } returns BattleFormat.GEN_9_SINGLES
        every { it.side1 } returns BattleSide()
        every { it.side2 } returns BattleSide()
        every { it.chatLog } returns mutableListOf()
    }

    @Test fun `interaction spectate uses stadium eligibility without regular battle music`() {
        val battle = battle()
        every { battle.stadiumSession } returns mockk(relaxed = true)
        every { BattleRegistry.getBattleByParticipatingPlayerId(participant) } returns battle
        StadiumSpectating.attachBattle(sessionId, battle)
        every { player.position() } returns Vec3(100.0, 0.0, 100.0)
        SpectateBattleHandler.handle(SpectateBattlePacket(participant), gameServer, player)
        assertTrue(battle.spectators.isEmpty())
        assertTrue(packets.isEmpty())
        verify(exactly = 0) { battle.actors }

        every { player.position() } returns eye.add(0.0, -1.6, 0.0)
        SpectateBattleHandler.handle(SpectateBattlePacket(participant), gameServer, player)
        assertTrue(playerId in battle.spectators)
        assertTrue(packets.any { it is BattleInitializePacket })
        assertFalse(packets.any { it is BattleMusicPacket })
    }

    @Test fun `intro viewer receives battle on launch and leaves without stopping participants`() {
        StadiumSpectating.join(player, sessionId)
        StadiumSpectating.join(player, sessionId)
        verify(exactly = 1) { scene.addViewerPlayer(player) }
        val battle = battle()
        StadiumSpectating.attachBattle(sessionId, battle)
        assertTrue(playerId in battle.spectators)
        assertEquals(1, packets.count { it is BattleInitializePacket })
        assertFalse(StadiumSpectating.leave(player, UUID.randomUUID()))
        assertTrue(StadiumSpectating.leaveBattle(player, battle.battleId))
        assertFalse(StadiumSpectating.leave(player, sessionId))
        assertTrue(battle.spectators.isEmpty())
        assertTrue(packets.any { it is SceneEndPacket && it.sceneId == sceneId })
        assertTrue(packets.any { it is BattleEndPacket })
        verify(exactly = 1) { scene.removeViewer(playerId) }
        verify(exactly = 0) { scene.stop() }
        verify(exactly = 0) { battle.stop() }
        assertTrue(StadiumSpectating.isRunning("stadium", Level.OVERWORLD.location()))
    }

    @Test fun `area gaze dimension occlusion participants settings and stale sessions are checked on server`() {
        every { player.position() } returns Vec3.ZERO
        StadiumSpectating.join(player, sessionId)
        every { player.position() } returns eye.add(0.0, -1.6, 0.0)
        every { player.lookAngle } returns Vec3(0.0, 0.0, -1.0)
        StadiumSpectating.join(player, sessionId)
        every { player.lookAngle } returns Vec3(0.0, 0.0, 1.0)
        every { level.dimension() } returns Level.NETHER
        StadiumSpectating.join(player, sessionId)
        every { level.dimension() } returns Level.OVERWORLD
        every { level.clip(any()) } returns BlockHitResult(eye.add(0.0, 0.0, 1.0), Direction.NORTH, BlockPos.ZERO, false)
        StadiumSpectating.join(player, sessionId)
        every { level.clip(any()) } returns BlockHitResult.miss(eye, Direction.NORTH, BlockPos.ZERO)
        every { player.uuid } returns participant
        StadiumSpectating.join(player, sessionId)
        every { player.uuid } returns playerId
        every { Cobblemon.config.allowSpectating } returns false
        StadiumSpectating.join(player, sessionId)
        every { Cobblemon.config.allowSpectating } returns true
        every { scene.isActive() } returns false
        StadiumSpectating.join(player, sessionId)
        StadiumSpectating.join(player, UUID.randomUUID())
        verify(exactly = 0) { scene.addViewerPlayer(any()) }
        assertFalse(packets.filterIsInstance<StadiumSpectateStatePacket>().any { it.watching })
    }

    @Test fun `late joining battle ends camera and spectator membership on battle end`() {
        val battle = battle()
        StadiumSpectating.attachBattle(sessionId, battle)
        StadiumSpectating.join(player, sessionId)
        assertTrue(playerId in battle.spectators)
        battle.onEndHandlers.toList().forEach { it(battle) }
        assertFalse(StadiumSpectating.isRunning("stadium", Level.OVERWORLD.location()))
        assertTrue(battle.spectators.isEmpty())
        assertFalse(packets.filterIsInstance<StadiumSpectateStatePacket>().last().watching)
        verify(exactly = 1) { scene.removeViewer(playerId) }
    }

    @Test fun `disconnect releases viewer and permits rejoin`() {
        StadiumSpectating.join(player, sessionId)
        StadiumSpectating.disconnect(player)
        StadiumSpectating.join(player, sessionId)
        verify(exactly = 2) { scene.addViewerPlayer(player) }
        verify(exactly = 1) { scene.removeViewer(playerId) }
    }

    @Test fun `failed camera join rolls back battle membership and permits retry`() {
        val battle = battle()
        StadiumSpectating.attachBattle(sessionId, battle)
        every { scene.addViewerPlayer(player) } throws IllegalStateException("camera unavailable")

        StadiumSpectating.join(player, sessionId)

        assertFalse(StadiumSpectating.isWatching(playerId))
        assertTrue(battle.spectators.isEmpty())
        assertTrue(packets.any { it is BattleEndPacket })
        assertFalse(packets.filterIsInstance<StadiumSpectateStatePacket>().any { it.watching })

        every { scene.addViewerPlayer(player) } just Runs
        StadiumSpectating.join(player, sessionId)
        assertTrue(StadiumSpectating.isWatching(playerId))
        assertTrue(playerId in battle.spectators)
    }

    @Test fun `failed scene end packet cannot retain battle membership or block battle end`() {
        val battle = battle()
        StadiumSpectating.attachBattle(sessionId, battle)
        StadiumSpectating.join(player, sessionId)
        every { CobblemonNetwork.sendPacketToPlayer(any(), any()) } answers {
            val packet = secondArg<NetworkPacket<*>>()
            if (packet is SceneEndPacket) throw IllegalStateException("connection failed")
            packets.add(packet)
        }

        battle.onEndHandlers.toList().forEach { it(battle) }

        assertFalse(StadiumSpectating.isWatching(playerId))
        assertFalse(StadiumSpectating.isRunning("stadium", Level.OVERWORLD.location()))
        assertTrue(battle.spectators.isEmpty())
        assertTrue(packets.any { it is BattleEndPacket })
        assertFalse(packets.filterIsInstance<StadiumSpectateStatePacket>().last().watching)
    }

    @Test fun `failed availability packet is retried without forcing refresh`() {
        every { CobblemonNetwork.sendPacketToPlayer(any(), any()) } throws IllegalStateException("connection failed")
        assertThrows(IllegalStateException::class.java) { StadiumSpectating.refresh(player) }
        every { CobblemonNetwork.sendPacketToPlayer(any(), any()) } answers { packets.add(secondArg()); Unit }

        StadiumSpectating.refresh(player)

        assertEquals(sessionId, packets.filterIsInstance<StadiumSpectateStatePacket>().single().sessionId)
    }
}

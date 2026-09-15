/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.SceneCue
import com.cobblemon.mod.common.api.cinematic.registry.SceneRegistry
import com.cobblemon.mod.common.api.cinematic.runtime.CinematicManager
import com.cobblemon.mod.common.api.cinematic.runtime.SceneInstance
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.SuccessfulBattleStart
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.command.StadiumBattleCommand
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.util.party
import com.cobblemon.mod.common.util.server
import io.mockk.*
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class StadiumCommandLifecycleTest {
    private val source = mockk<CommandSourceStack>(relaxed = true)
    private val level = mockk<ServerLevel>(relaxed = true)
    private val gameServer = mockk<MinecraftServer>(relaxed = true)
    private val players = List(2) { mockk<ServerPlayer>(relaxed = true) }
    private val scene = mockk<SceneInstance>(relaxed = true)
    private val sceneId = UUID.randomUUID()
    private val sceneKey = ResourceLocation.parse("cobblemon:stadium_test")
    private val stops = mutableListOf<() -> Unit>()
    private val side = StadiumBattleSide(listOf(placement(Vec3.ZERO)), listOf(placement(Vec3.ZERO)))
    private val config = StadiumBattleConfig("test", null, Vec3.ZERO, Vec3.ZERO,
        StadiumBattleLayout(side, side), null, cinematicScene = sceneKey)

    @BeforeEach fun setup() {
        mockkObject(BattleRegistry, StadiumSpectating, SceneRegistry, CinematicManager)
        mockkStatic(BattleRegistry::class)
        mockkStatic(ServerPlayer::party)
        mockkStatic(::server)
        every { server() } returns gameServer
        every { source.server } returns gameServer
        every { source.level } returns level
        every { level.dimension() } returns Level.OVERWORLD
        every { StadiumSpectating.isRunning(any(), any()) } returns false
        every { StadiumSpectating.isWatching(any()) } returns false
        every { StadiumSpectating.start(any(), any(), any(), any()) } returns UUID.randomUUID()
        every { BattleRegistry.getBattleByParticipatingPlayer(any()) } returns null
        every { BattleRegistry.validateStadium(any(), any(), any()) } returns StadiumShowdownProtocol.Result(true)
        players.forEachIndexed { index, player ->
            val id = UUID.randomUUID()
            val party = mockk<PlayerPartyStore>()
            val pokemon = mockk<BattlePokemon>(relaxed = true)
            every { pokemon.health } returns 10
            every { party.toBattleTeam(any(), any(), any()) } returns listOf(pokemon)
            every { player.party() } returns party
            every { player.uuid } returns id
            every { player.name } returns Component.literal("Player $index")
            every { player.level() } returns level
            every { player.position() } returns Vec3(10.0 + index, 64.0, 10.0)
            every { player.isAlive } returns true
            every { player.isRemoved } returns false
            every { gameServer.playerList.getPlayer(id) } returns player
        }
        every { SceneRegistry.getScene(sceneKey.toString()) } returns Scene(sceneKey.toString(), startBeatId = "intro",
            beats = mapOf("intro" to Beat("intro", duration = 2f, cues = listOf(SceneCue("battle_launch", 1f)))))
        every { CinematicManager.startScene(any(), any(), any(), any(), any(), any()) } returns sceneId
        every { CinematicManager.getScene(sceneId) } returns scene
        every { scene.id } returns sceneId
        every { scene.onStop(any()) } answers { stops.add(firstArg()); Unit }
        every { scene.stop() } answers {
            val callbacks = stops.toList()
            stops.clear()
            callbacks.forEach { it() }
        }
    }

    @AfterEach fun cleanup() {
        scene.stop()
        unmockkStatic(ServerPlayer::party)
        unmockkStatic(::server)
        unmockkStatic(BattleRegistry::class)
        unmockkObject(BattleRegistry, StadiumSpectating, SceneRegistry, CinematicManager)
    }

    @Test fun `failed viewer initialization restores both participants and allows another start`() {
        every { scene.addViewerPlayer(players[1]) } throws IllegalStateException("viewer failed")

        assertEquals(0, StadiumBattleCommand.start(source, config, "single", players[0], players[1]))

        players.forEachIndexed { index, player ->
            verify(exactly = 1) { player.teleportTo(level, 10.0 + index, 64.0, 10.0, any(), any()) }
        }
        every { scene.addViewerPlayer(players[1]) } just Runs
        assertEquals(1, StadiumBattleCommand.start(source, config, "single", players[0], players[1]))
    }

    @Test fun `failed first participant restoration does not prevent restoring the second`() {
        every { scene.addViewerPlayer(any()) } throws IllegalStateException("viewer failed")
        every { players[0].teleportTo(level, 10.0, 64.0, 10.0, any(), any()) } throws IllegalStateException("restore failed")

        assertEquals(0, StadiumBattleCommand.start(source, config, "single", players[0], players[1]))

        verify(exactly = 1) { players[1].teleportTo(level, 11.0, 64.0, 10.0, any(), any()) }
    }

    @Test fun `nonfinite pokemon placement is rejected before moving participants`() {
        val invalid = side.copy(pokemon = listOf(placement(Vec3(Double.NaN, 0.0, 0.0))))

        assertEquals(0, StadiumBattleCommand.start(source, config.copy(singles = StadiumBattleLayout(invalid, side)),
            "single", players[0], players[1]))

        players.forEach { player -> verify(exactly = 0) { player.teleportTo(any<ServerLevel>(), any(), any(), any(), any(), any()) } }
        verify(exactly = 0) { BattleRegistry.validateStadium(any(), any(), any()) }
    }

    @Test fun `cross dimension NPC is rejected before preparing a battle actor`() {
        val npc = mockk<NPCEntity>(relaxed = true)
        every { npc.level() } returns mockk<ServerLevel>()

        assertEquals(0, StadiumBattleCommand.start(source, config, "single", players[0], npc))

        verify(exactly = 0) { npc.getPartyForChallenge(any()) }
    }

    @Test fun `failed battle cinematic attachment aborts the newly started battle`() {
        val cue = slot<(SceneCue) -> Unit>()
        val battle = mockk<PokemonBattle>(relaxed = true)
        val battleId = UUID.randomUUID()
        val endHandlers = mutableListOf<(PokemonBattle) -> Unit>()
        every { battle.battleId } returns battleId
        every { battle.onEndHandlers } returns endHandlers
        every { scene.onCue(capture(cue)) } just Runs
        every { BattleRegistry.startStadiumBattle(any(), any(), any(), any(), any()) } returns SuccessfulBattleStart(battle)
        every { BattleRegistry.abortStadiumBattle(battleId) } just Runs
        every { CinematicManager.registerSceneForBattle(battleId, sceneId) } throws IllegalStateException("attachment failed")
        assertEquals(1, StadiumBattleCommand.start(source, config, "single", players[0], players[1]))

        cue.captured(SceneCue("battle_launch", 1f))

        assertTrue(endHandlers.isNotEmpty())
        verify(exactly = 1) { BattleRegistry.abortStadiumBattle(battleId) }
        verify(exactly = 0) { StadiumSpectating.attachBattle(any(), any()) }
    }

    private fun placement(position: Vec3) = StadiumPlacement(position, null, null, null)
}

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.Cobblemon

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.ErroredBattleStart
import com.cobblemon.mod.common.battles.SuccessfulBattleStart
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.battles.interpreter.instructions.StadiumTieInstruction
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import net.minecraft.network.chat.Component
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class StadiumLifecycleTest {
    private val engine = mockk<ShowdownService>(relaxed = true)
    private val actors = List(2) { object : BattleActor(UUID.randomUUID(), mutableListOf()) {
        override val type = ActorType.WILD
        override fun getName() = Component.literal("Test actor")
        override fun nameOwned(name: String) = Component.literal(name)
    } }
    private var accepted: PokemonBattle? = null
    private var failures = 0

    @BeforeEach fun setUp() {
        mockkObject(ShowdownService.Companion)
        every { ShowdownService.service } returns engine
        every { engine.getStadiumCapabilities() } returns StadiumShowdownProtocol.Capabilities(
            setOf("team-validation", "structured-startup", "mechanic-restrictions"), setOf("singles", "doubles"), setOf("species_clause"))
        every { engine.validateStadiumBattle(any()) } returns StadiumShowdownProtocol.Result(true)
        every { engine.startStadiumBattle(any(), any()) } answers {
            val battle = firstArg<PokemonBattle>()
            assertNull(BattleRegistry.getBattle(battle.battleId))
            accepted = battle
            StadiumShowdownProtocol.Result(true)
        }
    }

    @AfterEach fun tearDown() {
        accepted?.let { BattleRegistry.abortStadiumBattle(it.battleId) }
        unmockkObject(ShowdownService.Companion)
    }

    private fun start() = BattleRegistry.startStadiumBattle(BattleFormat.GEN_9_SINGLES,
        BattleSide(actors[0]), BattleSide(actors[1]), StadiumConfiguration.template()) { failures++ }

    @Test fun `draw ends normally without winners or failure rollback`() {
        mockkObject(Cobblemon)
        every { Cobblemon.config } returns mockk(relaxed = true)
        try {
            val battle = (start() as SuccessfulBattleStart).battle
            StadiumTieInstruction.invoke(battle)
            battle.tick()
            assertTrue(battle.ended)
            assertNull(BattleRegistry.getBattle(battle.battleId))
            assertTrue(battle.winners.isEmpty())
            assertTrue(battle.losers.isEmpty())
            assertEquals(0, failures)
        } finally { unmockkObject(Cobblemon) }
    }

    @Test fun `preflight rejection does not initialize actors or submit a battle`() {
        every { engine.validateStadiumBattle(any()) } returns StadiumShowdownProtocol.failure("banned_move", "Rejected")
        assertTrue(start() is ErroredBattleStart)
        assertTrue(actors.all { !it.isInitialized() && it.activePokemon.isEmpty() })
        verify(exactly = 0) { engine.startStadiumBattle(any(), any()) }
    }

    @Test fun `engine rejection clears active slots and never registers`() {
        var rejected: PokemonBattle? = null
        every { engine.startStadiumBattle(any(), any()) } answers {
            rejected = firstArg()
            StadiumShowdownProtocol.failure("startup_failed", "Rejected")
        }
        assertTrue(start() is ErroredBattleStart)
        assertNotNull(rejected)
        assertNull(BattleRegistry.getBattle(rejected!!.battleId))
        assertTrue(rejected!!.ended)
        assertTrue(actors.all { it.activePokemon.isEmpty() })
        assertEquals(0, failures)
    }

    @Test fun `event cancellation never submits a battle`() {
        val subscription = CobblemonEvents.BATTLE_STARTED_PRE.subscribe { it.cancel() }
        try {
            assertTrue(start() is ErroredBattleStart)
            assertTrue(actors.all { it.activePokemon.isEmpty() })
            verify(exactly = 0) { engine.startStadiumBattle(any(), any()) }
        } finally {
            subscription.unsubscribe()
        }
    }

    @Test fun `accepted battle registers then fatal cleanup runs exactly once`() {
        val result = start()
        assertTrue(result is SuccessfulBattleStart)
        val battle = (result as SuccessfulBattleStart).battle
        assertSame(battle, BattleRegistry.getBattle(battle.battleId))
        assertTrue(actors.all { it.activePokemon.size == 1 })
        var endCalls = 0
        battle.onEndHandlers += { endCalls++ }
        BattleRegistry.abortStadiumBattle(battle.battleId)
        BattleRegistry.abortStadiumBattle(battle.battleId)
        assertNull(BattleRegistry.getBattle(battle.battleId))
        assertTrue(battle.ended)
        assertTrue(actors.all { it.activePokemon.isEmpty() })
        assertEquals(1, failures)
        assertEquals(1, endCalls)
        verify(exactly = 1) { engine.endBattle(battle) }
    }

    @Test fun `battle is unregistered before reentrant end handlers run`() {
        val battle = (start() as SuccessfulBattleStart).battle
        var calls = 0
        battle.onEndHandlers += {
            calls++
            assertNull(BattleRegistry.getBattle(battle.battleId))
            BattleRegistry.closeBattle(battle)
        }

        BattleRegistry.closeBattle(battle)

        assertEquals(1, calls)
        verify(exactly = 1) { engine.endBattle(battle) }
    }

    @Test fun `failed stadium session close still releases engine and failure callback`() {
        val battle = (start() as SuccessfulBattleStart).battle
        battle.stadiumSession = mockk {
            every { close() } throws IllegalStateException("session cleanup failed")
        }

        BattleRegistry.closeBattle(battle)
        BattleRegistry.abortStadiumBattle(battle.battleId)

        assertNull(BattleRegistry.getBattle(battle.battleId))
        assertEquals(0, failures)
        verify(exactly = 1) { engine.endBattle(battle) }
    }
}

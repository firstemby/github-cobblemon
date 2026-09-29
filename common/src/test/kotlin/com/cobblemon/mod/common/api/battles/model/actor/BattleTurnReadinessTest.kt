/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.model.actor

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI
import com.cobblemon.mod.common.battles.ActiveBattlePokemon
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.DefaultActionResponse
import com.cobblemon.mod.common.battles.ForcePassActionResponse
import com.cobblemon.mod.common.battles.PassActionResponse
import com.cobblemon.mod.common.battles.ShowdownActionRequest
import com.cobblemon.mod.common.battles.ShowdownActionResponse
import com.cobblemon.mod.common.battles.ShowdownMoveset
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.UUID
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class BattleTurnReadinessTest {
    private val messages = mutableListOf<String>()

    @BeforeEach
    fun setup() {
        val engine = mockk<ShowdownService>()
        every { engine.send(any(), any()) } answers { messages.addAll(secondArg<Array<String>>()); Unit }
        mockkObject(ShowdownService.Companion)
        every { ShowdownService.service } returns engine
    }

    @AfterEach
    fun cleanup() {
        unmockkObject(ShowdownService.Companion)
    }

    @Test
    fun `all synchronous actors prepare before any choices dispatch`() {
        val first = Actor()
        val second = Actor()
        val battle = battle(first, second)
        addSlot(first)
        addSlot(second)
        first.request = moveRequest()
        second.request = moveRequest()
        battle.turn(1)
        assertEquals(listOf(">p1 pass", ">p2 pass"), messages)
        assertFalse(first.mustChoose)
        assertFalse(second.mustChoose)
        assertNull(first.request)
        assertNull(second.request)
    }

    @Test
    fun `missing waiting and empty requests clear stale readiness without blocking other actors`() {
        for (inactiveRequest in listOf(null, ShowdownActionRequest(wait = true), ShowdownActionRequest())) {
            messages.clear()
            val first = Actor()
            val waiting = Actor()
            val last = Actor()
            val battle = battle(first, waiting, last)
            listOf(first, waiting, last).forEach { addSlot(it) }
            first.request = moveRequest()
            last.request = moveRequest()
            waiting.request = inactiveRequest
            waiting.mustChoose = true
            waiting.responses.add(PassActionResponse)
            waiting.expectingPassActions.add(PassActionResponse)
            battle.turn(1)
            assertEquals(listOf(">p1 pass", ">p3 pass"), messages)
            assertFalse(waiting.mustChoose)
            assertNull(waiting.request)
            assertTrue(waiting.responses.isEmpty())
            assertTrue(waiting.expectingPassActions.isEmpty())
            verify(exactly = 0) { waiting.battleAI.choose(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun `forced replacement is retained for a fainted actor with no active moveset`() {
        val actor = Actor()
        val waiting = Actor()
        val battle = battle(actor, waiting)
        addSlot(actor, health = 0)
        actor.request = ShowdownActionRequest(forceSwitch = listOf(true))
        every { actor.battleAI.choose(any(), any(), any(), any(), any()) } returns DefaultActionResponse()
        battle.turn(1)
        assertEquals(listOf(">p1 default"), messages)
        verify(exactly = 1) { actor.battleAI.choose(any(), any(), any(), null, true) }
    }

    @Test
    fun `null request notification releases stale readiness and dispatches another ready actor`() {
        val ready = Actor()
        val missing = Actor()
        battle(ready, missing)
        addSlot(ready)
        ready.request = moveRequest()
        ready.responses.add(PassActionResponse)
        missing.mustChoose = true
        missing.responses.add(PassActionResponse)
        missing.onChoiceRequested()
        assertEquals(listOf(">p1 pass"), messages)
        assertFalse(missing.mustChoose)
        assertTrue(missing.responses.isEmpty())
    }

    @Test
    fun `AI retry discards accepted prefix and restores forced pass ownership`() {
        val actor = Actor()
        val blocker = Actor()
        battle(actor, blocker)
        val first = addSlot(actor)
        val second = addSlot(actor)
        actor.request = moveRequest(2)
        actor.mustChoose = true
        blocker.mustChoose = true
        val forced = mockk<ShowdownActionResponse>()
        actor.expectingPassActions.add(forced)
        every { actor.battleAI.choose(first, any(), any(), any(), any()) } returns ForcePassActionResponse()
        every { actor.battleAI.choose(second, any(), any(), any(), any()) } returns invalidResponse()
        actor.onChoiceRequested()
        assertEquals(2, actor.responses.size)
        assertSame(forced, actor.responses[0])
        assertSame(PassActionResponse, actor.responses[1])
        assertTrue(actor.expectingPassActions.isEmpty())
        assertFalse(actor.mustChoose)
        actor.pokemonList.forEach { pokemon -> verify { pokemon.willBeSwitchedIn = false } }
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `invalid forced switch choices fall back to complete default choices`() {
        val actor = Actor()
        val waiting = Actor()
        val battle = battle(actor, waiting)
        repeat(2) { addSlot(actor, health = 0) }
        actor.request = ShowdownActionRequest(forceSwitch = listOf(true, true))
        every { actor.battleAI.choose(any(), any(), any(), any(), any()) } returns invalidResponse()
        battle.turn(1)
        assertEquals(listOf(">p1 default, default"), messages)
        actor.pokemonList.forEach { pokemon -> verify { pokemon.willBeSwitchedIn = false } }
    }

    private fun invalidResponse() = mockk<ShowdownActionResponse>().also {
        every { it.isValid(any(), any(), any()) } returns false
    }

    private fun moveRequest(slots: Int = 1) = ShowdownActionRequest(active = MutableList(slots) {
        ShowdownMoveset().apply { moves = emptyList() }
    })

    private fun battle(vararg actors: Actor): PokemonBattle {
        actors.forEachIndexed { index, actor -> actor.showdownId = "p${index + 1}" }
        return PokemonBattle(BattleFormat.GEN_9_DOUBLES, BattleSide(actors.first()),
            BattleSide(*actors.drop(1).toTypedArray()), initializeProgress = false).also { it.mute = true }
    }

    private fun addSlot(actor: Actor, health: Int = 100): ActiveBattlePokemon {
        val pokemon = mockk<BattlePokemon>(relaxed = true)
        every { pokemon.health } returns health
        every { pokemon.getName() } returns Component.literal("test")
        every { pokemon.facedOpponents } returns mutableSetOf()
        actor.pokemonList.add(pokemon)
        return ActiveBattlePokemon(actor, pokemon).also { actor.activePokemon.add(it) }
    }

    private class Actor : AIBattleActor(UUID.randomUUID(), emptyList(), mockk<BattleAI>()) {
        init { every { battleAI.choose(any(), any(), any(), any(), any()) } returns PassActionResponse }
        override val type get() = ActorType.NPC
        override fun getName() = Component.literal("test")
        override fun nameOwned(name: String) = Component.literal(name)
    }
}

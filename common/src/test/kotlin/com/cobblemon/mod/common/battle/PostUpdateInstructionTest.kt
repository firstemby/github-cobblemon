/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battle

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI
import com.cobblemon.mod.common.battles.ActiveBattlePokemon
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.ShowdownActionRequest
import com.cobblemon.mod.common.battles.ShowdownActionResponse
import com.cobblemon.mod.common.battles.interpreter.instructions.PostUpdateInstruction
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

internal class PostUpdateInstructionTest {
    @Test
    fun `simultaneous AI switches are submitted together`() {
        val first = TestActor()
        val second = TestActor()
        val fixture = Fixture(listOf(first), listOf(second))
        first.request = switchRequest()
        second.request = switchRequest()

        fixture.postUpdate()

        assertEquals(listOf(">p1 switch 2", ">p2 switch 2"), fixture.sent)
        assertEquals(1, first.prompts)
        assertEquals(1, second.prompts)
        assertTrue(fixture.battle.actors.all { !it.mustChoose && it.request == null && it.responses.isEmpty() })
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `AI switches wait for a deferred actor regardless of actor order`(deferredFirst: Boolean) {
        val immediate = TestActor()
        val deferred = TestActor(immediate = false)
        val actors = if (deferredFirst) listOf(deferred, immediate) else listOf(immediate, deferred)
        val fixture = Fixture(actors.take(1), actors.drop(1))
        actors.forEach { it.request = switchRequest() }

        fixture.postUpdate()

        assertTrue(fixture.sent.isEmpty())
        assertTrue(deferred.mustChoose)
        assertFalse(immediate.mustChoose)
        assertNotNull(deferred.request)
        assertEquals(1, immediate.responses.size)
        deferred.respond()
        assertEquals(listOf(">p1 switch 2", ">p2 switch 2"), fixture.sent)
    }

    @Test
    fun `waiting actors do not block multiple AI switches on the other side`() {
        val waiting = List(2) { TestActor(immediate = false) }
        val switching = List(2) { TestActor() }
        val fixture = Fixture(waiting, switching)
        waiting.forEach { it.request = ShowdownActionRequest(wait = true) }
        switching.forEach { it.request = switchRequest() }

        fixture.postUpdate()

        assertEquals(listOf(">p3 switch 2", ">p4 switch 2"), fixture.sent)
        assertTrue(waiting.all { it.prompts == 0 && !it.mustChoose })
        assertTrue(switching.all { it.prompts == 1 })
    }

    @Test
    fun `actors without forced switches are not prompted`() {
        val actors = List(3) { TestActor() }
        val fixture = Fixture(actors.take(1), actors.drop(1))
        actors[1].request = ShowdownActionRequest(wait = true)
        actors[2].request = ShowdownActionRequest(forceSwitch = listOf(false))

        fixture.postUpdate()

        assertTrue(fixture.sent.isEmpty())
        assertTrue(actors.all { it.prompts == 0 && !it.mustChoose })
        assertNull(actors[0].request)
    }

    @Test
    fun `forced switches in later slots also participate in the batch`() {
        val first = TestActor()
        val second = TestActor()
        val fixture = Fixture(listOf(first), listOf(second))
        second.activePokemon.add(ActiveBattlePokemon(second))
        first.request = switchRequest()
        second.request = ShowdownActionRequest(forceSwitch = listOf(false, true))

        fixture.postUpdate()

        assertEquals(listOf(">p1 switch 2", ">p2 pass, switch 4"), fixture.sent)
    }

    @Test
    fun `requests are checked when the queued dispatch executes`() {
        val actors = List(2) { TestActor() }
        val fixture = Fixture(actors.take(1), actors.drop(1))
        PostUpdateInstruction.invoke(fixture.battle)
        assertTrue(fixture.sent.isEmpty())
        actors.forEach { it.request = switchRequest() }

        fixture.battle.dispatches.removeFirst().invoke(fixture.battle)

        assertEquals(listOf(">p1 switch 2", ">p2 switch 2"), fixture.sent)
    }

    private fun switchRequest() = ShowdownActionRequest(forceSwitch = listOf(true))

    private class Fixture(side1: List<TestActor>, side2: List<TestActor>) {
        val sent = mutableListOf<String>()
        val battle = spyk(PokemonBattle(mockk(), BattleSide(*side1.toTypedArray()), BattleSide(*side2.toTypedArray())))

        init {
            every { battle.writeShowdownAction(*anyVararg()) } answers {
                sent.addAll(firstArg<Array<String>>())
            }
            battle.actors.forEachIndexed { index, actor ->
                actor.battle = battle
                actor.showdownId = "p${index + 1}"
                actor.activePokemon.add(ActiveBattlePokemon(actor))
            }
        }

        fun postUpdate() {
            PostUpdateInstruction.invoke(battle)
            battle.dispatches.removeFirst().invoke(battle)
        }
    }

    private class TestActor(private val immediate: Boolean = true) : AIBattleActor(UUID.randomUUID(), emptyList(), mockk<BattleAI>()) {
        override val type = ActorType.NPC
        var prompts = 0

        init {
            every { battleAI.choose(any(), any(), any(), any(), any()) } answers {
                val active = firstArg<ActiveBattlePokemon>()
                val forceSwitch = arg<Boolean>(4)
                mockk<ShowdownActionResponse> {
                    every { isValid(any(), any(), any()) } returns true
                    every { toShowdownString(any(), any()) } returns if (forceSwitch) {
                        "switch ${activePokemon.size + activePokemon.indexOf(active) + 1}"
                    } else {
                        "pass"
                    }
                }
            }
        }

        override fun getName() = Component.literal("Test actor")
        override fun nameOwned(name: String) = Component.literal(name)

        override fun onChoiceRequested() {
            prompts++
            if (immediate) respond()
        }

        fun respond() = super.onChoiceRequested()
    }
}

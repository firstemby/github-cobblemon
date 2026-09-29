/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.ShowdownActionRequest
import com.cobblemon.mod.common.battles.ShowdownPokemon
import com.cobblemon.mod.common.battles.ShowdownSide
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class StadiumSessionTest {
    @Test fun `rejected preview restores battle roster and accepted order does not modify original party`() {
        val team = listOf(false, true).map { healthy -> mockk<BattlePokemon>(relaxed = true).also { pokemon ->
            every { pokemon.health } returns if (healthy) 70 else 0
            every { pokemon.uuid } returns UUID.randomUUID()
        } }
        val actor = object : BattleActor(UUID.randomUUID(), team.toMutableList()) {
            override val type = ActorType.NPC
            override fun getName() = Component.literal("NPC")
            override fun nameOwned(name: String) = Component.literal(name)
        }.also { it.showdownId = "p1" }
        val battle = mockk<PokemonBattle>(relaxed = true)
        every { battle.actors } returns listOf(actor)
        every { battle.format } returns BattleFormat.GEN_9_SINGLES
        every { battle.ended } returns false
        val id = UUID.randomUUID()
        every { battle.battleId } returns id
        val engine = mockk<ShowdownService>()
        mockkObject(ShowdownService.Companion)
        try {
            every { ShowdownService.service } returns engine
            every { engine.sendStadiumChoice(id, any()) } returns StadiumShowdownProtocol.failure("invalid_choice", "Rejected")
            val session = StadiumBattleSession(battle, StadiumConfiguration.template().copy(teamPreview = true))
            session.request(actor, ShowdownActionRequest(teamPreview = true, rqid = 1))
            assertEquals(team, actor.pokemonList)
            session.submit(actor, 99, listOf(2, 1))
            verify(exactly = 1) { engine.sendStadiumChoice(any(), any()) }
            every { engine.sendStadiumChoice(id, any()) } returns StadiumShowdownProtocol.Result(true)
            session.submit(actor, 1, listOf(2, 1))
            assertEquals(team.reversed(), actor.pokemonList)
            assertEquals(0, team[0].health)
            session.submit(actor, 1, listOf(1, 2))
            verify(exactly = 2) { engine.sendStadiumChoice(any(), any()) }
            val side = ShowdownSide().apply { pokemon = team.map { original -> ShowdownPokemon().apply { details = "Bulbasaur, ${original.uuid}" } } }
            session.request(actor, ShowdownActionRequest(rqid = 2, side = side))
            assertEquals(team, actor.pokemonList)
        } finally { unmockkObject(ShowdownService.Companion) }
    }
}

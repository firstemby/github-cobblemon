/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battle

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.battles.ActiveBattlePokemon
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleRules
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.battle.BattlePokemonDTO
import com.cobblemon.mod.common.net.messages.client.battle.TeamPreviewPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import io.mockk.every
import io.mockk.mockk
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@BootstrapMinecraft
class BattlePokemonVisibilityTest {
    private class BattleFixture(format: BattleFormat = BattleFormat()) {
        val source = mockk<Pokemon>(relaxed = true)
        val battle = mockk<PokemonBattle>(relaxed = true)
        val actor = mockk<BattleActor>(relaxed = true)
        val active = mutableListOf<ActiveBattlePokemon>()
        val pokemon: BattlePokemon

        init {
            every { source.uuid } returns UUID.randomUUID()
            every { source.currentHealth } returns 80
            every { source.maxHealth } returns 100
            every { source.isFainted() } returns false
            every { source.level } returns 50
            every { source.speed } returns 120
            every { source.status } returns null
            every { source.aspects } returns emptySet()
            every { source.heldItem } returns ItemStack.EMPTY
            every { source.ability.name } returns "overgrow"
            every { source.getDisplayName(any()) } returns Component.literal("Bulbasaur")
            every { source.createPokemonProperties(*anyVararg()) } answers {
                PokemonProperties().apply { species = "cobblemon:bulbasaur" }
            }
            every { source.moveSet.getMovesWithNulls() } returns listOf(
                mockk { every { displayName } returns Component.translatable("cobblemon.move.tackle") },
                null, null, null
            )
            every { actor.isInitialized() } returns true
            every { actor.battle } returns battle
            every { actor.activePokemon } returns active
            every { battle.format } returns format
            every { battle.activePokemon } returns active
            every { battle.actors } returns emptyList()
            every { battle.spectators } returns mutableSetOf()
            pokemon = BattlePokemon(source).also { it.actor = actor }
        }
    }

    private fun assertPrivateDetailsHidden(dto: BattlePokemonDTO) {
        assertNull(dto.ability)
        assertTrue(dto.moves.all { it == null })
        assertNull(dto.heldItem)
        assertNull(dto.speed)
    }

    @Test
    fun `unseen opponents stay unknown even when their data updates`() {
        val fixture = BattleFixture()
        assertNull(fixture.pokemon.toBattleDTO(ally = false).activeBattlePokemonDTO)
        fixture.pokemon.sendUpdate()
        val dto = fixture.pokemon.toBattleDTO(ally = false)
        assertNull(dto.activeBattlePokemonDTO)
        assertPrivateDetailsHidden(dto)
        assertFalse(fixture.pokemon.revealed)
    }

    @Test
    fun `entering battle reveals the pokemon and withdrawing keeps it known`() {
        val fixture = BattleFixture()
        fixture.active.add(ActiveBattlePokemon(fixture.actor, fixture.pokemon))
        fixture.pokemon.sendUpdate()
        assertTrue(fixture.pokemon.revealed)
        val revealed = fixture.pokemon.toBattleDTO(ally = false)
        assertEquals("cobblemon:bulbasaur", revealed.activeBattlePokemonDTO?.properties?.species)
        assertEquals(0.8F, revealed.activeBattlePokemonDTO?.hpValue)
        assertPrivateDetailsHidden(revealed)

        fixture.active.clear()
        fixture.pokemon.sendUpdate()
        assertNotNull(fixture.pokemon.toBattleDTO(ally = false).activeBattlePokemonDTO)
        val unseenTeammate = BattlePokemon(fixture.source).also { it.actor = fixture.actor }
        assertNull(unseenTeammate.toBattleDTO(ally = false).activeBattlePokemonDTO)
    }

    @Test
    fun `team preview shows unused pokemon without revealing their private details`() {
        for (rule in listOf(BattleRules.TEAM_PREVIEW, "teampreview", "TEAM PREVIEW")) {
            val fixture = BattleFixture(BattleFormat(ruleSet = setOf(rule)))
            val dto = fixture.pokemon.toBattleDTO(ally = false)
            assertNotNull(dto.activeBattlePokemonDTO, rule)
            assertPrivateDetailsHidden(dto)
            fixture.pokemon.sendUpdate()
            assertNotNull(fixture.pokemon.toBattleDTO(ally = false).activeBattlePokemonDTO)
        }
    }

    @Test
    fun `a negated team preview rule does not reveal the team`() {
        val fixture = BattleFixture(BattleFormat(ruleSet = setOf("!Team Preview")))
        assertNull(fixture.pokemon.toBattleDTO(ally = false).activeBattlePokemonDTO)
    }

    @Test
    fun `owner and open team sheet retain full team information`() {
        for ((format, ally) in listOf(BattleFormat() to true, BattleFormat(isOpenTeamSheet = true) to false)) {
            val fixture = BattleFixture(format)
            val dto = fixture.pokemon.toBattleDTO(ally)
            assertNotNull(dto.activeBattlePokemonDTO)
            assertEquals("overgrow", dto.ability)
            assertNotNull(dto.moves.first())
            assertNotNull(dto.heldItem)
            assertEquals(if (ally) 120 else null, dto.speed)
        }
    }

    @Test
    fun `preview packet reveals identities before pokemon have battle actors`() {
        val fixture = BattleFixture()
        val packet = TeamPreviewPacket(
            1, listOf(fixture.source), listOf(fixture.source), false,
            Instant.EPOCH, Component.literal("Opponent")
        )
        val opponent = packet.opponent.single()!!
        assertNotNull(opponent.activeBattlePokemonDTO)
        assertPrivateDetailsHidden(opponent)
        assertNotNull(packet.team.single()!!.ability)
    }
}

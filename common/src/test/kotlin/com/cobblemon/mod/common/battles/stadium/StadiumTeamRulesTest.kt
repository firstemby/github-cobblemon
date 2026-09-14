/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StadiumTeamRulesTest {
    private val pikachu = StadiumTeamRules.Entry("pikachu", "pikachu", 50, listOf("thunderbolt"), "static", "lightball")

    @Test fun `unrestricted templates accept existing parties`() {
        assertTrue(StadiumTeamRules.errors(StadiumConfiguration.template(), listOf(pikachu)).isEmpty())
    }

    @Test fun `party ceiling rejects without truncating and level ceiling does not scale`() {
        val team = listOf(pikachu, pikachu.copy(level = 51))
        val errors = StadiumTeamRules.errors(StadiumConfiguration.template().copy(pokemonLimit = 1, levelCap = 50), team)
        assertEquals(2, errors.size)
        assertEquals(2, team.size)
        assertEquals(51, team[1].level)
    }

    @Test fun `species clause counts forms as the same species`() {
        val config = StadiumConfiguration.template().copy(clauses = mutableSetOf("species_clause"))
        assertEquals(1, StadiumTeamRules.errors(config, listOf(pikachu, pikachu.copy(form = "pikachurockstar"))).size)
    }

    @Test fun `bans normalize Showdown spelling across all restriction categories`() {
        val config = StadiumConfiguration.template().copy(
            bannedPokemon = mutableSetOf("pika_chu"), bannedMoves = mutableSetOf("thunder_bolt"),
            bannedAbilities = mutableSetOf("static"), bannedItems = mutableSetOf("light_ball")
        )
        assertEquals(4, StadiumTeamRules.errors(config, listOf(pikachu)).size)
    }

    @Test fun `form specific ban does not ban the base form`() {
        val config = StadiumConfiguration.template().copy(bannedPokemon = mutableSetOf("pikachurockstar"))
        assertTrue(StadiumTeamRules.errors(config, listOf(pikachu)).isEmpty())
        assertEquals(1, StadiumTeamRules.errors(config, listOf(pikachu.copy(form = "pikachurockstar"))).size)
    }

    @Test fun `item clause compares effects and ignores empty slots`() {
        val config = StadiumConfiguration.template().copy(uniqueHeldItems = true)
        assertEquals(1, StadiumTeamRules.errors(config, listOf(pikachu, pikachu.copy(item = "light_ball"))).size)
        assertTrue(StadiumTeamRules.errors(config, listOf(pikachu.copy(item = ""), pikachu.copy(item = ""))).isEmpty())
    }

    @Test fun `disabling held battle items rejects held effects without modifying parties`() {
        val config = StadiumConfiguration.template().copy(allowHeldItems = false)
        assertEquals(1, StadiumTeamRules.errors(config, listOf(pikachu)).size)
        assertEquals("lightball", pikachu.item)
        assertTrue(StadiumTeamRules.errors(config, listOf(pikachu.copy(item = ""))).isEmpty())
    }
}

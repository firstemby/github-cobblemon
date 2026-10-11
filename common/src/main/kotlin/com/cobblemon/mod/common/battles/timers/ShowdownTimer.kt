/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.timers

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import java.time.Duration
import java.time.Instant

class ShowdownTimer(override val battle: PokemonBattle, override val actor: PlayerBattleActor): PlayerBattleTimer {
    private lateinit var turnStart: Instant
    private var secondsRemaining = 210
    private var hasSelected = true

    override fun tick() {
        if (hasSelected || !::turnStart.isInitialized || battle.ended) return
        val secondsSinceStart = Duration.between(turnStart, Instant.now()).seconds
        if (secondsRemaining - secondsSinceStart <= 0) {
            hasSelected = true
            timeout()
        }
    }

    override fun startSelection() {
        turnStart = Instant.now()
        hasSelected = false
    }

    override fun endSelection() {
        if (hasSelected || !::turnStart.isInitialized) return
        hasSelected = true
        val secondsSinceStart = Duration.between(turnStart, Instant.now()).seconds.toInt()
        secondsRemaining = secondsRemaining - secondsSinceStart + 10
    }

    override fun mustChooseBy(): Instant {
        return turnStart.plusSeconds(secondsRemaining.toLong())
    }
}

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.player

import com.cobblemon.mod.common.client.battle.ClientBallDisplay
import com.cobblemon.mod.common.pokeball.PokeBall
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

/**
 * Tracks which players are recalling a Pokémon so we can pose their arm, draw the Poké Ball in their
 * hand, and start the recall beam from it.
 *
 * Keyed by player UUID. Entries time out on their own once the beam is done, and a new recall resets
 * the timer, so nothing has to clear them.
 *
 * Written by PokemonClientDelegate on recall (beamMode 3); read by HumanoidModelMixin, the ball
 * renderers, and PokemonRenderer.
 */
object RecallArmAnimationTracker {

    // How long the arm stays raised, in seconds.
    const val ARM_HOLD_SECONDS = 0.8

    // Fraction of the hold spent easing the arm up at the start and down at the end.
    private const val EASE_SECONDS = 0.15

    class RecallAnim(
        val ball: PokeBall,
        val aspects: Set<String>,
        val startTimeMs: Long
    ) {
        val ballState = ClientBallDisplay(ball, aspects)
        var beamOrigin: Vec3? = null
    }

    private val activeRecalls = ConcurrentHashMap<UUID, RecallAnim>()

    fun start(playerUuid: UUID, ball: PokeBall, aspects: Set<String>) {
        activeRecalls[playerUuid] = RecallAnim(ball, aspects, System.currentTimeMillis())
    }

    /** Returns the active recall for the player, or null if there is none or if it has expired. */
    fun get(playerUuid: UUID): RecallAnim? {
        val anim = activeRecalls[playerUuid] ?: return null
        if (secondsSince(anim) > ARM_HOLD_SECONDS) {
            activeRecalls.remove(playerUuid)
            return null
        }
        return anim
    }

    fun isRecalling(playerUuid: UUID): Boolean = get(playerUuid) != null

    private fun secondsSince(anim: RecallAnim) = (System.currentTimeMillis() - anim.startTimeMs) / 1000.0

    /** 0..1 ramp: eases in at the start, holds at 1, eases out at the end. 0 when not recalling. */
    fun raiseFactor(playerUuid: UUID): Float {
        val anim = get(playerUuid) ?: return 0F
        val elapsed = secondsSince(anim)
        val riseIn = Mth.clamp((elapsed / EASE_SECONDS).toFloat(), 0F, 1F)
        val fallOut = Mth.clamp(((ARM_HOLD_SECONDS - elapsed) / EASE_SECONDS).toFloat(), 0F, 1F)
        return minOf(riseIn, fallOut)
    }
}

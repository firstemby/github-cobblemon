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
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

/**
 * Client-side bookkeeping for the "recall pose": when a player recalls a Pokémon we want them to
 * hold the matching Poké Ball out in their right hand and have the recall beam originate from it.
 *
 * Entries are keyed by the recalling player's UUID and self-expire once the beam effect is over, so
 * the arm automatically lowers without anything having to explicitly clear it (and a re-recall just
 * resets the timer).
 *
 * Populated from [com.cobblemon.mod.common.client.entity.PokemonClientDelegate] when a Pokémon
 * enters recall (beamMode 3). Read by the player arm pose (HumanoidModelMixin), the held-ball render
 * layer, the first-person ball renderer, and the beam origin in PokemonRenderer.
 */
object RecallArmAnimationTracker {
    /**
     * How long the arm stays raised, in seconds. Matches the full recall beam lifetime:
     * extend (BEAM_EXTEND_TIME) + hold full (BEAM_SHRINK_TIME) + retract (BEAM_EXTEND_TIME) = 0.8s.
     */
    const val ARM_HOLD_SECONDS = 0.8

    /** Portion of [ARM_HOLD_SECONDS] spent easing the arm up / ball in at the start and down at the end. */
    private const val EASE_SECONDS = 0.15

    class RecallAnim(
        val ball: PokeBall,
        val aspects: Set<String>,
        val startTimeMs: Long
    ) {
        /** Reused render state so the ball model's animation/locator data persists across frames. */
        val ballState = ClientBallDisplay(ball, aspects)

        /** World-space point the recall beam should emit from, captured by the held-ball render layer each frame. */
        var beamOrigin: Vec3? = null
    }

    private val active = mutableMapOf<UUID, RecallAnim>()

    fun start(playerUuid: UUID, ball: PokeBall, aspects: Set<String>) {
        active[playerUuid] = RecallAnim(ball, aspects, System.currentTimeMillis())
    }

    /** Returns the active recall for the player, or null if there is none / it has expired (lazily evicting). */
    fun get(playerUuid: UUID): RecallAnim? {
        val anim = active[playerUuid] ?: return null
        if (secondsSince(anim) > ARM_HOLD_SECONDS) {
            active.remove(playerUuid)
            return null
        }
        return anim
    }

    fun isRecalling(playerUuid: UUID): Boolean = get(playerUuid) != null

    private fun secondsSince(anim: RecallAnim) = (System.currentTimeMillis() - anim.startTimeMs) / 1000.0

    /**
     * 0..1 ramp for how raised the arm / how risen the ball should be: eases up at the start, holds
     * at 1, then eases back down at the very end. Returns 0 when the player is not recalling.
     */
    fun raiseFactor(playerUuid: UUID): Float {
        val anim = get(playerUuid) ?: return 0F
        val elapsed = secondsSince(anim)
        val riseIn = Mth.clamp((elapsed / EASE_SECONDS).toFloat(), 0F, 1F)
        val fallOut = Mth.clamp(((ARM_HOLD_SECONDS - elapsed) / EASE_SECONDS).toFloat(), 0F, 1F)
        return min(riseIn, fallOut)
    }

    private fun min(a: Float, b: Float) = if (a < b) a else b
}

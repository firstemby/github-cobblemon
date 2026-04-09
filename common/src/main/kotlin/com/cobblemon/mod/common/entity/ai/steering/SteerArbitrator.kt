/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.ai.steering

import com.cobblemon.mod.common.CobblemonNetwork.sendPacketToTrackingPlayers
import com.cobblemon.mod.common.client.render.debug.EntityDebugLine
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.debug.ClientboundUpdateObsAvoidPacket
import net.minecraft.world.phys.Vec3

// Runs a list of SteerRules in priority order, consuming a force budget.
// High priority rules get their full contribution first. Lower priority
// rules only get what budget remains.
class SteerArbitrator(
    val entity: PokemonEntity,
    val maxForce: Double,
    val rules: MutableList<SteerRule> = mutableListOf()
) {

    private var sortedRules: List<SteerRule> = emptyList()
    private var dirty = true

    fun addRule(rule: SteerRule) { rules.add(rule); dirty = true }
    fun removeRule(rule: SteerRule) { rules.remove(rule); dirty = true }
    fun clearRules() { rules.clear(); dirty = true }

    fun calculate(debug: Boolean = false): Vec3 {
        if (dirty) {
            sortedRules = rules.sortedByDescending { it.priority }
            dirty = false
        }

        var remaining = maxForce
        var result = Vec3.ZERO
        val debugLines = if (debug) mutableListOf<EntityDebugLine>() else null

        for (rule in sortedRules) {
            if (remaining <= 0.0) break

            val force = rule.calculate() ?: continue
            val scaled = force.scale(rule.weight.toDouble())
            val len = scaled.length()
            if (len <= 0.0) continue

            val contribution = if (len <= remaining) {
                remaining -= len
                scaled
            } else {
                val partial = remaining
                remaining = 0.0
                scaled.normalize().scale(partial)
            }

            result = result.add(contribution)

            debugLines?.let {
                val (r, g, b) = rule.debugColor
                it.add(EntityDebugLine(contribution.scale(50.0), r, g, b, 1.0f))
            }
        }

        debugLines?.let {
            if (result.lengthSqr() > 0.0)
                it.add(EntityDebugLine(result.scale(50.0), 1f, 1f, 1f, 1.5f))
            if (it.isNotEmpty())
                sendPacketToTrackingPlayers(entity, ClientboundUpdateObsAvoidPacket(entity.id, it))
        }

        return result
    }
}
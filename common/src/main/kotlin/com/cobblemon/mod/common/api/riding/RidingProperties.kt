/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourSettings
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.api.riding.stats.RidingStatDefinition
import com.cobblemon.mod.common.util.*
import com.cobblemon.mod.common.util.adapters.RidingBehaviourSettingsAdapter
import net.minecraft.network.RegistryFriendlyByteBuf

class RidingProperties(
    val stats: MutableMap<RidingStat, RidingStatDefinition> = mutableMapOf(),
    val seats: List<Seat> = listOf(),
    val conditions: List<Expression> = listOf(),
    val behaviour: RidingBehaviourSettings? = null
) {

    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf): RidingProperties {
            val stats: MutableMap<RidingStat, RidingStatDefinition> = buffer.readMap(
                { buffer.readEnum(RidingStat::class.java) },
                { RidingStatDefinition.decode(buffer) }
            ).toMutableMap()
            val seats: List<Seat> = buffer.readList { _ -> Seat.decode(buffer) }
            val conditions = buffer.readList { buffer.readString().asExpression() }
            val behaviour = buffer.readNullable { _ ->
                val key = buffer.readResourceLocation()
                val settings = RidingBehaviourSettingsAdapter.types[key]?.getConstructor()?.newInstance() ?: error("Unknown controller key: $key")
                settings.decode(buffer)
                return@readNullable settings
            }

            return RidingProperties(stats = stats, seats = seats, conditions = conditions, behaviour = behaviour)
        }
    }

    internal fun updateStatRange(stat: RidingStat, style: RidingStyle, min: Int, max: Int) {
        if (!Cobblemon.config.enableDebugKeys) return
        if (stats[stat] == null) {
            stats[stat] = RidingStatDefinition()
        }
        stats[stat]!!.ranges[style] = min..max
    }

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeMap(
            stats,
            { _, stat -> buffer.writeEnum(stat) },
            { _, stat -> stat.encode(buffer) }
        )
        buffer.writeCollection(seats) { _, seat -> seat.encode(buffer) }
        buffer.writeCollection(conditions) { _, condition -> buffer.writeString(condition.getString()) }
        buffer.writeNullable(behaviour) { _, behaviour ->
            behaviour.encode(buffer)
        }
    }

    fun calculate(stat: RidingStat, style: RidingStyle, boosts: Int): Float {
        val definitions = stats[stat] ?: return 0F
        return definitions.calculate(style, boosts)
    }

    fun hasStat(stat: RidingStat, style: RidingStyle): Boolean {
        return stats[stat]?.ranges?.containsKey(style) == true
    }
}

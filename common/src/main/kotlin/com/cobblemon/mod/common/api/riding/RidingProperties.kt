/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.riding.controller.RideController
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.api.riding.stats.RidingStatDefinitions
import com.cobblemon.mod.common.util.adapters.riding.RideControllerAdapter
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.getString
import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf

class RidingProperties(
    val stats: Map<RidingStat, RidingStatDefinitions> = mapOf(),
    val seats: List<Seat> = listOf(),
    val conditions: List<Expression> = listOf(),
    val controller: RideController? = null
) {
    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf): RidingProperties {
            val stats: Map<RidingStat, RidingStatDefinitions> = buffer.readMap(
                { buffer.readEnum<RidingStat>(RidingStat::class.java) },
                { RidingStatDefinitions.decode(buffer) }
            )
            val seats: List<Seat> = buffer.readList { _ -> Seat.decode(buffer) }
            val conditions = buffer.readList { buffer.readString().asExpression() }
            val controller = buffer.readNullable { _ ->
                val key = buffer.readIdentifier()
                val controller = RideControllerAdapter.types[key]?.getConstructor()?.newInstance() ?: error("Unknown controller key: $key")
                controller.decode(buffer)
                return@readNullable controller
            }

            return RidingProperties(stats = stats, seats = seats, conditions = conditions, controller = controller)
        }
    }

    val canRide: Boolean
        get() = seats.isNotEmpty() && controller != null

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeMap(
            stats,
            { _, stat -> buffer.writeEnum(stat) },
            { _, stat -> stat.encode(buffer) }
        )
        buffer.writeCollection(seats) { _, seat -> seat.encode(buffer) }
        buffer.writeCollection(conditions) { _, condition -> buffer.writeString(condition.getString()) }
        buffer.writeNullable(controller) { _, controller -> controller.encode(buffer) }
    }

    fun calculate(stat: RidingStat, style: RidingStyle, boosts: Int): Float {
        val definitions = stats[stat] ?: return 0F
        return definitions.calculate(style, boosts)
    }

    fun hasStat(stat: RidingStat, style: RidingStyle): Boolean {
        return stats[stat]?.ranges?.containsKey(style) == true
    }
}

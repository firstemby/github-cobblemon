/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.stats

import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.net.IntSize
import com.cobblemon.mod.common.util.readSizedInt
import com.cobblemon.mod.common.util.readText
import com.cobblemon.mod.common.util.writeSizedInt
import com.cobblemon.mod.common.util.writeText
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component

/**
 * The definition of all the different information related to a specific [RidingStat] on a Pokémon species.
 *
 * @author Hiroku
 * @since February 17th, 2025
 */
class RidingStatDefinition {
    // This object gets read from a JSON adapter manually (field-by-field) so don't add fields without updating the adapter.

    /** A map from [RidingStyle] to a minimum and maximum value for this stat. */
    val ranges = mutableMapOf<RidingStyle, IntRange>()
    /** Display name for the stat, if null falls back to [RidingStat.displayName]. */
    var displayName: Component? = null
    /** Description for the stat, if null falls back to [RidingStat.description]. */
    var description: Component? = null

    fun calculate(style: RidingStyle, aprijuiceBoost: Int): Float {
        val range = ranges[style] ?: ranges[RidingStyle.LAND] ?: return 0F
        val boostQuotient = aprijuiceBoost / 255F
        return range.first + (range.last - range.first) * boostQuotient
    }

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeNullable(displayName) { _, it -> buffer.writeText(it) }
        buffer.writeNullable(description) { _, it -> buffer.writeText(it) }
        buffer.writeMap(
                ranges,
                { _, it -> buffer.writeEnum(it) },
                { _, it ->
                    buffer.writeSizedInt(IntSize.U_BYTE, it.first)
                    buffer.writeSizedInt(IntSize.U_BYTE, it.last)
                }
        )
    }

    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf): RidingStatDefinition {
            val displayName = buffer.readNullable { buffer.readText() }
            val description = buffer.readNullable { buffer.readText() }
            val ranges = buffer.readMap<RidingStyle, IntRange>(
                    { buffer.readEnum<RidingStyle>(RidingStyle::class.java) },
                    { IntRange(buffer.readSizedInt(IntSize.U_BYTE), buffer.readSizedInt(IntSize.U_BYTE)) }
            )
            return RidingStatDefinition().apply {
                this.displayName = displayName
                this.description = description
                this.ranges.putAll(ranges)
            }
        }
    }
}
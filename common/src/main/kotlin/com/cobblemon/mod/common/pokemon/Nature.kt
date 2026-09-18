/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon

import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.cooking.Flavour
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.util.codec.CodecUtils
import com.mojang.serialization.Codec
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth.floor

class Nature(
    val name: ResourceLocation,
    val displayName: String,
    val increasedStat: Stat?,
    val decreasedStat: Stat?,
    val favouriteFlavour: Flavour?,
    val dislikedFlavour: Flavour?
) {
    val struct = ObjectValue(this).also {
        it.addFunction("name") { StringValue(name.toString()) }
        it.addFunction("display_name") { StringValue(displayName) }
        it.addFunction("increased_stat") { increasedStat?.let { StringValue(it.showdownId) } ?: DoubleValue.ZERO }
        it.addFunction("decreased_stat") { decreasedStat?.let { StringValue(it.showdownId) } ?: DoubleValue.ZERO }
        it.addFunction("favourite_flavour") { favouriteFlavour?.let { StringValue(it.name) } ?: DoubleValue.ZERO }
        it.addFunction("disliked_flavour") { dislikedFlavour?.let { StringValue(it.name) } ?: DoubleValue.ZERO }
    }

    fun modifyStat(stat: Stat, value: Int): Int {
        return when (stat) {
            increasedStat -> floor(value * 1.1)
            decreasedStat -> floor(value * 0.9)
            else -> value
        }
    }

    companion object {
        @JvmStatic
        val BY_IDENTIFIER_CODEC: Codec<Nature> = CodecUtils.createByIdentifierCodec(
            Natures::getNature,
            Nature::name
        ) { identifier -> "No nature for ID $identifier" }
    }
}
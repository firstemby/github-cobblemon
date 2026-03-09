/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.pokemon.evolution.variants.ItemInteractionEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.LevelUpEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.TradeEvolution
import java.util.HashMap
import java.util.function.Function

object EvolutionMoLangFunctions {
    val holder: MutableList<(Evolution) -> HashMap<String, (MoParams) -> Any>> =
        mutableListOf(
            { evolution ->
                val map = hashMapOf<String, (MoParams) -> Any>()

                map.put("result") { evolution.result.asMoLangValue() }

                if (evolution is LevelUpEvolution) {
                    map.put("is_level_up") { DoubleValue.ONE }
                } else if (evolution is TradeEvolution) {
                    map.put("is_trade") { DoubleValue.ONE }
                } else if (evolution is ItemInteractionEvolution) {
                    map.put("is_item") { DoubleValue.ONE }
                }

                map.put("is_optional") { DoubleValue(evolution.optional) }
                map.put("consumes_held_item") { DoubleValue(evolution.consumeHeldItem) }

                map
            }
        )
}

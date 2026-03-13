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

object EvolutionMoLangFunctions : AbstractMoLangFunctionHolder<Evolution>() {
    override fun Evolution.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val evolution = this
        val map = hashMapOf<String, (MoParams) -> Any>()

        map["result"] = { evolution.result.asMoLangValue() }
        map["is_optional"] = { DoubleValue(evolution.optional) }
        map["consumes_held_item"] = { DoubleValue(evolution.consumeHeldItem) }

        when (evolution) {
            is LevelUpEvolution -> {
                map["is_level_up"] = { DoubleValue.ONE }
            }

            is TradeEvolution -> {
                map["is_trade"] = { DoubleValue.ONE }
            }

            is ItemInteractionEvolution -> {
                map["is_item"] = { DoubleValue.ONE }
            }
        }

        return map
    }
}

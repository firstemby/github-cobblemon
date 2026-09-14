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
import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.pokemon.transformation.evolution.Evolution
import com.cobblemon.mod.common.pokemon.transformation.triggers.ItemInteractionTrigger
import com.cobblemon.mod.common.pokemon.transformation.triggers.LevelUpTrigger
import com.cobblemon.mod.common.pokemon.transformation.triggers.TradeTrigger

object EvolutionMoLangFunctions : AbstractMoLangFunctionHolder<Transformation>() {
    override fun Transformation.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val evolution = this
        val functions = hashMapOf<String, (MoParams) -> Any>()

        functions["reevaluate"] = { DoubleValue(evolution.reevaluate) }
        functions["is_optional"] = { DoubleValue(evolution.optional) }

        if (evolution is Evolution) {
            functions["result"] = { evolution.result.asMoLangValue() }
            when (evolution.trigger) {
                is LevelUpTrigger -> {
                    functions["is_level_up"] = { DoubleValue.ONE }
                }

                is TradeTrigger -> {
                    functions["is_trade"] = { DoubleValue.ONE }
                }

                is ItemInteractionTrigger -> {
                    functions["is_item"] = { DoubleValue.ONE }
                }
            }
        }

        return functions
    }
}

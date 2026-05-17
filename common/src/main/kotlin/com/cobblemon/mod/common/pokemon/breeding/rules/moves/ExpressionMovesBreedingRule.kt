/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.breeding.rules.moves

import com.bedrockk.molang.runtime.struct.ArrayStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.breeding.rule.layer.MovesBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.PokemonBreedingRule
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.util.asArrayValue
import com.cobblemon.mod.common.util.withQueryValue

class ExpressionMovesBreedingRule(
    val expression: ExpressionLike,
    override val order: Int = 0
) : MovesBreedingRule {
    override fun apply(input: PokemonBreedingRule.Input, moveStack: MutableList<MutableList<MoveTemplate>>) {
        input.runtime.withQueryValue("move_stack") { moveStack.asArrayValue { it.asArrayValue(MoveTemplate::struct) } }
        input.runtime.environment.query.addFunction("add_moves_to_stack") { params ->
            val moves = params.params.flatMap {
                if (it is ObjectValue<*>) {
                    listOf((it as ObjectValue<MoveTemplate>).obj)
                } else if (it is StringValue) {
                    Moves.getByName(it.value)?.let { listOf(it) } ?: emptyList()
                } else if (it is ArrayStruct) {
                    it.map.values.mapNotNull { value ->
                        if (value is ObjectValue<*>) {
                            (value as ObjectValue<MoveTemplate>).obj
                        } else if (value is StringValue) {
                            Moves.getByName(value.value)
                        } else {
                            null
                        }
                    }
                } else {
                    emptyList()
                }
            }.toMutableList()
            addMovesToStack(moveStack, moves)
            DoubleValue.ONE
        }
        input.runtime.environment.query.addFunction("clear") { _ ->
            moveStack.clear()
            DoubleValue.ONE
        }
        expression.resolve(input.runtime)
    }
}

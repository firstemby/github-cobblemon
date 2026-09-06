/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.breeding.rules.moves

import com.cobblemon.mod.common.api.breeding.rule.layer.MovesBreedingRule
import com.cobblemon.mod.common.api.breeding.rule.layer.PokemonBreedingRule
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveObject

class InheritEggMovesBreedingRule : MovesBreedingRule {
    override val order = 0
    val parent: ExpressionLike = "q.mother".asExpressionLike()
    val includeBenchedMoves: ExpressionLike = "false".asExpressionLike()

    override fun apply(input: PokemonBreedingRule.Input, moveStack: MutableList<MutableList<MoveTemplate>>) {
        val parent = input.runtime.resolveObject(parent).obj as? Pokemon ?: return
        val includeBenchedMoves = input.runtime.resolveBoolean(includeBenchedMoves)
        val parentMoves = getKnownMoves(parent, includeBenchedMoves)
        addMovesToStack(moveStack, moves = input.offspring.form.moves.eggMoves.filter { it in parentMoves })
    }
}
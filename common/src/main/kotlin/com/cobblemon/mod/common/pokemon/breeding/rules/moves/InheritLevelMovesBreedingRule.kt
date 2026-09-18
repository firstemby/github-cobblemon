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
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.resolveBoolean

/**
 * Inherits moves that existing in the offspring's level-up learnset where
 * both of the parents know the move.
 *
 * @author Hiroku
 * @since April 3rd, 2026
 */
class InheritLevelMovesBreedingRule : MovesBreedingRule {
    override val order = 0
    var includeBenchedMoves: ExpressionLike = "false".asExpressionLike()

    override fun apply(input: PokemonBreedingRule.Input, moveStack: MutableList<MutableList<MoveTemplate>>) {
        val includeBenchedMoves = input.runtime.resolveBoolean(includeBenchedMoves)
        val levelMoves = input.offspring.form.moves.levelUpMoves.values.flatten()
        val fatherMoves = getKnownMoves(input.father, includeBenchedMoves)
        val motherMoves = getKnownMoves(input.mother, includeBenchedMoves)
        addMovesToStack(moveStack, moves = levelMoves.filter { it in fatherMoves && it in motherMoves }.toMutableList())
    }
}
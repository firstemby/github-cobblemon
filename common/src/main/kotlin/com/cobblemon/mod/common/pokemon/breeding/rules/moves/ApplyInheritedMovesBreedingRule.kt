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
import com.cobblemon.mod.common.api.moves.BenchedMove
import com.cobblemon.mod.common.api.moves.MoveSet
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.resolveBoolean

class ApplyInheritedMovesBreedingRule : MovesBreedingRule {
    override val order: Int = 0
    val shouldBenchRemainder: ExpressionLike = "false".asExpressionLike()

    override fun apply(input: PokemonBreedingRule.Input, moveStack: MutableList<MutableList<MoveTemplate>>) {
        val selectedMoves = mutableSetOf<MoveTemplate>()
        while (selectedMoves.size < 4 && moveStack.isNotEmpty()) {
            val layer = moveStack.last()
            val move = layer.random()
            layer.remove(move)
            if (layer.isEmpty()) {
                moveStack.removeLast()
            }
            selectedMoves.add(move)
        }

        val moveSet = MoveSet()
        for (selectedMove in selectedMoves) {
            moveSet.add(selectedMove.create())
        }
        if (moveSet[0] == null) {
            moveSet.add(Moves.getExceptional().create())
        }

        input.offspring.moveSet.copyFrom(moveSet)

        val shouldBenchRemainder = input.runtime.resolveBoolean(shouldBenchRemainder)
        if (shouldBenchRemainder) {
            val remainderMoves = moveStack.flatten().map { BenchedMove(moveTemplate = it, ppRaisedStages = 0) }
            input.offspring.benchedMoves.clear()
            input.offspring.benchedMoves.addAll(benchedMoves = remainderMoves)
        }
    }
}
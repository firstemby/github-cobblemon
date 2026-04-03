/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.breeding.rule.layer

import com.cobblemon.mod.common.api.breeding.rule.BreedingRule
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.breeding.rules.moves.ApplyInheritedMovesBreedingRule
import com.cobblemon.mod.common.pokemon.breeding.rules.moves.ExpressionMovesBreedingRule
import com.cobblemon.mod.common.pokemon.breeding.rules.moves.InheritEggMovesBreedingRule
import com.cobblemon.mod.common.pokemon.breeding.rules.moves.InheritLevelMovesBreedingRule
import com.cobblemon.mod.common.util.cobblemonResource

/**
 * A breeding rule that affects a Pokémon's learnset. These mostly work through the logic
 * of a 'stack' of move lists. The top of the stack is applied first, the bottom of the stack
 * is applied last. Each layer of the stack has some number of moves.
 *
 * @author Hiroku
 * @since April 3rd, 2026
 */
interface MovesBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf(
            cobblemonResource("expression") to ExpressionMovesBreedingRule::class.java,
            cobblemonResource("inherit_level_moves") to InheritLevelMovesBreedingRule::class.java,
            cobblemonResource("inherit_egg_moves") to InheritEggMovesBreedingRule::class.java,
            cobblemonResource("apply_inherited_moves") to ApplyInheritedMovesBreedingRule::class.java
        )
    }

    fun addMovesToStack(moveStack: MutableList<MutableList<MoveTemplate>>, moves: Collection<MoveTemplate>) {
        if (moves.isEmpty()) {
            return
        }
        moveStack.forEach { it.removeIf { it in moves } } // If a move is added higher in the stack, remove from lower in the stack so it doesn't try adding again
        moveStack.removeIf { it.isEmpty() }
        moveStack.add(moves.toMutableList())
    }

    fun getKnownMoves(parent: Pokemon, includeBenchedMoves: Boolean): Collection<MoveTemplate> {
        val moves = mutableSetOf<MoveTemplate>()
        moves.addAll(parent.moveSet.getMoves().map { it.template })
        if (includeBenchedMoves) {
            moves.addAll(parent.benchedMoves.map { it.moveTemplate })
        }
        return moves
    }

    fun apply(input: PokemonBreedingRule.Input, moveStack: MutableList<MutableList<MoveTemplate>>)
}
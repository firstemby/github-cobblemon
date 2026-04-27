/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.moves

import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.api.pokemon.moves.Learnset
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.util.weightedSelection

/**
 * A function that selects a move from a given [Learnset] for a particular [FormData] at a certain level,
 * taking into account any moves that have already been chosen for the moveset.
 *
 * @author Hiroku
 * @since December 5th, 2025
 */
fun interface MoveSelector {
    operator fun invoke(form: FormData, learnset: Learnset, level: Int, chosenMoves: Set<MoveTemplate>): MoveTemplate?

    companion object {
        val NONE = MoveSelector { _, _, _, _ -> null }
        val LAST_LEVELUP = MoveSelector { form, learnset, level, chosenMoves ->
            val levels = learnset.levelUpMoves.keys.filter { it <= level }.sortedDescending()
            levels.forEach {
                val moves = (learnset.levelUpMoves[it] ?: return@forEach) - chosenMoves
                if (moves.isNotEmpty()) {
                    return@MoveSelector moves.weightedSelection { it.getSelectionWeight(form) }
                }
            }
            return@MoveSelector null
        }
        val LAST_OFFENSIVE = MoveSelector { form, learnset, level, chosenMoves ->
            val levels = learnset.levelUpMoves.keys.filter { it <= level }.sortedDescending()
            levels.forEach {
                val moves = (learnset.levelUpMoves[it]?.filter { it.damageCategory != DamageCategories.STATUS } ?: return@forEach) - chosenMoves
                if (moves.isNotEmpty()) {
                    return@MoveSelector moves.weightedSelection { it.getSelectionWeight(form) }
                }
            }
            return@MoveSelector null
        }
        val LAST_SUITABLE_OFFENSIVE = MoveSelector { form, learnset, level, chosenMoves ->
            val levels = learnset.levelUpMoves.keys.filter { it <= level }.sortedDescending()
            val suitableCategories = if (form.baseStats[Stats.ATTACK]!! > form.baseStats[Stats.SPECIAL_ATTACK]!!) {
                setOf(DamageCategories.PHYSICAL)
            } else if (form.baseStats[Stats.SPECIAL_ATTACK]!! > form.baseStats[Stats.ATTACK]!!) {
                setOf(DamageCategories.SPECIAL)
            } else {
                setOf(DamageCategories.PHYSICAL, DamageCategories.SPECIAL)
            }
            levels.forEach {
                val moves = (learnset.levelUpMoves[it]?.filter { it.damageCategory in suitableCategories } ?: return@forEach) - chosenMoves
                if (moves.isNotEmpty()) {
                    return@MoveSelector moves.weightedSelection { it.getSelectionWeight(form) }
                }
            }
            return@MoveSelector null
        }
        val LAST_STATUS = MoveSelector { form, learnset, level, chosenMoves ->
            val levels = learnset.levelUpMoves.keys.filter { it <= level }.sortedDescending()
            levels.forEach {
                val moves = (learnset.levelUpMoves[it]?.filter { it.damageCategory == DamageCategories.STATUS } ?: return@forEach) - chosenMoves
                if (moves.isNotEmpty()) {
                    return@MoveSelector moves.weightedSelection { it.getSelectionWeight(form) }
                }
            }
            return@MoveSelector null
        }
        val STAB = MoveSelector { form, learnset, level, chosenMoves ->
            val moves = learnset.getLevelUpMovesUpTo(level) - chosenMoves
            val stabMoves = moves.filter { it.elementalType in form.types && it.damageCategory != DamageCategories.STATUS }
            return@MoveSelector stabMoves.weightedSelection { it.getSelectionWeight(form) }
        }
        val STAB_PHYSICAL = MoveSelector { form, learnset, level, chosenMoves ->
            val moves = learnset.getLevelUpMovesUpTo(level) - chosenMoves
            val stabMoves = moves.filter { it.elementalType in form.types && it.damageCategory == DamageCategories.PHYSICAL }
            return@MoveSelector stabMoves.weightedSelection { it.getSelectionWeight(form) }
        }
        val STAB_SPECIAL = MoveSelector { form, learnset, level, chosenMoves ->
            val moves = learnset.getLevelUpMovesUpTo(level) - chosenMoves
            val stabMoves = moves.filter { it.elementalType in form.types && it.damageCategory == DamageCategories.SPECIAL }
            return@MoveSelector stabMoves.weightedSelection { it.getSelectionWeight(form) }
        }
        val PHYSICAL = MoveSelector { form, learnset, level, chosenMoves ->
            val moves = learnset.getLevelUpMovesUpTo(level) - chosenMoves
            val physicalMoves = moves.filter { it.damageCategory == DamageCategories.PHYSICAL }
            return@MoveSelector physicalMoves.weightedSelection { it.getSelectionWeight(form) }
        }
        val SPECIAL = MoveSelector { form, learnset, level, chosenMoves ->
            val moves = learnset.getLevelUpMovesUpTo(level) - chosenMoves
            val specialMoves = moves.filter { it.damageCategory == DamageCategories.SPECIAL }
            return@MoveSelector specialMoves.weightedSelection { it.getSelectionWeight(form) }
        }
        val OFFENSIVE = MoveSelector { form, learnset, level, chosenMoves ->
            val moves = learnset.getLevelUpMovesUpTo(level) - chosenMoves
            val offensiveMoves = moves.filter { it.damageCategory != DamageCategories.STATUS }
            return@MoveSelector offensiveMoves.weightedSelection { it.getSelectionWeight(form) }
        }
        val RANDOM_LEVELUP = MoveSelector { form, learnset, level, chosenMoves ->
            val moves = learnset.getLevelUpMovesUpTo(level) - chosenMoves
            return@MoveSelector moves.weightedSelection { it.getSelectionWeight(form) }
        }
        val STATUS = MoveSelector { form, learnset, level, chosenMoves ->
            val moves = learnset.getLevelUpMovesUpTo(level) - chosenMoves
            val statusMoves = moves.filter { it.damageCategory == DamageCategories.STATUS }
            return@MoveSelector statusMoves.weightedSelection { it.getSelectionWeight(form) }
        }
        val TM = MoveSelector { form, learnset, level, chosenMoves ->
            val moves = learnset.tmMoves - chosenMoves - learnset.getLevelUpMovesUpTo(level)
            return@MoveSelector moves.weightedSelection { it.getSelectionWeight(form) }
        }
        val STAB_TM = MoveSelector { form, learnset, level, chosenMoves ->
            val moves = learnset.tmMoves - chosenMoves - learnset.getLevelUpMovesUpTo(level)
            val stabMoves = moves.filter { it.elementalType in form.types }
            return@MoveSelector stabMoves.weightedSelection { it.getSelectionWeight(form) }
        }
        val EGG = MoveSelector { form, learnset, level, chosenMoves ->
            val moves = learnset.eggMoves - chosenMoves - learnset.getLevelUpMovesUpTo(level)
            return@MoveSelector moves.weightedSelection { it.getSelectionWeight(form) }
        }

        val selectors = mutableMapOf(
            "none" to NONE,
            "last_levelup" to LAST_LEVELUP,
            "last_offensive" to LAST_OFFENSIVE,
            "last_suitable_offensive" to LAST_SUITABLE_OFFENSIVE,
            "last_status" to LAST_STATUS,
            "levelup" to RANDOM_LEVELUP,
            "stab" to STAB,
            "stab_physical" to STAB_PHYSICAL,
            "stab_special" to STAB_SPECIAL,
            "physical" to PHYSICAL,
            "special" to SPECIAL,
            "offensive" to OFFENSIVE,
            "status" to STATUS,
            "tm" to TM,
            "stab_tm" to STAB_TM,
            "egg" to EGG
        )
    }
}
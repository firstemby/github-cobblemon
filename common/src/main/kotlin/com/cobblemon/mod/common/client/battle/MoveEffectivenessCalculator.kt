/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.battle

import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes

object MoveEffectivenessCalculator {

    fun getMoveEffectiveness(attack: ElementalType, defenderType1: ElementalType, defenderType2: ElementalType?): Effectiveness {
        val type1Effectiveness = getEffectiveness(attack, defenderType1)
        val type2Effectiveness = if (defenderType2 == null) 1f else getEffectiveness(attack, defenderType2)
        val typeEffectiveness = type1Effectiveness * type2Effectiveness

        return when {
            typeEffectiveness == 0f -> Effectiveness.IMMUNE
            typeEffectiveness == 0.25f -> Effectiveness.MOSTLY_INEFFECTIVE
            typeEffectiveness == 0.5f -> Effectiveness.NOT_VERY_EFFECTIVE
            typeEffectiveness == 1f -> Effectiveness.EFFECTIVE
            typeEffectiveness == 2f -> Effectiveness.SUPER_EFFECTIVE
            typeEffectiveness == 4f -> Effectiveness.EXTREMELY_EFFECTIVE
            else -> Effectiveness.IMMUNE
        }
    }

    fun getEffectiveness(attack: ElementalType, defender: ElementalType): Float {
        if (isImmune(attack, defender)) return 0f
        if (isSuperEffective(attack, defender)) return 2f
        if (isNotVeryEffective(attack, defender)) return 0.5f
        return 1f
    }

    fun isImmune(attack: ElementalType, defender: ElementalType): Boolean {
        when (attack) {
            ElementalTypes.NORMAL -> return defender == ElementalTypes.GHOST
            ElementalTypes.FIRE -> return false
            ElementalTypes.WATER -> return false
            ElementalTypes.GRASS -> return false
            ElementalTypes.ELECTRIC -> return defender == ElementalTypes.GROUND
            ElementalTypes.ICE -> return false
            ElementalTypes.FIGHTING -> return defender == ElementalTypes.GHOST
            ElementalTypes.POISON -> return defender == ElementalTypes.STEEL
            ElementalTypes.GROUND -> return defender == ElementalTypes.FLYING
            ElementalTypes.FLYING -> return false
            ElementalTypes.PSYCHIC -> return defender == ElementalTypes.DARK
            ElementalTypes.BUG -> return false
            ElementalTypes.ROCK -> return false
            ElementalTypes.GHOST -> return defender == ElementalTypes.NORMAL
            ElementalTypes.DRAGON -> return defender == ElementalTypes.FAIRY
            ElementalTypes.DARK -> return false
            ElementalTypes.STEEL -> return false
            ElementalTypes.FAIRY -> return false
        }
        return false
    }

    fun isSuperEffective(attack: ElementalType, defender: ElementalType): Boolean {
        when (attack) {
            ElementalTypes.NORMAL -> return false
            ElementalTypes.FIRE -> return defender in listOf(ElementalTypes.GRASS, ElementalTypes.ICE, ElementalTypes.BUG, ElementalTypes.STEEL)
            ElementalTypes.WATER -> return defender in listOf(ElementalTypes.FIRE, ElementalTypes.GROUND, ElementalTypes.ROCK)
            ElementalTypes.GRASS -> return defender in listOf(ElementalTypes.WATER, ElementalTypes.GROUND, ElementalTypes.ROCK)
            ElementalTypes.ELECTRIC -> return defender in listOf(ElementalTypes.WATER, ElementalTypes.FLYING)
            ElementalTypes.ICE -> return defender in listOf(ElementalTypes.GRASS, ElementalTypes.GROUND, ElementalTypes.FLYING, ElementalTypes.DRAGON)
            ElementalTypes.FIGHTING -> return defender in listOf(ElementalTypes.NORMAL, ElementalTypes.ICE, ElementalTypes.ROCK, ElementalTypes.DARK, ElementalTypes.STEEL)
            ElementalTypes.POISON -> return defender in listOf(ElementalTypes.GRASS, ElementalTypes.FAIRY)
            ElementalTypes.GROUND -> return defender in listOf(ElementalTypes.FIRE, ElementalTypes.ELECTRIC, ElementalTypes.POISON, ElementalTypes.ROCK, ElementalTypes.STEEL)
            ElementalTypes.FLYING -> return defender in listOf(ElementalTypes.GRASS, ElementalTypes.FIGHTING, ElementalTypes.BUG)
            ElementalTypes.PSYCHIC -> return defender in listOf(ElementalTypes.FIGHTING, ElementalTypes.POISON)
            ElementalTypes.BUG -> return defender in listOf(ElementalTypes.GRASS, ElementalTypes.PSYCHIC, ElementalTypes.DARK)
            ElementalTypes.ROCK -> return defender in listOf(ElementalTypes.FIRE, ElementalTypes.ICE, ElementalTypes.FLYING, ElementalTypes.BUG)
            ElementalTypes.GHOST -> return defender in listOf(ElementalTypes.PSYCHIC, ElementalTypes.GHOST)
            ElementalTypes.DRAGON -> return defender in listOf(ElementalTypes.DRAGON)
            ElementalTypes.DARK -> return defender in listOf(ElementalTypes.PSYCHIC, ElementalTypes.GHOST)
            ElementalTypes.STEEL -> return defender in listOf(ElementalTypes.ICE, ElementalTypes.ROCK, ElementalTypes.FAIRY)
            ElementalTypes.FAIRY -> return defender in listOf(ElementalTypes.FIGHTING, ElementalTypes.DRAGON, ElementalTypes.DARK)
        }
        return false
    }

    fun isNotVeryEffective(attack: ElementalType, defender: ElementalType): Boolean {
        when (attack) {
            ElementalTypes.NORMAL -> return defender in listOf(ElementalTypes.ROCK, ElementalTypes.STEEL)
            ElementalTypes.FIRE -> return defender in listOf(ElementalTypes.FIRE, ElementalTypes.WATER, ElementalTypes.ROCK, ElementalTypes.DRAGON)
            ElementalTypes.WATER -> return defender in listOf(ElementalTypes.WATER, ElementalTypes.GRASS, ElementalTypes.DRAGON)
            ElementalTypes.GRASS -> return defender in listOf(ElementalTypes.FIRE, ElementalTypes.GRASS, ElementalTypes.POISON, ElementalTypes.FLYING, ElementalTypes.BUG, ElementalTypes.DRAGON, ElementalTypes.STEEL)
            ElementalTypes.ELECTRIC -> return defender in listOf(ElementalTypes.GRASS, ElementalTypes.ELECTRIC, ElementalTypes.DRAGON)
            ElementalTypes.ICE -> return defender in listOf(ElementalTypes.FIRE, ElementalTypes.WATER, ElementalTypes.ICE, ElementalTypes.STEEL)
            ElementalTypes.FIGHTING -> return defender in listOf(ElementalTypes.POISON, ElementalTypes.FLYING, ElementalTypes.PSYCHIC, ElementalTypes.BUG, ElementalTypes.FAIRY)
            ElementalTypes.POISON -> return defender in listOf(ElementalTypes.POISON, ElementalTypes.GROUND, ElementalTypes.ROCK, ElementalTypes.GHOST)
            ElementalTypes.GROUND -> return defender in listOf(ElementalTypes.GRASS, ElementalTypes.BUG)
            ElementalTypes.FLYING -> return defender in listOf(ElementalTypes.ELECTRIC, ElementalTypes.ROCK, ElementalTypes.STEEL)
            ElementalTypes.PSYCHIC -> return defender in listOf(ElementalTypes.PSYCHIC, ElementalTypes.STEEL)
            ElementalTypes.BUG -> return defender in listOf(ElementalTypes.FIRE, ElementalTypes.FIGHTING, ElementalTypes.POISON, ElementalTypes.FLYING, ElementalTypes.GHOST, ElementalTypes.STEEL, ElementalTypes.FAIRY)
            ElementalTypes.ROCK -> return defender in listOf(ElementalTypes.FIGHTING, ElementalTypes.GROUND, ElementalTypes.STEEL)
            ElementalTypes.GHOST -> return defender in listOf(ElementalTypes.DARK)
            ElementalTypes.DRAGON -> return defender in listOf(ElementalTypes.STEEL)
            ElementalTypes.DARK -> return defender in listOf(ElementalTypes.FIGHTING, ElementalTypes.DARK, ElementalTypes.FAIRY)
            ElementalTypes.STEEL -> return defender in listOf(ElementalTypes.FIRE, ElementalTypes.WATER, ElementalTypes.ELECTRIC, ElementalTypes.STEEL)
            ElementalTypes.FAIRY -> return defender in listOf(ElementalTypes.FIRE, ElementalTypes.POISON, ElementalTypes.STEEL)
        }
        return false
    }

}

enum class Effectiveness {
    EXTREMELY_EFFECTIVE,
    SUPER_EFFECTIVE,
    EFFECTIVE,
    NOT_VERY_EFFECTIVE,
    MOSTLY_INEFFECTIVE,
    IMMUNE
}
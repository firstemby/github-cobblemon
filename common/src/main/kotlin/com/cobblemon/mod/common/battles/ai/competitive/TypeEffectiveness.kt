package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes


object TypeEffectiveness {


    val typeEffectivenessMap: Map<ElementalType, Map<ElementalType, Double>> = mapOf(
        ElementalTypes.NORMAL to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 1.0,
            ElementalTypes.WATER to 1.0,
            ElementalTypes.ELECTRIC to 1.0,
            ElementalTypes.GRASS to 1.0,
            ElementalTypes.ICE to 1.0,
            ElementalTypes.FIGHTING to 1.0,
            ElementalTypes.POISON to 1.0,
            ElementalTypes.GROUND to 1.0,
            ElementalTypes.FLYING to 1.0,
            ElementalTypes.PSYCHIC to 1.0,
            ElementalTypes.BUG to 1.0,
            ElementalTypes.ROCK to 0.5,
            ElementalTypes.GHOST to 0.0,
            ElementalTypes.DRAGON to 1.0,
            ElementalTypes.DARK to 1.0,
            ElementalTypes.STEEL to 0.5,
            ElementalTypes.FAIRY to 1.0
        ),
        ElementalTypes.FIRE to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 0.5,
            ElementalTypes.WATER to 0.5,
            ElementalTypes.ELECTRIC to 1.0,
            ElementalTypes.GRASS to 2.0,
            ElementalTypes.ICE to 2.0,
            ElementalTypes.FIGHTING to 1.0,
            ElementalTypes.POISON to 1.0,
            ElementalTypes.GROUND to 1.0,
            ElementalTypes.FLYING to 1.0,
            ElementalTypes.PSYCHIC to 1.0,
            ElementalTypes.BUG to 2.0,
            ElementalTypes.ROCK to 0.5,
            ElementalTypes.GHOST to 1.0,
            ElementalTypes.DRAGON to 0.5,
            ElementalTypes.DARK to 1.0,
            ElementalTypes.STEEL to 2.0,
            ElementalTypes.FAIRY to 1.0
        ),
        ElementalTypes.WATER to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 2.0,
            ElementalTypes.WATER to 0.5,
            ElementalTypes.ELECTRIC to 1.0,
            ElementalTypes.GRASS to 0.5,
            ElementalTypes.ICE to 1.0,
            ElementalTypes.FIGHTING to 1.0,
            ElementalTypes.POISON to 1.0,
            ElementalTypes.GROUND to 2.0,
            ElementalTypes.FLYING to 1.0,
            ElementalTypes.PSYCHIC to 1.0,
            ElementalTypes.BUG to 1.0,
            ElementalTypes.ROCK to 2.0,
            ElementalTypes.GHOST to 1.0,
            ElementalTypes.DRAGON to 0.5,
            ElementalTypes.DARK to 1.0,
            ElementalTypes.STEEL to 1.0,
            ElementalTypes.FAIRY to 1.0
        ),
        ElementalTypes.ELECTRIC to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 1.0,
            ElementalTypes.WATER to 2.0,
            ElementalTypes.ELECTRIC to 0.5,
            ElementalTypes.GRASS to 0.5,
            ElementalTypes.ICE to 1.0,
            ElementalTypes.FIGHTING to 1.0,
            ElementalTypes.POISON to 1.0,
            ElementalTypes.GROUND to 0.0,
            ElementalTypes.FLYING to 2.0,
            ElementalTypes.PSYCHIC to 1.0,
            ElementalTypes.BUG to 1.0,
            ElementalTypes.ROCK to 1.0,
            ElementalTypes.GHOST to 1.0,
            ElementalTypes.DRAGON to 0.5,
            ElementalTypes.DARK to 1.0,
            ElementalTypes.STEEL to 1.0,
            ElementalTypes.FAIRY to 1.0
        ),
        ElementalTypes.GRASS to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 0.5,
            ElementalTypes.WATER to 2.0,
            ElementalTypes.ELECTRIC to 1.0,
            ElementalTypes.GRASS to 0.5,
            ElementalTypes.ICE to 1.0,
            ElementalTypes.FIGHTING to 1.0,
            ElementalTypes.POISON to 0.5,
            ElementalTypes.GROUND to 2.0,
            ElementalTypes.FLYING to 0.5,
            ElementalTypes.PSYCHIC to 1.0,
            ElementalTypes.BUG to 0.5,
            ElementalTypes.ROCK to 2.0,
            ElementalTypes.GHOST to 1.0,
            ElementalTypes.DRAGON to 0.5,
            ElementalTypes.DARK to 1.0,
            ElementalTypes.STEEL to 0.5,
            ElementalTypes.FAIRY to 1.0
        ),
        ElementalTypes.ICE to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 0.5,
            ElementalTypes.WATER to 0.5,
            ElementalTypes.ELECTRIC to 1.0,
            ElementalTypes.GRASS to 2.0,
            ElementalTypes.ICE to 0.5,
            ElementalTypes.FIGHTING to 1.0,
            ElementalTypes.POISON to 1.0,
            ElementalTypes.GROUND to 2.0,
            ElementalTypes.FLYING to 2.0,
            ElementalTypes.PSYCHIC to 1.0,
            ElementalTypes.BUG to 1.0,
            ElementalTypes.ROCK to 1.0,
            ElementalTypes.GHOST to 1.0,
            ElementalTypes.DRAGON to 2.0,
            ElementalTypes.DARK to 1.0,
            ElementalTypes.STEEL to 0.5,
            ElementalTypes.FAIRY to 1.0
        ),
        ElementalTypes.FIGHTING to mapOf(
            ElementalTypes.NORMAL to 2.0,
            ElementalTypes.FIRE to 1.0,
            ElementalTypes.WATER to 1.0,
            ElementalTypes.ELECTRIC to 1.0,
            ElementalTypes.GRASS to 1.0,
            ElementalTypes.ICE to 2.0,
            ElementalTypes.FIGHTING to 1.0,
            ElementalTypes.POISON to 0.5,
            ElementalTypes.GROUND to 1.0,
            ElementalTypes.FLYING to 0.5,
            ElementalTypes.PSYCHIC to 0.5,
            ElementalTypes.BUG to 0.5,
            ElementalTypes.ROCK to 2.0,
            ElementalTypes.GHOST to 0.0,
            ElementalTypes.DRAGON to 1.0,
            ElementalTypes.DARK to 2.0,
            ElementalTypes.STEEL to 2.0,
            ElementalTypes.FAIRY to 0.5
        ),
        ElementalTypes.POISON to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 1.0,
            ElementalTypes.WATER to 1.0,
            ElementalTypes.ELECTRIC to 1.0,
            ElementalTypes.GRASS to 2.0,
            ElementalTypes.ICE to 1.0,
            ElementalTypes.FIGHTING to 1.0,
            ElementalTypes.POISON to 0.5,
            ElementalTypes.GROUND to 0.5,
            ElementalTypes.FLYING to 1.0,
            ElementalTypes.PSYCHIC to 1.0,
            ElementalTypes.BUG to 1.0,
            ElementalTypes.ROCK to 0.5,
            ElementalTypes.GHOST to 0.5,
            ElementalTypes.DRAGON to 1.0,
            ElementalTypes.DARK to 1.0,
            ElementalTypes.STEEL to 0.0,
            ElementalTypes.FAIRY to 2.0
        ),
        ElementalTypes.GROUND to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 2.0,
            ElementalTypes.WATER to 1.0,
            ElementalTypes.ELECTRIC to 2.0,
            ElementalTypes.GRASS to 0.5,
            ElementalTypes.ICE to 1.0,
            ElementalTypes.FIGHTING to 1.0,
            ElementalTypes.POISON to 2.0,
            ElementalTypes.GROUND to 1.0,
            ElementalTypes.FLYING to 0.0,
            ElementalTypes.PSYCHIC to 1.0,
            ElementalTypes.BUG to 0.5,
            ElementalTypes.ROCK to 2.0,
            ElementalTypes.GHOST to 1.0,
            ElementalTypes.DRAGON to 1.0,
            ElementalTypes.DARK to 1.0,
            ElementalTypes.STEEL to 2.0,
            ElementalTypes.FAIRY to 1.0
        ),
        ElementalTypes.FLYING to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 1.0,
            ElementalTypes.WATER to 1.0,
            ElementalTypes.ELECTRIC to 0.5,
            ElementalTypes.GRASS to 2.0,
            ElementalTypes.ICE to 1.0,
            ElementalTypes.FIGHTING to 2.0,
            ElementalTypes.POISON to 1.0,
            ElementalTypes.GROUND to 1.0,
            ElementalTypes.FLYING to 1.0,
            ElementalTypes.PSYCHIC to 1.0,
            ElementalTypes.BUG to 2.0,
            ElementalTypes.ROCK to 0.5,
            ElementalTypes.GHOST to 1.0,
            ElementalTypes.DRAGON to 1.0,
            ElementalTypes.DARK to 1.0,
            ElementalTypes.STEEL to 0.5,
            ElementalTypes.FAIRY to 1.0
        ),
        ElementalTypes.PSYCHIC to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 1.0,
            ElementalTypes.WATER to 1.0,
            ElementalTypes.ELECTRIC to 1.0,
            ElementalTypes.GRASS to 1.0,
            ElementalTypes.ICE to 1.0,
            ElementalTypes.FIGHTING to 2.0,
            ElementalTypes.POISON to 2.0,
            ElementalTypes.GROUND to 1.0,
            ElementalTypes.FLYING to 1.0,
            ElementalTypes.PSYCHIC to 0.5,
            ElementalTypes.BUG to 1.0,
            ElementalTypes.ROCK to 1.0,
            ElementalTypes.GHOST to 1.0,
            ElementalTypes.DRAGON to 1.0,
            ElementalTypes.DARK to 0.0,
            ElementalTypes.STEEL to 0.5,
            ElementalTypes.FAIRY to 1.0
        ),
        ElementalTypes.BUG to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 0.5,
            ElementalTypes.WATER to 1.0,
            ElementalTypes.ELECTRIC to 1.0,
            ElementalTypes.GRASS to 2.0,
            ElementalTypes.ICE to 1.0,
            ElementalTypes.FIGHTING to 0.5,
            ElementalTypes.POISON to 0.5,
            ElementalTypes.GROUND to 1.0,
            ElementalTypes.FLYING to 0.5,
            ElementalTypes.PSYCHIC to 2.0,
            ElementalTypes.BUG to 1.0,
            ElementalTypes.ROCK to 1.0,
            ElementalTypes.GHOST to 0.5,
            ElementalTypes.DRAGON to 1.0,
            ElementalTypes.DARK to 2.0,
            ElementalTypes.STEEL to 0.5,
            ElementalTypes.FAIRY to 0.5
        ),
        ElementalTypes.ROCK to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 2.0,
            ElementalTypes.WATER to 1.0,
            ElementalTypes.ELECTRIC to 1.0,
            ElementalTypes.GRASS to 1.0,
            ElementalTypes.ICE to 2.0,
            ElementalTypes.FIGHTING to 0.5,
            ElementalTypes.POISON to 1.0,
            ElementalTypes.GROUND to 0.5,
            ElementalTypes.FLYING to 2.0,
            ElementalTypes.PSYCHIC to 1.0,
            ElementalTypes.BUG to 2.0,
            ElementalTypes.ROCK to 1.0,
            ElementalTypes.GHOST to 1.0,
            ElementalTypes.DRAGON to 1.0,
            ElementalTypes.DARK to 1.0,
            ElementalTypes.STEEL to 0.5,
            ElementalTypes.FAIRY to 1.0
        ),
        ElementalTypes.GHOST to mapOf(
            ElementalTypes.NORMAL to 0.0,
            ElementalTypes.FIRE to 1.0,
            ElementalTypes.WATER to 1.0,
            ElementalTypes.ELECTRIC to 1.0,
            ElementalTypes.GRASS to 1.0,
            ElementalTypes.ICE to 1.0,
            ElementalTypes.FIGHTING to 1.0,
            ElementalTypes.POISON to 1.0,
            ElementalTypes.GROUND to 1.0,
            ElementalTypes.FLYING to 1.0,
            ElementalTypes.PSYCHIC to 2.0,
            ElementalTypes.BUG to 1.0,
            ElementalTypes.ROCK to 1.0,
            ElementalTypes.GHOST to 2.0,
            ElementalTypes.DRAGON to 1.0,
            ElementalTypes.DARK to 0.5,
            ElementalTypes.STEEL to 1.0,
            ElementalTypes.FAIRY to 1.0
        ),
        ElementalTypes.DRAGON to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 1.0,
            ElementalTypes.WATER to 1.0,
            ElementalTypes.ELECTRIC to 1.0,
            ElementalTypes.GRASS to 1.0,
            ElementalTypes.ICE to 1.0,
            ElementalTypes.FIGHTING to 1.0,
            ElementalTypes.POISON to 1.0,
            ElementalTypes.GROUND to 1.0,
            ElementalTypes.FLYING to 1.0,
            ElementalTypes.PSYCHIC to 1.0,
            ElementalTypes.BUG to 1.0,
            ElementalTypes.ROCK to 1.0,
            ElementalTypes.GHOST to 1.0,
            ElementalTypes.DRAGON to 2.0,
            ElementalTypes.DARK to 1.0,
            ElementalTypes.STEEL to 0.5,
            ElementalTypes.FAIRY to 0.0
        ),
        ElementalTypes.DARK to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 1.0,
            ElementalTypes.WATER to 1.0,
            ElementalTypes.ELECTRIC to 1.0,
            ElementalTypes.GRASS to 1.0,
            ElementalTypes.ICE to 1.0,
            ElementalTypes.FIGHTING to 0.5,
            ElementalTypes.POISON to 1.0,
            ElementalTypes.GROUND to 1.0,
            ElementalTypes.FLYING to 1.0,
            ElementalTypes.PSYCHIC to 2.0,
            ElementalTypes.BUG to 1.0,
            ElementalTypes.ROCK to 1.0,
            ElementalTypes.GHOST to 2.0,
            ElementalTypes.DRAGON to 1.0,
            ElementalTypes.DARK to 0.5,
            ElementalTypes.STEEL to 1.0,
            ElementalTypes.FAIRY to 0.5
        ),
        ElementalTypes.STEEL to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 0.5,
            ElementalTypes.WATER to 0.5,
            ElementalTypes.ELECTRIC to 0.5,
            ElementalTypes.GRASS to 1.0,
            ElementalTypes.ICE to 2.0,
            ElementalTypes.FIGHTING to 1.0,
            ElementalTypes.POISON to 1.0,
            ElementalTypes.GROUND to 1.0,
            ElementalTypes.FLYING to 1.0,
            ElementalTypes.PSYCHIC to 1.0,
            ElementalTypes.BUG to 1.0,
            ElementalTypes.ROCK to 2.0,
            ElementalTypes.GHOST to 1.0,
            ElementalTypes.DRAGON to 1.0,
            ElementalTypes.DARK to 1.0,
            ElementalTypes.STEEL to 0.5,
            ElementalTypes.FAIRY to 2.0
        ),
        ElementalTypes.FAIRY to mapOf(
            ElementalTypes.NORMAL to 1.0,
            ElementalTypes.FIRE to 0.5,
            ElementalTypes.WATER to 1.0,
            ElementalTypes.ELECTRIC to 1.0,
            ElementalTypes.GRASS to 1.0,
            ElementalTypes.ICE to 1.0,
            ElementalTypes.FIGHTING to 2.0,
            ElementalTypes.POISON to 0.5,
            ElementalTypes.GROUND to 1.0,
            ElementalTypes.FLYING to 1.0,
            ElementalTypes.PSYCHIC to 1.0,
            ElementalTypes.BUG to 1.0,
            ElementalTypes.ROCK to 1.0,
            ElementalTypes.GHOST to 1.0,
            ElementalTypes.DRAGON to 2.0,
            ElementalTypes.DARK to 2.0,
            ElementalTypes.STEEL to 0.5,
            ElementalTypes.FAIRY to 1.0
        )
    )


    fun getDamageMultiplier(attackerType: ElementalType, defenderType: ElementalType): Double {
        return typeEffectivenessMap[attackerType]?.get(defenderType) ?: 1.0
    }


    fun calculateTypeEffectiveness(moveType: ElementalType, defenderTypes: List<String>): Double {
        var multiplier = 1.0
        for (typeString in defenderTypes) {
            val defenderType = ElementalTypes.get(typeString.lowercase())
            if (defenderType != null) {
                multiplier *= getDamageMultiplier(moveType, defenderType)
            }
        }
        return multiplier
    }


    fun getEffectivenessCategory(effectiveness: Double): EffectivenessCategory {
        return when {
            effectiveness == 0.0 -> EffectivenessCategory.IMMUNE
            effectiveness < 1.0 -> EffectivenessCategory.NOT_VERY_EFFECTIVE
            effectiveness == 1.0 -> EffectivenessCategory.NEUTRAL
            else -> EffectivenessCategory.SUPER_EFFECTIVE
        }
    }

    enum class EffectivenessCategory {
        IMMUNE, NOT_VERY_EFFECTIVE, NEUTRAL, SUPER_EFFECTIVE
    }
}

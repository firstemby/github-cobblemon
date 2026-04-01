package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility

object TypeEffectiveness {
    fun getDamageMultiplier(attackerType: ElementalType, defenderType: ElementalType): Double {
        return AIUtility.getDamageMultiplier(attackerType, defenderType)
    }

    fun calculateTypeEffectiveness(moveType: ElementalType, defenderTypes: List<String>): Double {
        var multiplier = 1.0
        for (typeString in defenderTypes) {
            val defenderType = ElementalTypes.get(typeString.lowercase()) ?: continue
            multiplier *= getDamageMultiplier(moveType, defenderType)
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
        IMMUNE,
        NOT_VERY_EFFECTIVE,
        NEUTRAL,
        SUPER_EFFECTIVE
    }
}

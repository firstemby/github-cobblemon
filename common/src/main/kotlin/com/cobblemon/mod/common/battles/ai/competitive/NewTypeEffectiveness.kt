package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.api.types.ElementalType

object NewTypeEffectiveness {
    fun getDamageMultiplier(attackerType: ElementalType, defenderType: ElementalType): Double =
        TypeEffectiveness.getDamageMultiplier(attackerType, defenderType)

    fun calculateTypeEffectiveness(moveType: ElementalType, defenderTypes: List<String>): Double =
        TypeEffectiveness.calculateTypeEffectiveness(moveType, defenderTypes)
}

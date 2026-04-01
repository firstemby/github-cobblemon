package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.battles.ai.strongBattleAI.BattleAITags

object AiBattleUtils {
    fun normalizeWeatherId(weather: String?): String? {
        val id = weather?.lowercase() ?: return null
        return when (id) {
            "sun", "sunny", "sunnyday", "harshsunlight", "desolateland" -> "sunnyday"
            "rain", "raindance", "heavyrain", "primordialsea", "raining" -> "raindance"
            "sand", "sandstorm" -> "sandstorm"
            "hail" -> "hail"
            "snow", "snowscape" -> "snow"
            else -> id
        }
    }

    fun hasStab(moveTypeName: String, pokemonTypes: List<String>): Boolean {
        val moveType = moveTypeName.lowercase()
        return pokemonTypes.any { it.lowercase() == moveType }
    }

    fun parseTagPercent(tag: String, prefix: String): Int? {
        if (!tag.startsWith(prefix)) {
            return null
        }
        return tag.removePrefix(prefix).toIntOrNull()
    }

    fun abilityHasTag(abilityId: String?, tag: String): Boolean {
        if (abilityId.isNullOrBlank()) {
            return false
        }
        return tag.lowercase() in BattleAITags.getAbilityTags(abilityId)
    }
}

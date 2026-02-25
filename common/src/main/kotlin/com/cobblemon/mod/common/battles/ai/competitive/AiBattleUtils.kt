package com.cobblemon.mod.common.battles.ai.competitive

object AiBattleUtils {
    fun normalizeWeatherId(weather: String?): String? {
        val id = weather?.lowercase() ?: return null
        return when (id) {
            "sun", "sunny", "sunnyday", "harshsunlight" -> "sunnyday"
            "rain", "raindance", "heavyrain" -> "raindance"
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
}

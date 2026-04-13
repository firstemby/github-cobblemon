package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.battles.ai.strongBattleAI.BattleAITags

object NewAiBattleUtils {
    private val percentSuffixRegex = Regex("_(\\d+)$")

    fun normalizeWeatherId(weather: String?): String? = AiBattleUtils.normalizeWeatherId(weather)

    fun hasStab(moveTypeName: String, pokemonTypes: List<String>): Boolean =
        AiBattleUtils.hasStab(moveTypeName, pokemonTypes)

    fun moveTags(moveId: String?): Set<String> = BattleAITags.getMoveTags(moveId)

    fun abilityTags(abilityId: String?): Set<String> = BattleAITags.getAbilityTags(abilityId)

    fun parseTagPercent(tag: String, prefix: String): Int? = AiBattleUtils.parseTagPercent(tag, prefix)

    fun parseTrailingPercent(tag: String): Int? =
        percentSuffixRegex.find(tag)?.groupValues?.getOrNull(1)?.toIntOrNull()

    fun hasTag(tags: Set<String>, tag: String): Boolean = tag.lowercase() in tags

    fun hasTagPrefix(tags: Set<String>, prefix: String): Boolean =
        tags.any { it.startsWith(prefix.lowercase()) }

    fun anyTag(tags: Set<String>, candidates: Set<String>): Boolean =
        candidates.any { it in tags }

    fun firstTagValue(tags: Set<String>, prefix: String): String? =
        tags.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
}

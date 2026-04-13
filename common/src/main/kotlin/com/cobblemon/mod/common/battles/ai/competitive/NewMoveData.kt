package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.pokemon.status.Statuses

object NewMoveData {
    private val statTagRegex = Regex("^stat_(atk|def|spatk|spdef|speed|accuracy|evasion)_(-?\\d+)(?:_(\\d+))?$")

    private val knownAntiHazardsFallback = listOf("rapidspin", "defog", "tidyup", "courtchange", "mortalspin")
    private val knownAntiBoostFallback = listOf("clearsmog", "haze", "topsyturvy", "roar", "whirlwind")
    private val knownWeatherAbilityFallback = mapOf(
        "swiftswim" to "rain",
        "raindish" to "rain",
        "dryskin" to "rain",
        "chlorophyll" to "sun",
        "leafguard" to "sun",
        "flowergift" to "sun",
        "solarpower" to "sun",
        "sandrush" to "sand",
        "sandveil" to "sand",
        "sandforce" to "sand",
        "icebody" to "hail",
        "snowcloak" to "snow"
    )

    private fun moveTags(moveId: String): Set<String> = NewAiBattleUtils.moveTags(moveId)

    private fun allMoveIds(): List<String> = Moves.all().map { it.name.lowercase() }

    val multiHitMoves: Map<String, Map<Int, Int>> by lazy {
        allMoveIds().mapNotNull { moveId ->
            val tags = moveTags(moveId)
            val min = tags.firstOrNull { it.startsWith("multi_hit_min_") }?.removePrefix("multi_hit_min_")?.toIntOrNull()
            val max = tags.firstOrNull { it.startsWith("multi_hit_max_") }?.removePrefix("multi_hit_max_")?.toIntOrNull()
            when {
                min != null && max != null -> moveId to mapOf(min to max)
                "multi_hit" in tags -> moveId to mapOf(2 to 5)
                else -> null
            }
        }.toMap()
    }

    private val statusByMoveId: Map<String, String> by lazy {
        allMoveIds().mapNotNull { moveId ->
            val tags = moveTags(moveId)
            when {
                tags.any { it.startsWith("sleep_") } -> moveId to Statuses.SLEEP.showdownName
                tags.any { it.startsWith("paralysis_") } -> moveId to Statuses.PARALYSIS.showdownName
                tags.any { it.startsWith("burn_") } -> moveId to Statuses.BURN.showdownName
                tags.any { it.startsWith("freeze_") } -> moveId to Statuses.FROZEN.showdownName
                tags.any { it.startsWith("toxic_") } -> moveId to Statuses.POISON_BADLY.showdownName
                tags.any { it.startsWith("poison_") } -> moveId to Statuses.POISON.showdownName
                tags.any { it.startsWith("confusion_") } -> moveId to Statuses.CONFUSE.showdownName
                tags.any { it.startsWith("attract_") } -> moveId to Statuses.ATTRACT.showdownName
                "partiallytrapped_100" in tags -> moveId to "partiallytrapped"
                "curse_100" in tags -> moveId to "cursed"
                "leechseed_100" in tags -> moveId to "leech"
                else -> null
            }
        }.toMap()
    }

    fun statusForMove(moveId: String): String? = statusByMoveId[moveId.lowercase()]

    fun isStatusMove(moveId: String): Boolean {
        val normalized = moveId.lowercase()
        val tags = moveTags(normalized)
        return "category_status" in tags || statusByMoveId.containsKey(normalized)
    }

    val boostFromMoves: Map<String, Map<Stat, Int>> by lazy {
        val result = mutableMapOf<String, MutableMap<Stat, Int>>()
        for (moveId in allMoveIds()) {
            val tags = moveTags(moveId)
            for (tag in tags) {
                val match = statTagRegex.find(tag) ?: continue
                val stat = when (match.groupValues[1]) {
                    "atk" -> Stats.ATTACK
                    "def" -> Stats.DEFENCE
                    "spatk" -> Stats.SPECIAL_ATTACK
                    "spdef" -> Stats.SPECIAL_DEFENCE
                    "speed" -> Stats.SPEED
                    "accuracy" -> Stats.ACCURACY
                    "evasion" -> Stats.EVASION
                    else -> continue
                }
                val stages = match.groupValues[2].toIntOrNull() ?: continue
                result.computeIfAbsent(moveId) { mutableMapOf() }[stat] = stages
            }
        }
        result
    }

    val weatherMoves: Map<String, String> by lazy {
        allMoveIds().mapNotNull { moveId ->
            val weatherTag = moveTags(moveId).firstOrNull { it.startsWith("sets_weather_") } ?: return@mapNotNull null
            moveId to weatherTag.removePrefix("sets_weather_")
        }.toMap()
    }

    val weatherDependentMoves: Map<String, String> by lazy {
        allMoveIds().mapNotNull { moveId ->
            val tags = moveTags(moveId)
            val required = tags.firstOrNull { it.startsWith("context_weather_") }?.removePrefix("context_weather_")
            when {
                required != null -> moveId to required
                "context_weather_sensitive" in tags -> moveId to "any"
                else -> null
            }
        }.toMap()
    }

    val weatherAbilities: Map<String, String> by lazy {
        val fromTags = Abilities.all()
            .map { it.name.lowercase() }
            .mapNotNull { abilityId ->
                val abilityTags = NewAiBattleUtils.abilityTags(abilityId)
                if (
                    "weather_setter" !in abilityTags &&
                    "weather_interaction" !in abilityTags &&
                    "self_chip_weather" !in abilityTags
                ) {
                    return@mapNotNull null
                }
                val weather = abilityTags.firstOrNull { it.startsWith("conditional_weather_") }
                    ?.removePrefix("conditional_weather_")
                    ?: "any"
                abilityId to weather
            }
            .toMap()
        knownWeatherAbilityFallback + fromTags
    }

    val entryHazards: List<String> by lazy {
        allMoveIds().filter { moveId ->
            val tags = moveTags(moveId)
            "hazard_set" in tags || tags.any { it.startsWith("sets_side_condition_") && it !in setOf("sets_side_condition_mist", "sets_side_condition_safeguard") }
        }
    }

    val antiHazardsMoves: List<String> by lazy {
        val tagged = allMoveIds().filter { moveId ->
            val tags = moveTags(moveId)
            "hazard_clear" in tags || "removes_hazards" in tags || "context_anti_hazard" in tags
        }.toSet()
        (tagged + knownAntiHazardsFallback).toList().filter { Moves.getByName(it) != null }
    }

    val antiBoostMoves: List<String> by lazy {
        val tagged = allMoveIds().filter { moveId ->
            val tags = moveTags(moveId)
            "clear_boosts" in tags || "anti_boost" in tags || "resets_stats" in tags || "phaze" in tags
        }.toSet()
        (tagged + knownAntiBoostFallback).toList().filter { Moves.getByName(it) != null }
    }

    val pivotMoves: List<String> by lazy {
        allMoveIds().filter { "pivot" in moveTags(it) }
    }

    val setupMoves: Set<String> by lazy {
        allMoveIds().filter { moveId ->
            val tags = moveTags(moveId)
            "screen_set" in tags ||
                "sets_terrain" in tags ||
                "sets_weather" in tags ||
                "sets_pseudo_weather" in tags ||
                tags.any { it.startsWith("stat_") && !it.contains("_-") && "target_self" in tags }
        }.toSet()
    }

    val selfRecoveryMoves: List<String> by lazy {
        allMoveIds().filter { moveId ->
            val tags = moveTags(moveId)
            tags.any { it.startsWith("heal_") } && "target_self" in tags
        }
    }

    val priorityMoves: List<String> by lazy {
        allMoveIds().filter { moveId ->
            moveTags(moveId).any { it.startsWith("priority_") && (it.removePrefix("priority_").toIntOrNull() ?: 0) > 0 }
        }
    }

    val recoilMoves: List<String> by lazy {
        allMoveIds().filter { moveId ->
            val tags = moveTags(moveId)
            "recoil_move" in tags || tags.any { it.startsWith("recoil_") }
        }
    }

    val selfKoMoves: List<String> by lazy {
        allMoveIds().filter { "self_ko" in moveTags(it) }
    }

    val crashRiskMoves: List<String> by lazy {
        allMoveIds().filter { "crash_risk" in moveTags(it) }
    }

    val highRiskMoves: List<String> by lazy {
        (recoilMoves + selfKoMoves + crashRiskMoves).distinct()
    }
}

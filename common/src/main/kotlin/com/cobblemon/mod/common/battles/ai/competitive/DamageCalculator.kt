package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.InBattleMove
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.registries.BuiltInRegistries
import java.util.*
import kotlin.math.max

class DamageCalculator(
    private val battle: PokemonBattle?,
    private val consumedItems: MutableMap<UUID, MutableList<String>> = mutableMapOf()
) {

    fun calculateDamage(
        move: InBattleMove,
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        weather: String? = null,
        criticalHit: Boolean = false,
        isSpreadMove: Boolean = false,
        targetCount: Int = 1
    ): Double {
        val moveData = Moves.getByName(move.id) ?: return 0.0
        var power = moveData.power.toDouble()

        if (power <= 0) return 0.0

        if (isSpreadMove && targetCount > 1) {
            power *= 0.75
        }

        val immunityMultiplier = checkAllImmunities(moveData, attacker, defender)
        if (immunityMultiplier == 0.0) return 0.0

        val level = attacker.pokemon?.level ?: 50
        val attackStat = getAttackStat(moveData, attacker)
        val defenseStat = getDefenseStat(moveData, defender)

        var damage = ((2.0 * level / 5.0 + 2.0) * power * (attackStat / defenseStat)) / 50.0 + 2.0

        damage *= calculateCriticalMultiplier(criticalHit)
        damage *= calculateSTABMultiplier(attacker, moveData)
        damage *= TypeEffectiveness.calculateTypeEffectiveness(moveData.elementalType, defender.currentTypes ?: listOf())
        damage *= calculateWeatherMultiplier(moveData, AiBattleUtils.normalizeWeatherId(weather))
        damage *= calculateAbilityMultiplier(attacker, defender, moveData)
        damage *= calculateItemMultiplier(attacker, defender, moveData)
        damage *= calculateBurnMultiplier(attacker, moveData)
        damage *= calculateScreenMultiplier(attacker, defender, moveData)
        damage *= calculateRandomFactor()
        damage = calculateMultiHitDamage(moveData, damage)

        val finalDamage = max(1.0, damage)

        if (finalDamage > 0) {
            trackConsumedItems(defender, moveData)
        }

        return finalDamage
    }

    private fun getAttackStat(moveData: MoveTemplate, attacker: TrackedPokemon): Double {
        val pokemon = attacker.pokemon ?: return 100.0

        val baseStat = if (moveData.damageCategory == DamageCategories.PHYSICAL) {
            getEffectiveStat(pokemon, Stats.ATTACK)
        } else {
            getEffectiveStat(pokemon, Stats.SPECIAL_ATTACK)
        }

        val boost = if (moveData.damageCategory == DamageCategories.PHYSICAL) {
            attacker.atkBoost
        } else {
            attacker.spaBoost
        }

        return applyStatBoosts(baseStat, boost)
    }

    private fun getDefenseStat(moveData: MoveTemplate, defender: TrackedPokemon): Double {
        val pokemon = defender.pokemon ?: return 100.0 

        val baseStat = if (moveData.damageCategory == DamageCategories.PHYSICAL) {
            getEffectiveStat(pokemon, Stats.DEFENCE)
        } else {
            getEffectiveStat(pokemon, Stats.SPECIAL_DEFENCE)
        }

        val boost = if (moveData.damageCategory == DamageCategories.PHYSICAL) {
            defender.defBoost
        } else {
            defender.spdBoost
        }

        return applyStatBoosts(baseStat, boost)
    }

     
    private fun applyStatBoosts(baseStat: Double, boost: Int): Double {
        val clampedBoost = boost.coerceIn(-6, 6)
        return if (clampedBoost >= 0) {
            baseStat * (2.0 + clampedBoost) / 2.0
        } else {
            baseStat * 2.0 / (2.0 - clampedBoost)
        }
    }

     
    private fun getEffectiveStat(pokemon: Pokemon, stat: Stat): Double {
        val base = pokemon.species.baseStats[stat] ?: 0
        val iv = pokemon.ivs[stat] ?: 31
        val ev = pokemon.evs[stat] ?: 0
        val level = pokemon.level

        return if (stat == Stats.HP) {
            ((2.0 * base + iv + ev / 4.0) * level / 100.0 + level + 10.0)
        } else {
            ((2.0 * base + iv + ev / 4.0) * level / 100.0 + 5.0)
        }
    }

    private fun checkAllImmunities(
        moveData: MoveTemplate,
        attacker: TrackedPokemon,
        defender: TrackedPokemon
    ): Double {
        if (checkTypeImmunity(moveData, defender)) return 0.0
        if (checkAbilityImmunity(moveData, attacker, defender)) return 0.0
        if (checkItemImmunity(moveData, defender)) return 0.0
        if (checkMoveSpecificImmunity(moveData, defender)) return 0.0
        return 1.0
    }

    private fun checkTypeImmunity(moveData: MoveTemplate, defender: TrackedPokemon): Boolean {
        val defenderTypes = (defender.currentTypes ?: defender.pokemon?.types?.map { it.name } ?: listOf())
            .map { it.lowercase() }

        return when (moveData.elementalType) {
            ElementalTypes.GROUND -> "flying" in defenderTypes
            ElementalTypes.ELECTRIC -> "ground" in defenderTypes
            ElementalTypes.NORMAL, ElementalTypes.FIGHTING -> "ghost" in defenderTypes
            ElementalTypes.PSYCHIC -> "dark" in defenderTypes
            ElementalTypes.POISON -> "steel" in defenderTypes
            ElementalTypes.DRAGON -> "fairy" in defenderTypes
            ElementalTypes.GHOST -> "normal" in defenderTypes
            else -> false
        }
    }

    private fun checkAbilityImmunity(
        moveData: MoveTemplate,
        attacker: TrackedPokemon,
        defender: TrackedPokemon
    ): Boolean {
        val defenderAbility = defender.currentAbility?.lowercase()
            ?: defender.pokemon?.ability?.name?.lowercase()
            ?: return false

        val attackerAbility = attacker.currentAbility?.lowercase()
            ?: attacker.pokemon?.ability?.name?.lowercase()
        if (attackerAbility in listOf("moldbreaker", "turboblaze", "teravolt")) {
            if (defenderAbility != "wonderguard") return false
        }

        return when (defenderAbility) {
            "levitate" -> moveData.elementalType == ElementalTypes.GROUND
            "voltabsorb", "lightningrod", "motordrive" -> moveData.elementalType == ElementalTypes.ELECTRIC
            "flashfire", "wellbakedbody" -> moveData.elementalType == ElementalTypes.FIRE
            "waterabsorb", "stormdrain", "dryskin" -> moveData.elementalType == ElementalTypes.WATER
            "sapsipper" -> moveData.elementalType == ElementalTypes.GRASS
            "eartheater" -> moveData.elementalType == ElementalTypes.GROUND
            "soundproof" -> isSoundMove(moveData)
            "bulletproof" -> isBallOrBombMove(moveData)
            "queenlymajesty", "dazzling" -> moveData.priority > 0
            "wonderguard" -> {
                val effectiveness = TypeEffectiveness.calculateTypeEffectiveness(
                    moveData.elementalType,
                    defender.currentTypes ?: listOf()
                )
                effectiveness <= 1.0
            }
            "goodasgold" -> moveData.power.toInt() == 0 
            else -> false
        }
    }

    private fun checkItemImmunity(moveData: MoveTemplate, defender: TrackedPokemon): Boolean {
        val defenderItem = defender.pokemon?.heldItem()?.item?.let {
            BuiltInRegistries.ITEM.getKey(it).toString()
        } ?: return false

        return when (defenderItem) {
            "cobblemon:air_balloon" -> {
                val notConsumed = consumedItems[defender.pokemon?.uuid]?.contains("airballoon") != true
                notConsumed && moveData.elementalType == ElementalTypes.GROUND
            }
            "cobblemon:safety_goggles" -> isPowderMove(moveData) || isWeatherDamageMove(moveData)
            else -> false
        }
    }

    private fun checkMoveSpecificImmunity(moveData: MoveTemplate, defender: TrackedPokemon): Boolean {
        val defenderTypes = (defender.currentTypes ?: defender.pokemon?.types?.map { it.name } ?: listOf())
            .map { it.lowercase() }
        val defenderAbility = defender.currentAbility?.lowercase()
            ?: defender.pokemon?.ability?.name?.lowercase()

        return when (moveData.name.lowercase()) {
            "thunderwave" -> {
                "electric" in defenderTypes || "ground" in defenderTypes ||
                defenderAbility in listOf("limber", "volt absorb", "lightningrod", "motordrive")
            }
            "willowisp" -> {
                "fire" in defenderTypes ||
                defenderAbility in listOf("waterveil", "waterbubble", "flashfire")
            }
            "toxic", "poisonpowder", "poisongas" -> {
                "poison" in defenderTypes || "steel" in defenderTypes ||
                defenderAbility in listOf("immunity", "poisonheal", "pastelveil")
            }
            "sleeppowder", "spore", "hypnosis", "sing" -> {
                defenderAbility in listOf("insomnia", "vitalspirit", "comatose", "sweetveil")
            }
            else -> false
        }
    }

    private fun calculateCriticalMultiplier(criticalHit: Boolean): Double {
        return if (criticalHit) 1.5 else 1.0
    }

    private fun calculateSTABMultiplier(attacker: TrackedPokemon, moveData: MoveTemplate): Double {
        val pokemonTypes = attacker.currentTypes ?: attacker.pokemon?.types?.map { it.name } ?: return 1.0
        return if (AiBattleUtils.hasStab(moveData.elementalType.name, pokemonTypes)) {
            if (attacker.currentAbility?.lowercase() == "adaptability") 2.0 else 1.5
        } else 1.0
    }

    private fun calculateWeatherMultiplier(moveData: MoveTemplate, weather: String?): Double {
        return when (weather) {
            "sunnyday" -> when {
                moveData.elementalType == ElementalTypes.FIRE -> 1.5
                moveData.elementalType == ElementalTypes.WATER && moveData.name != "hydrosteam" -> 0.5
                moveData.name == "hydrosteam" -> 1.5 
                else -> 1.0
            }
            "raindance" -> when {
                moveData.elementalType == ElementalTypes.WATER -> 1.5
                moveData.elementalType == ElementalTypes.FIRE -> 0.5
                else -> 1.0
            }
            "sandstorm", "hail", "snow" -> if (moveData.name == "weatherball") 2.0 else 1.0
            else -> 1.0
        }
    }

    private fun calculateAbilityMultiplier(
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        moveData: MoveTemplate
    ): Double {
        var multiplier = 1.0

        when (attacker.currentAbility?.lowercase()) {
            "hugepower", "purepower" -> {
                if (moveData.damageCategory == DamageCategories.PHYSICAL) multiplier *= 2.0
            }
            "guts" -> {
                if (attacker.pokemon?.status != null && moveData.damageCategory == DamageCategories.PHYSICAL) {
                    multiplier *= 1.5
                }
            }
            "overgrow" -> {
                if (moveData.elementalType == ElementalTypes.GRASS && attacker.currentHpPercent <= 0.33) {
                    multiplier *= 1.5
                }
            }
            "blaze" -> {
                if (moveData.elementalType == ElementalTypes.FIRE && attacker.currentHpPercent <= 0.33) {
                    multiplier *= 1.5
                }
            }
            "torrent" -> {
                if (moveData.elementalType == ElementalTypes.WATER && attacker.currentHpPercent <= 0.33) {
                    multiplier *= 1.5
                }
            }
            "swarm" -> {
                if (moveData.elementalType == ElementalTypes.BUG && attacker.currentHpPercent <= 0.33) {
                    multiplier *= 1.5
                }
            }
        }

        when (defender.currentAbility?.lowercase()) {
            "thickfat" -> {
                if (moveData.elementalType == ElementalTypes.FIRE || moveData.elementalType == ElementalTypes.ICE) {
                    multiplier *= 0.5
                }
            }
            "filter", "solidrock" -> {
                val effectiveness = TypeEffectiveness.calculateTypeEffectiveness(
                    moveData.elementalType,
                    defender.currentTypes ?: listOf()
                )
                if (effectiveness > 1.0) multiplier *= 0.75
            }
        }

        return multiplier
    }

    private fun calculateItemMultiplier(
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        moveData: MoveTemplate
    ): Double {
        var multiplier = 1.0

        val attackerItem = attacker.pokemon?.heldItem()?.item?.let {
            BuiltInRegistries.ITEM.getKey(it).toString()
        }

        when (attackerItem) {
            "cobblemon:choice_band" -> {
                if (moveData.damageCategory == DamageCategories.PHYSICAL) multiplier *= 1.5
            }
            "cobblemon:choice_specs" -> {
                if (moveData.damageCategory == DamageCategories.SPECIAL) multiplier *= 1.5
            }
            "cobblemon:life_orb" -> multiplier *= 1.3
        }

        return multiplier
    }

    private fun calculateBurnMultiplier(attacker: TrackedPokemon, moveData: MoveTemplate): Double {
        return if (attacker.pokemon?.status?.status?.showdownName == "burn" &&
            moveData.damageCategory == DamageCategories.PHYSICAL &&
            attacker.currentAbility != "guts") {
            0.5
        } else {
            1.0
        }
    }

    private fun calculateScreenMultiplier(
        attacker: TrackedPokemon,
        defender: TrackedPokemon,
        moveData: MoveTemplate
    ): Double {
        val attackerAbility = attacker.currentAbility?.lowercase() ?: attacker.pokemon?.ability?.name?.lowercase()
        if (attackerAbility == "infiltrator") {
            return 1.0
        }

        val sideConditions = defender.sideConditions.keys.map { it.lowercase() }

        val hasReflect = "reflect" in sideConditions
        val hasLightScreen = "lightscreen" in sideConditions || "light screen" in sideConditions
        val hasAuroraVeil = "auroraveil" in sideConditions || "aurora veil" in sideConditions

        val multiplier = when (moveData.damageCategory) {
            DamageCategories.PHYSICAL -> {
                when {
                    hasAuroraVeil -> 0.5
                    hasReflect -> 0.5
                    else -> 1.0
                }
            }
            DamageCategories.SPECIAL -> {
                when {
                    hasAuroraVeil -> 0.5
                    hasLightScreen -> 0.5
                    else -> 1.0
                }
            }
            else -> 1.0
        }

        return multiplier
    }

    private fun calculateRandomFactor(): Double = 0.925

    private fun calculateMultiHitDamage(moveData: MoveTemplate, baseDamage: Double): Double {
        val moveName = moveData.name.lowercase()

        return when {
            moveName == "populationbomb" -> {
                val accuracy = moveData.accuracy / 100.0
                var expectedDamage = 0.0
                var probability = 1.0

                for (hit in 1..10) {
                    expectedDamage += baseDamage * probability
                    probability *= accuracy
                }
                expectedDamage
            }

            moveName in listOf("triplekick", "tripleaxel") -> {
                val accuracy = moveData.accuracy / 100.0
                baseDamage * 1.0 +
                    baseDamage * 2.0 * accuracy +
                    baseDamage * 3.0 * accuracy * accuracy
            }

            moveName in MoveData.multiHitMoves.keys -> {
                val hitRange = MoveData.multiHitMoves[moveName]!!
                val minHits = hitRange.keys.minOrNull() ?: 1
                val maxHits = hitRange.values.maxOrNull() ?: 1

                when {
                    minHits == 2 && maxHits == 5 -> baseDamage * 3.0
                    minHits == 2 && maxHits == 2 -> baseDamage * 2.0
                    minHits == 3 && maxHits == 3 -> baseDamage * 3.0
                    else -> baseDamage * ((minHits + maxHits) / 2.0)
                }
            }

            else -> baseDamage
        }
    }

    private fun isSoundMove(moveData: MoveTemplate): Boolean {
        return moveData.name.lowercase() in listOf(
            "boomburst", "bugbuzz", "chatter", "clangoroussoul", "disarmingvoice",
            "echoedvoice", "grasswhistle", "growl", "hypervoice", "metalsound",
            "nobleroar", "overdrive", "perishsong", "relicsong", "roar", "round",
            "screech", "sing", "snore", "sparklingaria", "supersonic", "uproar"
        )
    }

    private fun isBallOrBombMove(moveData: MoveTemplate): Boolean {
        return moveData.name.lowercase() in listOf(
            "acidspray", "aurasphere", "barrage", "bulletseed", "eggbomb",
            "electroball", "energyball", "focusblast", "gyroball", "iceball",
            "magnetbomb", "mistball", "mudbomb", "octazooka", "pollenpuff",
            "rockblast", "searingshot", "seedbomb", "shadowball", "sludgebomb",
            "weatherball", "zapcannon"
        )
    }

    private fun isPowderMove(moveData: MoveTemplate): Boolean {
        return moveData.name.lowercase() in listOf(
            "sleeppowder", "stunspore", "poisonpowder", "cottonspore",
            "ragepowder", "spore"
        )
    }

    private fun isWeatherDamageMove(moveData: MoveTemplate): Boolean {
        return moveData.name.lowercase() in listOf("sandstorm", "hail")
    }

    private fun trackConsumedItems(defender: TrackedPokemon, moveData: MoveTemplate) {
        val pokemon = defender.pokemon ?: return
        val defenderItem = pokemon.heldItem()?.item?.let {
            BuiltInRegistries.ITEM.getKey(it).toString()
        } ?: return

        if (defenderItem == "cobblemon:air_balloon") {
            val alreadyConsumed = consumedItems[pokemon.uuid]?.contains("airballoon") == true
            if (!alreadyConsumed) {
                addConsumedItem(pokemon.uuid, "airballoon")
            }
        }
    }

    private fun addConsumedItem(pokemonUuid: UUID, item: String) {
        val items = consumedItems.getOrPut(pokemonUuid) { mutableListOf() }
        items.add(item)
    }

}

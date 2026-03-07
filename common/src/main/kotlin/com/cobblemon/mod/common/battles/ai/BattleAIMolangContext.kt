/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.ai

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.struct.ArrayStruct
import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.interpreter.BattleContext
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility
import com.cobblemon.mod.common.api.battles.ai.config.BagItemCategory
import com.cobblemon.mod.common.api.battles.ai.config.BattleBagState
import com.cobblemon.mod.common.api.battles.ai.config.HeldItemEffectsRegistry
import com.cobblemon.mod.common.api.battles.ai.config.MoveCategory
import com.cobblemon.mod.common.api.battles.ai.config.MoveCategoryMappings
import com.cobblemon.mod.common.api.battles.ai.config.Personality
import com.cobblemon.mod.common.pokemon.helditem.CobblemonHeldItemManager
import com.cobblemon.mod.common.api.molang.MoLangFunctions.addStandardFunctions
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.ActiveBattlePokemon
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.MoveTarget
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.battles.ShowdownMoveset
import com.cobblemon.mod.common.battles.InBattleMove
import java.util.UUID

/**
 * Context object that provides battle state for evaluating Molang expressions during AI decision-making.
 */
data class BattleAIMolangContext(
    val battle: PokemonBattle,
    val actor: BattleActor,
    val activePokemon: ActiveBattlePokemon?,
    val opponents: List<ActiveBattlePokemon>,
    val allies: List<ActiveBattlePokemon>,
    val availableSwitches: List<BattlePokemon>,
    val memory: AIBattleMemory,
    val personality: Personality = Personality.NEUTRAL,
    var currentMove: InBattleMove? = null,
    var currentTarget: ActiveBattlePokemon? = null,
    var switchCandidate: BattlePokemon? = null,
    val bagState: BattleBagState? = null
) {
    fun createRuntime(): MoLangRuntime {
        val runtime = MoLangRuntime().setup()

        // Add battle AI specific queries
        runtime.environment.query.addBattleAIFunctions(this)

        return runtime
    }

    fun applyTo(runtime: MoLangRuntime) {
        runtime.environment.query.addBattleAIFunctions(this)
    }

    fun withMove(move: InBattleMove, target: ActiveBattlePokemon? = opponents.firstOrNull()): BattleAIMolangContext {
        return this.copy(currentMove = move, currentTarget = target)
    }

    fun withSwitchCandidate(pokemon: BattlePokemon): BattleAIMolangContext {
        return this.copy(switchCandidate = pokemon)
    }

    companion object {
        fun fromBattleState(
            battle: PokemonBattle,
            actor: BattleActor,
            memory: AIBattleMemory,
            personality: Personality = Personality.NEUTRAL
        ): BattleAIMolangContext {
            val side = actor.getSide()
            val activePokemon = actor.activePokemon.firstOrNull()

            val opponents = battle.sides
                .filter { it != side }
                .flatMap { it.activePokemon }
                .filter { it.battlePokemon != null }

            val allies = side.activePokemon
                .filter { it != activePokemon && it.battlePokemon != null }

            val availableSwitches = actor.pokemonList
                .filter { it.canBeSentOut() && it.uuid != activePokemon?.battlePokemon?.uuid }

            return BattleAIMolangContext(
                battle = battle,
                actor = actor,
                activePokemon = activePokemon,
                opponents = opponents,
                allies = allies,
                availableSwitches = availableSwitches,
                memory = memory,
                personality = personality
            )
        }
    }
}

fun QueryStruct.addBattleAIFunctions(context: BattleAIMolangContext): QueryStruct {
    addFunction("pokemon") { createPokemonStruct(context) }
    addFunction("target") { createTargetStruct(context) }
    addFunction("opponent") { createTargetStruct(context) }
    addFunction("move") { createMoveStruct(context) }
    addFunction("battle") { createBattleStruct(context) }
    addFunction("memory") { createMemoryStruct(context) }
    addFunction("matchup") { createMatchupStruct(context) }
    addFunction("ally") { createAllyStruct(context) }
    addFunction("allies") { createAlliesArray(context) }
    addFunction("field") { createFieldStruct(context) }
    addFunction("team") { createTeamStruct(context) }

    addFunction("opponents_count") { DoubleValue(context.opponents.size.toDouble()) }
    addFunction("opponent_remaining_count") {
        // Count all remaining Pokemon on opponent's side (not just active ones)
        val opponentSide = context.battle.sides.firstOrNull { it != context.actor.getSide() }
        val remaining = opponentSide?.actors?.flatMap { it.pokemonList }?.count { it.health > 0 } ?: 0
        DoubleValue(remaining.toDouble())
    }
    addFunction("opponents_faster_count") {
        val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val ourSpeed = calculateEffectiveSpeed(pokemon, context)
        val fasterCount = context.opponents.count { opp ->
            val oppPokemon = opp.battlePokemon ?: return@count false
            calculateEffectiveSpeed(oppPokemon, context) > ourSpeed
        }
        DoubleValue(fasterCount.toDouble())
    }
    addFunction("opponents_physical_count") {
        val physicalCount = context.opponents.count { opp ->
            val pokemon = opp.battlePokemon ?: return@count false
            pokemon.originalPokemon.getStat(Stats.ATTACK) > pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
        }
        DoubleValue(physicalCount.toDouble())
    }
    addFunction("opponent_has_spread_move") {
        val hasSpread = context.opponents.any { opp ->
            val pokemon = opp.battlePokemon ?: return@any false
            pokemon.originalPokemon.moveSet.any { move ->
                move.template.target in SPREAD_TARGETS
            }
        }
        DoubleValue(hasSpread)
    }
    addFunction("opponent_has_priority") {
        val hasPriority = context.opponents.any { opp ->
            val pokemon = opp.battlePokemon ?: return@any false
            pokemon.originalPokemon.moveSet.any { move ->
                MoveCategoryMappings.hasCategory(move.name, MoveCategory.PRIORITY)
            }
        }
        DoubleValue(hasPriority)
    }
    addFunction("opponent_has_special_threat") {
        val hasSpecialThreat = context.opponents.any { opp ->
            val pokemon = opp.battlePokemon ?: return@any false
            pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK) > 100
        }
        DoubleValue(hasSpecialThreat)
    }
    addFunction("opponent_is_setting_up") {
        val isSettingUp = context.opponents.any { opp ->
            val pokemon = opp.battlePokemon ?: return@any false
            context.memory.getLastUsedMove(pokemon.uuid)?.let { lastMove ->
                MoveCategoryMappings.hasCategory(lastMove, MoveCategory.BUFF)
            } ?: false
        }
        DoubleValue(isSettingUp)
    }

    addFunction("personality") { createPersonalityStruct(context) }
    addFunction("switch_candidate") { createSwitchCandidateStruct(context) }
    addFunction("bag") { createBagStruct(context) }
    addFunction("item") { createHeldItemStruct(context) }

    return this
}

private fun createPokemonStruct(context: BattleAIMolangContext): QueryStruct {
    val struct = QueryStruct(hashMapOf())
    val pokemon = context.activePokemon?.battlePokemon ?: return struct

    struct.addFunction("id") { StringValue(pokemon.uuid.toString()) }
    struct.addFunction("species") { StringValue(pokemon.originalPokemon.species.name) }
    struct.addFunction("level") { DoubleValue(pokemon.originalPokemon.level.toDouble()) }
    struct.addFunction("current_hp") { DoubleValue(pokemon.health.toDouble()) }
    struct.addFunction("max_hp") { DoubleValue(pokemon.maxHealth.toDouble()) }
    struct.addFunction("current_hp_percent") {
        if (pokemon.maxHealth > 0) {
            DoubleValue(pokemon.health.toDouble() / pokemon.maxHealth.toDouble())
        } else {
            DoubleValue.ZERO
        }
    }

    // Types
    struct.addFunction("types") {
        pokemon.originalPokemon.types.map { it.name }.asArrayValue()
    }
    struct.addFunction("has_type") { params ->
        val typeName = params.getString(0).lowercase()
        val hasType = pokemon.originalPokemon.types.any { it.name.equals(typeName, ignoreCase = true) }
        DoubleValue(hasType)
    }

    struct.addFunction("ability") { StringValue(pokemon.originalPokemon.ability.name) }
    struct.addFunction("has_ability") { params ->
        val abilityName = params.getString(0).lowercase().replace(" ", "")
        val currentAbility = pokemon.originalPokemon.ability.name.lowercase().replace(" ", "")
        DoubleValue(currentAbility == abilityName)
    }

    struct.addFunction("stat_boosts") { createStatBoostsStruct(context, pokemon.uuid) }
    struct.addFunction("speed") { DoubleValue(pokemon.originalPokemon.getStat(Stats.SPEED).toDouble()) }
    struct.addFunction("attack") { DoubleValue(pokemon.originalPokemon.getStat(Stats.ATTACK).toDouble()) }
    struct.addFunction("defence") { DoubleValue(pokemon.originalPokemon.getStat(Stats.DEFENCE).toDouble()) }
    struct.addFunction("special_attack") { DoubleValue(pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK).toDouble()) }
    struct.addFunction("special_defence") { DoubleValue(pokemon.originalPokemon.getStat(Stats.SPECIAL_DEFENCE).toDouble()) }

    struct.addFunction("status") { StringValue(pokemon.originalPokemon.status?.status?.name?.path ?: "") }
    struct.addFunction("has_status") { DoubleValue(pokemon.originalPokemon.status != null) }

    val volatiles = pokemon.contextManager.get(BattleContext.Type.VOLATILE)?.map { it.id.lowercase() } ?: emptyList()
    struct.addFunction("volatiles") { volatiles.asArrayValue() }
    struct.addFunction("has_volatile") { params ->
        val volatileName = params.getString(0).lowercase()
        DoubleValue(volatiles.any { it.contains(volatileName) })
    }
    struct.addFunction("has_substitute") { DoubleValue(volatiles.any { it.contains("substitute") }) }
    struct.addFunction("has_confusion") { DoubleValue(volatiles.any { it.contains("confusion") }) }
    struct.addFunction("has_leech_seed") { DoubleValue(volatiles.any { it.contains("leechseed") }) }
    struct.addFunction("has_encore") { DoubleValue(volatiles.any { it.contains("encore") }) }
    struct.addFunction("has_taunt") { DoubleValue(volatiles.any { it.contains("taunt") }) }
    struct.addFunction("has_disable") { DoubleValue(volatiles.any { it.contains("disable") }) }
    struct.addFunction("has_torment") { DoubleValue(volatiles.any { it.contains("torment") }) }

    struct.addFunction("is_first_turn") {
        DoubleValue(context.memory.getTurnsOnField(pokemon.uuid) <= 1)
    }

    struct.addFunction("last_move") {
        StringValue(context.memory.getLastUsedMove(pokemon.uuid) ?: "")
    }

    val heldItem = pokemon.originalPokemon.heldItem()
    val heldItemName = if (!heldItem.isEmpty) {
        heldItem.item.toString().substringAfterLast(":").lowercase().replace("_", "").replace(" ", "")
    } else ""

    struct.addFunction("item") { StringValue(heldItemName) }
    struct.addFunction("has_item") { DoubleValue(!heldItem.isEmpty) }

    struct.addFunction("item_boosts_type") { params ->
        val typeName = params.getString(0).lowercase()
        if (heldItemName.isNotEmpty()) {
            DoubleValue(HeldItemEffectsRegistry.boostsType(heldItemName, typeName))
        } else DoubleValue.ZERO
    }

    struct.addFunction("item_type_boost_multiplier") { params ->
        val typeName = params.getString(0).lowercase()
        if (heldItemName.isNotEmpty()) {
            DoubleValue(HeldItemEffectsRegistry.getTypeMultiplier(heldItemName, typeName))
        } else DoubleValue(1.0)
    }

    struct.addFunction("item_boosted_type") {
        if (heldItemName.isNotEmpty()) {
            StringValue(HeldItemEffectsRegistry.getBoostedType(heldItemName) ?: "")
        } else StringValue("")
    }

    struct.addFunction("item_has_category") { params ->
        val categoryName = params.getString(0).lowercase()
        if (heldItemName.isEmpty()) return@addFunction DoubleValue.ZERO
        DoubleValue(HeldItemEffectsRegistry.hasCategory(heldItemName, categoryName))
    }

    struct.addFunction("item_is_choice") {
        DoubleValue(heldItemName.isNotEmpty() && HeldItemEffectsRegistry.isChoiceItem(heldItemName))
    }

    struct.addFunction("item_is_berry") {
        DoubleValue(heldItemName.isNotEmpty() && HeldItemEffectsRegistry.isBerry(heldItemName))
    }

    struct.addFunction("item_is_consumable") {
        DoubleValue(heldItemName.isNotEmpty() && HeldItemEffectsRegistry.isConsumable(heldItemName))
    }

    struct.addFunction("item_stat_multiplier") { params ->
        val statName = params.getString(0).lowercase().replace(" ", "")
        if (heldItemName.isEmpty()) return@addFunction DoubleValue(1.0)
        DoubleValue(HeldItemEffectsRegistry.getStatMultiplier(heldItemName, statName))
    }

    struct.addFunction("item_restricts_move") {
        DoubleValue(heldItemName.isNotEmpty() && HeldItemEffectsRegistry.restrictsMove(heldItemName))
    }

    struct.addFunction("item_recoil") {
        if (heldItemName.isNotEmpty()) {
            DoubleValue(HeldItemEffectsRegistry.getRecoil(heldItemName))
        } else DoubleValue.ZERO
    }

    struct.addFunction("is_choice_locked") {
        val choiceItems = setOf("choiceband", "choicescarf", "choicespecs")
        val hasChoiceItem = heldItemName in choiceItems
        val lastMove = context.memory.getLastUsedMove(pokemon.uuid)
        DoubleValue(hasChoiceItem && lastMove != null)
    }

    // Effective speed including stat boosts, abilities, items, weather
    struct.addFunction("effective_speed") {
        DoubleValue(calculateEffectiveSpeed(pokemon, context))
    }

    struct.addFunction("action_speed") {
        DoubleValue(pokemon.originalPokemon.getStat(Stats.SPEED).toDouble())
    }

    struct.addFunction("has_recovery_move") {
        val hasRecovery = pokemon.originalPokemon.moveSet.any { move ->
            MoveCategoryMappings.getCategories(move.name).contains(MoveCategory.HEAL)
        }
        DoubleValue(hasRecovery)
    }
    struct.addFunction("has_priority_move") {
        val hasPriority = pokemon.originalPokemon.moveSet.any { move ->
            MoveCategoryMappings.getCategories(move.name).contains(MoveCategory.PRIORITY)
        }
        DoubleValue(hasPriority)
    }
    struct.addFunction("has_pivot_move") {
        val hasPivot = pokemon.originalPokemon.moveSet.any { move ->
            MoveCategoryMappings.getCategories(move.name).contains(MoveCategory.PIVOT)
        }
        DoubleValue(hasPivot)
    }
    struct.addFunction("has_setup_move") {
        val hasSetup = pokemon.originalPokemon.moveSet.any { move ->
            MoveCategoryMappings.getCategories(move.name).contains(MoveCategory.BUFF)
        }
        DoubleValue(hasSetup)
    }
    struct.addFunction("has_hazard_move") {
        val hasHazard = pokemon.originalPokemon.moveSet.any { move ->
            MoveCategoryMappings.getCategories(move.name).contains(MoveCategory.HAZARD)
        }
        DoubleValue(hasHazard)
    }

    struct.addFunction("is_trapped") {
        DoubleValue(context.memory.isTrapped(pokemon.uuid))
    }

    struct.addFunction("has_move") { params ->
        val moveName = params.getString(0).lowercase().replace(" ", "")
        val hasMove = pokemon.originalPokemon.moveSet.any { move ->
            move.name.lowercase().replace(" ", "") == moveName
        }
        DoubleValue(hasMove)
    }

    struct.addFunction("turns_on_field") {
        DoubleValue(context.memory.getTurnsOnField(pokemon.uuid).toDouble())
    }

    struct.addFunction("protected_last_turn") {
        val lastMove = context.memory.getLastUsedMove(pokemon.uuid)
        val protectedLastTurn = lastMove != null && MoveCategoryMappings.hasCategory(lastMove, MoveCategory.PROTECTION)
        DoubleValue(protectedLastTurn)
    }

    struct.addFunction("is_grounded") {
        val isFlying = pokemon.originalPokemon.types.any { it.name.equals("flying", ignoreCase = true) }
        val hasLevitate = pokemon.originalPokemon.ability.name.equals("levitate", ignoreCase = true)
        val hasAirBalloon = heldItemName == "airballoon"
        val grounded = !isFlying && !hasLevitate && !hasAirBalloon
        DoubleValue(grounded)
    }

    struct.addFunction("is_win_condition") {
        val hasSetup = pokemon.originalPokemon.moveSet.any { move ->
            MoveCategoryMappings.hasCategory(move.name, MoveCategory.BUFF)
        }
        val highOffense = pokemon.originalPokemon.getStat(Stats.ATTACK) > 120 ||
                         pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK) > 120
        DoubleValue(hasSetup || highOffense)
    }

    struct.addFunction("is_threatening") {
        val attack = pokemon.originalPokemon.getStat(Stats.ATTACK)
        val spAttack = pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
        val threatening = attack > 100 || spAttack > 100
        DoubleValue(threatening)
    }

    struct.addFunction("is_targeted") {
        val target = context.opponents.firstOrNull()?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val ourTypes = pokemon.originalPokemon.types.map { it.name }
        val theirTypes = target.originalPokemon.types.map { it.name }
        var threatened = false
        for (theirType in theirTypes) {
            val eff = calculateTypeEffectiveness(theirType, ourTypes)
            if (eff >= 2.0) threatened = true
        }
        DoubleValue(threatened)
    }

    struct.addFunction("is_terastallized") {
        DoubleValue(context.memory.isTerastallized(pokemon.uuid))
    }
    struct.addFunction("is_mega_evolved") {
        DoubleValue(context.memory.isMegaEvolved(pokemon.uuid))
    }
    struct.addFunction("is_dynamaxed") {
        DoubleValue(context.memory.isDynamaxed(pokemon.uuid))
    }
    struct.addFunction("tera_type") {
        StringValue(context.memory.getTeraType(pokemon.uuid) ?: "")
    }

    struct.addFunction("can_tera") {
        if (context.memory.isTerastallized(pokemon.uuid)) return@addFunction DoubleValue.ZERO
        val moveset = getActiveMoveset(context, pokemon.uuid)
        DoubleValue(moveset?.canTerastallize != null)
    }
    struct.addFunction("can_mega") {
        if (context.memory.isMegaEvolved(pokemon.uuid)) return@addFunction DoubleValue.ZERO
        val moveset = getActiveMoveset(context, pokemon.uuid)
        DoubleValue(moveset?.canMegaEvo ?: false)
    }
    struct.addFunction("can_dynamax") {
        if (context.memory.isDynamaxed(pokemon.uuid)) return@addFunction DoubleValue.ZERO
        val moveset = getActiveMoveset(context, pokemon.uuid)
        DoubleValue(moveset?.canDynamax ?: false)
    }
    struct.addFunction("can_zmove") {
        val moveset = getActiveMoveset(context, pokemon.uuid)
        DoubleValue(moveset?.canZMove != null)
    }
    struct.addFunction("tera_type_advantageous") {
        val teraTypeName = pokemon.originalPokemon.teraType.name.lowercase()
        ElementalTypes.get(teraTypeName) ?: return@addFunction DoubleValue.ZERO
        val opponent = context.opponents.firstOrNull()?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val defTypes = opponent.originalPokemon.types.map { it.name.lowercase() }
        val effectiveness = calculateTypeEffectiveness(teraTypeName, defTypes)
        DoubleValue(effectiveness > 1.0)
    }
    struct.addFunction("tera_resists_incoming") {
        val teraTypeName = pokemon.originalPokemon.teraType.name.lowercase()
        ElementalTypes.get(teraTypeName) ?: return@addFunction DoubleValue.ZERO
        val opponent = context.opponents.firstOrNull()?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val resists = opponent.originalPokemon.types.any { oppType ->
            calculateTypeEffectiveness(oppType.name.lowercase(), listOf(teraTypeName)) < 1.0
        }
        DoubleValue(resists)
    }
    struct.addFunction("will_be_koed") {
        val target = context.opponents.firstOrNull()?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val lowHp = pokemon.health.toDouble() / pokemon.maxHealth.toDouble() < 0.3
        val theyAreFaster = calculateEffectiveSpeed(target, context) > calculateEffectiveSpeed(pokemon, context)
        DoubleValue(lowHp && theyAreFaster)
    }


    struct.addFunction("should_protect") {
        val hasProtect = pokemon.originalPokemon.moveSet.any { move ->
            MoveCategoryMappings.hasCategory(move.name, MoveCategory.PROTECTION)
        }
        if (!hasProtect) return@addFunction DoubleValue.ZERO

        val lastMove = context.memory.getLastUsedMove(pokemon.uuid)
        if (lastMove != null && MoveCategoryMappings.hasCategory(lastMove, MoveCategory.PROTECTION)) {
            return@addFunction DoubleValue(0.1)
        }

        var protectValue = 0.0

        for (opponent in context.opponents) {
            val oppPokemon = opponent.battlePokemon ?: continue

            val theirTypes = oppPokemon.originalPokemon.types.map { it.name }
            val ourTypes = pokemon.originalPokemon.types.map { it.name }
            for (theirType in theirTypes) {
                val eff = calculateTypeEffectiveness(theirType, ourTypes)
                if (eff >= 2.0) protectValue += 0.2
            }

            val maxOffense = maxOf(
                oppPokemon.originalPokemon.getStat(Stats.ATTACK),
                oppPokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
            )
            if (maxOffense > 120) protectValue += 0.15

            if (calculateEffectiveSpeed(oppPokemon, context) > calculateEffectiveSpeed(pokemon, context)) {
                protectValue += 0.1
            }
        }

        val hpPercent = pokemon.health.toDouble() / pokemon.maxHealth.toDouble()
        if (hpPercent < 0.4) protectValue += 0.2

        val totalBoosts = context.memory.getAllStatBoosts(pokemon.uuid).values.filter { it > 0 }.sum()
        if (totalBoosts >= 2) protectValue += 0.15

        if (context.allies.isNotEmpty()) {
            for (ally in context.allies) {
                val allyPokemon = ally.battlePokemon ?: continue
                val allyLastMove = context.memory.getLastUsedMove(allyPokemon.uuid)
                if (allyLastMove != null && (
                    MoveCategoryMappings.hasCategory(allyLastMove, MoveCategory.BUFF) ||
                    MoveCategoryMappings.hasCategory(allyLastMove, MoveCategory.SPEED_CONTROL)
                )) {
                    protectValue += 0.1
                }
            }
        }

        DoubleValue(protectValue.coerceIn(0.0, 0.9))
    }

    struct.addFunction("under_threat") {
        var threatLevel = 0.0

        for (opponent in context.opponents) {
            val oppPokemon = opponent.battlePokemon ?: continue

            val theirTypes = oppPokemon.originalPokemon.types.map { it.name }
            val ourTypes = pokemon.originalPokemon.types.map { it.name }
            for (theirType in theirTypes) {
                val eff = calculateTypeEffectiveness(theirType, ourTypes)
                if (eff >= 4.0) threatLevel += 0.5
                else if (eff >= 2.0) threatLevel += 0.25
            }

            if (calculateEffectiveSpeed(oppPokemon, context) > calculateEffectiveSpeed(pokemon, context)) {
                threatLevel += 0.1
            }

            val oppBoosts = maxOf(
                context.memory.getStatBoost(oppPokemon.uuid, "atk"),
                context.memory.getStatBoost(oppPokemon.uuid, "spa")
            )
            if (oppBoosts >= 2) threatLevel += 0.2
        }

        val hpPercent = pokemon.health.toDouble() / pokemon.maxHealth.toDouble()
        if (hpPercent < 0.5) threatLevel *= 1.3
        if (hpPercent < 0.25) threatLevel *= 1.3

        DoubleValue(threatLevel.coerceIn(0.0, 1.0))
    }

    struct.addFunction("opponents_likely_to_protect") {
        if (context.opponents.isEmpty()) return@addFunction DoubleValue.ZERO

        var totalProtectChance = 0.0

        for (opponent in context.opponents) {
            val oppPokemon = opponent.battlePokemon ?: continue

            val hasProtect = oppPokemon.originalPokemon.moveSet.any { move ->
                MoveCategoryMappings.hasCategory(move.name, MoveCategory.PROTECTION)
            }
            if (!hasProtect) continue

            var protectChance = 0.15

            val lastMove = context.memory.getLastUsedMove(oppPokemon.uuid)
            if (lastMove != null && MoveCategoryMappings.hasCategory(lastMove, MoveCategory.PROTECTION)) {
                protectChance = 0.05
            } else {
                val hpPercent = oppPokemon.health.toDouble() / oppPokemon.maxHealth.toDouble()
                if (hpPercent < 0.4) protectChance += 0.2
                if (context.memory.getTurnsOnField(oppPokemon.uuid) <= 1) protectChance += 0.15
            }

            totalProtectChance += protectChance
        }

        val avgChance = totalProtectChance / context.opponents.size
        DoubleValue(avgChance.coerceIn(0.0, 0.8))
    }

    struct.addFunction("se_threat_count") {
        var count = 0
        val ourTypes = pokemon.originalPokemon.types.map { it.name }

        for (opponent in context.opponents) {
            val oppPokemon = opponent.battlePokemon ?: continue
            val theirTypes = oppPokemon.originalPokemon.types.map { it.name }

            for (theirType in theirTypes) {
                val eff = calculateTypeEffectiveness(theirType, ourTypes)
                if (eff >= 2.0) {
                    count++
                    break // Only count each opponent once
                }
            }
        }

        DoubleValue(count.toDouble())
    }

    struct.addFunction("should_switch") {
        if (context.memory.isTrapped(pokemon.uuid)) return@addFunction DoubleValue.ZERO
        if (context.availableSwitches.isEmpty()) return@addFunction DoubleValue.ZERO

        var switchValue = 0.0

        val ourTypes = pokemon.originalPokemon.types.map { it.name }
        for (opponent in context.opponents) {
            val oppPokemon = opponent.battlePokemon ?: continue
            val theirTypes = oppPokemon.originalPokemon.types.map { it.name }
            for (theirType in theirTypes) {
                val eff = calculateTypeEffectiveness(theirType, ourTypes)
                if (eff >= 4.0) switchValue += 0.4
                else if (eff >= 2.0) switchValue += 0.2
            }
        }

        val hpPercent = pokemon.health.toDouble() / pokemon.maxHealth.toDouble()
        if (hpPercent < 0.25) switchValue += 0.15

        val negativeBoosts = context.memory.getAllStatBoosts(pokemon.uuid).values.filter { it < 0 }.sum()
        if (negativeBoosts <= -2) switchValue += 0.1

        val status = pokemon.originalPokemon.status?.status?.showdownName
        val isPhysical = pokemon.originalPokemon.getStat(Stats.ATTACK) > pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
        if (status == "brn" && isPhysical) switchValue += 0.2

        DoubleValue(switchValue.coerceIn(0.0, 0.8))
    }

    struct.addFunction("used_ally_switch_recently") { DoubleValue.ZERO }
    struct.addFunction("held_item") { StringValue(heldItemName) }

    return struct
}

private fun createTargetStruct(context: BattleAIMolangContext): QueryStruct {
    val struct = QueryStruct(hashMapOf())
    val target = context.currentTarget ?: context.opponents.firstOrNull()
    val pokemon = target?.battlePokemon ?: return struct

    struct.addFunction("id") { StringValue(pokemon.uuid.toString()) }
    struct.addFunction("species") { StringValue(pokemon.originalPokemon.species.name) }
    struct.addFunction("level") { DoubleValue(pokemon.originalPokemon.level.toDouble()) }
    struct.addFunction("current_hp") { DoubleValue(pokemon.health.toDouble()) }
    struct.addFunction("max_hp") { DoubleValue(pokemon.maxHealth.toDouble()) }
    struct.addFunction("current_hp_percent") {
        if (pokemon.maxHealth > 0) {
            DoubleValue(pokemon.health.toDouble() / pokemon.maxHealth.toDouble())
        } else {
            DoubleValue.ZERO
        }
    }

    // Types
    struct.addFunction("types") {
        pokemon.originalPokemon.types.map { it.name }.asArrayValue()
    }
    struct.addFunction("has_type") { params ->
        val typeName = params.getString(0).lowercase()
        val hasType = pokemon.originalPokemon.types.any { it.name.equals(typeName, ignoreCase = true) }
        DoubleValue(hasType)
    }

    struct.addFunction("revealed_ability") {
        StringValue(context.memory.getRevealedAbility(pokemon.uuid) ?: "")
    }
    struct.addFunction("has_revealed_ability") { params ->
        val abilityName = params.getString(0)
        DoubleValue(context.memory.hasRevealedAbility(pokemon.uuid, abilityName))
    }

    struct.addFunction("revealed_item") {
        StringValue(context.memory.getRevealedItem(pokemon.uuid) ?: "")
    }

    struct.addFunction("status") { StringValue(pokemon.originalPokemon.status?.status?.name?.path ?: "") }
    struct.addFunction("has_status") { DoubleValue(pokemon.originalPokemon.status != null) }
    struct.addFunction("stat_boosts") { createStatBoostsStruct(context, pokemon.uuid) }

    struct.addFunction("predicted_speed") {
        DoubleValue(pokemon.originalPokemon.getStat(Stats.SPEED).toDouble())
    }
    struct.addFunction("speed") {
        DoubleValue(pokemon.originalPokemon.getStat(Stats.SPEED).toDouble())
    }

    struct.addFunction("attack") {
        DoubleValue(pokemon.originalPokemon.getStat(Stats.ATTACK).toDouble())
    }
    struct.addFunction("special_attack") {
        DoubleValue(pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK).toDouble())
    }
    struct.addFunction("defence") {
        DoubleValue(pokemon.originalPokemon.getStat(Stats.DEFENCE).toDouble())
    }
    struct.addFunction("special_defence") {
        DoubleValue(pokemon.originalPokemon.getStat(Stats.SPECIAL_DEFENCE).toDouble())
    }

    struct.addFunction("is_physical_attacker") {
        val atk = pokemon.originalPokemon.getStat(Stats.ATTACK)
        val spa = pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
        DoubleValue(atk > spa)
    }

    struct.addFunction("is_dynamaxed") {
        DoubleValue(context.memory.isDynamaxed(pokemon.uuid))
    }
    struct.addFunction("is_terastallized") {
        DoubleValue(context.memory.isTerastallized(pokemon.uuid))
    }
    struct.addFunction("is_mega_evolved") {
        DoubleValue(context.memory.isMegaEvolved(pokemon.uuid))
    }
    struct.addFunction("tera_type") {
        StringValue(context.memory.getTeraType(pokemon.uuid) ?: "")
    }

    struct.addFunction("item_consumed") {
        DoubleValue(context.memory.hasItemBeenConsumed(pokemon.uuid))
    }

    struct.addFunction("is_threatening") {
        val atk = pokemon.originalPokemon.getStat(Stats.ATTACK)
        val spa = pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
        DoubleValue(atk > 100 || spa > 100)
    }

    struct.addFunction("is_biggest_threat") {
        val targetMaxOffense = maxOf(
            pokemon.originalPokemon.getStat(Stats.ATTACK),
            pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
        )
        val isBiggest = context.opponents.all { opp ->
            val oppPokemon = opp.battlePokemon ?: return@all true
            val oppMaxOffense = maxOf(
                oppPokemon.originalPokemon.getStat(Stats.ATTACK),
                oppPokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
            )
            targetMaxOffense >= oppMaxOffense
        }
        DoubleValue(isBiggest)
    }

    struct.addFunction("will_attack") {
        val hasDamagingMove = pokemon.originalPokemon.moveSet.any { move ->
            val template = Moves.getByName(move.name)
            template != null && template.power > 0
        }
        DoubleValue(hasDamagingMove)
    }

    struct.addFunction("used_setup_move") {
        val lastMove = context.memory.getLastUsedMove(pokemon.uuid)
        val usedSetup = lastMove != null && MoveCategoryMappings.hasCategory(lastMove, MoveCategory.BUFF)
        DoubleValue(usedSetup)
    }

    struct.addFunction("is_support") {
        val hasRedirection = pokemon.originalPokemon.moveSet.any { move ->
            MoveCategoryMappings.hasCategory(move.name, MoveCategory.REDIRECTION)
        }
        val hasFlinchSupport = pokemon.originalPokemon.moveSet.any { move ->
            MoveCategoryMappings.hasCategory(move.name, MoveCategory.FLINCH)
        }
        val lowOffense = pokemon.originalPokemon.getStat(Stats.ATTACK) < 80 &&
                        pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK) < 80
        DoubleValue(hasRedirection || hasFlinchSupport || lowOffense)
    }


    struct.addFunction("likely_to_protect") {
        val hasProtect = pokemon.originalPokemon.moveSet.any { move ->
            MoveCategoryMappings.hasCategory(move.name, MoveCategory.PROTECTION)
        }
        if (!hasProtect) return@addFunction DoubleValue.ZERO

        var protectChance = 0.0

        val lastMove = context.memory.getLastUsedMove(pokemon.uuid)
        if (lastMove != null && MoveCategoryMappings.hasCategory(lastMove, MoveCategory.PROTECTION)) {
            return@addFunction DoubleValue(0.1)
        }

        val hpPercent = pokemon.health.toDouble() / pokemon.maxHealth.toDouble()
        if (hpPercent < 0.3) protectChance += 0.3

        if (context.allies.isNotEmpty()) protectChance += 0.15

        val turnsOnField = context.memory.getTurnsOnField(pokemon.uuid)
        if (turnsOnField <= 1) protectChance += 0.2

        val totalBoosts = context.memory.getAllStatBoosts(pokemon.uuid).values.filter { it > 0 }.sum()
        if (totalBoosts >= 2) protectChance += 0.15

        val threateningOpponents = context.opponents.count { opp ->
            val oppPokemon = opp.battlePokemon ?: return@count false
            val maxOffense = maxOf(
                oppPokemon.originalPokemon.getStat(Stats.ATTACK),
                oppPokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
            )
            maxOffense > 100
        }
        if (threateningOpponents >= 2) protectChance += 0.2

        DoubleValue(protectChance.coerceIn(0.0, 0.9))
    }

    struct.addFunction("likely_to_switch") {
        if (context.memory.isTrapped(pokemon.uuid)) {
            return@addFunction DoubleValue.ZERO
        }

        var switchChance = 0.0

        val hpPercent = pokemon.health.toDouble() / pokemon.maxHealth.toDouble()
        if (hpPercent < 0.25) switchChance += 0.25

        val ourPokemon = context.activePokemon?.battlePokemon
        if (ourPokemon != null) {
            val ourTypes = ourPokemon.originalPokemon.types.map { it.name }
            val targetTypes = pokemon.originalPokemon.types.map { it.name }
            for (ourType in ourTypes) {
                val effectiveness = calculateTypeEffectiveness(ourType, targetTypes)
                if (effectiveness >= 2.0) switchChance += 0.2
            }
        }

        val negativeBoosts = context.memory.getAllStatBoosts(pokemon.uuid).values.filter { it < 0 }.sum()
        if (negativeBoosts <= -2) switchChance += 0.15

        val hasPivotMove = pokemon.originalPokemon.moveSet.any { move ->
            MoveCategoryMappings.hasCategory(move.name, MoveCategory.PIVOT)
        }
        if (hasPivotMove) switchChance += 0.1

        val status = pokemon.originalPokemon.status?.status?.showdownName
        val isPhysical = pokemon.originalPokemon.getStat(Stats.ATTACK) > pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
        if (status == "brn" && isPhysical) switchChance += 0.2
        if (status == "par") switchChance += 0.1

        DoubleValue(switchChance.coerceIn(0.0, 0.8))
    }

    struct.addFunction("threat_to_active") {
        val ourPokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue(0.5)
        var threatLevel = 0.0

        val targetTypes = pokemon.originalPokemon.types.map { it.name }
        val ourTypes = ourPokemon.originalPokemon.types.map { it.name }

        var maxEffectiveness = 1.0
        for (targetType in targetTypes) {
            val effectiveness = calculateTypeEffectiveness(targetType, ourTypes)
            if (effectiveness > maxEffectiveness) maxEffectiveness = effectiveness
        }

        threatLevel += when {
            maxEffectiveness >= 4.0 -> 0.5
            maxEffectiveness >= 2.0 -> 0.35
            maxEffectiveness >= 1.0 -> 0.15
            else -> 0.0
        }

        val maxOffense = maxOf(
            pokemon.originalPokemon.getStat(Stats.ATTACK),
            pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
        )
        threatLevel += when {
            maxOffense >= 150 -> 0.25
            maxOffense >= 120 -> 0.15
            maxOffense >= 100 -> 0.1
            else -> 0.0
        }

        val targetSpeed = calculateEffectiveSpeed(pokemon, context)
        val ourSpeed = calculateEffectiveSpeed(ourPokemon, context)
        if (targetSpeed > ourSpeed) threatLevel += 0.15

        val offensiveBoosts = maxOf(
            context.memory.getStatBoost(pokemon.uuid, "atk"),
            context.memory.getStatBoost(pokemon.uuid, "spa")
        )
        if (offensiveBoosts >= 2) threatLevel += 0.15
        else if (offensiveBoosts >= 1) threatLevel += 0.05

        val ourHpPercent = ourPokemon.health.toDouble() / ourPokemon.maxHealth.toDouble()
        if (ourHpPercent < 0.5) threatLevel += 0.1
        if (ourHpPercent < 0.25) threatLevel += 0.1

        DoubleValue(threatLevel.coerceIn(0.0, 1.0))
    }

    struct.addFunction("can_ko_active") {
        val ourPokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue.ZERO

        val targetTypes = pokemon.originalPokemon.types.map { it.name }
        val ourTypes = ourPokemon.originalPokemon.types.map { it.name }

        var bestDamageEstimate = 0.0

        for (move in pokemon.originalPokemon.moveSet.getMoves()) {
            val template = Moves.getByName(move.name) ?: continue
            if (template.power <= 0) continue

            val moveType = template.elementalType.name
            val effectiveness = calculateTypeEffectiveness(moveType, ourTypes)

            val isStab = targetTypes.any { it.equals(moveType, ignoreCase = true) }
            val stabMod = if (isStab) 1.5 else 1.0

            val isPhysical = template.damageCategory.name == "physical"
            val attack = if (isPhysical) pokemon.originalPokemon.getStat(Stats.ATTACK)
                        else pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
            val defense = if (isPhysical) ourPokemon.originalPokemon.getStat(Stats.DEFENCE)
                         else ourPokemon.originalPokemon.getStat(Stats.SPECIAL_DEFENCE)

            val level = pokemon.originalPokemon.level.toDouble()
            val baseDamage = ((2.0 * level / 5.0 + 2.0) * template.power * attack / defense / 50.0 + 2.0)
            val estimatedDamage = baseDamage * stabMod * effectiveness

            if (estimatedDamage > bestDamageEstimate) {
                bestDamageEstimate = estimatedDamage
            }
        }

        val canKO = bestDamageEstimate >= ourPokemon.health
        DoubleValue(canKO)
    }

    struct.addFunction("predicted_action") {
        var attackScore = 0.5
        var protectScore = 0.0
        var switchScore = 0.0
        var setupScore = 0.0
        var supportScore = 0.0

        val hasProtect = pokemon.originalPokemon.moveSet.any { move ->
            MoveCategoryMappings.hasCategory(move.name, MoveCategory.PROTECTION)
        }
        if (hasProtect) {
            val hpPercent = pokemon.health.toDouble() / pokemon.maxHealth.toDouble()
            protectScore += 0.1
            if (hpPercent < 0.4) protectScore += 0.15
            val lastMove = context.memory.getLastUsedMove(pokemon.uuid)
            if (lastMove != null && MoveCategoryMappings.hasCategory(lastMove, MoveCategory.PROTECTION)) {
                protectScore -= 0.2 // Just used it
            }
        }

        // Has setup? Use BUFF category
        val hasSetup = pokemon.originalPokemon.moveSet.any { move ->
            MoveCategoryMappings.hasCategory(move.name, MoveCategory.BUFF)
        }
        if (hasSetup) {
            val hpPercent = pokemon.health.toDouble() / pokemon.maxHealth.toDouble()
            val currentBoosts = context.memory.getAllStatBoosts(pokemon.uuid).values.filter { it > 0 }.sum()
            if (hpPercent > 0.7 && currentBoosts < 2) setupScore += 0.3
            if (context.memory.getTurnsOnField(pokemon.uuid) <= 1) setupScore += 0.1
        }

        // Switch likelihood
        if (!context.memory.isTrapped(pokemon.uuid)) {
            val hpPercent = pokemon.health.toDouble() / pokemon.maxHealth.toDouble()
            if (hpPercent < 0.25) switchScore += 0.2
            val negativeBoosts = context.memory.getAllStatBoosts(pokemon.uuid).values.filter { it < 0 }.sum()
            if (negativeBoosts <= -2) switchScore += 0.15
        }

        // Determine highest scoring action
        val scores = mapOf(
            "attack" to attackScore,
            "protect" to protectScore,
            "switch" to switchScore,
            "setup" to setupScore,
            "support" to supportScore
        )
        val predictedAction = scores.maxByOrNull { it.value }?.key ?: "attack"

        StringValue(predictedAction)
    }

    // Overall threat priority score (for target selection)
    struct.addFunction("priority_score") {
        var score = 50.0 // Base score

        // Offensive threat
        val maxOffense = maxOf(
            pokemon.originalPokemon.getStat(Stats.ATTACK),
            pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
        )
        score += (maxOffense - 80) * 0.3

        // Current boosts
        val offensiveBoosts = maxOf(
            context.memory.getStatBoost(pokemon.uuid, "atk"),
            context.memory.getStatBoost(pokemon.uuid, "spa")
        )
        score += offensiveBoosts * 15

        // Low HP = easier to finish
        val hpPercent = pokemon.health.toDouble() / pokemon.maxHealth.toDouble()
        if (hpPercent < 0.5) score += 20
        if (hpPercent < 0.25) score += 15

        // Support Pokemon are often high priority - check for support/redirection/speed control moves
        val isSupport = pokemon.originalPokemon.moveSet.any { move ->
            MoveCategoryMappings.hasCategory(move.name, MoveCategory.SUPPORT) ||
            MoveCategoryMappings.hasCategory(move.name, MoveCategory.REDIRECTION) ||
            MoveCategoryMappings.hasCategory(move.name, MoveCategory.SPEED_CONTROL) ||
            MoveCategoryMappings.hasCategory(move.name, MoveCategory.FLINCH)
        }
        if (isSupport) score += 25

        DoubleValue(score.coerceIn(0.0, 100.0))
    }

    return struct
}

private fun createMoveStruct(context: BattleAIMolangContext): QueryStruct {
    val struct = QueryStruct(hashMapOf())
    val move = context.currentMove ?: return struct
    val moveTemplate = Moves.getByName(move.id) ?: return struct

    struct.addFunction("name") { StringValue(move.id) }
    struct.addFunction("type") { StringValue(moveTemplate.elementalType.name) }
    struct.addFunction("power") { DoubleValue(moveTemplate.power.toDouble()) }
    struct.addFunction("accuracy") { DoubleValue(moveTemplate.accuracy.toDouble()) }
    struct.addFunction("pp") { DoubleValue(move.pp.toDouble()) }
    struct.addFunction("max_pp") { DoubleValue(move.maxpp.toDouble()) }
    struct.addFunction("priority") { DoubleValue(moveTemplate.priority.toDouble()) }

    // Category (Physical, Special, Status)
    struct.addFunction("damage_category") { StringValue(moveTemplate.damageCategory.name) }
    struct.addFunction("is_physical") { DoubleValue(moveTemplate.damageCategory.name == "physical") }
    struct.addFunction("is_special") { DoubleValue(moveTemplate.damageCategory.name == "special") }
    struct.addFunction("is_status") { DoubleValue(moveTemplate.damageCategory.name == "status") }

    // AI move categories
    struct.addFunction("categories") {
        MoveCategoryMappings.getCategories(move.id).map { it.name.lowercase() }.asArrayValue()
    }
    struct.addFunction("has_category") { params ->
        val categoryName = params.getString(0)
        val category = try { MoveCategory.valueOf(categoryName.uppercase()) } catch (e: Exception) { null }
        val hasCategory = category != null && MoveCategoryMappings.getCategories(move.id).contains(category)
        DoubleValue(hasCategory)
    }

    // STAB check
    struct.addFunction("is_stab") {
        val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val isStab = pokemon.originalPokemon.types.any {
            it.name.equals(moveTemplate.elementalType.name, ignoreCase = true)
        }
        DoubleValue(isStab)
    }

    // Type effectiveness - uses Kotlin type chart calculation
    val getEffectiveness: () -> Double = {
        val target = context.currentTarget?.battlePokemon
        if (target == null) {
            1.0
        } else {
            calculateTypeEffectiveness(moveTemplate.elementalType.name, target.originalPokemon.types.map { it.name })
        }
    }

    struct.addFunction("type_effectiveness") {
        DoubleValue(getEffectiveness())
    }

    struct.addFunction("effectiveness") {
        DoubleValue(getEffectiveness())
    }

    struct.addFunction("predicted_damage") {
        val target = context.currentTarget?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        DoubleValue(estimateDamage(pokemon, target, moveTemplate, context))
    }
    struct.addFunction("predicted_damage_percent") {
        val target = context.currentTarget?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val damage = estimateDamage(pokemon, target, moveTemplate, context)
        if (target.maxHealth > 0) {
            DoubleValue(damage / target.maxHealth.toDouble())
        } else {
            DoubleValue.ZERO
        }
    }

    struct.addFunction("min_damage") {
        val target = context.currentTarget?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val avg = estimateDamage(pokemon, target, moveTemplate, context)
        DoubleValue(avg * 0.85)
    }
    struct.addFunction("max_damage") {
        val target = context.currentTarget?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val avg = estimateDamage(pokemon, target, moveTemplate, context)
        DoubleValue(avg * 1.0)
    }
    struct.addFunction("is_ohko") {
        val target = context.currentTarget?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val minDamage = estimateDamage(pokemon, target, moveTemplate, context) * 0.85
        DoubleValue(minDamage >= target.health)
    }
    struct.addFunction("is_2hko") {
        val target = context.currentTarget?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val minDamage = estimateDamage(pokemon, target, moveTemplate, context) * 0.85
        DoubleValue(minDamage * 2 >= target.health)
    }
    struct.addFunction("hits_to_ko") {
        val target = context.currentTarget?.battlePokemon ?: return@addFunction DoubleValue(99.0)
        val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue(99.0)
        val avgDamage = estimateDamage(pokemon, target, moveTemplate, context)
        if (avgDamage <= 0) {
            DoubleValue(99.0)
        } else {
            DoubleValue(kotlin.math.ceil(target.health.toDouble() / avgDamage).coerceAtMost(99.0))
        }
    }

    struct.addFunction("is_spread") {
        DoubleValue(moveTemplate.target in SPREAD_TARGETS)
    }

    struct.addFunction("is_protect") {
        DoubleValue(MoveCategoryMappings.hasCategory(move.id, MoveCategory.PROTECTION))
    }

    struct.addFunction("hits_ally") {
        val hitsAlly = moveTemplate.target in ALLY_HITTING_TARGETS && context.allies.isNotEmpty()
        DoubleValue(hitsAlly)
    }

    struct.addFunction("type") { StringValue(moveTemplate.elementalType.name.lowercase()) }


    struct.addFunction("likely_blocked_by_protect") {
        val target = context.currentTarget?.battlePokemon ?: return@addFunction DoubleValue.ZERO

        // TODO: Add a BYPASS_PROTECT category
        if (moveTemplate.priority < 0 && moveTemplate.power == 0.0) {
            return@addFunction DoubleValue.ZERO
        }

        val hasProtect = target.originalPokemon.moveSet.any { m ->
            MoveCategoryMappings.hasCategory(m.name, MoveCategory.PROTECTION)
        }
        if (!hasProtect) return@addFunction DoubleValue.ZERO

        val lastMove = context.memory.getLastUsedMove(target.uuid)
        if (lastMove != null && MoveCategoryMappings.hasCategory(lastMove, MoveCategory.PROTECTION)) {
            return@addFunction DoubleValue(0.1)
        }

        var protectChance = 0.15

        val hpPercent = target.health.toDouble() / target.maxHealth.toDouble()
        if (hpPercent < 0.4) protectChance += 0.2
        if (context.memory.getTurnsOnField(target.uuid) <= 1) protectChance += 0.15

        DoubleValue(protectChance.coerceIn(0.0, 0.7))
    }

    struct.addFunction("is_good_timing") {
        val target = context.currentTarget?.battlePokemon ?: return@addFunction DoubleValue(0.5)

        var timing = 0.5

        if (moveTemplate.power > 0) {
            val hasProtect = target.originalPokemon.moveSet.any { m ->
                MoveCategoryMappings.hasCategory(m.name, MoveCategory.PROTECTION)
            }
            if (hasProtect) {
                val lastMove = context.memory.getLastUsedMove(target.uuid)
                if (lastMove != null && MoveCategoryMappings.hasCategory(lastMove, MoveCategory.PROTECTION)) {
                    timing += 0.3
                } else {
                    timing -= 0.1
                }
            } else {
                timing += 0.1
            }
        }

        if (MoveCategoryMappings.hasCategory(move.id, MoveCategory.BUFF) ||
            MoveCategoryMappings.hasCategory(move.id, MoveCategory.SPEED_CONTROL)) {
            val hpPercent = target.health.toDouble() / target.maxHealth.toDouble()
            if (hpPercent < 0.3) timing += 0.2
            if (context.battle.turn <= 2) timing += 0.15
            val ourPokemon = context.activePokemon?.battlePokemon
            if (ourPokemon != null) {
                val ourHpPercent = ourPokemon.health.toDouble() / ourPokemon.maxHealth.toDouble()
                if (ourHpPercent < 0.5) timing -= 0.2
            }
        }

        if (MoveCategoryMappings.hasCategory(move.id, MoveCategory.STATUS)) {
            val hpPercent = target.health.toDouble() / target.maxHealth.toDouble()
            if (hpPercent < 0.25) timing -= 0.2
            if (hpPercent > 0.7) timing += 0.15
        }

        DoubleValue(timing.coerceIn(0.0, 1.0))
    }

    struct.addFunction("better_target_exists") {
        if (context.opponents.size <= 1) return@addFunction DoubleValue.ZERO

        val currentTarget = context.currentTarget?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val moveType = moveTemplate.elementalType.name

        val currentTypes = currentTarget.originalPokemon.types.map { it.name }
        val currentEffectiveness = calculateTypeEffectiveness(moveType, currentTypes)

        for (opponent in context.opponents) {
            val oppPokemon = opponent.battlePokemon ?: continue
            if (oppPokemon.uuid == currentTarget.uuid) continue

            val oppTypes = oppPokemon.originalPokemon.types.map { it.name }
            val oppEffectiveness = calculateTypeEffectiveness(moveType, oppTypes)

            if (oppEffectiveness > currentEffectiveness * 1.5) {
                return@addFunction DoubleValue.ONE
            }

            val currentMightProtect = currentTarget.originalPokemon.moveSet.any { m ->
                MoveCategoryMappings.hasCategory(m.name, MoveCategory.PROTECTION)
            }
            val otherMightProtect = oppPokemon.originalPokemon.moveSet.any { m ->
                MoveCategoryMappings.hasCategory(m.name, MoveCategory.PROTECTION)
            }
            if (currentMightProtect && !otherMightProtect && oppEffectiveness >= currentEffectiveness) {
                return@addFunction DoubleValue.ONE
            }
        }

        DoubleValue.ZERO
    }

    struct.addFunction("is_good_fake_out_target") {
        if (!MoveCategoryMappings.hasCategory(move.id, MoveCategory.FLINCH)) {
            return@addFunction DoubleValue.ZERO
        }

        val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        if (context.memory.getTurnsOnField(pokemon.uuid) > 1) {
            return@addFunction DoubleValue.ZERO
        }

        val target = context.currentTarget?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        var score = 0.5

        val maxOffense = maxOf(
            target.originalPokemon.getStat(Stats.ATTACK),
            target.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
        )
        if (maxOffense > 120) score += 0.2

        val hasSetup = target.originalPokemon.moveSet.any { m ->
            MoveCategoryMappings.hasCategory(m.name, MoveCategory.BUFF) ||
            MoveCategoryMappings.hasCategory(m.name, MoveCategory.SPEED_CONTROL)
        }
        if (hasSetup) score += 0.2

        val isSupport = target.originalPokemon.moveSet.any { m ->
            MoveCategoryMappings.hasCategory(m.name, MoveCategory.SUPPORT) ||
            MoveCategoryMappings.hasCategory(m.name, MoveCategory.REDIRECTION)
        }
        if (isSupport) score += 0.25

        val flinchImmune = target.originalPokemon.ability.name.lowercase().replace(" ", "") in
            setOf("innerfocus", "shielddust", "oblivious")
        if (flinchImmune) score -= 0.5

        DoubleValue(score.coerceIn(0.0, 1.0))
    }

    struct.addFunction("opponent_reaction_penalty") {
        val target = context.currentTarget?.battlePokemon ?: return@addFunction DoubleValue.ZERO

        var penalty = 0.0

        if (moveTemplate.priority >= 1) {
            val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue.ZERO
            if (calculateEffectiveSpeed(pokemon, context) > calculateEffectiveSpeed(target, context)) {
                // Already faster, priority less relevant
            } else {
                val hpPercent = target.health.toDouble() / target.maxHealth.toDouble()
                if (hpPercent > 0.7) penalty += 0.05
            }
        }

        if (moveTemplate.target in ALLY_HITTING_TARGETS && context.allies.isNotEmpty()) {
            penalty += 0.15
        }

        DoubleValue(penalty.coerceIn(0.0, 0.5))
    }

    // Gimmick move name queries
    struct.addFunction("zmove_name") {
        val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction StringValue("")
        val moveset = getActiveMoveset(context, pokemon.uuid) ?: return@addFunction StringValue("")
        val moveIndex = moveset.moves.indexOfFirst { it.id == move.id }
        if (moveIndex < 0) return@addFunction StringValue("")
        val zMove = moveset.canZMove?.getOrNull(moveIndex)
        StringValue(zMove?.move ?: "")
    }

    struct.addFunction("max_move_name") {
        val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction StringValue("")
        val moveset = getActiveMoveset(context, pokemon.uuid) ?: return@addFunction StringValue("")
        val moveIndex = moveset.moves.indexOfFirst { it.id == move.id }
        if (moveIndex < 0) return@addFunction StringValue("")
        val maxMove = moveset.maxMoves?.getOrNull(moveIndex)
        StringValue(maxMove?.move ?: "")
    }

    return struct
}

private fun createBattleStruct(context: BattleAIMolangContext): QueryStruct {
    val struct = QueryStruct(hashMapOf())
    val battle = context.battle

    struct.addFunction("turn") { DoubleValue(battle.turn.toDouble()) }
    struct.addFunction("format") { StringValue(battle.format.battleType.name) }
    struct.addFunction("is_doubles") { DoubleValue(battle.format.battleType.name.contains("double", ignoreCase = true)) }

    struct.addFunction("weather") { StringValue(battle.contextManager.get(BattleContext.Type.WEATHER)?.firstOrNull()?.id ?: "") }
    struct.addFunction("has_weather") { params ->
        val weatherName = params.getString(0).lowercase()
        val currentWeather = battle.contextManager.get(BattleContext.Type.WEATHER)?.firstOrNull()?.id?.lowercase() ?: ""
        DoubleValue(currentWeather.contains(weatherName))
    }

    struct.addFunction("terrain") { StringValue(battle.contextManager.get(BattleContext.Type.TERRAIN)?.firstOrNull()?.id ?: "") }
    struct.addFunction("has_terrain") { params ->
        val terrainName = params.getString(0).lowercase()
        val currentTerrain = battle.contextManager.get(BattleContext.Type.TERRAIN)?.firstOrNull()?.id?.lowercase() ?: ""
        DoubleValue(currentTerrain.contains(terrainName))
    }

    struct.addFunction("hazards_on_our_side") { createHazardsStruct(context, context.actor.getSide()) }
    struct.addFunction("hazards_on_enemy_side") {
        val enemySide = battle.sides.firstOrNull { it != context.actor.getSide() }
        if (enemySide != null) createHazardsStruct(context, enemySide) else QueryStruct(hashMapOf())
    }

    struct.addFunction("is_opponent_switching") { DoubleValue.ZERO }
    struct.addFunction("has_trick_room") {
        val trickRoom = battle.contextManager.get(BattleContext.Type.ROOM)?.firstOrNull()
        DoubleValue(trickRoom != null && trickRoom.id == "trickroom")
    }

    return struct
}

private fun createMemoryStruct(context: BattleAIMolangContext): QueryStruct {
    val struct = QueryStruct(hashMapOf())
    val target = context.currentTarget ?: context.opponents.firstOrNull()
    val targetId = target?.battlePokemon?.uuid

    struct.addFunction("opponent_revealed_moves") {
        if (targetId != null) {
            context.memory.getRevealedMoves(targetId).toList().asArrayValue()
        } else {
            ArrayStruct(hashMapOf())
        }
    }

    struct.addFunction("opponent_revealed_item") {
        if (targetId != null) {
            StringValue(context.memory.getRevealedItem(targetId) ?: "")
        } else {
            StringValue("")
        }
    }

    struct.addFunction("opponent_revealed_ability") {
        if (targetId != null) {
            StringValue(context.memory.getRevealedAbility(targetId) ?: "")
        } else {
            StringValue("")
        }
    }

    struct.addFunction("times_move_used") { params ->
        val moveName = params.getString(0)
        if (targetId != null) {
            DoubleValue(context.memory.getMoveUsageCount(targetId, moveName).toDouble())
        } else {
            DoubleValue.ZERO
        }
    }

    struct.addFunction("total_switches") {
        DoubleValue(context.memory.getTotalSwitchCount().toDouble())
    }

    struct.addFunction("opponent_switch_likelihood") {
        // Heuristic: more switches = more likely to switch again
        val switchCount = context.memory.getTotalSwitchCount()
        val turn = context.battle.turn
        if (turn > 0) {
            DoubleValue((switchCount.toDouble() / turn).coerceAtMost(1.0))
        } else {
            DoubleValue.ZERO
        }
    }

    return struct
}

private fun createMatchupStruct(context: BattleAIMolangContext): QueryStruct {
    val struct = QueryStruct(hashMapOf())
    val pokemon = context.activePokemon?.battlePokemon
    val target = context.currentTarget?.battlePokemon ?: context.opponents.firstOrNull()?.battlePokemon

    struct.addFunction("score") {
        if (pokemon == null || target == null) return@addFunction DoubleValue.ZERO

        val ourTypes = pokemon.originalPokemon.types.map { it.name }
        val theirTypes = target.originalPokemon.types.map { it.name }

        var score = 0.0

        for (ourType in ourTypes) {
            val eff = calculateTypeEffectiveness(ourType, theirTypes)
            score += when {
                eff >= 4.0 -> 2.0
                eff >= 2.0 -> 1.0
                eff <= 0.25 -> -2.0
                eff <= 0.5 -> -1.0
                else -> 0.0
            }
        }

        for (theirType in theirTypes) {
            val eff = calculateTypeEffectiveness(theirType, ourTypes)
            score -= when {
                eff >= 4.0 -> 2.0
                eff >= 2.0 -> 1.0
                eff <= 0.25 -> -2.0
                eff <= 0.5 -> -1.0
                else -> 0.0
            }
        }

        DoubleValue(score)
    }

    struct.addFunction("can_outspeed") {
        if (pokemon == null || target == null) return@addFunction DoubleValue.ZERO

        val ourSpeed = calculateEffectiveSpeed(pokemon, context)
        val theirSpeed = calculateEffectiveSpeed(target, context)

        val trickRoom = context.battle.contextManager.get(BattleContext.Type.ROOM)?.firstOrNull()
        val hasTrickRoom = trickRoom != null && trickRoom.id == "trickroom"

        val faster = if (hasTrickRoom) ourSpeed < theirSpeed else ourSpeed > theirSpeed
        DoubleValue(faster)
    }

    struct.addFunction("speed_tier") {
        if (pokemon == null || target == null) return@addFunction DoubleValue.ZERO

        val ourSpeed = calculateEffectiveSpeed(pokemon, context)
        val theirSpeed = calculateEffectiveSpeed(target, context)

        // Check for Trick Room
        val trickRoom = context.battle.contextManager.get(BattleContext.Type.ROOM)?.firstOrNull()
        val hasTrickRoom = trickRoom != null && trickRoom.id == "trickroom"

        if (theirSpeed > 0) {
            // Under Trick Room, invert the ratio so >1 still means "I move first"
            if (hasTrickRoom) {
                DoubleValue(theirSpeed / ourSpeed)
            } else {
                DoubleValue(ourSpeed / theirSpeed)
            }
        } else {
            DoubleValue(2.0)
        }
    }

    return struct
}

private fun createAllyStruct(context: BattleAIMolangContext): QueryStruct {
    val struct = QueryStruct(hashMapOf())
    val ally = context.allies.firstOrNull()?.battlePokemon ?: return struct

    struct.addFunction("id") { StringValue(ally.uuid.toString()) }
    struct.addFunction("species") { StringValue(ally.originalPokemon.species.name) }
    struct.addFunction("current_hp_percent") {
        if (ally.maxHealth > 0) {
            DoubleValue(ally.health.toDouble() / ally.maxHealth.toDouble())
        } else {
            DoubleValue.ZERO
        }
    }

    struct.addFunction("types") {
        ally.originalPokemon.types.map { it.name }.asArrayValue()
    }

    struct.addFunction("has_ability") { params ->
        val abilityName = params.getString(0).lowercase().replace(" ", "")
        val allyAbility = ally.originalPokemon.ability.name.lowercase().replace(" ", "")
        DoubleValue(allyAbility == abilityName)
    }

    struct.addFunction("benefits_from_weather") { params ->
        val weather = params.getString(0).lowercase()
        val allyAbility = ally.originalPokemon.ability.name.lowercase().replace(" ", "")

        val benefits = when (weather) {
            "sun", "harsh sunshine", "harshsunshine" -> allyAbility in listOf("chlorophyll", "solarpower", "leafguard", "flowergift")
            "rain", "heavy rain", "heavyrain" -> allyAbility in listOf("swiftswim", "raindish", "hydration", "dryskin")
            "sand", "sandstorm" -> allyAbility in listOf("sandrush", "sandforce", "sandveil")
            "hail", "snow" -> allyAbility in listOf("slushrush", "snowcloak", "icebody")
            else -> false
        }
        DoubleValue(benefits)
    }

    struct.addFunction("is_setting_up") {
        val lastMove = context.memory.getLastUsedMove(ally.uuid)
        DoubleValue(lastMove != null && MoveCategoryMappings.hasCategory(lastMove, MoveCategory.BUFF))
    }

    struct.addFunction("will_ko_target") {
        val target = context.currentTarget?.battlePokemon ?: context.opponents.firstOrNull()?.battlePokemon
        if (target == null) return@addFunction DoubleValue.ZERO

            val allyTypes = ally.originalPokemon.types.map { it.name }
        val targetTypes = target.originalPokemon.types.map { it.name }
        val hasSuperEffective = allyTypes.any { type ->
            calculateTypeEffectiveness(type, targetTypes) >= 2.0
        }
        val allyHighOffense = ally.originalPokemon.getStat(Stats.ATTACK) > 100 ||
                             ally.originalPokemon.getStat(Stats.SPECIAL_ATTACK) > 100
        val targetLowHp = target.health.toDouble() / target.maxHealth.toDouble() < 0.4

        DoubleValue(hasSuperEffective && allyHighOffense && targetLowHp)
    }

    // TODO: Track from battle action queue
    struct.addFunction("is_targeting_same") {
        DoubleValue.ZERO
    }

    struct.addFunction("resists_predicted_move") {
        val opponent = context.opponents.firstOrNull()?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val oppTypes = opponent.originalPokemon.types.map { it.name }
        val allyTypes = ally.originalPokemon.types.map { it.name }
        val resists = oppTypes.any { oppType ->
            calculateTypeEffectiveness(oppType, allyTypes) < 1.0
        }
        DoubleValue(resists)
    }

    struct.addFunction("is_protecting_or_redirecting") {
        val lastMove = context.memory.getLastUsedMove(ally.uuid)
        val isProtecting = lastMove != null && (
            MoveCategoryMappings.hasCategory(lastMove, MoveCategory.PROTECTION) ||
            MoveCategoryMappings.hasCategory(lastMove, MoveCategory.REDIRECTION)
        )
        DoubleValue(isProtecting)
    }

    struct.addFunction("has_spread_move") {
        val hasSpread = ally.originalPokemon.moveSet.any { move ->
            move.template.target in SPREAD_TARGETS
        }
        DoubleValue(hasSpread)
    }

    return struct
}

private fun createAlliesArray(context: BattleAIMolangContext): ArrayStruct {
    val array = ArrayStruct(hashMapOf())
    context.allies.forEachIndexed { index, ally ->
        val pokemon = ally.battlePokemon ?: return@forEachIndexed
        val struct = QueryStruct(hashMapOf())
        struct.addFunction("id") { StringValue(pokemon.uuid.toString()) }
        struct.addFunction("species") { StringValue(pokemon.originalPokemon.species.name) }
        struct.addFunction("current_hp_percent") {
            if (pokemon.maxHealth > 0) {
                DoubleValue(pokemon.health.toDouble() / pokemon.maxHealth.toDouble())
            } else {
                DoubleValue.ZERO
            }
        }
        array.setDirectly("$index", struct)
    }
    return array
}

private fun createPersonalityStruct(context: BattleAIMolangContext): QueryStruct {
    val struct = QueryStruct(hashMapOf())
    val personality = context.personality

    struct.addFunction("aggression") { DoubleValue(personality.aggression) }
    struct.addFunction("risk_tolerance") { DoubleValue(personality.riskTolerance) }
    struct.addFunction("setup_preference") { DoubleValue(personality.setupPreference) }
    struct.addFunction("switchiness") { DoubleValue(personality.switchiness) }
    struct.addFunction("item_conservatism") { DoubleValue(personality.itemConservatism) }

    return struct
}

private fun createSwitchCandidateStruct(context: BattleAIMolangContext): QueryStruct {
    val struct = QueryStruct(hashMapOf())
    val candidate = context.switchCandidate ?: return struct

    struct.addFunction("id") { StringValue(candidate.uuid.toString()) }
    struct.addFunction("species") { StringValue(candidate.originalPokemon.species.name) }
    struct.addFunction("level") { DoubleValue(candidate.originalPokemon.level.toDouble()) }
    struct.addFunction("current_hp") { DoubleValue(candidate.health.toDouble()) }
    struct.addFunction("max_hp") { DoubleValue(candidate.maxHealth.toDouble()) }
    struct.addFunction("current_hp_percent") {
        if (candidate.maxHealth > 0) {
            DoubleValue(candidate.health.toDouble() / candidate.maxHealth.toDouble())
        } else {
            DoubleValue.ZERO
        }
    }

    struct.addFunction("types") {
        candidate.originalPokemon.types.map { it.name }.asArrayValue()
    }

    struct.addFunction("ability") { StringValue(candidate.originalPokemon.ability.name) }

    struct.addFunction("matchup_score") {
        val target = context.currentTarget?.battlePokemon ?: context.opponents.firstOrNull()?.battlePokemon
        if (target == null) return@addFunction DoubleValue.ZERO

        val ourTypes = candidate.originalPokemon.types.map { it.name }
        val theirTypes = target.originalPokemon.types.map { it.name }

        var score = 0.0
        for (ourType in ourTypes) {
            val eff = calculateTypeEffectiveness(ourType, theirTypes)
            score += when {
                eff >= 4.0 -> 2.0
                eff >= 2.0 -> 1.0
                eff <= 0.25 -> -2.0
                eff <= 0.5 -> -1.0
                else -> 0.0
            }
        }
        for (theirType in theirTypes) {
            val eff = calculateTypeEffectiveness(theirType, ourTypes)
            score -= when {
                eff >= 4.0 -> 2.0
                eff >= 2.0 -> 1.0
                eff <= 0.25 -> -2.0
                eff <= 0.5 -> -1.0
                else -> 0.0
            }
        }
        DoubleValue(score)
    }

    struct.addFunction("has_move") { params ->
        val moveName = params.getString(0).lowercase().replace(" ", "")
        val hasMove = candidate.originalPokemon.moveSet.any { move ->
            move.name.lowercase().replace(" ", "") == moveName
        }
        DoubleValue(hasMove)
    }

    struct.addFunction("sets_weather") {
        val weatherSetters = setOf("drizzle", "drought", "sandstream", "snowwarning", "primordialsea", "desolateland", "deltastream")
        val ability = candidate.originalPokemon.ability.name.lowercase().replace(" ", "")
        DoubleValue(ability in weatherSetters)
    }

    struct.addFunction("sets_terrain") {
        val terrainSetters = setOf("grassysurge", "electricsurge", "psychicsurge", "mistysurge", "hadronengine")
        val ability = candidate.originalPokemon.ability.name.lowercase().replace(" ", "")
        DoubleValue(ability in terrainSetters)
    }

    return struct
}

private fun createBagStruct(context: BattleAIMolangContext): QueryStruct {
    val struct = QueryStruct(hashMapOf())
    val bagState = context.bagState

    struct.addFunction("enabled") {
        DoubleValue(bagState != null && bagState.getTotalRemainingUses() > 0)
    }

    struct.addFunction("remaining_uses") {
        DoubleValue((bagState?.getTotalRemainingUses() ?: 0).toDouble())
    }

    struct.addFunction("has_healing_item") {
        if (bagState == null) return@addFunction DoubleValue.ZERO
        val hasHealing = bagState.getAvailableItems().any { (item, _) ->
            item.itemId.contains("potion") || item.itemId.contains("restore")
        }
        DoubleValue(hasHealing)
    }

    // Check if any status cure items are available
    struct.addFunction("has_status_cure") {
        if (bagState == null) return@addFunction DoubleValue.ZERO
        val hasCure = bagState.getAvailableItems().any { (item, _) ->
            item.itemId.contains("antidote") || item.itemId.contains("heal") ||
            item.itemId.contains("awakening") || item.itemId.contains("ice_heal") ||
            item.itemId.contains("parlyz_heal") || item.itemId.contains("burn_heal")
        }
        DoubleValue(hasCure)
    }

    // Check if any revive items are available
    struct.addFunction("has_revive") {
        if (bagState == null) return@addFunction DoubleValue.ZERO
        val hasRevive = bagState.getAvailableItems().any { (item, _) ->
            item.itemId.contains("revive")
        }
        DoubleValue(hasRevive)
    }

    // Check if any stat boost items are available
    struct.addFunction("has_stat_boost") {
        if (bagState == null) return@addFunction DoubleValue.ZERO
        val hasStatBoost = bagState.getAvailableItems().any { (item, _) ->
            item.itemId.contains("x_attack") || item.itemId.contains("x_defense") ||
            item.itemId.contains("x_speed") || item.itemId.contains("x_special")
        }
        DoubleValue(hasStatBoost)
    }

    // Check remaining quantity of a specific item
    struct.addFunction("remaining_quantity") { params ->
        val itemId = params.getString(0)
        DoubleValue((bagState?.getRemainingQuantity(itemId) ?: 0).toDouble())
    }

    // Check if a specific item can still be used
    struct.addFunction("can_use_item") { params ->
        val itemId = params.getString(0)
        DoubleValue(bagState?.canUseItem(itemId) ?: false)
    }

    return struct
}

/**
 * Creates query struct for held item lookups from the unified JSON-defined registry.
 * This allows Molang expressions to query item effects defined in datapacks.
 *
 * Usage examples:
 * - q.item.boosts_type("charcoal", "fire") - check if an item boosts a type
 * - q.item.has_category("choiceband", "choice") - check if item is in a category
 * - q.item.type_boost_multiplier("charcoal", "fire") - get boost multiplier
 * - q.item.stat_multiplier("choiceband", "attack") - get stat multiplier
 */
private fun createHeldItemStruct(context: BattleAIMolangContext): QueryStruct {
    val struct = QueryStruct(hashMapOf())

    // Check if an item boosts a specific type
    // q.item.boosts_type("charcoal", "fire") -> 1 or 0
    struct.addFunction("boosts_type") { params ->
        val itemName = params.getString(0).lowercase().replace(" ", "")
        val typeName = params.getString(1).lowercase()
        DoubleValue(HeldItemEffectsRegistry.boostsType(itemName, typeName))
    }

    // Get the type boost multiplier for an item and type
    // q.item.type_boost_multiplier("charcoal", "fire") -> 1.2
    struct.addFunction("type_boost_multiplier") { params ->
        val itemName = params.getString(0).lowercase().replace(" ", "")
        val typeName = params.getString(1).lowercase()
        DoubleValue(HeldItemEffectsRegistry.getTypeMultiplier(itemName, typeName))
    }

    // Get the boosted type for an item (if any)
    // q.item.boosted_type("charcoal") -> "fire"
    struct.addFunction("boosted_type") { params ->
        val itemName = params.getString(0).lowercase().replace(" ", "")
        StringValue(HeldItemEffectsRegistry.getBoostedType(itemName) ?: "")
    }

    // Check if an item is in a specific category
    // q.item.has_category("choiceband", "choice") -> 1 or 0
    struct.addFunction("has_category") { params ->
        val itemName = params.getString(0).lowercase().replace(" ", "")
        val categoryName = params.getString(1).lowercase()
        DoubleValue(HeldItemEffectsRegistry.hasCategory(itemName, categoryName))
    }

    // Get all categories for an item
    // q.item.categories("choiceband") -> ["choice", "stat_boost"]
    struct.addFunction("categories") { params ->
        val itemName = params.getString(0).lowercase().replace(" ", "")
        HeldItemEffectsRegistry.getCategories(itemName).asArrayValue()
    }

    // Check if item is a choice item
    // q.item.is_choice("choiceband") -> 1 or 0
    struct.addFunction("is_choice") { params ->
        val itemName = params.getString(0).lowercase().replace(" ", "")
        DoubleValue(HeldItemEffectsRegistry.isChoiceItem(itemName))
    }

    // Check if item is a type boost item
    // q.item.is_type_boost("charcoal") -> 1 or 0
    struct.addFunction("is_type_boost") { params ->
        val itemName = params.getString(0).lowercase().replace(" ", "")
        DoubleValue(HeldItemEffectsRegistry.hasCategory(itemName, "type_boost"))
    }

    // Check if item is a berry
    // q.item.is_berry("sitrusberry") -> 1 or 0
    struct.addFunction("is_berry") { params ->
        val itemName = params.getString(0).lowercase().replace(" ", "")
        DoubleValue(HeldItemEffectsRegistry.isBerry(itemName))
    }

    // Check if item is consumable
    // q.item.is_consumable("focussash") -> 1 or 0
    struct.addFunction("is_consumable") { params ->
        val itemName = params.getString(0).lowercase().replace(" ", "")
        DoubleValue(HeldItemEffectsRegistry.isConsumable(itemName))
    }

    // Get stat multiplier for an item
    // q.item.stat_multiplier("choiceband", "attack") -> 1.5
    struct.addFunction("stat_multiplier") { params ->
        val itemName = params.getString(0).lowercase().replace(" ", "")
        val statName = params.getString(1).lowercase().replace(" ", "")
        DoubleValue(HeldItemEffectsRegistry.getStatMultiplier(itemName, statName))
    }

    // Check if item restricts move selection (choice items)
    // q.item.restricts_move("choiceband") -> 1 or 0
    struct.addFunction("restricts_move") { params ->
        val itemName = params.getString(0).lowercase().replace(" ", "")
        DoubleValue(HeldItemEffectsRegistry.restrictsMove(itemName))
    }

    // Get recoil from item (life orb, etc) as decimal
    // q.item.recoil("lifeorb") -> 0.1
    struct.addFunction("recoil") { params ->
        val itemName = params.getString(0).lowercase().replace(" ", "")
        DoubleValue(HeldItemEffectsRegistry.getRecoil(itemName))
    }

    // Check if item has a specific property
    // q.item.has_property("expertbelt", "super_effective_multiplier") -> 1 or 0
    struct.addFunction("has_property") { params ->
        val itemName = params.getString(0).lowercase().replace(" ", "")
        val propertyName = params.getString(1).lowercase()
        val effects = HeldItemEffectsRegistry.getEffects(itemName)
        DoubleValue(effects.hasProperty(propertyName))
    }

    // Get a property's numeric value
    // q.item.property("expertbelt", "super_effective_multiplier") -> 1.2
    struct.addFunction("property") { params ->
        val itemName = params.getString(0).lowercase().replace(" ", "")
        val propertyName = params.getString(1).lowercase()
        val effects = HeldItemEffectsRegistry.getEffects(itemName)
        DoubleValue(effects.getProperty(propertyName))
    }

    // Get a property's string value
    // q.item.property_string("eviolite", "requires_evolution") -> "true"
    struct.addFunction("property_string") { params ->
        val itemName = params.getString(0).lowercase().replace(" ", "")
        val propertyName = params.getString(1).lowercase()
        val effects = HeldItemEffectsRegistry.getEffects(itemName)
        StringValue(effects.getPropertyString(propertyName))
    }

    return struct
}

private fun createHazardsStruct(context: BattleAIMolangContext, side: BattleSide): QueryStruct {
    val struct = QueryStruct(hashMapOf())

    // Determine sideId for memory lookup
    val sideId = if (side == context.battle.side1) "side1" else "side2"
    val conditions = context.memory.getSideConditions(sideId)

    // Check for hazards in memory
    struct.addFunction("spikes") {
        val spikeCount = conditions.count { it.contains("spike") && !it.contains("toxic") }
        DoubleValue(spikeCount.toDouble().coerceAtMost(3.0))
    }
    struct.addFunction("toxic_spikes") {
        val toxicCount = conditions.count { it.contains("toxicspike") }
        DoubleValue(toxicCount.toDouble().coerceAtMost(2.0))
    }
    struct.addFunction("stealth_rock") {
        DoubleValue(if (conditions.any { it.contains("stealthrock") }) 1.0 else 0.0)
    }
    struct.addFunction("sticky_web") {
        DoubleValue(if (conditions.any { it.contains("stickyweb") }) 1.0 else 0.0)
    }

    return struct
}

private fun createStatBoostsStruct(context: BattleAIMolangContext, pokemonUuid: UUID?): QueryStruct {
    val struct = QueryStruct(hashMapOf())

    // Get stat boosts from memory
    struct.addFunction("atk") {
        DoubleValue(pokemonUuid?.let { context.memory.getStatBoost(it, "atk") }?.toDouble() ?: 0.0)
    }
    struct.addFunction("def") {
        DoubleValue(pokemonUuid?.let { context.memory.getStatBoost(it, "def") }?.toDouble() ?: 0.0)
    }
    struct.addFunction("spa") {
        DoubleValue(pokemonUuid?.let { context.memory.getStatBoost(it, "spa") }?.toDouble() ?: 0.0)
    }
    struct.addFunction("spd") {
        DoubleValue(pokemonUuid?.let { context.memory.getStatBoost(it, "spd") }?.toDouble() ?: 0.0)
    }
    struct.addFunction("spe") {
        DoubleValue(pokemonUuid?.let { context.memory.getStatBoost(it, "spe") }?.toDouble() ?: 0.0)
    }
    struct.addFunction("accuracy") {
        DoubleValue(pokemonUuid?.let { context.memory.getStatBoost(it, "accuracy") }?.toDouble() ?: 0.0)
    }
    struct.addFunction("evasion") {
        DoubleValue(pokemonUuid?.let { context.memory.getStatBoost(it, "evasion") }?.toDouble() ?: 0.0)
    }

    return struct
}

/**
 * Creates a query struct for field conditions (weather, terrain, tailwind, etc.)
 */
private fun createFieldStruct(context: BattleAIMolangContext): QueryStruct {
    val struct = QueryStruct(hashMapOf())
    val battle = context.battle

    // Weather
    val currentWeather = battle.contextManager.get(BattleContext.Type.WEATHER)?.firstOrNull()?.id?.lowercase() ?: ""
    struct.addFunction("weather") { StringValue(currentWeather) }
    struct.addFunction("has_weather") { params ->
        val weatherName = params.getString(0).lowercase()
        DoubleValue(currentWeather.contains(weatherName))
    }

    // Terrain
    val currentTerrain = battle.contextManager.get(BattleContext.Type.TERRAIN)?.firstOrNull()?.id?.lowercase() ?: ""
    struct.addFunction("terrain") { StringValue(currentTerrain) }
    struct.addFunction("has_terrain") { params ->
        val terrainName = params.getString(0).lowercase()
        DoubleValue(currentTerrain.contains(terrainName))
    }
    struct.addFunction("has_electric_terrain") { DoubleValue(currentTerrain.contains("electric")) }
    struct.addFunction("has_grassy_terrain") { DoubleValue(currentTerrain.contains("grassy")) }
    struct.addFunction("has_psychic_terrain") { DoubleValue(currentTerrain.contains("psychic")) }
    struct.addFunction("has_misty_terrain") { DoubleValue(currentTerrain.contains("misty")) }

    // Trick Room
    val trickRoom = battle.contextManager.get(BattleContext.Type.ROOM)?.firstOrNull()
    struct.addFunction("has_trick_room") { DoubleValue(trickRoom != null && trickRoom.id == "trickroom") }

    // Tailwind
    val ourSideId = if (context.actor.getSide() == battle.side1) "side1" else "side2"
    val enemySideId = if (ourSideId == "side1") "side2" else "side1"
    struct.addFunction("has_tailwind_ally") {
        DoubleValue(context.memory.hasSideCondition(ourSideId, "tailwind"))
    }
    struct.addFunction("has_tailwind_opponent") {
        DoubleValue(context.memory.hasSideCondition(enemySideId, "tailwind"))
    }

    // Screen conditions (Light Screen, Reflect, Aurora Veil)
    val ourSide = context.actor.getSide()
    val enemySide = ourSide.getOppositeSide()
    val ourScreens = ourSide.contextManager.get(BattleContext.Type.SCREEN)?.map { it.id.lowercase() } ?: emptyList()
    val enemyScreens = enemySide.contextManager.get(BattleContext.Type.SCREEN)?.map { it.id.lowercase() } ?: emptyList()

    struct.addFunction("has_light_screen_ally") { DoubleValue(ourScreens.any { it.contains("lightscreen") }) }
    struct.addFunction("has_reflect_ally") { DoubleValue(ourScreens.any { it.contains("reflect") }) }
    struct.addFunction("has_aurora_veil_ally") { DoubleValue(ourScreens.any { it.contains("auroraveil") }) }
    struct.addFunction("has_light_screen_opponent") { DoubleValue(enemyScreens.any { it.contains("lightscreen") }) }
    struct.addFunction("has_reflect_opponent") { DoubleValue(enemyScreens.any { it.contains("reflect") }) }
    struct.addFunction("has_aurora_veil_opponent") { DoubleValue(enemyScreens.any { it.contains("auroraveil") }) }

    // Any screen up on our/enemy side
    struct.addFunction("has_screens_ally") { DoubleValue(ourScreens.isNotEmpty()) }
    struct.addFunction("has_screens_opponent") { DoubleValue(enemyScreens.isNotEmpty()) }

    // Gravity
    val gravity = battle.contextManager.get(BattleContext.Type.GRAVITY)?.firstOrNull()
    struct.addFunction("has_gravity") { DoubleValue(gravity != null) }

    // Sport (Mud Sport, Water Sport - reduce Fire/Electric damage)
    val sports = battle.contextManager.get(BattleContext.Type.SPORT)?.map { it.id.lowercase() } ?: emptyList()
    struct.addFunction("has_mud_sport") { DoubleValue(sports.any { it.contains("mudsport") }) }
    struct.addFunction("has_water_sport") { DoubleValue(sports.any { it.contains("watersport") }) }

    // Preferred weather/terrain for team (based on abilities)
    struct.addFunction("has_preferred_weather") {
        val actor = context.actor
        val teamWantsWeather = actor.pokemonList.any { pokemon ->
            val ability = pokemon.originalPokemon.ability.name.lowercase().replace(" ", "")
            ability in setOf("chlorophyll", "solarpower", "swiftswim", "sandrush", "slushrush", "drizzle", "drought", "sandstream", "snowwarning")
        }
        val currentWeatherMatches = currentWeather.isNotEmpty()
        DoubleValue(teamWantsWeather && currentWeatherMatches)
    }

    struct.addFunction("has_preferred_terrain") {
        val actor = context.actor
        val teamWantsTerrain = actor.pokemonList.any { pokemon ->
            val ability = pokemon.originalPokemon.ability.name.lowercase().replace(" ", "")
            ability in setOf("surgesurfer", "grassysurge", "electricsurge", "psychicsurge", "mistysurge")
        }
        val currentTerrainMatches = currentTerrain.isNotEmpty()
        DoubleValue(teamWantsTerrain && currentTerrainMatches)
    }

    return struct
}

/**
 * Creates a query struct for team-wide information.
 */
private fun createTeamStruct(context: BattleAIMolangContext): QueryStruct {
    val struct = QueryStruct(hashMapOf())
    val actor = context.actor

    // Remaining Pokemon count
    struct.addFunction("remaining_count") {
        val remaining = actor.pokemonList.count { it.health > 0 }
        DoubleValue(remaining.toDouble())
    }

    // Has Intimidate available (not active, but in back)
    struct.addFunction("has_intimidate_available") {
        val activeIds = context.activePokemon?.battlePokemon?.uuid?.let { setOf(it) } ?: emptySet()
        val hasIntimidateInBack = actor.pokemonList.any { pokemon ->
            pokemon.uuid !in activeIds &&
            pokemon.health > 0 &&
            pokemon.originalPokemon.ability.name.equals("intimidate", ignoreCase = true)
        }
        DoubleValue(hasIntimidateInBack)
    }

    // Has weather abuser
    struct.addFunction("has_weather_abuser") {
        val weatherAbilities = setOf("chlorophyll", "solarpower", "swiftswim", "sandrush", "slushrush", "raindish", "dryskin", "sandforce")
        val hasAbuser = actor.pokemonList.any { pokemon ->
            pokemon.originalPokemon.ability.name.lowercase().replace(" ", "") in weatherAbilities
        }
        DoubleValue(hasAbuser)
    }

    // Has terrain abuser
    struct.addFunction("has_terrain_abuser") {
        val hasAbuser = actor.pokemonList.any { pokemon ->
            val ability = pokemon.originalPokemon.ability.name.lowercase().replace(" ", "")
            ability in setOf("surgesurfer", "grasspelt", "mimicry")
        }
        DoubleValue(hasAbuser)
    }

    // Team wants weather
    struct.addFunction("wants_weather") {
        val weatherSetters = setOf("drizzle", "drought", "sandstream", "snowwarning")
        val hasWeatherSetter = actor.pokemonList.any { pokemon ->
            pokemon.originalPokemon.ability.name.lowercase().replace(" ", "") in weatherSetters
        }
        DoubleValue(hasWeatherSetter)
    }

    // Team wants terrain
    struct.addFunction("wants_terrain") {
        val terrainSetters = setOf("grassysurge", "electricsurge", "psychicsurge", "mistysurge")
        val hasTerrainSetter = actor.pokemonList.any { pokemon ->
            pokemon.originalPokemon.ability.name.lowercase().replace(" ", "") in terrainSetters
        }
        DoubleValue(hasTerrainSetter)
    }

    // Team wants Trick Room
    struct.addFunction("wants_trick_room") {
        val avgSpeed = actor.pokemonList.map { it.originalPokemon.getStat(Stats.SPEED) }.average()
        DoubleValue(avgSpeed < 70)
    }

    // Team wants max weather (Dynamax weather moves)
    struct.addFunction("wants_max_weather") {
        // TODO: Check for Pokemon that benefit from max weather
        DoubleValue.ZERO
    }

    return struct
}

/** MoveTarget values that hit multiple targets (spread moves). */
private val SPREAD_TARGETS = setOf(MoveTarget.allAdjacentFoes, MoveTarget.allAdjacent, MoveTarget.all)

/** MoveTarget values that hit allies (allAdjacent and all). */
private val ALLY_HITTING_TARGETS = setOf(MoveTarget.allAdjacent, MoveTarget.all)

/**
 * Gets the ShowdownMoveset for the active Pokémon from the actor's current request.
 */
private fun getActiveMoveset(context: BattleAIMolangContext, pokemonUuid: UUID): ShowdownMoveset? {
    val actor = context.actor
    val index = actor.activePokemon.indexOfFirst { it.battlePokemon?.uuid == pokemonUuid }
    if (index < 0) return null
    return actor.request?.active?.getOrNull(index)
}

/**
 * Calculates effective speed for a Pokemon accounting for stat boosts, paralysis,
 * Choice Scarf, Tailwind, weather abilities, and Unburden.
 * Used for speed comparisons between Pokemon in AI decision-making.
 */
private fun calculateEffectiveSpeed(
    pokemon: BattlePokemon,
    context: BattleAIMolangContext
): Double {
    var speed = pokemon.originalPokemon.getStat(Stats.SPEED).toDouble()

    val boostStage = context.memory.getStatBoost(pokemon.uuid, "spe")
    val boostMultiplier = when {
        boostStage >= 0 -> (2.0 + boostStage) / 2.0
        else -> 2.0 / (2.0 - boostStage)
    }
    speed *= boostMultiplier

    val isParalyzed = pokemon.originalPokemon.status?.status?.name?.path?.contains("paralysis") == true
    val abilityName = pokemon.originalPokemon.ability.name.lowercase().replace(" ", "")
    val hasQuickFeet = abilityName == "quickfeet"
    if (isParalyzed && !hasQuickFeet) {
        speed *= 0.5
    }

    val heldItem = pokemon.originalPokemon.heldItem()
    val heldItemName = if (!heldItem.isEmpty) {
        heldItem.item.toString().substringAfterLast(":").lowercase().replace("_", "").replace(" ", "")
    } else ""

    if (heldItemName == "choicescarf") {
        speed *= 1.5
    }

    // Determine which side this Pokemon is on for Tailwind check
    val pokemonSide = context.battle.sides.firstOrNull { side ->
        side.actors.any { actor -> actor.pokemonList.any { it.uuid == pokemon.uuid } }
    }
    val sideId = if (pokemonSide == context.battle.side1) "side1" else "side2"
    if (context.memory.hasSideCondition(sideId, "tailwind")) {
        speed *= 2.0
    }

    when (abilityName) {
        "chlorophyll" -> {
            val weather = context.battle.contextManager.get(BattleContext.Type.WEATHER)?.firstOrNull()?.id?.lowercase() ?: ""
            if (weather.contains("sun") || weather == "desolateland") speed *= 2.0
        }
        "swiftswim" -> {
            val weather = context.battle.contextManager.get(BattleContext.Type.WEATHER)?.firstOrNull()?.id?.lowercase() ?: ""
            if (weather.contains("rain") || weather == "primordialsea") speed *= 2.0
        }
        "sandrush" -> {
            val weather = context.battle.contextManager.get(BattleContext.Type.WEATHER)?.firstOrNull()?.id?.lowercase() ?: ""
            if (weather.contains("sand")) speed *= 2.0
        }
        "slushrush" -> {
            val weather = context.battle.contextManager.get(BattleContext.Type.WEATHER)?.firstOrNull()?.id?.lowercase() ?: ""
            if (weather.contains("hail") || weather.contains("snow")) speed *= 2.0
        }
        "surgesurfer" -> {
            val terrain = context.battle.contextManager.get(BattleContext.Type.TERRAIN)?.firstOrNull()?.id?.lowercase() ?: ""
            if (terrain.contains("electric")) speed *= 2.0
        }
        "unburden" -> {
            if (context.memory.hasItemBeenConsumed(pokemon.uuid)) speed *= 2.0
        }
    }

    return speed
}

/**
 * Calculate type effectiveness multiplier using the Kotlin type chart.
 * Does not account for ability-based immunities (e.g., Flash Fire, Levitate).
 */
private fun calculateTypeEffectiveness(attackingType: String, defendingTypes: List<String>): Double {
    var multiplier = 1.0

    val attackType = ElementalTypes.get(attackingType) ?: return multiplier

    for (defType in defendingTypes) {
        val defendType = ElementalTypes.get(defType) ?: continue
        multiplier *= AIUtility.getDamageMultiplier(attackType, defendType)
    }

    return multiplier
}

/**
 * Rough damage estimation for AI decision making.
 * Incorporates held item effects, weather, terrain, and ability immunities.
 *
 * Simplified damage formula that accounts for type effectiveness, STAB,
 * abilities, held items, and weather. Does not cover all edge cases.
 */
private fun estimateDamage(
    attacker: BattlePokemon,
    defender: BattlePokemon,
    move: MoveTemplate,
    context: BattleAIMolangContext? = null
): Double {
    if (move.power <= 0) return 0.0

    val moveType = move.elementalType.name.lowercase()
    val defenderAbility = defender.originalPokemon.ability.name.lowercase().replace(" ", "")

    // Check ability-based immunities first (return 0 if immune)
    val abilityImmunity = AIUtility.typeImmuneAbilities[defenderAbility]
    if (abilityImmunity != null && abilityImmunity.name.equals(moveType, ignoreCase = true)) {
        return 0.0
    }

    val isPhysical = move.damageCategory.name.equals("physical", ignoreCase = true)

    // Get base stats
    var attack = if (isPhysical) {
        attacker.originalPokemon.getStat(Stats.ATTACK).toDouble()
    } else {
        attacker.originalPokemon.getStat(Stats.SPECIAL_ATTACK).toDouble()
    }

    var defense = if (isPhysical) {
        defender.originalPokemon.getStat(Stats.DEFENCE).toDouble()
    } else {
        defender.originalPokemon.getStat(Stats.SPECIAL_DEFENCE).toDouble()
    }

    // Get attacker's held item name (normalized)
    val attackerItem = attacker.originalPokemon.heldItem()
    val attackerItemName = if (!attackerItem.isEmpty) {
        attackerItem.item.toString().substringAfterLast(":").lowercase().replace("_", "").replace(" ", "")
    } else ""

    // Apply stat boosts from held items
    if (attackerItemName.isNotEmpty()) {
        val statToCheck = if (isPhysical) "attack" else "specialattack"
        val statMultiplier = HeldItemEffectsRegistry.getStatMultiplier(attackerItemName, statToCheck)
        attack *= statMultiplier
    }

    val level = attacker.originalPokemon.level.toDouble()
    var power = move.power.toDouble()

    // Simplified damage formula
    val baseDamage = ((2 * level / 5 + 2) * power * attack / defense / 50 + 2)

    // Type effectiveness
    var effectiveness = calculateTypeEffectiveness(move.elementalType.name, defender.originalPokemon.types.map { it.name })

    // STAB
    var stab = if (attacker.originalPokemon.types.any { it.name.equals(move.elementalType.name, ignoreCase = true) }) 1.5 else 1.0

    // Attacker ability modifiers
    val attackerAbility = attacker.originalPokemon.ability.name.lowercase().replace(" ", "")
    if (attackerAbility == "adaptability") {
        stab = if (stab > 1.0) 2.0 else 1.0
    }

    // Held item type boost (charcoal, mystic water, etc.)
    val typeBoost = if (attackerItemName.isNotEmpty()) {
        HeldItemEffectsRegistry.getTypeMultiplier(attackerItemName, move.elementalType.name.lowercase())
    } else 1.0

    // Weather modifiers
    var weatherMod = 1.0
    if (context != null) {
        val weather = context.battle.contextManager.get(BattleContext.Type.WEATHER)?.firstOrNull()?.id?.lowercase() ?: ""
        val moveTypeName = move.elementalType.name.lowercase()

        weatherMod = when {
            // Sun boosts Fire, weakens Water (except Hydro Steam)
            (weather == "sunnyday" || weather == "sunny" || weather == "desolateland") -> when {
                moveTypeName == "fire" -> 1.5
                move.name.lowercase() == "hydrosteam" -> 1.5 // Hydro Steam is boosted in sun
                moveTypeName == "water" -> 0.5
                else -> 1.0
            }
            // Rain boosts Water, weakens Fire
            (weather == "raindance" || weather == "raining" || weather == "primordialsea") -> when {
                moveTypeName == "water" -> 1.5
                moveTypeName == "fire" -> 0.5
                else -> 1.0
            }
            else -> 1.0
        }

        // Terrain modifiers (grounded check simplified - doesn't account for Levitate/Flying)
        val terrain = context.battle.contextManager.get(BattleContext.Type.TERRAIN)?.firstOrNull()?.id?.lowercase() ?: ""
        val attackerGrounded = !attacker.originalPokemon.types.any { it.name.equals("flying", ignoreCase = true) }
                && attackerAbility != "levitate"

        if (attackerGrounded && terrain.isNotEmpty()) {
            when {
                terrain.contains("electric") && moveTypeName == "electric" -> weatherMod *= 1.3
                terrain.contains("grassy") && moveTypeName == "grass" -> weatherMod *= 1.3
                terrain.contains("psychic") && moveTypeName == "psychic" -> weatherMod *= 1.3
            }
        }
    }

    return baseDamage * effectiveness * stab * typeBoost * weatherMod
}

/**
 * Helper extension to convert a list of strings to an ArrayStruct.
 */
private fun List<String>.asArrayValue(): ArrayStruct {
    val array = ArrayStruct(hashMapOf())
    this.forEachIndexed { index, value ->
        array.setDirectly("$index", StringValue(value))
    }
    return array
}

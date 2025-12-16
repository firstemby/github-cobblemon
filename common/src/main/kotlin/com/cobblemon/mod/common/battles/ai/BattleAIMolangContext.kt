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
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.battles.ShowdownMoveset
import com.cobblemon.mod.common.battles.InBattleMove
import java.util.UUID

/**
 * Context object that provides all the state needed for evaluating Molang
 * expressions during battle AI decision-making.
 *
 * @property battle The current battle
 * @property actor The AI actor making the decision
 * @property activePokemon The currently active Pokemon on the AI's side
 * @property opponents List of active opponent Pokemon
 * @property allies List of active ally Pokemon (for doubles)
 * @property availableSwitches List of Pokemon available to switch to
 * @property memory The AI's memory of the battle
 * @property personality Optional personality affecting decisions
 *
 * @author Amo Aster
 * @since December 2025
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
    // For move-specific evaluation
    var currentMove: InBattleMove? = null,
    var currentTarget: ActiveBattlePokemon? = null,
    // For switch evaluation
    var switchCandidate: BattlePokemon? = null,
    // For bag item tracking
    val bagState: BattleBagState? = null
) {
    /**
     * Creates a MoLangRuntime with all battle AI query functions populated.
     */
    fun createRuntime(): MoLangRuntime {
        val runtime = MoLangRuntime().setup()

        // Add battle AI specific queries
        runtime.environment.query.addBattleAIFunctions(this)

        return runtime
    }

    /**
     * Creates a copy of this context with a specific move being evaluated.
     */
    fun withMove(move: InBattleMove, target: ActiveBattlePokemon? = opponents.firstOrNull()): BattleAIMolangContext {
        return this.copy(currentMove = move, currentTarget = target)
    }

    /**
     * Creates a copy of this context with a specific switch candidate.
     */
    fun withSwitchCandidate(pokemon: BattlePokemon): BattleAIMolangContext {
        return this.copy(switchCandidate = pokemon)
    }

    companion object {
        /**
         * Creates a context from the current battle state.
         */
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

/**
 * Extension function to add battle AI query functions to a QueryStruct.
 */
fun QueryStruct.addBattleAIFunctions(context: BattleAIMolangContext): QueryStruct {
    // Add q.pokemon.* queries for the active Pokemon
    addFunction("pokemon") { createPokemonStruct(context) }

    // Add q.target.* / q.opponent.* queries
    addFunction("target") { createTargetStruct(context) }
    addFunction("opponent") { createTargetStruct(context) }

    // Add q.move.* queries for current move being evaluated
    addFunction("move") { createMoveStruct(context) }

    // Add q.battle.* queries
    addFunction("battle") { createBattleStruct(context) }

    // Add q.memory.* queries
    addFunction("memory") { createMemoryStruct(context) }

    // Add q.matchup.* queries
    addFunction("matchup") { createMatchupStruct(context) }

    // Add q.ally.* queries for doubles
    addFunction("ally") { createAllyStruct(context) }

    // Add q.allies array for doubles
    addFunction("allies") { createAlliesArray(context) }

    // Add q.field.* queries for field conditions
    addFunction("field") { createFieldStruct(context) }

    // Add q.team.* queries for team-wide info
    addFunction("team") { createTeamStruct(context) }

    // Add aggregate opponent queries
    addFunction("opponents_count") { DoubleValue(context.opponents.size.toDouble()) }
    addFunction("opponents_faster_count") {
        val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val ourSpeed = pokemon.originalPokemon.getStat(Stats.SPEED)
        val fasterCount = context.opponents.count { opp ->
            val oppSpeed = opp.battlePokemon?.originalPokemon?.getStat(Stats.SPEED) ?: 0
            oppSpeed > ourSpeed
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
                isSpreadMove(move.name)
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
        // Check if opponent used a setup move last turn (from memory)
        val isSettingUp = context.opponents.any { opp ->
            val pokemon = opp.battlePokemon ?: return@any false
            context.memory.getLastUsedMove(pokemon.uuid)?.let { lastMove ->
                MoveCategoryMappings.hasCategory(lastMove, MoveCategory.BUFF)
            } ?: false
        }
        DoubleValue(isSettingUp)
    }

    // Add q.personality.* queries
    addFunction("personality") { createPersonalityStruct(context) }

    // Add q.switch_candidate.* queries
    addFunction("switch_candidate") { createSwitchCandidateStruct(context) }

    // Add q.bag.* queries for items
    addFunction("bag") { createBagStruct(context) }

    // Add q.item.* queries for held item lookups
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

    // Ability
    struct.addFunction("ability") { StringValue(pokemon.originalPokemon.ability.name) }
    struct.addFunction("has_ability") { params ->
        val abilityName = params.getString(0).lowercase().replace(" ", "")
        val currentAbility = pokemon.originalPokemon.ability.name.lowercase().replace(" ", "")
        DoubleValue(currentAbility == abilityName)
    }

    // Stats and boosts
    struct.addFunction("stat_boosts") { createStatBoostsStruct(context, pokemon.uuid) }
    struct.addFunction("speed") { DoubleValue(pokemon.originalPokemon.getStat(Stats.SPEED).toDouble()) }
    struct.addFunction("attack") { DoubleValue(pokemon.originalPokemon.getStat(Stats.ATTACK).toDouble()) }
    struct.addFunction("defence") { DoubleValue(pokemon.originalPokemon.getStat(Stats.DEFENCE).toDouble()) }
    struct.addFunction("special_attack") { DoubleValue(pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK).toDouble()) }
    struct.addFunction("special_defence") { DoubleValue(pokemon.originalPokemon.getStat(Stats.SPECIAL_DEFENCE).toDouble()) }

    // Status
    struct.addFunction("status") { StringValue(pokemon.originalPokemon.status?.status?.name?.path ?: "") }
    struct.addFunction("has_status") { DoubleValue(pokemon.originalPokemon.status != null) }

    // Item - basic info
    val heldItem = pokemon.originalPokemon.heldItem()
    val heldItemName = if (!heldItem.isEmpty) {
        heldItem.item.toString().substringAfterLast(":").lowercase().replace("_", "").replace(" ", "")
    } else ""

    struct.addFunction("item") { StringValue(heldItemName) }
    struct.addFunction("has_item") { DoubleValue(!heldItem.isEmpty) }

    // Item effect queries using unified JSON-defined registry
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

    // Move checks
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

    // Trapped status
    struct.addFunction("is_trapped") {
        DoubleValue(context.memory.isTrapped(pokemon.uuid))
    }

    // Check if Pokemon has a specific move by name
    struct.addFunction("has_move") { params ->
        val moveName = params.getString(0).lowercase().replace(" ", "")
        val hasMove = pokemon.originalPokemon.moveSet.any { move ->
            move.name.lowercase().replace(" ", "") == moveName
        }
        DoubleValue(hasMove)
    }

    // Turns on field
    struct.addFunction("turns_on_field") {
        DoubleValue(context.memory.getTurnsOnField(pokemon.uuid).toDouble())
    }

    // Protected last turn (would need tracking)
    struct.addFunction("protected_last_turn") {
        val lastMove = context.memory.getLastUsedMove(pokemon.uuid)
        val protectMoves = setOf("protect", "detect", "spikyshield", "kingsshield", "banefulbunker", "obstruct", "silktrap", "burningbulwark")
        DoubleValue(lastMove?.lowercase()?.replace(" ", "") in protectMoves)
    }

    // Is grounded (not Flying type, no Levitate, no Air Balloon, no Magnet Rise)
    struct.addFunction("is_grounded") {
        val isFlying = pokemon.originalPokemon.types.any { it.name.equals("flying", ignoreCase = true) }
        val hasLevitate = pokemon.originalPokemon.ability.name.equals("levitate", ignoreCase = true)
        val hasAirBalloon = heldItemName == "airballoon"
        val grounded = !isFlying && !hasLevitate && !hasAirBalloon
        DoubleValue(grounded)
    }

    // Is this Pokemon a win condition (heuristic: high offensive stats, set up potential)
    struct.addFunction("is_win_condition") {
        val hasSetup = pokemon.originalPokemon.moveSet.any { move ->
            MoveCategoryMappings.hasCategory(move.name, MoveCategory.BUFF)
        }
        val highOffense = pokemon.originalPokemon.getStat(Stats.ATTACK) > 120 ||
                         pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK) > 120
        DoubleValue(hasSetup || highOffense)
    }

    // Is this Pokemon threatening (high offensive presence)
    struct.addFunction("is_threatening") {
        val attack = pokemon.originalPokemon.getStat(Stats.ATTACK)
        val spAttack = pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
        val threatening = attack > 100 || spAttack > 100
        DoubleValue(threatening)
    }

    // Is this Pokemon targeted by opponents (heuristic based on matchup)
    struct.addFunction("is_targeted") {
        // Assume we're targeted if we're weaker or they have super effective moves
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

    // Gimmick status from memory
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

    // Gimmick availability (from battle context - these remain TODOs as they depend on battle rules)
    struct.addFunction("can_tera") {
        // Check if already terastallized
        if (context.memory.isTerastallized(pokemon.uuid)) return@addFunction DoubleValue.ZERO
        // TODO: Check if Tera is available in this battle format
        DoubleValue.ZERO
    }
    struct.addFunction("can_mega") {
        // Check if already mega evolved
        if (context.memory.isMegaEvolved(pokemon.uuid)) return@addFunction DoubleValue.ZERO
        // TODO: Check if Mega Evolution is available
        DoubleValue.ZERO
    }
    struct.addFunction("can_dynamax") {
        // Check if already dynamaxed
        if (context.memory.isDynamaxed(pokemon.uuid)) return@addFunction DoubleValue.ZERO
        // TODO: Check if Dynamax is available
        DoubleValue.ZERO
    }
    struct.addFunction("tera_type_advantageous") { DoubleValue.ZERO }
    struct.addFunction("tera_resists_incoming") { DoubleValue.ZERO }
    struct.addFunction("will_be_koed") {
        // Estimate if we'll be KO'd this turn based on opponent's predicted damage
        val target = context.opponents.firstOrNull()?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        // Simple heuristic: if we're low HP and they're faster and can hit us hard
        val lowHp = pokemon.health.toDouble() / pokemon.maxHealth.toDouble() < 0.3
        val theyAreFaster = target.originalPokemon.getStat(Stats.SPEED) > pokemon.originalPokemon.getStat(Stats.SPEED)
        DoubleValue(lowHp && theyAreFaster)
    }

    // Used ally switch recently (would need tracking)
    struct.addFunction("used_ally_switch_recently") { DoubleValue.ZERO }

    // Held item name for direct comparison
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

    // Known ability from memory
    struct.addFunction("revealed_ability") {
        StringValue(context.memory.getRevealedAbility(pokemon.uuid) ?: "")
    }
    struct.addFunction("has_revealed_ability") { params ->
        val abilityName = params.getString(0)
        DoubleValue(context.memory.hasRevealedAbility(pokemon.uuid, abilityName))
    }

    // Known item from memory
    struct.addFunction("revealed_item") {
        StringValue(context.memory.getRevealedItem(pokemon.uuid) ?: "")
    }

    // Status
    struct.addFunction("status") { StringValue(pokemon.originalPokemon.status?.status?.name?.path ?: "") }
    struct.addFunction("has_status") { DoubleValue(pokemon.originalPokemon.status != null) }

    // Stat boosts
    struct.addFunction("stat_boosts") { createStatBoostsStruct(context, pokemon.uuid) }

    // Speed estimation
    struct.addFunction("predicted_speed") {
        DoubleValue(pokemon.originalPokemon.getStat(Stats.SPEED).toDouble())
    }
    struct.addFunction("speed") {
        DoubleValue(pokemon.originalPokemon.getStat(Stats.SPEED).toDouble())
    }

    // Offensive stats
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

    // Is physical attacker (Atk > SpA)
    struct.addFunction("is_physical_attacker") {
        val atk = pokemon.originalPokemon.getStat(Stats.ATTACK)
        val spa = pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
        DoubleValue(atk > spa)
    }

    // Gimmick status from memory
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

    // Item consumed status
    struct.addFunction("item_consumed") {
        DoubleValue(context.memory.hasItemBeenConsumed(pokemon.uuid))
    }

    // Is threatening (high offensive stats)
    struct.addFunction("is_threatening") {
        val atk = pokemon.originalPokemon.getStat(Stats.ATTACK)
        val spa = pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK)
        DoubleValue(atk > 100 || spa > 100)
    }

    // Is biggest threat (highest offensive stat among opponents)
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

    // Will attack (heuristic: has damaging moves, not pure support)
    struct.addFunction("will_attack") {
        val hasDamagingMove = pokemon.originalPokemon.moveSet.any { move ->
            val template = Moves.getByName(move.name)
            template != null && template.power > 0
        }
        DoubleValue(hasDamagingMove)
    }

    // Used setup move (from memory)
    struct.addFunction("used_setup_move") {
        val lastMove = context.memory.getLastUsedMove(pokemon.uuid)
        val usedSetup = lastMove != null && MoveCategoryMappings.hasCategory(lastMove, MoveCategory.BUFF)
        DoubleValue(usedSetup)
    }

    // Is support (has support moves, low offensive stats)
    struct.addFunction("is_support") {
        val hasRedirection = pokemon.originalPokemon.moveSet.any { move ->
            move.name.lowercase().replace(" ", "") in setOf("followme", "ragepowder", "spotlight")
        }
        val hasFakeOut = pokemon.originalPokemon.moveSet.any { move ->
            move.name.lowercase().replace(" ", "") == "fakeout"
        }
        val lowOffense = pokemon.originalPokemon.getStat(Stats.ATTACK) < 80 &&
                        pokemon.originalPokemon.getStat(Stats.SPECIAL_ATTACK) < 80
        DoubleValue(hasRedirection || hasFakeOut || lowOffense)
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

    // Type effectiveness
    struct.addFunction("type_effectiveness") {
        val target = context.currentTarget?.battlePokemon ?: return@addFunction DoubleValue(1.0)
        val effectiveness = calculateTypeEffectiveness(moveTemplate.elementalType.name, target.originalPokemon.types.map { it.name })
        DoubleValue(effectiveness)
    }

    // Predicted damage (simplified)
    struct.addFunction("predicted_damage") {
        val target = context.currentTarget?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val damage = estimateDamage(pokemon, target, moveTemplate)
        DoubleValue(damage)
    }
    struct.addFunction("predicted_damage_percent") {
        val target = context.currentTarget?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val pokemon = context.activePokemon?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val damage = estimateDamage(pokemon, target, moveTemplate)
        if (target.maxHealth > 0) {
            DoubleValue(damage / target.maxHealth.toDouble())
        } else {
            DoubleValue.ZERO
        }
    }

    // Is spread move (hits multiple targets)
    struct.addFunction("is_spread") {
        DoubleValue(isSpreadMove(move.id))
    }

    // Is protect move
    struct.addFunction("is_protect") {
        val protectMoves = setOf("protect", "detect", "spikyshield", "kingsshield", "banefulbunker", "obstruct", "silktrap", "burningbulwark", "wideguard", "quickguard", "matblock")
        DoubleValue(move.id.lowercase().replace(" ", "") in protectMoves)
    }

    // Hits ally (spread moves that can damage ally)
    struct.addFunction("hits_ally") {
        val allyHittingMoves = setOf("earthquake", "surf", "discharge", "explosion", "selfdestruct", "bulldoze")
        val hitsAlly = move.id.lowercase().replace(" ", "") in allyHittingMoves && context.allies.isNotEmpty()
        DoubleValue(hitsAlly)
    }

    // Move type as string for comparison
    struct.addFunction("type") { StringValue(moveTemplate.elementalType.name.lowercase()) }

    return struct
}

private fun createBattleStruct(context: BattleAIMolangContext): QueryStruct {
    val struct = QueryStruct(hashMapOf())
    val battle = context.battle

    struct.addFunction("turn") { DoubleValue(battle.turn.toDouble()) }
    struct.addFunction("format") { StringValue(battle.format.battleType.name) }
    struct.addFunction("is_doubles") { DoubleValue(battle.format.battleType.name.contains("double", ignoreCase = true)) }

    // Weather (from Showdown context)
    struct.addFunction("weather") { StringValue(battle.contextManager.get(BattleContext.Type.WEATHER)?.firstOrNull()?.id ?: "") }
    struct.addFunction("has_weather") { params ->
        val weatherName = params.getString(0).lowercase()
        val currentWeather = battle.contextManager.get(BattleContext.Type.WEATHER)?.firstOrNull()?.id?.lowercase() ?: ""
        DoubleValue(currentWeather.contains(weatherName))
    }

    // Terrain
    struct.addFunction("terrain") { StringValue(battle.contextManager.get(BattleContext.Type.TERRAIN)?.firstOrNull()?.id ?: "") }
    struct.addFunction("has_terrain") { params ->
        val terrainName = params.getString(0).lowercase()
        val currentTerrain = battle.contextManager.get(BattleContext.Type.TERRAIN)?.firstOrNull()?.id?.lowercase() ?: ""
        DoubleValue(currentTerrain.contains(terrainName))
    }

    // Hazards on each side
    struct.addFunction("hazards_on_our_side") { createHazardsStruct(context, context.actor.getSide()) }
    struct.addFunction("hazards_on_enemy_side") {
        val enemySide = battle.sides.firstOrNull { it != context.actor.getSide() }
        if (enemySide != null) createHazardsStruct(context, enemySide) else QueryStruct(hashMapOf())
    }

    // Opponent switching check (would need to be set from battle messages)
    struct.addFunction("is_opponent_switching") { DoubleValue.ZERO }

    // Rooms
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

        // Calculate type matchup score
        val ourTypes = pokemon.originalPokemon.types.map { it.name }
        val theirTypes = target.originalPokemon.types.map { it.name }

        var score = 0.0

        // How well we hit them
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

        // How well they hit us
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

        val ourSpeed = pokemon.originalPokemon.getStat(Stats.SPEED)
        val theirSpeed = target.originalPokemon.getStat(Stats.SPEED)

        // Check for Trick Room
        val trickRoom = context.battle.contextManager.get(BattleContext.Type.ROOM)?.firstOrNull()
        val hasTrickRoom = trickRoom != null && trickRoom.id == "trickroom"

        val faster = if (hasTrickRoom) ourSpeed < theirSpeed else ourSpeed > theirSpeed
        DoubleValue(faster)
    }

    struct.addFunction("speed_tier") {
        if (pokemon == null || target == null) return@addFunction DoubleValue.ZERO

        val ourSpeed = pokemon.originalPokemon.getStat(Stats.SPEED)
        val theirSpeed = target.originalPokemon.getStat(Stats.SPEED)

        if (theirSpeed > 0) {
            DoubleValue(ourSpeed.toDouble() / theirSpeed.toDouble())
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

    // Is ally setting up (used a buff move last turn)
    struct.addFunction("is_setting_up") {
        val lastMove = context.memory.getLastUsedMove(ally.uuid)
        DoubleValue(lastMove != null && MoveCategoryMappings.hasCategory(lastMove, MoveCategory.BUFF))
    }

    // Will ally KO target (estimate based on damage)
    struct.addFunction("will_ko_target") {
        val target = context.currentTarget?.battlePokemon ?: context.opponents.firstOrNull()?.battlePokemon
        if (target == null) return@addFunction DoubleValue.ZERO

        // Check if ally has super effective moves
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

    // Is ally targeting same opponent
    struct.addFunction("is_targeting_same") {
        // TODO: Track from battle action queue
        DoubleValue.ZERO
    }

    // Does ally resist the predicted incoming move
    struct.addFunction("resists_predicted_move") {
        // Heuristic based on opponent's types
        val opponent = context.opponents.firstOrNull()?.battlePokemon ?: return@addFunction DoubleValue.ZERO
        val oppTypes = opponent.originalPokemon.types.map { it.name }
        val allyTypes = ally.originalPokemon.types.map { it.name }
        val resists = oppTypes.any { oppType ->
            calculateTypeEffectiveness(oppType, allyTypes) < 1.0
        }
        DoubleValue(resists)
    }

    // Is ally using protection or redirection
    struct.addFunction("is_protecting_or_redirecting") {
        val lastMove = context.memory.getLastUsedMove(ally.uuid)
        val protectMoves = setOf("protect", "detect", "followme", "ragepowder", "wideguard", "quickguard")
        DoubleValue(lastMove?.lowercase()?.replace(" ", "") in protectMoves)
    }

    // Does ally have a spread move
    struct.addFunction("has_spread_move") {
        val hasSpread = ally.originalPokemon.moveSet.any { move ->
            isSpreadMove(move.name)
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

    // Matchup against current opponent
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

    // Has a specific move
    struct.addFunction("has_move") { params ->
        val moveName = params.getString(0).lowercase().replace(" ", "")
        val hasMove = candidate.originalPokemon.moveSet.any { move ->
            move.name.lowercase().replace(" ", "") == moveName
        }
        DoubleValue(hasMove)
    }

    // Sets weather (has weather-setting ability)
    struct.addFunction("sets_weather") {
        val weatherSetters = setOf("drizzle", "drought", "sandstream", "snowwarning", "primordialsea", "desolateland", "deltastream")
        val ability = candidate.originalPokemon.ability.name.lowercase().replace(" ", "")
        DoubleValue(ability in weatherSetters)
    }

    // Sets terrain (has terrain-setting ability)
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

    // Check if bag items are enabled/available
    struct.addFunction("enabled") {
        DoubleValue(bagState != null && bagState.getTotalRemainingUses() > 0)
    }

    // Get total remaining item uses
    struct.addFunction("remaining_uses") {
        DoubleValue((bagState?.getTotalRemainingUses() ?: 0).toDouble())
    }

    // Check if any healing items are available
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

/**
 * Helper function to check if a move is a spread move (hits multiple targets).
 */
private fun isSpreadMove(moveName: String): Boolean {
    val spreadMoves = setOf(
        "earthquake", "surf", "discharge", "heatwave", "rockslide", "dazzlinggleam",
        "blizzard", "explosion", "selfdestruct", "icywind", "snarl", "bulldoze",
        "electrowebelectroweb", "muddywater", "sludgewave", "breakingswipe", "hypervoice",
        "boomburst", "eruption", "waterspout", "spiritbreak", "astralbarrage",
        "fieryrage", "ragingfury", "makeitrain", "bloodmoon", "psystorm",
        "landswrath", "originpulse", "precipiceblades"
    )
    return moveName.lowercase().replace(" ", "") in spreadMoves
}

/**
 * Calculate type effectiveness multiplier.
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
 * Incorporates held item effects from JSON-defined registries.
 */
private fun estimateDamage(attacker: BattlePokemon, defender: BattlePokemon, move: MoveTemplate): Double {
    if (move.power <= 0) return 0.0

    val isPhysical = move.damageCategory.name.equals("physical", ignoreCase = true)

    // Get base stats
    var attack = if (isPhysical) {
        attacker.originalPokemon.getStat(Stats.ATTACK).toDouble()
    } else {
        attacker.originalPokemon.getStat(Stats.SPECIAL_ATTACK).toDouble()
    }

    val defense = if (isPhysical) {
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
    val power = move.power.toDouble()

    // Simplified damage formula
    val baseDamage = ((2 * level / 5 + 2) * power * attack / defense / 50 + 2)

    // Type effectiveness
    val effectiveness = calculateTypeEffectiveness(move.elementalType.name, defender.originalPokemon.types.map { it.name })

    // STAB
    val stab = if (attacker.originalPokemon.types.any { it.name.equals(move.elementalType.name, ignoreCase = true) }) 1.5 else 1.0

    // Held item type boost (charcoal, mystic water, etc.)
    val typeBoost = if (attackerItemName.isNotEmpty()) {
        HeldItemEffectsRegistry.getTypeMultiplier(attackerItemName, move.elementalType.name.lowercase())
    } else 1.0

    return baseDamage * effectiveness * stab * typeBoost
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

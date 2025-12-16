/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.ai

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI
import com.cobblemon.mod.common.api.battles.ai.config.*
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.battles.*
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.item.battle.BagItem
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.util.UUID
import kotlin.random.Random

/**
 * A battle AI implementation that is fully configurable via datapacks.
 * This AI evaluates moves, switches, and items based on Molang conditions
 * and weighted scoring rules defined in BattleAIConfig JSON files.
 *
 * @property configId The resource location of the AI configuration to use
 *
 * @author Amo Aster
 * @since December 2025
 */
class ConfigurableBattleAI(
    private val configId: ResourceLocation = BattleAIConfigs.MEDIUM,
    private val battleBagId: ResourceLocation? = null,
    var debugMode: Boolean = true // Enable debug logging to chat
) : BattleAI {

    private val personalities = mutableMapOf<UUID, Personality>()
    private val bagStates = mutableMapOf<UUID, BattleBagState>()

    /**
     * Gets or creates the memory for a specific battle.
     * Uses the global BattleMemoryRegistry so battle instructions can also record events.
     */
    private fun getMemory(battle: PokemonBattle): AIBattleMemory {
        val config = BattleAIConfigs.getConfig(configId)
        BattleMemoryRegistry.setConfig(battle.battleId, config?.prediction ?: PredictionConfig.DEFAULT)
        return BattleMemoryRegistry.getOrCreate(battle.battleId)
    }

    /**
     * Gets or creates the personality for a specific actor.
     */
    private fun getPersonality(actor: BattleActor): Personality {
        return personalities.getOrPut(actor.uuid) {
            val config = BattleAIConfigs.getConfig(configId)
            val personalityConfig = config?.personality ?: PersonalityConfig.DEFAULT

            if (personalityConfig.enabled) {
                personalityConfig.basePersonality ?: Personality.random(personalityConfig.traitVariance)
            } else {
                Personality.NEUTRAL
            }
        }
    }

    /**
     * Gets or creates the battle bag state for a specific actor.
     * Returns null if no battle bag is configured.
     */
    private fun getBagState(actor: BattleActor): BattleBagState? {
        val bagId = battleBagId ?: return null
        return bagStates.getOrPut(actor.uuid) {
            BattleBagConfigs.createState(bagId)
        }
    }

    /**
     * Checks if the actor can fit a forced action (item use).
     */
    private fun canUseItem(actor: BattleActor): Boolean {
        return actor.canFitForcedAction()
    }

    override fun choose(
        activeBattlePokemon: ActiveBattlePokemon,
        battle: PokemonBattle,
        aiSide: BattleSide,
        moveset: ShowdownMoveset?,
        forceSwitch: Boolean
    ): ShowdownActionResponse {
        val config = BattleAIConfigs.getConfig(configId) ?: BattleAIConfigs.getOrDefault(null)
        val memory = getMemory(battle)
        val personality = getPersonality(activeBattlePokemon.actor)

        // Debug header
        if (debugMode) {
            val pokemonName = activeBattlePokemon.battlePokemon?.getName()?.string ?: "Unknown"
            val pokemonHp = activeBattlePokemon.battlePokemon?.let { "${it.health}/${it.maxHealth}" } ?: "?"
            debugHeader(battle, "$pokemonName's Decision (HP: $pokemonHp)")
            debugLog(battle, "Config: $configId | Skill: ${config.getEffectiveSkillLevel()}", ChatFormatting.GRAY)
            debugLog(battle, "Personality: aggro=${"%.2f".format(personality.aggression)} setup=${"%.2f".format(personality.setupPreference)} risk=${"%.2f".format(personality.riskTolerance)} switch=${"%.2f".format(personality.switchiness)}", ChatFormatting.GRAY)
        }

        val context = BattleAIMolangContext.fromBattleState(
            battle = battle,
            actor = activeBattlePokemon.actor,
            memory = memory,
            personality = personality
        )

        if (forceSwitch || activeBattlePokemon.isGone()) {
            return handleForceSwitch(activeBattlePokemon, context, config)
        }

        // No moveset means we can only pass
        if (moveset == null) {
            return PassActionResponse
        }

        moveset.moves.firstOrNull { it.mustBeUsed() }?.let { move ->
            return chooseMove(move, activeBattlePokemon)
        }

        // Skill check - lower skill may result in random choices
        if (!checkSkillLevel(config.getEffectiveSkillLevel())) {
            return chooseRandomMove(moveset, activeBattlePokemon)
        }

        // Score all available actions
        val availableMoves = moveset.moves.filter { it.canBeUsed() }
        val availableSwitches = activeBattlePokemon.actor.pokemonList.filter { it.canBeSentOut() }

        val scoredActions = mutableListOf<ScoredAction>()

        // Score moves
        if (debugMode) {
            debugSection(battle, "SCORING MOVES (${availableMoves.size} available)")
        }
        for (move in availableMoves) {
            val targets = move.target.targetList(activeBattlePokemon)
                ?.filterIsInstance<ActiveBattlePokemon>()
                ?.filter { it.battlePokemon != null }
                ?: emptyList()

            if (targets.isEmpty() && !move.mustBeUsed()) continue

            val target = targets.firstOrNull()
            val moveContext = context.withMove(move, target)
            val baseScore = scoreMove(move, moveContext, config, battle)
            val finalScore = baseScore * config.getEffectiveMoveBias()

            if (debugMode) {
                debugLog(battle, "  └─ move bias ×${"%.2f".format(config.getEffectiveMoveBias())} → FINAL: ${"%.1f".format(finalScore)}", ChatFormatting.GREEN, 2)
            }

            scoredActions.add(ScoredAction.MoveAction(move, target, finalScore))
        }

        // Score switches
        if (debugMode && availableSwitches.isNotEmpty()) {
            debugSection(battle, "SCORING SWITCHES (${availableSwitches.size} available)")
        }
        for (switchTarget in availableSwitches) {
            val switchContext = context.withSwitchCandidate(switchTarget)
            val baseScore = scoreSwitch(switchTarget, switchContext, config, battle)
            val finalScore = baseScore * config.getEffectiveSwitchBias()

            if (debugMode) {
                debugLog(battle, "  └─ switch bias ×${"%.2f".format(config.getEffectiveSwitchBias())} → FINAL: ${"%.2f".format(finalScore)}", ChatFormatting.GREEN, 2)
            }

            scoredActions.add(ScoredAction.SwitchAction(switchTarget, finalScore))
        }

        // Score items (if bag is configured and items can be used)
        val bagState = getBagState(activeBattlePokemon.actor)
        if (bagState != null && canUseItem(activeBattlePokemon.actor)) {
            val availableItems = bagState.getAvailableItems()
            if (debugMode && availableItems.isNotEmpty()) {
                debugSection(battle, "SCORING ITEMS (${availableItems.size} available)")
            }

            val itemTargets = activeBattlePokemon.actor.pokemonList

            for ((bagItemConfig, remaining) in availableItems) {
                val bagItem = BagItemRegistry.getBagItem(bagItemConfig.itemId)
                if (bagItem == null) {
                    if (debugMode) {
                        debugLog(battle, "Unknown item: ${bagItemConfig.itemId}", ChatFormatting.RED, 2)
                    }
                    continue
                }

                // Find valid targets for this item
                for (target in itemTargets) {
                    if (!bagItem.canUse(ItemStack.EMPTY, battle, target)) continue

                    val baseScore = scoreItem(bagItemConfig, bagItem, target, context, config, battle)
                    val finalScore = baseScore * config.getEffectiveItemBias()

                    if (debugMode) {
                        debugLog(battle, "${bagItemConfig.itemId} → ${target.getName().string}: base=${"%.1f".format(baseScore)} × itemBias=${"%.2f".format(config.getEffectiveItemBias())} → ${"%.1f".format(finalScore)}", ChatFormatting.GOLD, 2)
                    }

                    scoredActions.add(ScoredAction.ItemAction(bagItem, bagItemConfig, target, finalScore))
                }
            }
        }

        // Apply action priorities from config
        if (debugMode && config.actionPriorities.orEmpty().isNotEmpty()) {
            debugSection(battle, "APPLYING ACTION PRIORITIES")
        }
        scoredActions.forEach { action ->
            applyActionPriorities(action, context, config, battle)
        }

        // Apply personality modifiers
        if (debugMode) {
            debugSection(battle, "APPLYING PERSONALITY MODIFIERS")
        }
        scoredActions.forEach { action ->
            applyPersonalityModifiers(action, personality, config, battle)
        }

        // Debug: Log final scored actions summary
        if (debugMode) {
            debugSection(battle, "FINAL SCORES (after all modifiers)")
            val sortedActions = scoredActions.sortedByDescending { it.score }
            for (action in sortedActions.take(8)) { // Show top 8
                when (action) {
                    is ScoredAction.MoveAction -> {
                        val targetName = action.target?.battlePokemon?.getName()?.string ?: "?"
                        val categories = MoveCategoryMappings.getCategories(action.move.id)
                        val catStr = if (categories.isNotEmpty()) " [${categories.joinToString(",")}]" else ""
                        debugLog(battle, "${action.move.id}$catStr → $targetName: ${"%.1f".format(action.score)}", ChatFormatting.AQUA, 1)
                    }
                    is ScoredAction.SwitchAction -> {
                        debugLog(battle, "SWITCH → ${action.pokemon.getName().string}: ${"%.1f".format(action.score)}", ChatFormatting.LIGHT_PURPLE, 1)
                    }
                    is ScoredAction.ItemAction -> {
                        debugLog(battle, "ITEM ${action.bagItemConfig.itemId} → ${action.target.getName().string}: ${"%.1f".format(action.score)}", ChatFormatting.GOLD, 1)
                    }
                }
            }
        }

        // Select best action within margin
        val bestAction = selectBestAction(scoredActions, config.getEffectiveMaxSelectMargin())

        return when (bestAction) {
            is ScoredAction.MoveAction -> {
                debugLog(battle, "→ DECISION: Use ${bestAction.move.id}!", ChatFormatting.GREEN)
                chooseMove(bestAction.move, activeBattlePokemon, bestAction.target)
            }
            is ScoredAction.SwitchAction -> {
                debugLog(battle, "→ DECISION: Switch to ${bestAction.pokemon.getName().string}!", ChatFormatting.GREEN)
                bestAction.pokemon.willBeSwitchedIn = true
                SwitchActionResponse(bestAction.pokemon.uuid)
            }
            is ScoredAction.ItemAction -> {
                debugLog(battle, "→ DECISION: Use ${bestAction.bagItemConfig.itemId} on ${bestAction.target.getName().string}!", ChatFormatting.GOLD)
                getBagState(activeBattlePokemon.actor)?.useItem(bestAction.bagItemConfig.itemId)
                activeBattlePokemon.actor.forceChoose(BagItemActionResponse(bestAction.bagItem, bestAction.target))
                ForcePassActionResponse()
            }
            null -> {
                debugLog(battle, "→ DECISION: No good options, random move!", ChatFormatting.RED)
                chooseRandomMove(moveset, activeBattlePokemon)
            }
        }
    }

    /**
     * Handles force switch situations where the AI must switch to another Pokemon.
     */
    private fun handleForceSwitch(
        activeBattlePokemon: ActiveBattlePokemon,
        context: BattleAIMolangContext,
        config: BattleAIConfig
    ): ShowdownActionResponse {
        val availableSwitches = context.availableSwitches
        val battle = context.battle

        if (debugMode) {
            debugSection(battle, "FORCE SWITCH (must switch)")
        }

        if (availableSwitches.isEmpty()) {
            if (debugMode) {
                debugLog(battle, "No available switches - passing", ChatFormatting.RED)
            }
            return PassActionResponse
        }

        if (context.battle.turn <= 1) {
            val randomSwitch = availableSwitches.randomOrNull() ?: return PassActionResponse
            if (debugMode) {
                debugLog(battle, "Turn 1 - random pick: ${randomSwitch.getName().string}", ChatFormatting.GRAY)
            }
            randomSwitch.willBeSwitchedIn = true
            return SwitchActionResponse(randomSwitch.uuid)
        }

        // Score each potential switch
        if (debugMode) {
            debugSection(battle, "SCORING FORCE SWITCH OPTIONS")
        }
        val scoredSwitches = availableSwitches.map { pokemon ->
            val switchContext = context.withSwitchCandidate(pokemon)
            val score = scoreSwitch(pokemon, switchContext, config, battle)
            pokemon to score
        }

        // Pick the best one
        val best = scoredSwitches.maxByOrNull { it.second }?.first
            ?: availableSwitches.first()

        if (debugMode) {
            debugLog(battle, "→ DECISION: Switch to ${best.getName().string}!", ChatFormatting.GREEN)
        }

        best.willBeSwitchedIn = true
        return SwitchActionResponse(best.uuid)
    }

    /**
     * Scores a move based on configuration rules.
     */
    private fun scoreMove(
        move: InBattleMove,
        context: BattleAIMolangContext,
        config: BattleAIConfig,
        battle: PokemonBattle? = null
    ): Double {
        var score = 1.0
        val moveTemplate = Moves.getByName(move.id)
        val runtime = context.createRuntime()

        // this is heavily wip and kind of non-functional rn
        if (config.shouldUseDamageCalc()) {
            try {
                val damagePercent = runtime.environment.query.functions["move"]
                    ?.apply(com.bedrockk.molang.runtime.MoParams(runtime.environment, emptyList()))
                    ?.let { moveStruct ->
                        if (moveStruct is com.bedrockk.molang.runtime.struct.QueryStruct) {
                            val result = moveStruct.functions["predicted_damage_percent"]
                                ?.apply(com.bedrockk.molang.runtime.MoParams(runtime.environment, emptyList()))
                            (result as? MoValue)?.asDouble() ?: 0.0
                        } else 0.0
                    } ?: 0.0

                score = (damagePercent * 100).coerceAtLeast(1.0)
                if (battle != null && debugMode) {
                    debugLog(battle, "${move.id}: base=${moveTemplate?.power ?: 0} dmg%=${"%.1f".format(damagePercent * 100)} → score=${"%.1f".format(score)}", ChatFormatting.DARK_AQUA, 2)
                }
            } catch (e: Exception) {
                // Fallback if Molang evaluation fails
                score = moveTemplate?.power?.toDouble()?.coerceAtLeast(1.0) ?: 1.0
                if (battle != null && debugMode) {
                    debugLog(battle, "${move.id}: dmg calc failed, power=${"%.1f".format(score)}", ChatFormatting.DARK_AQUA, 2)
                }
            }
        } else {
            // Simple mode - just use base power
            score = moveTemplate?.power?.toDouble()?.coerceAtLeast(1.0) ?: 1.0
            if (battle != null && debugMode) {
                debugLog(battle, "${move.id}: power=${"%.1f".format(score)}", ChatFormatting.DARK_AQUA, 2)
            }
        }

        val categories = MoveCategoryMappings.getCategories(move.id)
        if (categories.contains(MoveCategory.STATUS) || categories.contains(MoveCategory.BUFF) ||
            categories.contains(MoveCategory.MALUS)) {
            val oldScore = score
            score *= config.getEffectiveStatusMoveBias()
            if (battle != null && debugMode && oldScore != score) {
                debugLog(battle, "  └─ status bias ×${"%.2f".format(config.getEffectiveStatusMoveBias())} → ${"%.1f".format(score)}", ChatFormatting.DARK_GRAY, 2)
            }
        }

        // Apply move scoring rules from config
        for (rule in config.moveScoringRules.orEmpty()) {
            try {
                val conditionResult = rule.condition.resolve(runtime)
                if (conditionResult.asDouble() > 0) {
                    val moveConditionPasses = rule.moveCondition?.let {
                        it.resolve(runtime).asDouble() > 0
                    } ?: true

                    if (moveConditionPasses) {
                        val oldScore = score
                        score = applyScoreModifier(score, rule.scoreModifier, runtime)
                        if (battle != null && debugMode) {
                            val ruleId = rule.id ?: "unnamed"
                            debugLog(battle, "  └─ rule '$ruleId' ${rule.scoreModifier.type}(${rule.scoreModifier.value}) ${"%.1f".format(oldScore)}→${"%.1f".format(score)}", ChatFormatting.WHITE, 2)
                        }
                    }
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.debug("Failed to evaluate move scoring rule ${rule.id}: ${e.message}")
            }
        }

        return score
    }

    /**
     * Scores a switch based on configuration rules.
     */
    private fun scoreSwitch(
        pokemon: BattlePokemon,
        context: BattleAIMolangContext,
        config: BattleAIConfig,
        battle: PokemonBattle? = null
    ): Double {
        var score = 0.5 // Base switch score is lower than moves
        val runtime = context.createRuntime()

        if (battle != null && debugMode) {
            val hpPercent = (pokemon.health.toDouble() / pokemon.maxHealth.toDouble() * 100).toInt()
            debugLog(battle, "${pokemon.getName().string} (HP: $hpPercent%): base=0.5", ChatFormatting.LIGHT_PURPLE, 2)
        }

        try {
            val matchupScore = runtime.environment.query.functions["switch_candidate"]
                ?.apply(com.bedrockk.molang.runtime.MoParams(runtime.environment, emptyList()))
                ?.let { struct ->
                    if (struct is com.bedrockk.molang.runtime.struct.QueryStruct) {
                        val result = struct.functions["matchup_score"]
                            ?.apply(com.bedrockk.molang.runtime.MoParams(runtime.environment, emptyList()))
                        (result as? MoValue)?.asDouble() ?: 0.0
                    } else 0.0
                } ?: 0.0

            // Positive matchup = good switch
            val matchupBonus = matchupScore * 0.1
            score += matchupBonus

            // HP consideration
            val hpPercent = pokemon.health.toDouble() / pokemon.maxHealth.toDouble()
            val hpBonus = hpPercent * 0.2
            score += hpBonus

            if (battle != null && debugMode) {
                debugLog(battle, "  └─ matchup: ${"%.2f".format(matchupScore)} (+${"%.2f".format(matchupBonus)}), HP bonus: +${"%.2f".format(hpBonus)} → ${"%.2f".format(score)}", ChatFormatting.DARK_GRAY, 2)
            }
        } catch (e: Exception) {
            Cobblemon.LOGGER.debug("Failed to evaluate switch score: ${e.message}")
        }

        // Apply switch conditions from config
        for (condition in config.switchConditions.orEmpty()) {
            try {
                val conditionResult = condition.condition.resolve(runtime)
                if (conditionResult.asDouble() > 0) {
                    val targetConditionPasses = condition.targetCondition?.let {
                        it.resolve(runtime).asDouble() > 0
                    } ?: true

                    if (targetConditionPasses) {
                        val oldScore = score
                        score *= condition.biasMultiplier
                        if (battle != null && debugMode) {
                            val condId = condition.id ?: "unnamed"
                            debugLog(battle, "  └─ switch condition '$condId' ×${"%.2f".format(condition.biasMultiplier)} ${"%.2f".format(oldScore)}→${"%.2f".format(score)}", ChatFormatting.WHITE, 2)
                        }
                    }
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.debug("Failed to evaluate switch condition ${condition.id}: ${e.message}")
            }
        }

        return score
    }

    /**
     * Scores an item based on configuration rules and current battle state.
     */
    private fun scoreItem(
        bagItemConfig: BattleBagItem,
        bagItem: BagItem,
        target: BattlePokemon,
        context: BattleAIMolangContext,
        config: BattleAIConfig,
        battle: PokemonBattle? = null
    ): Double {
        var score = bagItemConfig.baseScore
        val runtime = context.createRuntime()

        // Calculate urgency based on item category and target state
        when (bagItemConfig.category) {
            BagItemCategory.HEALING -> {
                // Higher score when target has low HP
                val hpPercent = target.health.toDouble() / target.maxHealth.toDouble()
                val hpMissing = 1.0 - hpPercent
                // Score increases as HP decreases (inverse relationship)
                score *= (1.0 + hpMissing * 2.0) // Up to 3x multiplier at 0% HP
                if (battle != null && debugMode) {
                    debugLog(battle, "  └─ healing urgency (HP=${"%.0f".format(hpPercent * 100)}%): ×${"%.2f".format(1.0 + hpMissing * 2.0)}", ChatFormatting.DARK_GRAY, 2)
                }
            }
            BagItemCategory.REVIVE -> {
                // Revives are high priority if Pokemon is fainted
                if (target.health <= 0) {
                    score *= 2.0
                    if (battle != null && debugMode) {
                        debugLog(battle, "  └─ revive priority: ×2.0", ChatFormatting.DARK_GRAY, 2)
                    }
                }
            }
            BagItemCategory.STATUS_CURE -> {
                // Higher score if target has a status
                if (target.originalPokemon.status != null) {
                    score *= 1.5
                    if (battle != null && debugMode) {
                        debugLog(battle, "  └─ status cure priority: ×1.5", ChatFormatting.DARK_GRAY, 2)
                    }
                }
            }
            BagItemCategory.PP_RESTORE -> {
                // Score based on PP depletion (not easily accessible, use base score)
                score *= 0.5 // Lower priority than healing
            }
            BagItemCategory.STAT_BOOST -> {
                // X Items and similar - moderate priority
                score *= 0.8
            }
            BagItemCategory.GUARD -> {
                // Guard Spec, Dire Hit - moderate priority
                score *= 0.8
            }
            BagItemCategory.OTHER -> {
                // Other items - default scoring
            }
        }

        // Apply item usage rules from bag config
        val bagId = battleBagId
        if (bagId != null) {
            val bagConfig = BattleBagConfigs.getConfig(bagId)
            for (rule in bagConfig.usageRules.sortedByDescending { it.priority }) {
                try {
                    if (!bagItemConfig.matches(rule.itemFilter)) continue

                    val conditionResult = rule.condition.resolve(runtime)
                    if (conditionResult.asDouble() > 0) {
                        val oldScore = score
                        score *= rule.biasMultiplier
                        score += rule.scoreBonus

                        if (battle != null && debugMode) {
                            val ruleId = rule.id ?: "unnamed"
                            debugLog(battle, "  └─ item rule '$ruleId': ×${"%.2f".format(rule.biasMultiplier)} +${"%.1f".format(rule.scoreBonus)} ${"%.1f".format(oldScore)}→${"%.1f".format(score)}", ChatFormatting.WHITE, 2)
                        }
                    }
                } catch (e: Exception) {
                    Cobblemon.LOGGER.debug("Failed to evaluate item usage rule ${rule.id}: ${e.message}")
                }
            }
        }

        return score
    }

    /**
     * Applies action priorities from config to modify scores.
     */
    private fun applyActionPriorities(
        action: ScoredAction,
        context: BattleAIMolangContext,
        config: BattleAIConfig,
        battle: PokemonBattle? = null
    ) {
        val runtime = when (action) {
            is ScoredAction.MoveAction -> context.withMove(action.move, action.target).createRuntime()
            is ScoredAction.SwitchAction -> context.withSwitchCandidate(action.pokemon).createRuntime()
            is ScoredAction.ItemAction -> context.createRuntime()
        }

        val actionName = when (action) {
            is ScoredAction.MoveAction -> action.move.id
            is ScoredAction.SwitchAction -> "switch:${action.pokemon.getName().string}"
            is ScoredAction.ItemAction -> "item:${action.bagItemConfig.itemId}"
        }

        for (priority in config.actionPriorities.orEmpty().sortedByDescending { it.priority }) {
            try {
                val conditionResult = priority.condition.resolve(runtime)
                if (conditionResult.asDouble() <= 0) continue

                val actionMatches = when (action) {
                    is ScoredAction.MoveAction -> priority.actionType == ActionType.MOVE
                    is ScoredAction.SwitchAction -> priority.actionType == ActionType.SWITCH
                    is ScoredAction.ItemAction -> priority.actionType == ActionType.ITEM
                }

                if (!actionMatches) continue

                if (action is ScoredAction.MoveAction && priority.moveFilter != null) {
                    val filter = priority.moveFilter
                    val moveCategories = MoveCategoryMappings.getCategories(action.move.id)

                    if (filter.categories != null && !filter.categories.any { it in moveCategories }) continue
                    if (filter.types != null) {
                        val moveTemplate = Moves.getByName(action.move.id)
                        if (moveTemplate != null && moveTemplate.elementalType.name.lowercase() !in filter.types.map { it.lowercase() }) continue
                    }
                    if (filter.moveIds != null && action.move.id !in filter.moveIds) continue
                    if (filter.condition != null) {
                        val filterResult = filter.condition.resolve(runtime)
                        if (filterResult.asDouble() <= 0) continue
                    }
                }

                // Apply the bias multiplier
                val oldScore = action.score
                action.score *= priority.biasMultiplier

                if (battle != null && debugMode) {
                    val prioId = priority.id ?: "unnamed"
                    debugLog(battle, "$actionName: priority '$prioId' (p=${priority.priority}) ×${"%.2f".format(priority.biasMultiplier)} ${"%.1f".format(oldScore)}→${"%.1f".format(action.score)}", ChatFormatting.YELLOW, 1)
                }

            } catch (e: Exception) {
                Cobblemon.LOGGER.debug("Failed to evaluate action priority ${priority.id}: ${e.message}")
            }
        }
    }

    /**
     * Applies personality modifiers to action scores.
     */
    private fun applyPersonalityModifiers(
        action: ScoredAction,
        personality: Personality,
        config: BattleAIConfig,
        battle: PokemonBattle? = null
    ) {
        val personalityConfig = config.personality ?: return
        if (!personalityConfig.enabled) return

        val actionName = when (action) {
            is ScoredAction.MoveAction -> action.move.id
            is ScoredAction.SwitchAction -> "switch:${action.pokemon.getName().string}"
            is ScoredAction.ItemAction -> "item:${action.bagItemConfig.itemId}"
        }
        val oldScore = action.score

        when (action) {
            is ScoredAction.MoveAction -> {
                val categories = MoveCategoryMappings.getCategories(action.move.id)
                val modifiers = mutableListOf<String>()

                // Aggressive personalities prefer damage moves
                if (categories.contains(MoveCategory.DAMAGE)) {
                    val mult = lerp(0.8, 1.2, personality.aggression)
                    action.score *= mult
                    modifiers.add("aggro×${"%.2f".format(mult)}")
                }

                if (categories.contains(MoveCategory.BUFF)) {
                    val mult = lerp(0.7, 1.3, personality.setupPreference)
                    action.score *= mult
                    modifiers.add("setup×${"%.2f".format(mult)}")
                }

                // Risk tolerance affects high-risk moves
                val moveTemplate = Moves.getByName(action.move.id)
                if (moveTemplate != null && moveTemplate.accuracy < 100 && moveTemplate.accuracy > 0) {
                    val mult = lerp(0.8, 1.2, personality.riskTolerance)
                    action.score *= mult
                    modifiers.add("risk×${"%.2f".format(mult)} (acc=${moveTemplate.accuracy})")
                }

                if (battle != null && debugMode && modifiers.isNotEmpty()) {
                    debugLog(battle, "$actionName: ${modifiers.joinToString(", ")} ${"%.1f".format(oldScore)}→${"%.1f".format(action.score)}", ChatFormatting.DARK_PURPLE, 1)
                }
            }
            is ScoredAction.SwitchAction -> {
                // Switchy personalities are more likely to switch
                val mult = lerp(0.7, 1.3, personality.switchiness)
                action.score *= mult

                if (battle != null && debugMode) {
                    debugLog(battle, "$actionName: switchiness×${"%.2f".format(mult)} ${"%.2f".format(oldScore)}→${"%.2f".format(action.score)}", ChatFormatting.DARK_PURPLE, 1)
                }
            }
            is ScoredAction.ItemAction -> {
                // Items don't have personality modifiers currently
            }
        }
    }

    /**
     * Applies a score modifier to the current score.
     */
    private fun applyScoreModifier(
        score: Double,
        modifier: ScoreModifier,
        runtime: MoLangRuntime
    ): Double {
        return when (modifier.type) {
            ModifierType.MULTIPLY -> {
                val factor = modifier.value.resolve(runtime).asDouble()
                score * factor
            }
            ModifierType.ADD -> {
                val value = modifier.value.resolve(runtime).asDouble()
                score + value
            }
            ModifierType.SET -> {
                modifier.value.resolve(runtime).asDouble()
            }
            ModifierType.SCRIPT -> {
                modifier.value.resolve(runtime).asDouble()
            }
        }
    }

    /**
     * Selects the best action from scored actions, with some randomness within the margin.
     */
    private fun selectBestAction(
        actions: List<ScoredAction>,
        margin: Double
    ): ScoredAction? {
        if (actions.isEmpty()) return null

        val maxScore = actions.maxOf { it.score }
        val threshold = maxScore * (1 - margin)

        // Filter to actions within margin of best
        val viableActions = actions.filter { it.score >= threshold }

        // Weighted random selection among viable actions
        val totalWeight = viableActions.sumOf { it.score }
        if (totalWeight <= 0) return viableActions.randomOrNull()

        var random = Random.nextDouble() * totalWeight
        for (action in viableActions) {
            random -= action.score
            if (random <= 0) return action
        }

        return viableActions.lastOrNull()
    }

    /**
     * Chooses a random move when skill check fails.
     */
    private fun chooseRandomMove(
        moveset: ShowdownMoveset,
        activeBattlePokemon: ActiveBattlePokemon
    ): ShowdownActionResponse {
        val availableMoves = moveset.moves.filter { it.canBeUsed() }
            .filter { it.target.targetList(activeBattlePokemon)?.isEmpty() != true }

        if (availableMoves.isEmpty()) {
            return MoveActionResponse("struggle")
        }

        val move = availableMoves.random()
        return chooseMove(move, activeBattlePokemon)
    }

    /**
     * Creates a move action response with proper targeting.
     */
    private fun chooseMove(
        move: InBattleMove,
        activeBattlePokemon: ActiveBattlePokemon,
        target: ActiveBattlePokemon? = null
    ): ShowdownActionResponse {
        val targetPnx = target?.let { getTargetPnx(activeBattlePokemon, it) }
        return MoveActionResponse(move.id, targetPnx)
    }

    /**
     * Gets the position notation (pnx) for targeting.
     */
    private fun getTargetPnx(attacker: ActiveBattlePokemon, target: ActiveBattlePokemon): String? {
        if (attacker.isAllied(target)) {
            return target.getPNX()
        }
        return target.getPNX()
    }

    /**
     * Skill check based on configured skill level.
     */
    private fun checkSkillLevel(skillLevel: Int): Boolean {
        if (skillLevel >= 5) return true
        val randomNumber = Random.nextInt(100)
        return randomNumber < skillLevel * 20
    }

    /**
     * Linear interpolation helper.
     */
    private fun lerp(a: Double, b: Double, t: Double): Double {
        return a + (b - a) * t.coerceIn(0.0, 1.0)
    }

    /**
     * Sends a debug message to all players in the battle via system chat.
     */
    private fun debugLog(battle: PokemonBattle, message: String, color: ChatFormatting = ChatFormatting.GRAY, indent: Int = 0) {
        if (!debugMode) return
        val prefix = "  ".repeat(indent)
        val component = Component.literal("§7[AI]§r $prefix$message").withStyle(color)
        battle.actors.filterIsInstance<PlayerBattleActor>().forEach { actor ->
            actor.entity?.sendSystemMessage(component)
        }
    }

    /**
     * Logs a header section.
     */
    private fun debugHeader(battle: PokemonBattle, message: String) {
        debugLog(battle, "═══════════════════════════════════════", ChatFormatting.DARK_GRAY)
        debugLog(battle, message, ChatFormatting.YELLOW)
        debugLog(battle, "═══════════════════════════════════════", ChatFormatting.DARK_GRAY)
    }

    /**
     * Logs a sub-section.
     */
    private fun debugSection(battle: PokemonBattle, message: String) {
        debugLog(battle, "─── $message ───", ChatFormatting.GOLD)
    }

    /**
     * Clean up battle data when battles end.
     */
    fun onBattleEnd(battleId: UUID) {
        BattleMemoryRegistry.remove(battleId)
    }

    /**
     * Clean up actor data.
     */
    fun onActorRemoved(actorId: UUID) {
        personalities.remove(actorId)
        bagStates.remove(actorId)
    }

    /**
     * Sealed class representing scored actions for comparison.
     */
    private sealed class ScoredAction {
        abstract var score: Double

        data class MoveAction(
            val move: InBattleMove,
            val target: ActiveBattlePokemon?,
            override var score: Double
        ) : ScoredAction()

        data class SwitchAction(
            val pokemon: BattlePokemon,
            override var score: Double
        ) : ScoredAction()

        data class ItemAction(
            val bagItem: BagItem,
            val bagItemConfig: BattleBagItem,
            val target: BattlePokemon,
            override var score: Double
        ) : ScoredAction()
    }

    companion object {
        /**
         * Creates a ConfigurableBattleAI from a legacy skill level.
         *
         * @param skill The skill level (1-5)
         * @param battleBagId Optional battle bag configuration ID
         */
        fun fromSkillLevel(skill: Int, battleBagId: ResourceLocation? = null): ConfigurableBattleAI {
            val configId = BattleAIConfigs.fromSkillLevel(skill)
            return ConfigurableBattleAI(configId, battleBagId)
        }

        /**
         * Creates a ConfigurableBattleAI with full configuration.
         *
         * @param configId The AI configuration ID
         * @param battleBagId Optional battle bag configuration ID
         * @param debugMode Whether to enable debug logging
         */
        fun create(
            configId: ResourceLocation,
            battleBagId: ResourceLocation? = null,
            debugMode: Boolean = false
        ): ConfigurableBattleAI {
            return ConfigurableBattleAI(configId, battleBagId, debugMode)
        }
    }
}

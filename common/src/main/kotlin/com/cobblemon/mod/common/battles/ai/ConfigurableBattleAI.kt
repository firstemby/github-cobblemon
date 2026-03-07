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
import com.cobblemon.mod.common.CobblemonBuildDetails
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI
import com.cobblemon.mod.common.api.battles.ai.config.*
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.battles.*
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.api.battles.ai.config.BagItemCategory
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
 */
class ConfigurableBattleAI(
    private val configId: ResourceLocation = BattleAIConfigs.MEDIUM,
    private val battleBagId: ResourceLocation? = null,
    var debugMode: Boolean = false // Set to true for debug logging to chat
) : BattleAI {

    private val personalities = mutableMapOf<UUID, Personality>()
    private val bagStates = mutableMapOf<UUID, BattleBagState>()
    private val runtimes = mutableMapOf<UUID, MoLangRuntime>()

    private fun getOrCreateRuntime(actorId: UUID): MoLangRuntime {
        return runtimes.getOrPut(actorId) { MoLangRuntime().setup() }
    }

    private fun getMemory(battle: PokemonBattle): AIBattleMemory {
        val config = BattleAIConfigs.getConfig(configId)
        BattleMemoryRegistry.setConfig(battle.battleId, config?.prediction ?: PredictionConfig.DEFAULT)
        return BattleMemoryRegistry.getOrCreate(battle.battleId)
    }

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

    private fun getBagState(actor: BattleActor): BattleBagState? {
        val bagId = battleBagId ?: return null
        return bagStates.getOrPut(actor.uuid) {
            BattleBagConfigs.createState(bagId)
        }
    }

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
        val loadedConfig = BattleAIConfigs.getConfig(configId)

        val config = loadedConfig ?: BattleAIConfigs.getOrDefault(null)
        val memory = getMemory(battle)
        val personality = getPersonality(activeBattlePokemon.actor)

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

        val runtime = getOrCreateRuntime(activeBattlePokemon.actor.uuid)
        context.applyTo(runtime)

        // Run on_turn_start hook
        config.onTurnStart?.let { hook ->
            try {
                hook.resolve(runtime)
            } catch (e: Exception) {
                if (CobblemonBuildDetails.SNAPSHOT) Cobblemon.LOGGER.debug("on_turn_start hook failed: ${e.message}")
            }
        }

        if (forceSwitch || activeBattlePokemon.isGone()) {
            return handleForceSwitch(activeBattlePokemon, context, config, runtime)
        }

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
            moveContext.applyTo(runtime)
            val baseScore = scoreMove(move, moveContext, config, battle, runtime)
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
            switchContext.applyTo(runtime)

            // Run on_switch_evaluate hook
            config.onSwitchEvaluate?.let { hook ->
                try {
                    hook.resolve(runtime)
                } catch (e: Exception) {
                    if (CobblemonBuildDetails.SNAPSHOT) Cobblemon.LOGGER.debug("on_switch_evaluate hook failed: ${e.message}")
                }
            }

            val scoringResult = scoreSwitchWithDetails(switchTarget, switchContext, config, battle, runtime)
            val finalScore = scoringResult.first * config.getEffectiveSwitchBias()

            if (debugMode) {
                debugLog(battle, "  └─ switch bias ×${"%.2f".format(config.getEffectiveSwitchBias())} → FINAL: ${"%.2f".format(finalScore)}", ChatFormatting.GREEN, 2)
            }

            scoredActions.add(ScoredAction.SwitchAction(switchTarget, finalScore))
        }

        // Score items
        val bagState = getBagState(activeBattlePokemon.actor)
        if (bagState != null && canUseItem(activeBattlePokemon.actor)) {
            val availableItems = bagState.getAvailableItems()
            if (debugMode && availableItems.isNotEmpty()) {
                debugSection(battle, "SCORING ITEMS (${availableItems.size} available)")
            }

            val activePokemon = activeBattlePokemon.battlePokemon
            val allPokemon = activeBattlePokemon.actor.pokemonList

            for ((bagItemConfig, remaining) in availableItems) {
                val bagItem = BagItemRegistry.getBagItem(bagItemConfig.itemId)
                if (bagItem == null) {
                    if (debugMode) {
                        debugLog(battle, "Unknown item: ${bagItemConfig.itemId}", ChatFormatting.RED, 2)
                    }
                    continue
                }

                // Revives target fainted benched Pokemon; everything else targets the active Pokemon
                val itemTargets = if (bagItemConfig.category == BagItemCategory.REVIVE) {
                    allPokemon.filter { it.health <= 0 }
                } else {
                    listOfNotNull(activePokemon)
                }

                for (target in itemTargets) {
                    if (!bagItem.canUse(ItemStack.EMPTY, battle, target)) continue

                    context.applyTo(runtime)
                    val baseScore = scoreItem(bagItemConfig, bagItem, target, context, config, battle, runtime)
                    val finalScore = baseScore * config.getEffectiveItemBias()

                    if (debugMode) {
                        debugLog(battle, "${bagItemConfig.itemId} → ${target.getName().string}: base=${"%.1f".format(baseScore)} × itemBias=${"%.2f".format(config.getEffectiveItemBias())} → ${"%.1f".format(finalScore)}", ChatFormatting.GOLD, 2)
                    }

                    scoredActions.add(ScoredAction.ItemAction(bagItem, bagItemConfig, target, finalScore))
                }
            }
        }

        if (debugMode && config.actionPriorities.orEmpty().isNotEmpty()) {
            debugSection(battle, "APPLYING ACTION PRIORITIES")
        }
        scoredActions.forEach { action ->
            applyActionPriorities(action, context, config, battle, runtime)
        }

        // Evaluate gimmick rules
        var selectedGimmickId: String? = null
        if (config.gimmickRules.orEmpty().isNotEmpty() && moveset != null) {
            if (debugMode) {
                debugSection(battle, "EVALUATING GIMMICK RULES")
            }
            selectedGimmickId = evaluateGimmickRules(
                scoredActions, context, config, moveset, runtime, battle
            )
            // Re-apply base context after gimmick evaluation may have modified it
            context.applyTo(runtime)
        }

        if (debugMode) {
            debugSection(battle, "APPLYING PERSONALITY MODIFIERS")
        }
        scoredActions.forEach { action ->
            applyPersonalityModifiers(action, personality, config, battle)
        }

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

        val bestAction = selectBestAction(scoredActions, config.getEffectiveMaxSelectMargin())

        return when (bestAction) {
            is ScoredAction.MoveAction -> {
                debugLog(battle, "→ DECISION: Use ${bestAction.move.id}!", ChatFormatting.GREEN)

                // Run on_move_select hook
                config.onMoveSelect?.let { hook ->
                    try {
                        context.withMove(bestAction.move, bestAction.target).applyTo(runtime)
                        hook.resolve(runtime)
                    } catch (e: Exception) {
                        if (CobblemonBuildDetails.SNAPSHOT) Cobblemon.LOGGER.debug("on_move_select hook failed: ${e.message}")
                    }
                }

                chooseMove(bestAction.move, activeBattlePokemon, bestAction.target, selectedGimmickId)
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

    private fun handleForceSwitch(
        activeBattlePokemon: ActiveBattlePokemon,
        context: BattleAIMolangContext,
        config: BattleAIConfig,
        runtime: MoLangRuntime
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

        if (debugMode) {
            debugSection(battle, "SCORING FORCE SWITCH OPTIONS")
        }
        val scoredSwitches = availableSwitches.map { pokemon ->
            val switchContext = context.withSwitchCandidate(pokemon)
            switchContext.applyTo(runtime)
            val score = scoreSwitch(pokemon, switchContext, config, battle, runtime)
            pokemon to score
        }

        val best = scoredSwitches.maxByOrNull { it.second }?.first
            ?: availableSwitches.first()

        if (debugMode) {
            debugLog(battle, "→ DECISION: Switch to ${best.getName().string}!", ChatFormatting.GREEN)
        }

        best.willBeSwitchedIn = true
        return SwitchActionResponse(best.uuid)
    }

    private fun scoreMove(
        move: InBattleMove,
        context: BattleAIMolangContext,
        config: BattleAIConfig,
        battle: PokemonBattle? = null,
        runtime: MoLangRuntime
    ): Double {
        var score = 1.0
        val moveTemplate = Moves.getByName(move.id)

        val target = context.currentTarget?.battlePokemon ?: context.opponents.firstOrNull()?.battlePokemon
        val targetName = target?.getName()?.string ?: "opponent"
        val targetTypes = target?.originalPokemon?.form?.types?.map { it.name } ?: emptyList()

        if (config.shouldUseDamageCalc() && moveTemplate != null) {
            val attacker = context.activePokemon?.battlePokemon
            val defender = target

            if (attacker != null && defender != null) {
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
                        debugLog(battle, "${move.id}: base=${moveTemplate.power} dmg%=${"%.1f".format(damagePercent * 100)} → score=${"%.1f".format(score)}", ChatFormatting.DARK_AQUA, 2)
                    }
                } catch (e: Exception) {
                    score = moveTemplate.power.toDouble().coerceAtLeast(1.0)
                    if (battle != null && debugMode) {
                        debugLog(battle, "${move.id}: dmg calc error, power=${"%.1f".format(score)}", ChatFormatting.DARK_AQUA, 2)
                    }
                }
            } else {
                score = moveTemplate.power.toDouble().coerceAtLeast(1.0)
            }
        } else {
            score = moveTemplate?.power?.toDouble()?.coerceAtLeast(1.0) ?: 1.0
            if (battle != null && debugMode) {
                debugLog(battle, "${move.id}: power=${"%.1f".format(score)}", ChatFormatting.DARK_AQUA, 2)
            }
        }

        val categories = MoveCategoryMappings.getCategories(move.id)
        if (categories.contains(MoveCategory.STATUS) || categories.contains(MoveCategory.BUFF) ||
            categories.contains(MoveCategory.MALUS)) {
            val oldScore = score
            val bias = config.getEffectiveStatusMoveBias()
            score *= bias
            if (battle != null && debugMode && oldScore != score) {
                debugLog(battle, "  └─ status bias ×${"%.2f".format(bias)} → ${"%.1f".format(score)}", ChatFormatting.DARK_GRAY, 2)
            }
        }

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
                if (CobblemonBuildDetails.SNAPSHOT) Cobblemon.LOGGER.debug("Failed to evaluate move scoring rule ${rule.id}: ${e.message}")
            }
        }

        return score
    }

    private fun scoreSwitch(
        pokemon: BattlePokemon,
        context: BattleAIMolangContext,
        config: BattleAIConfig,
        battle: PokemonBattle? = null,
        runtime: MoLangRuntime
    ): Double {
        var score = 15.0

        if (battle != null && debugMode) {
            val hpPercent = (pokemon.health.toDouble() / pokemon.maxHealth.toDouble() * 100).toInt()
            debugLog(battle, "${pokemon.getName().string} (HP: $hpPercent%): base=15.0", ChatFormatting.LIGHT_PURPLE, 2)
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

            val matchupBonus = matchupScore * 5.0
            score += matchupBonus

            val hpPercent = pokemon.health.toDouble() / pokemon.maxHealth.toDouble()
            val hpBonus = hpPercent * 5.0
            score += hpBonus

            score = score.coerceAtLeast(1.0)

            if (battle != null && debugMode) {
                debugLog(battle, "  └─ matchup: ${"%.2f".format(matchupScore)} (+${"%.2f".format(matchupBonus)}), HP bonus: +${"%.2f".format(hpBonus)} → ${"%.2f".format(score)}", ChatFormatting.DARK_GRAY, 2)
            }
        } catch (e: Exception) {
            if (CobblemonBuildDetails.SNAPSHOT) Cobblemon.LOGGER.debug("Failed to evaluate switch score: ${e.message}")
        }

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
                if (CobblemonBuildDetails.SNAPSHOT) Cobblemon.LOGGER.debug("Failed to evaluate switch condition ${condition.id}: ${e.message}")
            }
        }

        return score
    }

    private fun scoreSwitchWithDetails(
        pokemon: BattlePokemon,
        context: BattleAIMolangContext,
        config: BattleAIConfig,
        battle: PokemonBattle? = null,
        runtime: MoLangRuntime
    ): Pair<Double, Double> {
        var score = 15.0
        var matchupScore = 0.0

        if (battle != null && debugMode) {
            val hpPercent = (pokemon.health.toDouble() / pokemon.maxHealth.toDouble() * 100).toInt()
            debugLog(battle, "${pokemon.getName().string} (HP: $hpPercent%): base=15.0", ChatFormatting.LIGHT_PURPLE, 2)
        }

        try {
            matchupScore = runtime.environment.query.functions["switch_candidate"]
                ?.apply(com.bedrockk.molang.runtime.MoParams(runtime.environment, emptyList()))
                ?.let { struct ->
                    if (struct is com.bedrockk.molang.runtime.struct.QueryStruct) {
                        val result = struct.functions["matchup_score"]
                            ?.apply(com.bedrockk.molang.runtime.MoParams(runtime.environment, emptyList()))
                        (result as? MoValue)?.asDouble() ?: 0.0
                    } else 0.0
                } ?: 0.0

            val matchupBonus = matchupScore * 5.0
            score += matchupBonus

            val hpPercent = pokemon.health.toDouble() / pokemon.maxHealth.toDouble()
            val hpBonus = hpPercent * 5.0
            score += hpBonus

            score = score.coerceAtLeast(1.0)

            if (battle != null && debugMode) {
                debugLog(battle, "  └─ matchup: ${"%.2f".format(matchupScore)} (+${"%.2f".format(matchupBonus)}), HP bonus: +${"%.2f".format(hpBonus)} → ${"%.2f".format(score)}", ChatFormatting.DARK_GRAY, 2)
            }
        } catch (e: Exception) {
            if (CobblemonBuildDetails.SNAPSHOT) Cobblemon.LOGGER.debug("Failed to evaluate switch score: ${e.message}")
        }

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
                if (CobblemonBuildDetails.SNAPSHOT) Cobblemon.LOGGER.debug("Failed to evaluate switch condition ${condition.id}: ${e.message}")
            }
        }

        return Pair(score, matchupScore)
    }

    private fun scoreItem(
        bagItemConfig: BattleBagItem,
        bagItem: BagItem,
        target: BattlePokemon,
        context: BattleAIMolangContext,
        config: BattleAIConfig,
        battle: PokemonBattle? = null,
        runtime: MoLangRuntime
    ): Double {
        var score = bagItemConfig.baseScore

        when (bagItemConfig.category) {
            BagItemCategory.HEALING -> {
                val hpPercent = target.health.toDouble() / target.maxHealth.toDouble()
                val hpMissing = 1.0 - hpPercent
                score *= (1.0 + hpMissing * 2.0)
                if (battle != null && debugMode) {
                    debugLog(battle, "  └─ healing urgency (HP=${"%.0f".format(hpPercent * 100)}%): ×${"%.2f".format(1.0 + hpMissing * 2.0)}", ChatFormatting.DARK_GRAY, 2)
                }
            }
            BagItemCategory.REVIVE -> {
                if (target.health <= 0) {
                    score *= 2.0
                    if (battle != null && debugMode) {
                        debugLog(battle, "  └─ revive priority: ×2.0", ChatFormatting.DARK_GRAY, 2)
                    }
                }
            }
            BagItemCategory.STATUS_CURE -> {
                if (target.originalPokemon.status != null) {
                    score *= 1.5
                    if (battle != null && debugMode) {
                        debugLog(battle, "  └─ status cure priority: ×1.5", ChatFormatting.DARK_GRAY, 2)
                    }
                }
            }
            BagItemCategory.PP_RESTORE -> {
                score *= 0.5
            }
            BagItemCategory.STAT_BOOST -> {
                score *= 0.8
            }
            BagItemCategory.GUARD -> {
                score *= 0.8
            }
            BagItemCategory.OTHER -> {}
        }

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
                    if (CobblemonBuildDetails.SNAPSHOT) Cobblemon.LOGGER.debug("Failed to evaluate item usage rule ${rule.id}: ${e.message}")
                }
            }
        }

        return score
    }

    private fun applyActionPriorities(
        action: ScoredAction,
        context: BattleAIMolangContext,
        config: BattleAIConfig,
        battle: PokemonBattle? = null,
        runtime: MoLangRuntime
    ) {
        val adjustedContext = when (action) {
            is ScoredAction.MoveAction -> context.withMove(action.move, action.target)
            is ScoredAction.SwitchAction -> context.withSwitchCandidate(action.pokemon)
            is ScoredAction.ItemAction -> context
        }
        adjustedContext.applyTo(runtime)

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
                    val allMoveNames = filter.getAllMoveNames()
                    if (allMoveNames != null && action.move.id !in allMoveNames) continue
                    if (filter.condition != null) {
                        val filterResult = filter.condition.resolve(runtime)
                        if (filterResult.asDouble() <= 0) continue
                    }
                }

                val oldScore = action.score
                action.score *= priority.biasMultiplier
                action.score += priority.scoreBonus

                if (battle != null && debugMode) {
                    val prioId = priority.id ?: "unnamed"
                    val bonusText = if (priority.scoreBonus != 0.0) " +${"%.1f".format(priority.scoreBonus)}" else ""
                    debugLog(battle, "$actionName: priority '$prioId' (p=${priority.priority}) ×${"%.2f".format(priority.biasMultiplier)}$bonusText ${"%.1f".format(oldScore)}→${"%.1f".format(action.score)}", ChatFormatting.YELLOW, 1)
                }

            } catch (e: Exception) {
                if (CobblemonBuildDetails.SNAPSHOT) Cobblemon.LOGGER.debug("Failed to evaluate action priority ${priority.id}: ${e.message}")
            }
        }
    }

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
                val mult = lerp(0.7, 1.3, personality.switchiness)
                action.score *= mult

                if (battle != null && debugMode) {
                    debugLog(battle, "$actionName: switchiness×${"%.2f".format(mult)} ${"%.2f".format(oldScore)}→${"%.2f".format(action.score)}", ChatFormatting.DARK_PURPLE, 1)
                }
            }
            is ScoredAction.ItemAction -> {}
        }
    }

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

    private fun evaluateGimmickRules(
        scoredActions: List<ScoredAction>,
        context: BattleAIMolangContext,
        config: BattleAIConfig,
        moveset: ShowdownMoveset,
        runtime: MoLangRuntime,
        battle: PokemonBattle? = null
    ): String? {
        val gimmickRules = config.gimmickRules.orEmpty()
        if (gimmickRules.isEmpty()) return null

        // Get available gimmicks from Showdown's action request
        val availableGimmicks = moveset.getGimmicks().map { it.id }.toSet()
        if (availableGimmicks.isEmpty()) return null

        // Re-apply base context for gimmick evaluation
        context.applyTo(runtime)

        // Evaluate rules sorted by priority (highest first), first match wins
        for (rule in gimmickRules.sortedByDescending { it.priority }) {
            val gimmickId = rule.gimmickType.toGimmickId()

            // Check if this gimmick is actually available
            if (gimmickId !in availableGimmicks) continue

            // Evaluate the condition
            try {
                val conditionResult = rule.condition.resolve(runtime)
                if (conditionResult.asDouble() <= 0) continue
            } catch (e: Exception) {
                if (CobblemonBuildDetails.SNAPSHOT) Cobblemon.LOGGER.debug("Failed to evaluate gimmick rule condition: ${e.message}")
                continue
            }

            if (debugMode && battle != null) {
                debugLog(battle, "Gimmick rule matched: ${rule.gimmickType} (priority ${rule.priority})", ChatFormatting.LIGHT_PURPLE)
            }

            // Apply bias_multiplier (combined with global gimmick_bias) to matching move actions
            val effectiveBias = rule.biasMultiplier * config.getEffectiveGimmickBias()
            if (effectiveBias != 1.0) {
                for (action in scoredActions) {
                    if (action !is ScoredAction.MoveAction) continue

                    // If moveFilter is specified, only apply bias to matching moves
                    if (rule.moveFilter != null) {
                        val filter = rule.moveFilter
                        val moveCategories = MoveCategoryMappings.getCategories(action.move.id)

                        if (filter.categories != null && !filter.categories.any { it in moveCategories }) continue
                        if (filter.types != null) {
                            val moveTemplate = Moves.getByName(action.move.id)
                            if (moveTemplate != null && moveTemplate.elementalType.name.lowercase() !in filter.types.map { it.lowercase() }) continue
                        }
                        val allMoveNames = filter.getAllMoveNames()
                        if (allMoveNames != null && action.move.id !in allMoveNames) continue
                        if (filter.condition != null) {
                            context.withMove(action.move, action.target).applyTo(runtime)
                            try {
                                val filterResult = filter.condition.resolve(runtime)
                                if (filterResult.asDouble() <= 0) continue
                            } catch (e: Exception) {
                                continue
                            }
                        }
                    }

                    val oldScore = action.score
                    action.score *= effectiveBias

                    if (debugMode && battle != null) {
                        debugLog(battle, "  ${action.move.id}: gimmick bias x${"%.2f".format(effectiveBias)} ${"%.1f".format(oldScore)} -> ${"%.1f".format(action.score)}", ChatFormatting.LIGHT_PURPLE, 1)
                    }
                }
            }

            return gimmickId  // First matching rule wins
        }

        return null
    }

    private fun selectBestAction(
        actions: List<ScoredAction>,
        margin: Double
    ): ScoredAction? {
        if (actions.isEmpty()) return null

        val maxScore = actions.maxOf { it.score }
        val threshold = maxScore * (1 - margin)
        val viableActions = actions.filter { it.score >= threshold }

        val totalWeight = viableActions.sumOf { it.score }
        if (totalWeight <= 0) return viableActions.randomOrNull()

        var random = Random.nextDouble() * totalWeight
        for (action in viableActions) {
            random -= action.score
            if (random <= 0) return action
        }

        return viableActions.lastOrNull()
    }

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

    private fun chooseMove(
        move: InBattleMove,
        activeBattlePokemon: ActiveBattlePokemon,
        target: ActiveBattlePokemon? = null,
        gimmickId: String? = null
    ): ShowdownActionResponse {
        val targets = if (move.mustBeUsed()) null else move.target.targetList(activeBattlePokemon)
        val targetPnx = if (target != null) {
            getTargetPnx(activeBattlePokemon, target)
        } else if (targets != null && targets.isNotEmpty()) {
            val chosen = targets.filter { !it.isAllied(activeBattlePokemon) }.randomOrNull() ?: targets.random()
            chosen.getPNX()
        } else {
            null
        }
        return MoveActionResponse(move.id, targetPnx, gimmickId)
    }

    private fun getTargetPnx(attacker: ActiveBattlePokemon, target: ActiveBattlePokemon): String? {
        if (attacker.isAllied(target)) {
            return target.getPNX()
        }
        return target.getPNX()
    }

    private fun checkSkillLevel(skillLevel: Int): Boolean {
        if (skillLevel >= 5) return true
        val randomNumber = Random.nextInt(100)
        return randomNumber < skillLevel * 20
    }

    private fun lerp(a: Double, b: Double, t: Double): Double {
        return a + (b - a) * t.coerceIn(0.0, 1.0)
    }

    private fun debugLog(battle: PokemonBattle, message: String, color: ChatFormatting = ChatFormatting.GRAY, indent: Int = 0) {
        if (!debugMode) return
        val prefix = "  ".repeat(indent)
        val component = Component.literal("§7[AI]§r $prefix$message").withStyle(color)
        battle.actors.filterIsInstance<PlayerBattleActor>().forEach { actor ->
            actor.entity?.sendSystemMessage(component)
        }
    }

    private fun debugHeader(battle: PokemonBattle, message: String) {
        debugLog(battle, "═══════════════════════════════════════", ChatFormatting.DARK_GRAY)
        debugLog(battle, message, ChatFormatting.YELLOW)
        debugLog(battle, "═══════════════════════════════════════", ChatFormatting.DARK_GRAY)
    }

    private fun debugSection(battle: PokemonBattle, message: String) {
        debugLog(battle, "─── $message ───", ChatFormatting.GOLD)
    }

    fun onBattleEnd(battleId: UUID) {
        BattleMemoryRegistry.remove(battleId)
    }

    fun onActorRemoved(actorId: UUID) {
        // Evaluate on_battle_end hook before cleanup
        val runtime = runtimes[actorId]
        if (runtime != null) {
            val config = BattleAIConfigs.getConfig(configId)
            config?.onBattleEnd?.let { hook ->
                try {
                    hook.resolve(runtime)
                } catch (e: Exception) {
                    if (CobblemonBuildDetails.SNAPSHOT) Cobblemon.LOGGER.debug("on_battle_end hook failed: ${e.message}")
                }
            }
        }
        personalities.remove(actorId)
        bagStates.remove(actorId)
        runtimes.remove(actorId)
    }

    private sealed class ScoredAction {
        abstract var score: Double

        data class MoveAction(
            val move: InBattleMove,
            val target: ActiveBattlePokemon?,
            override var score: Double,
            var gimmickId: String? = null
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
        fun fromSkillLevel(skill: Int, battleBagId: ResourceLocation? = null): ConfigurableBattleAI {
            val configId = BattleAIConfigs.fromSkillLevel(skill)
            return ConfigurableBattleAI(configId, battleBagId)
        }

        fun create(
            configId: ResourceLocation,
            battleBagId: ResourceLocation? = null,
            debugMode: Boolean = false
        ): ConfigurableBattleAI {
            return ConfigurableBattleAI(configId, battleBagId, debugMode)
        }
    }
}

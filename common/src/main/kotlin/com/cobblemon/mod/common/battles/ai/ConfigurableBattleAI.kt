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
import com.cobblemon.mod.common.battles.ai.debug.*
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
    var debugMode: Boolean = false // Set to true for debug logging to chat
) : BattleAI {

    init {
        Cobblemon.LOGGER.info("[ConfigurableBattleAI] Created with configId=$configId, battleBagId=$battleBagId, debugMode=$debugMode")
    }

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
        Cobblemon.LOGGER.info("[ConfigurableBattleAI] choose() called for battle=${battle.battleId}, configId=$configId, forceSwitch=$forceSwitch")
        val loadedConfig = BattleAIConfigs.getConfig(configId)
        Cobblemon.LOGGER.info("[ConfigurableBattleAI] Loaded config '$configId': ${loadedConfig?.let { "parent=${it.parent}, skillLevel=${it.getEffectiveSkillLevel()}, useDamageCalc=${it.shouldUseDamageCalc()}, skill=${it.skill}" } ?: "NULL (using default)"}")

        val config = loadedConfig ?: BattleAIConfigs.getOrDefault(null)
        val memory = getMemory(battle)
        val personality = getPersonality(activeBattlePokemon.actor)

        // Initialize debug log builder if debug server is running
        val logBuilder = if (AIDebugServer.isRunning) {
            AIDecisionLogBuilder(battle, configId, activeBattlePokemon, personality).also {
                it.addThinkingStep(
                    ThinkingPhase.CONTEXT_ANALYSIS,
                    "Analyzing Battle Context",
                    "Turn ${battle.turn} - Evaluating options for ${activeBattlePokemon.battlePokemon?.getName()?.string ?: "Unknown"}",
                    "🔍"
                )
            }
        } else null

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
        logBuilder?.addThinkingStep(
            ThinkingPhase.MOVE_SCORING,
            "Evaluating ${availableMoves.size} Available Moves",
            "Calculating damage potential and strategic value for each move",
            "⚔️"
        )

        for (move in availableMoves) {
            val targets = move.target.targetList(activeBattlePokemon)
                ?.filterIsInstance<ActiveBattlePokemon>()
                ?.filter { it.battlePokemon != null }
                ?: emptyList()

            if (targets.isEmpty() && !move.mustBeUsed()) continue

            val target = targets.firstOrNull()
            val moveContext = context.withMove(move, target)
            val baseScore = scoreMove(move, moveContext, config, battle, logBuilder)
            val finalScore = baseScore * config.getEffectiveMoveBias()

            if (debugMode) {
                debugLog(battle, "  └─ move bias ×${"%.2f".format(config.getEffectiveMoveBias())} → FINAL: ${"%.1f".format(finalScore)}", ChatFormatting.GREEN, 2)
            }

            // Add to log builder with cached scoring steps
            val categories = MoveCategoryMappings.getCategories(move.id)
            val cachedSteps = logBuilder?.getMoveScoringSteps(move.id)
            val evaluation = logBuilder?.addMoveEvaluation(
                move = move,
                target = target,
                baseScore = baseScore,
                finalScore = finalScore,
                damageCalc = cachedSteps?.second,
                categories = categories.map { it.name }
            )
            // Add cached scoring steps to the evaluation
            cachedSteps?.first?.forEach { step ->
                evaluation?.scoringSteps?.add(step)
            }
            // Add the move bias step
            if (evaluation != null && config.getEffectiveMoveBias() != 1.0) {
                evaluation.scoringSteps.add(ScoringStep(
                    type = ScoringStepType.BIAS_MULTIPLIER,
                    description = "Config applies ×${"%.2f".format(config.getEffectiveMoveBias())} move bias to all move scores.",
                    modifier = "×${"%.2f".format(config.getEffectiveMoveBias())} (move bias)",
                    scoreBefore = baseScore,
                    scoreAfter = finalScore
                ))
            }

            scoredActions.add(ScoredAction.MoveAction(move, target, finalScore))
        }

        // Score switches
        if (debugMode && availableSwitches.isNotEmpty()) {
            debugSection(battle, "SCORING SWITCHES (${availableSwitches.size} available)")
        }
        if (availableSwitches.isNotEmpty()) {
            logBuilder?.addThinkingStep(
                ThinkingPhase.SWITCH_SCORING,
                "Evaluating ${availableSwitches.size} Switch Options",
                "Considering type matchups and HP for potential switches",
                "🔄"
            )
        }

        for (switchTarget in availableSwitches) {
            val switchContext = context.withSwitchCandidate(switchTarget)
            val scoringResult = scoreSwitchWithDetails(switchTarget, switchContext, config, battle, logBuilder)
            val finalScore = scoringResult.first * config.getEffectiveSwitchBias()

            if (debugMode) {
                debugLog(battle, "  └─ switch bias ×${"%.2f".format(config.getEffectiveSwitchBias())} → FINAL: ${"%.2f".format(finalScore)}", ChatFormatting.GREEN, 2)
            }

            // Add to log builder with cached scoring steps
            val cachedSwitchSteps = logBuilder?.getSwitchScoringSteps(switchTarget.uuid.toString())
            val switchEval = logBuilder?.addSwitchEvaluation(
                pokemon = switchTarget,
                baseScore = 15.0,
                matchupScore = scoringResult.second,
                finalScore = finalScore
            )
            // Add cached scoring steps
            cachedSwitchSteps?.forEach { step ->
                switchEval?.scoringSteps?.add(step)
            }
            // Add switch bias step
            if (switchEval != null && config.getEffectiveSwitchBias() != 1.0) {
                switchEval.scoringSteps.add(ScoringStep(
                    type = ScoringStepType.BIAS_MULTIPLIER,
                    description = "Config applies ×${"%.2f".format(config.getEffectiveSwitchBias())} switch bias to all switch scores.",
                    modifier = "×${"%.2f".format(config.getEffectiveSwitchBias())} (switch bias)",
                    scoreBefore = scoringResult.first,
                    scoreAfter = finalScore
                ))
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
        if (config.actionPriorities.orEmpty().isNotEmpty()) {
            logBuilder?.addThinkingStep(
                ThinkingPhase.PRIORITY_APPLICATION,
                "Applying ${config.actionPriorities?.size ?: 0} Priority Rules",
                "Evaluating action priority conditions from config",
                "⚡"
            )
        }
        scoredActions.forEach { action ->
            applyActionPriorities(action, context, config, battle, logBuilder)
        }

        // Apply personality modifiers
        if (debugMode) {
            debugSection(battle, "APPLYING PERSONALITY MODIFIERS")
        }
        logBuilder?.addThinkingStep(
            ThinkingPhase.PERSONALITY_APPLICATION,
            "Applying Personality Modifiers",
            "Trainer personality affects scoring: aggro=${"%.0f".format(personality.aggression * 100)}%, " +
                "risk=${"%.0f".format(personality.riskTolerance * 100)}%, setup=${"%.0f".format(personality.setupPreference * 100)}%, " +
                "switch=${"%.0f".format(personality.switchiness * 100)}%",
            "🧠"
        )
        scoredActions.forEach { action ->
            applyPersonalityModifiers(action, personality, config, battle, logBuilder)
        }
        // Update final scores after personality modifiers
        scoredActions.forEach { action ->
            when (action) {
                is ScoredAction.MoveAction -> logBuilder?.updateMoveFinalScore(action.move.id, action.score)
                is ScoredAction.SwitchAction -> logBuilder?.updateSwitchFinalScore(action.pokemon.uuid.toString(), action.score)
                else -> {}
            }
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

        // Add final selection thinking step
        logBuilder?.addThinkingStep(
            ThinkingPhase.FINAL_SELECTION,
            "Making Final Decision",
            "Selected from ${scoredActions.size} evaluated options with ${(config.getEffectiveMaxSelectMargin() * 100).toInt()}% margin",
            "🎯"
        )

        val maxScore = scoredActions.maxOfOrNull { it.score } ?: 0.0

        return when (bestAction) {
            is ScoredAction.MoveAction -> {
                debugLog(battle, "→ DECISION: Use ${bestAction.move.id}!", ChatFormatting.GREEN)

                // Build detailed reasoning
                val moveEval = logBuilder?.let { lb ->
                    lb.build().moveEvaluations.find { it.moveId == bestAction.move.id }
                }
                val damageInfo = moveEval?.damageCalc?.let {
                    "Expected ${it.avgPercent.toInt()}% damage (${it.effectivenessLabel}${if (it.stab) ", STAB" else ""}). "
                } ?: ""
                val targetInfo = bestAction.target?.battlePokemon?.getName()?.string?.let { "Targeting $it. " } ?: ""
                val scoringInfo = moveEval?.scoringSteps?.size?.let { "Applied $it scoring factors. " } ?: ""

                val reasoning = buildString {
                    append(damageInfo)
                    append(targetInfo)
                    append(scoringInfo)
                    append("Final score: ${"%.1f".format(bestAction.score)} (")
                    val margin = ((1 - config.getEffectiveMaxSelectMargin()) * 100).toInt()
                    append("within top $margin% of options).")
                }

                // Record to debug log
                logBuilder?.setFinalDecision(
                    type = DecisionType.MOVE,
                    actionId = bestAction.move.id,
                    actionName = Moves.getByName(bestAction.move.id)?.displayName?.string ?: bestAction.move.id,
                    targetUuid = bestAction.target?.battlePokemon?.uuid?.toString(),
                    targetName = bestAction.target?.battlePokemon?.getName()?.string,
                    score = bestAction.score,
                    reasoning = reasoning,
                    alternativesConsidered = scoredActions.size,
                    marginFromBest = if (maxScore > 0) (maxScore - bestAction.score) / maxScore else 0.0
                )
                logBuilder?.buildAndRecord()

                chooseMove(bestAction.move, activeBattlePokemon, bestAction.target)
            }
            is ScoredAction.SwitchAction -> {
                debugLog(battle, "→ DECISION: Switch to ${bestAction.pokemon.getName().string}!", ChatFormatting.GREEN)

                // Build detailed reasoning
                val switchEval = logBuilder?.let { lb ->
                    lb.build().switchEvaluations.find { it.pokemonUuid == bestAction.pokemon.uuid.toString() }
                }
                val hpInfo = "HP: ${"%.0f".format(switchEval?.hpPercent ?: 0.0)}%. "
                val matchupInfo = switchEval?.matchupScore?.let {
                    when {
                        it > 1 -> "Excellent type matchup (${"+%.1f".format(it)}). "
                        it > 0 -> "Good type matchup (+${"%.1f".format(it)}). "
                        it < -1 -> "Disadvantageous matchup (${"%.1f".format(it)}), but still best option. "
                        it < 0 -> "Neutral-to-poor matchup (${"%.1f".format(it)}). "
                        else -> "Neutral matchup. "
                    }
                } ?: ""
                val scoringInfo = switchEval?.scoringSteps?.size?.let { "Applied $it scoring factors. " } ?: ""

                val reasoning = buildString {
                    append("Switching to ${bestAction.pokemon.getName().string}. ")
                    append(hpInfo)
                    append(matchupInfo)
                    append(scoringInfo)
                    append("Final score: ${"%.2f".format(bestAction.score)} (beat move options).")
                }

                // Record to debug log
                logBuilder?.setFinalDecision(
                    type = DecisionType.SWITCH,
                    actionId = bestAction.pokemon.uuid.toString(),
                    actionName = bestAction.pokemon.getName().string,
                    targetUuid = null,
                    targetName = null,
                    score = bestAction.score,
                    reasoning = reasoning,
                    alternativesConsidered = scoredActions.size,
                    marginFromBest = if (maxScore > 0) (maxScore - bestAction.score) / maxScore else 0.0
                )
                logBuilder?.buildAndRecord()

                bestAction.pokemon.willBeSwitchedIn = true
                SwitchActionResponse(bestAction.pokemon.uuid)
            }
            is ScoredAction.ItemAction -> {
                debugLog(battle, "→ DECISION: Use ${bestAction.bagItemConfig.itemId} on ${bestAction.target.getName().string}!", ChatFormatting.GOLD)

                val targetHp = (bestAction.target.health.toDouble() / bestAction.target.maxHealth.toDouble() * 100).toInt()
                val reasoning = buildString {
                    append("Using ${bestAction.bagItemConfig.itemId} on ${bestAction.target.getName().string} (HP: $targetHp%). ")
                    append("Category: ${bestAction.bagItemConfig.category}. ")
                    append("Item use scored highest at ${"%.1f".format(bestAction.score)}.")
                }

                // Record to debug log
                logBuilder?.setFinalDecision(
                    type = DecisionType.ITEM,
                    actionId = bestAction.bagItemConfig.itemId,
                    actionName = bestAction.bagItemConfig.itemId,
                    targetUuid = bestAction.target.uuid.toString(),
                    targetName = bestAction.target.getName().string,
                    score = bestAction.score,
                    reasoning = reasoning,
                    alternativesConsidered = scoredActions.size,
                    marginFromBest = if (maxScore > 0) (maxScore - bestAction.score) / maxScore else 0.0
                )
                logBuilder?.buildAndRecord()

                getBagState(activeBattlePokemon.actor)?.useItem(bestAction.bagItemConfig.itemId)
                activeBattlePokemon.actor.forceChoose(BagItemActionResponse(bestAction.bagItem, bestAction.target))
                ForcePassActionResponse()
            }
            null -> {
                debugLog(battle, "→ DECISION: No good options, random move!", ChatFormatting.RED)

                val reasoning = "No actions scored positively. Falling back to random move selection. " +
                    "This may happen when all moves have 0 PP or no valid targets exist."

                // Record to debug log
                logBuilder?.setFinalDecision(
                    type = DecisionType.RANDOM,
                    actionId = "random",
                    actionName = "Random Move",
                    targetUuid = null,
                    targetName = null,
                    score = 0.0,
                    reasoning = reasoning,
                    alternativesConsidered = 0,
                    marginFromBest = 0.0
                )
                logBuilder?.buildAndRecord()

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
     * Returns a pair of (score, MoveEvaluation for debug logging)
     */
    private fun scoreMove(
        move: InBattleMove,
        context: BattleAIMolangContext,
        config: BattleAIConfig,
        battle: PokemonBattle? = null,
        logBuilder: AIDecisionLogBuilder? = null
    ): Double {
        var score = 1.0
        val moveTemplate = Moves.getByName(move.id)
        val runtime = context.createRuntime()
        val scoringSteps = mutableListOf<ScoringStep>()
        var damageCalcResult: DamageCalcResult? = null

        val target = context.currentTarget?.battlePokemon ?: context.opponents.firstOrNull()?.battlePokemon
        val targetName = target?.getName()?.string ?: "opponent"
        val targetTypes = target?.originalPokemon?.form?.types?.map { it.name } ?: emptyList()

        if (config.shouldUseDamageCalc() && moveTemplate != null) {
            // Use damage calculation for scoring
            val attacker = context.activePokemon?.battlePokemon
            val defender = target

            if (attacker != null && defender != null) {
                // Get predicted damage percent via Molang (which uses estimateDamage internally)
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

                    val oldScore = score
                    // Scale damage percent to a reasonable score (0-100% maps to roughly 1-100)
                    score = (damagePercent * 100).coerceAtLeast(1.0)

                    // Get effectiveness for display
                    val effectiveness = runtime.environment.query.functions["move"]
                        ?.apply(com.bedrockk.molang.runtime.MoParams(runtime.environment, emptyList()))
                        ?.let { moveStruct ->
                            if (moveStruct is com.bedrockk.molang.runtime.struct.QueryStruct) {
                                val result = moveStruct.functions["effectiveness"]
                                    ?.apply(com.bedrockk.molang.runtime.MoParams(runtime.environment, emptyList()))
                                (result as? MoValue)?.asDouble() ?: 1.0
                            } else 1.0
                        } ?: 1.0

                    val isStab = runtime.environment.query.functions["move"]
                        ?.apply(com.bedrockk.molang.runtime.MoParams(runtime.environment, emptyList()))
                        ?.let { moveStruct ->
                            if (moveStruct is com.bedrockk.molang.runtime.struct.QueryStruct) {
                                val result = moveStruct.functions["is_stab"]
                                    ?.apply(com.bedrockk.molang.runtime.MoParams(runtime.environment, emptyList()))
                                (result as? MoValue)?.asDouble()?.let { it > 0 } ?: false
                            } else false
                        } ?: false

                    val effectivenessLabel = when {
                        effectiveness == 0.0 -> "immune"
                        effectiveness < 0.5 -> "very not effective"
                        effectiveness < 1.0 -> "not very effective"
                        effectiveness == 1.0 -> "neutral"
                        effectiveness < 2.0 -> "super effective"
                        else -> "very super effective"
                    }

                    // Build damage calc result for display
                    val avgDamage = (damagePercent * defender.maxHealth).toInt()
                    damageCalcResult = DamageCalcResult(
                        basePower = moveTemplate.power.toInt(),
                        effectiveness = effectiveness,
                        effectivenessLabel = effectivenessLabel,
                        stab = isStab,
                        minDamage = (avgDamage * 0.85).toInt(),
                        maxDamage = avgDamage,
                        avgDamage = avgDamage,
                        minPercent = damagePercent * 85,
                        maxPercent = damagePercent * 100,
                        avgPercent = damagePercent * 100,
                        isOHKO = damagePercent >= 1.0,
                        is2HKO = damagePercent >= 0.5
                    )

                    // Build detailed description
                    val stabText = if (isStab) " with STAB bonus" else ""
                    val description = "${moveTemplate.elementalType.name} move vs ${targetTypes.joinToString("/")} $targetName is $effectivenessLabel (${effectiveness}x)$stabText. " +
                        "Predicted to deal ~${"%.0f".format(damagePercent * 100)}% damage."

                    scoringSteps.add(ScoringStep(
                        type = ScoringStepType.DAMAGE_CALC,
                        description = description,
                        modifier = "Damage calculation: ${moveTemplate.power} base power → ${"%.1f".format(damagePercent * 100)}% damage",
                        scoreBefore = oldScore,
                        scoreAfter = score
                    ))

                    if (battle != null && debugMode) {
                        debugLog(battle, "${move.id}: base=${moveTemplate.power} dmg%=${"%.1f".format(damagePercent * 100)} → score=${"%.1f".format(score)}", ChatFormatting.DARK_AQUA, 2)
                    }
                } catch (e: Exception) {
                    // Fallback to base power if Molang fails
                    val oldScore = score
                    score = moveTemplate.power.toDouble().coerceAtLeast(1.0)
                    scoringSteps.add(ScoringStep(
                        type = ScoringStepType.BASE_POWER,
                        description = "Using base power ${moveTemplate.power} as fallback (damage calc failed: ${e.message})",
                        modifier = "Base power: ${moveTemplate.power}",
                        scoreBefore = oldScore,
                        scoreAfter = score
                    ))
                    if (battle != null && debugMode) {
                        debugLog(battle, "${move.id}: dmg calc error, power=${"%.1f".format(score)}", ChatFormatting.DARK_AQUA, 2)
                    }
                }
            } else {
                val oldScore = score
                score = moveTemplate.power.toDouble().coerceAtLeast(1.0)
                scoringSteps.add(ScoringStep(
                    type = ScoringStepType.BASE_POWER,
                    description = "No valid target for damage calculation, using base power ${moveTemplate.power}",
                    modifier = "Base power: ${moveTemplate.power}",
                    scoreBefore = oldScore,
                    scoreAfter = score
                ))
            }
        } else {
            // Simple mode - just use base power
            val oldScore = score
            score = moveTemplate?.power?.toDouble()?.coerceAtLeast(1.0) ?: 1.0
            val reason = if (!config.shouldUseDamageCalc()) "Damage calculation disabled in config" else "No move template"
            scoringSteps.add(ScoringStep(
                type = ScoringStepType.BASE_POWER,
                description = "$reason. Using base power ${moveTemplate?.power ?: 1} as starting score.",
                modifier = "Base power: ${moveTemplate?.power ?: 1}",
                scoreBefore = oldScore,
                scoreAfter = score
            ))
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
            val categoryNames = categories.filter { it in listOf(MoveCategory.STATUS, MoveCategory.BUFF, MoveCategory.MALUS) }
                .joinToString(", ") { it.name.lowercase() }
            scoringSteps.add(ScoringStep(
                type = ScoringStepType.STATUS_BIAS,
                description = "This is a $categoryNames move. Config applies ×${"%.2f".format(bias)} status move bias " +
                    "(${if (bias > 1) "AI prefers" else if (bias < 1) "AI discourages" else "neutral on"} status moves).",
                modifier = "×${"%.2f".format(bias)} (status move bias)",
                scoreBefore = oldScore,
                scoreAfter = score
            ))
            if (battle != null && debugMode && oldScore != score) {
                debugLog(battle, "  └─ status bias ×${"%.2f".format(bias)} → ${"%.1f".format(score)}", ChatFormatting.DARK_GRAY, 2)
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
                        val ruleId = rule.id ?: "unnamed"
                        val modifierDesc = when (rule.scoreModifier.type) {
                            ModifierType.MULTIPLY -> "×${rule.scoreModifier.value}"
                            ModifierType.ADD -> "+${rule.scoreModifier.value}"
                            ModifierType.SET -> "=${rule.scoreModifier.value}"
                            ModifierType.SCRIPT -> "script(${rule.scoreModifier.value})"
                        }
                        scoringSteps.add(ScoringStep(
                            type = ScoringStepType.MOVE_RULE,
                            description = "Rule '$ruleId' matched: ${rule.condition}. Applied $modifierDesc modifier.",
                            modifier = "$modifierDesc (rule: $ruleId)",
                            scoreBefore = oldScore,
                            scoreAfter = score
                        ))
                        if (battle != null && debugMode) {
                            debugLog(battle, "  └─ rule '$ruleId' ${rule.scoreModifier.type}(${rule.scoreModifier.value}) ${"%.1f".format(oldScore)}→${"%.1f".format(score)}", ChatFormatting.WHITE, 2)
                        }
                    }
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.debug("Failed to evaluate move scoring rule ${rule.id}: ${e.message}")
            }
        }

        // Store scoring steps in the log builder if available
        logBuilder?.setMoveScoringSteps(move.id, scoringSteps, damageCalcResult)

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
        // Switch scores must be on the same scale as move scores (1-100 range).
        // Base of 15 represents "average neutral move damage" so switches compete fairly.
        var score = 15.0
        val runtime = context.createRuntime()

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

            // Positive matchup = good switch. Scale by 5 to produce meaningful score differences.
            // matchup_score ranges roughly -6 to +6, so this adds -30 to +30.
            val matchupBonus = matchupScore * 5.0
            score += matchupBonus

            // HP consideration - healthy switch-ins are better (adds 0-5)
            val hpPercent = pokemon.health.toDouble() / pokemon.maxHealth.toDouble()
            val hpBonus = hpPercent * 5.0
            score += hpBonus

            // Floor at 1.0 so switches are always at least considered
            score = score.coerceAtLeast(1.0)

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
     * Scores a switch and returns both the final score and the matchup score for debugging.
     * @return Pair of (finalScore, matchupScore)
     */
    private fun scoreSwitchWithDetails(
        pokemon: BattlePokemon,
        context: BattleAIMolangContext,
        config: BattleAIConfig,
        battle: PokemonBattle? = null,
        logBuilder: AIDecisionLogBuilder? = null
    ): Pair<Double, Double> {
        // Switch scores must be on the same scale as move scores (1-100 range).
        // Base of 15 represents "average neutral move damage" so switches compete fairly.
        var score = 15.0
        val runtime = context.createRuntime()
        var matchupScore = 0.0
        val scoringSteps = mutableListOf<ScoringStep>()

        val switchTypes = pokemon.originalPokemon.form.types.map { it.name }
        val opponent = context.opponents.firstOrNull()?.battlePokemon
        val opponentTypes = opponent?.originalPokemon?.form?.types?.map { it.name } ?: emptyList()
        val opponentName = opponent?.getName()?.string ?: "opponent"

        scoringSteps.add(ScoringStep(
            type = ScoringStepType.BASE_POWER,
            description = "Switch actions start with a base score of 15.0 (comparable to a neutral damage move, so switches can compete when matchups warrant it).",
            modifier = "Base: 15.0",
            scoreBefore = 0.0,
            scoreAfter = score
        ))

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

            // Positive matchup = good switch. Scale by 5 to produce meaningful score differences.
            // matchup_score ranges roughly -6 to +6, so this adds -30 to +30.
            val oldScoreMatchup = score
            val matchupBonus = matchupScore * 5.0
            score += matchupBonus

            val matchupDescription = when {
                matchupScore > 2 -> "Excellent type matchup vs $opponentName (${switchTypes.joinToString("/")} vs ${opponentTypes.joinToString("/")})"
                matchupScore > 0 -> "Good type matchup vs $opponentName (${switchTypes.joinToString("/")} vs ${opponentTypes.joinToString("/")})"
                matchupScore < -2 -> "Poor type matchup vs $opponentName (${switchTypes.joinToString("/")} vs ${opponentTypes.joinToString("/")})"
                matchupScore < 0 -> "Disadvantageous matchup vs $opponentName (${switchTypes.joinToString("/")} vs ${opponentTypes.joinToString("/")})"
                else -> "Neutral type matchup vs $opponentName"
            }

            scoringSteps.add(ScoringStep(
                type = ScoringStepType.MATCHUP_BONUS,
                description = "$matchupDescription. Matchup score: ${"%.2f".format(matchupScore)} → bonus: +${"%.1f".format(matchupBonus)}",
                modifier = "+${"%.1f".format(matchupBonus)} (matchup)",
                scoreBefore = oldScoreMatchup,
                scoreAfter = score
            ))

            // HP consideration - healthy switch-ins are better (adds 0-5)
            val oldScoreHp = score
            val hpPercent = pokemon.health.toDouble() / pokemon.maxHealth.toDouble()
            val hpBonus = hpPercent * 5.0
            score += hpBonus

            val hpDescription = when {
                hpPercent >= 0.9 -> "Nearly full HP (${"%.0f".format(hpPercent * 100)}%) - excellent switch-in candidate"
                hpPercent >= 0.6 -> "Good HP (${"%.0f".format(hpPercent * 100)}%) - viable switch-in"
                hpPercent >= 0.3 -> "Moderate HP (${"%.0f".format(hpPercent * 100)}%) - risky switch-in"
                else -> "Low HP (${"%.0f".format(hpPercent * 100)}%) - poor switch-in candidate"
            }

            // Floor at 1.0 so switches are always at least considered
            score = score.coerceAtLeast(1.0)

            scoringSteps.add(ScoringStep(
                type = ScoringStepType.HP_BONUS,
                description = "$hpDescription. HP bonus: +${"%.1f".format(hpBonus)}",
                modifier = "+${"%.1f".format(hpBonus)} (HP: ${"%.0f".format(hpPercent * 100)}%)",
                scoreBefore = oldScoreHp,
                scoreAfter = score
            ))

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
                        val condId = condition.id ?: "unnamed"
                        scoringSteps.add(ScoringStep(
                            type = ScoringStepType.SWITCH_CONDITION,
                            description = "Switch condition '$condId' matched: ${condition.condition}. Applied ×${"%.2f".format(condition.biasMultiplier)} multiplier.",
                            modifier = "×${"%.2f".format(condition.biasMultiplier)} (condition: $condId)",
                            scoreBefore = oldScore,
                            scoreAfter = score
                        ))
                        if (battle != null && debugMode) {
                            debugLog(battle, "  └─ switch condition '$condId' ×${"%.2f".format(condition.biasMultiplier)} ${"%.2f".format(oldScore)}→${"%.2f".format(score)}", ChatFormatting.WHITE, 2)
                        }
                    }
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.debug("Failed to evaluate switch condition ${condition.id}: ${e.message}")
            }
        }

        // Store scoring steps in log builder
        logBuilder?.setSwitchScoringSteps(pokemon.uuid.toString(), scoringSteps)

        return Pair(score, matchupScore)
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
        battle: PokemonBattle? = null,
        logBuilder: AIDecisionLogBuilder? = null
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
                    // Check both moveIds and names fields
                    val allMoveNames = filter.getAllMoveNames()
                    if (allMoveNames != null && action.move.id !in allMoveNames) continue
                    if (filter.condition != null) {
                        val filterResult = filter.condition.resolve(runtime)
                        if (filterResult.asDouble() <= 0) continue
                    }
                }

                // Apply the bias multiplier
                val oldScore = action.score
                action.score *= priority.biasMultiplier

                val prioId = priority.id ?: "unnamed"
                val step = ScoringStep(
                    type = ScoringStepType.ACTION_PRIORITY,
                    description = "Action priority rule '$prioId' matched (priority level: ${priority.priority}). " +
                        "Condition: ${priority.condition}. Applied ×${"%.2f".format(priority.biasMultiplier)} multiplier.",
                    modifier = "×${"%.2f".format(priority.biasMultiplier)} (priority: $prioId)",
                    scoreBefore = oldScore,
                    scoreAfter = action.score
                )

                when (action) {
                    is ScoredAction.MoveAction -> logBuilder?.addActionPriorityStep(action.move.id, step)
                    is ScoredAction.SwitchAction -> logBuilder?.addActionPrioritySwitchStep(action.pokemon.uuid.toString(), step)
                    else -> {}
                }

                if (battle != null && debugMode) {
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
        battle: PokemonBattle? = null,
        logBuilder: AIDecisionLogBuilder? = null
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
                    val beforeAggro = action.score
                    val mult = lerp(0.8, 1.2, personality.aggression)
                    action.score *= mult
                    modifiers.add("aggro×${"%.2f".format(mult)}")

                    logBuilder?.addPersonalityStep(action.move.id,
                        ScoringStep(
                            type = ScoringStepType.PERSONALITY,
                            description = "This is a damage move. Trainer's aggression (${"%.0f".format(personality.aggression * 100)}%) " +
                                "${if (mult > 1) "boosts" else "reduces"} score by ×${"%.2f".format(mult)}. " +
                                "${if (personality.aggression > 0.6) "Aggressive trainers prefer direct damage." else if (personality.aggression < 0.4) "Cautious trainers may prefer other options." else "Balanced preference for damage moves."}",
                            modifier = "×${"%.2f".format(mult)} (aggression: ${"%.0f".format(personality.aggression * 100)}%)",
                            scoreBefore = beforeAggro,
                            scoreAfter = action.score
                        )
                    )
                }

                if (categories.contains(MoveCategory.BUFF)) {
                    val beforeSetup = action.score
                    val mult = lerp(0.7, 1.3, personality.setupPreference)
                    action.score *= mult
                    modifiers.add("setup×${"%.2f".format(mult)}")

                    logBuilder?.addPersonalityStep(action.move.id,
                        ScoringStep(
                            type = ScoringStepType.PERSONALITY,
                            description = "This is a setup/buff move. Trainer's setup preference (${"%.0f".format(personality.setupPreference * 100)}%) " +
                                "${if (mult > 1) "boosts" else "reduces"} score. " +
                                "${if (personality.setupPreference > 0.6) "This trainer likes to set up before attacking." else if (personality.setupPreference < 0.4) "This trainer prefers immediate action over setup." else "Balanced approach to setup moves."}",
                            modifier = "×${"%.2f".format(mult)} (setup pref: ${"%.0f".format(personality.setupPreference * 100)}%)",
                            scoreBefore = beforeSetup,
                            scoreAfter = action.score
                        )
                    )
                }

                // Risk tolerance affects high-risk moves
                val moveTemplate = Moves.getByName(action.move.id)
                if (moveTemplate != null && moveTemplate.accuracy < 100 && moveTemplate.accuracy > 0) {
                    val beforeRisk = action.score
                    val mult = lerp(0.8, 1.2, personality.riskTolerance)
                    action.score *= mult
                    modifiers.add("risk×${"%.2f".format(mult)} (acc=${moveTemplate.accuracy})")

                    logBuilder?.addPersonalityStep(action.move.id,
                        ScoringStep(
                            type = ScoringStepType.PERSONALITY,
                            description = "Move has ${moveTemplate.accuracy}% accuracy (risky). Trainer's risk tolerance (${"%.0f".format(personality.riskTolerance * 100)}%) " +
                                "${if (mult > 1) "increases" else "decreases"} willingness to use this move. " +
                                "${if (personality.riskTolerance > 0.6) "Risk-taking trainer is willing to gamble on accuracy." else if (personality.riskTolerance < 0.4) "Risk-averse trainer prefers reliable moves." else "Moderate risk tolerance."}",
                            modifier = "×${"%.2f".format(mult)} (risk: ${"%.0f".format(personality.riskTolerance * 100)}%, acc: ${moveTemplate.accuracy}%)",
                            scoreBefore = beforeRisk,
                            scoreAfter = action.score
                        )
                    )
                }

                if (battle != null && debugMode && modifiers.isNotEmpty()) {
                    debugLog(battle, "$actionName: ${modifiers.joinToString(", ")} ${"%.1f".format(oldScore)}→${"%.1f".format(action.score)}", ChatFormatting.DARK_PURPLE, 1)
                }
            }
            is ScoredAction.SwitchAction -> {
                // Switchy personalities are more likely to switch
                val beforeSwitch = action.score
                val mult = lerp(0.7, 1.3, personality.switchiness)
                action.score *= mult

                logBuilder?.addPersonalitySwitchStep(action.pokemon.uuid.toString(),
                    ScoringStep(
                        type = ScoringStepType.PERSONALITY,
                        description = "Trainer's switchiness (${"%.0f".format(personality.switchiness * 100)}%) affects likelihood to switch. " +
                            "${if (personality.switchiness > 0.6) "This trainer frequently rotates Pokemon for advantage." else if (personality.switchiness < 0.4) "This trainer prefers to stay in and fight." else "Balanced switching habits."}",
                        modifier = "×${"%.2f".format(mult)} (switchiness: ${"%.0f".format(personality.switchiness * 100)}%)",
                        scoreBefore = beforeSwitch,
                        scoreAfter = action.score
                    )
                )

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
        val targets = if (move.mustBeUsed()) null else move.target.targetList(activeBattlePokemon)
        val targetPnx = if (target != null) {
            getTargetPnx(activeBattlePokemon, target)
        } else if (targets != null && targets.isNotEmpty()) {
            val chosen = targets.filter { !it.isAllied(activeBattlePokemon) }.randomOrNull() ?: targets.random()
            chosen.getPNX()
        } else {
            null
        }
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

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.ai.debug

import com.cobblemon.mod.common.api.battles.ai.config.Personality
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.battles.ActiveBattlePokemon
import com.cobblemon.mod.common.battles.InBattleMove
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import net.minecraft.resources.ResourceLocation

/**
 * Builder for creating AIDecisionLog instances during AI decision-making.
 *
 * @author Amo Aster
 * @since December 2025
 */
class AIDecisionLogBuilder(
    private val battle: PokemonBattle,
    private val configId: ResourceLocation,
    private val activeBattlePokemon: ActiveBattlePokemon,
    private val personality: Personality
) {
    private val log = AIDecisionLog(
        battleId = battle.battleId.toString(),
        turn = battle.turn,
        configId = configId.toString(),
        activePokemon = createPokemonSnapshot(activeBattlePokemon.battlePokemon, isActive = true),
        opponents = findOpponents().map { createPokemonSnapshot(it, isActive = true, side = "opponent") },
        allies = findAllies().map { createPokemonSnapshot(it, isActive = true, side = "ally") },
        personality = PersonalitySnapshot(
            aggression = personality.aggression,
            riskTolerance = personality.riskTolerance,
            setupPreference = personality.setupPreference,
            switchiness = personality.switchiness,
            itemConservatism = personality.itemConservatism
        ),
        battleState = createBattleStateSnapshot()
    )

    private fun findOpponents(): List<BattlePokemon?> {
        val myActor = activeBattlePokemon.actor
        return battle.activePokemon
            .filter { it.actor != myActor }
            .map { it.battlePokemon }
    }

    private fun findAllies(): List<BattlePokemon?> {
        val myActor = activeBattlePokemon.actor
        return battle.activePokemon
            .filter { it.actor == myActor && it != activeBattlePokemon }
            .map { it.battlePokemon }
    }

    private fun createPokemonSnapshot(pokemon: BattlePokemon?, isActive: Boolean = false, side: String? = null): PokemonSnapshot {
        if (pokemon == null) {
            return PokemonSnapshot(
                uuid = "",
                species = "Unknown",
                form = null,
                nickname = null,
                level = 0,
                currentHp = 0,
                maxHp = 0,
                hpPercent = 0.0,
                status = null,
                types = emptyList(),
                ability = null,
                heldItem = null,
                moves = emptyList(),
                stats = null,
                isActive = isActive,
                side = side
            )
        }

        val originalPokemon = pokemon.originalPokemon
        val species = originalPokemon.species
        val form = originalPokemon.form

        return PokemonSnapshot(
            uuid = pokemon.uuid.toString(),
            species = species.name,
            form = if (form.name != "Normal" && form.name != species.name) form.name else null,
            nickname = originalPokemon.nickname?.string,
            level = originalPokemon.level,
            currentHp = pokemon.health,
            maxHp = pokemon.maxHealth,
            hpPercent = (pokemon.health.toDouble() / pokemon.maxHealth.toDouble() * 100),
            status = originalPokemon.status?.status?.showdownName,
            types = form.types.map { it.name },
            ability = originalPokemon.ability.name,
            heldItem = originalPokemon.heldItem().item.descriptionId,
            moves = createMoveSnapshots(pokemon),
            stats = StatsSnapshot(
                hp = originalPokemon.hp,
                atk = originalPokemon.attack,
                def = originalPokemon.defence,
                spa = originalPokemon.specialAttack,
                spd = originalPokemon.specialDefence,
                spe = originalPokemon.speed
            ),
            isActive = isActive,
            side = side
        )
    }

    private fun createMoveSnapshots(pokemon: BattlePokemon): List<MoveSnapshot> {
        return pokemon.moveSet.getMoves().map { move ->
            val template = move.template
            MoveSnapshot(
                id = template.name,
                displayName = template.displayName.string,
                type = template.elementalType.name,
                category = template.damageCategory.name,
                power = template.power.toInt(),
                accuracy = template.accuracy.toInt(),
                pp = move.currentPp,
                maxPp = move.maxPp,
                priority = template.priority,
                canBeUsed = move.currentPp > 0
            )
        }
    }

    private fun createBattleStateSnapshot(): BattleStateSnapshot {
        // We don't have direct access to Showdown's battle state from here,
        // but we can capture what we know
        val sideConditions = mutableMapOf<String, List<String>>()

        return BattleStateSnapshot(
            weather = null, // Would need to query Showdown
            terrain = null,
            trickRoom = false,
            sideConditions = sideConditions,
            availableSwitches = activeBattlePokemon.actor.pokemonList.count { it.canBeSentOut() },
            canUseItems = activeBattlePokemon.actor.canFitForcedAction()
        )
    }

    fun addThinkingStep(phase: ThinkingPhase, title: String, details: String, icon: String? = null) {
        log.thinkingSteps.add(ThinkingStep(phase, title, details, icon))
    }

    fun addMoveEvaluation(
        move: InBattleMove,
        target: ActiveBattlePokemon?,
        baseScore: Double,
        finalScore: Double,
        damageCalc: DamageCalcResult? = null,
        categories: List<String> = emptyList()
    ): MoveEvaluation {
        val moveTemplate = Moves.getByName(move.id)
        val evaluation = MoveEvaluation(
            moveId = move.id,
            moveName = moveTemplate?.displayName?.string ?: move.id,
            moveType = moveTemplate?.elementalType?.name ?: "Normal",
            moveCategory = moveTemplate?.damageCategory?.name ?: "Physical",
            targetUuid = target?.battlePokemon?.uuid?.toString(),
            targetName = target?.battlePokemon?.getName()?.string,
            baseScore = baseScore,
            finalScore = finalScore,
            damageCalc = damageCalc,
            categories = categories
        )
        log.moveEvaluations.add(evaluation)
        return evaluation
    }

    fun addSwitchEvaluation(
        pokemon: BattlePokemon,
        baseScore: Double,
        matchupScore: Double,
        finalScore: Double
    ): SwitchEvaluation {
        val originalPokemon = pokemon.originalPokemon
        val evaluation = SwitchEvaluation(
            pokemonUuid = pokemon.uuid.toString(),
            pokemonName = pokemon.getName().string,
            species = originalPokemon.species.name,
            hpPercent = (pokemon.health.toDouble() / pokemon.maxHealth.toDouble() * 100),
            types = originalPokemon.form.types.map { it.name },
            baseScore = baseScore,
            matchupScore = matchupScore,
            finalScore = finalScore
        )
        log.switchEvaluations.add(evaluation)
        return evaluation
    }

    fun addItemEvaluation(
        itemId: String,
        itemName: String,
        category: String,
        target: BattlePokemon,
        baseScore: Double,
        finalScore: Double
    ): ItemEvaluation {
        val evaluation = ItemEvaluation(
            itemId = itemId,
            itemName = itemName,
            category = category,
            targetUuid = target.uuid.toString(),
            targetName = target.getName().string,
            baseScore = baseScore,
            finalScore = finalScore
        )
        log.itemEvaluations.add(evaluation)
        return evaluation
    }

    // Temporary storage for scoring steps before MoveEvaluation is created
    private val moveScoringStepsCache = mutableMapOf<String, Pair<List<ScoringStep>, DamageCalcResult?>>()
    private val switchScoringStepsCache = mutableMapOf<String, List<ScoringStep>>()

    fun setMoveScoringSteps(moveId: String, steps: List<ScoringStep>, damageCalc: DamageCalcResult?) {
        moveScoringStepsCache[moveId] = Pair(steps, damageCalc)
    }

    fun setSwitchScoringSteps(pokemonUuid: String, steps: List<ScoringStep>) {
        switchScoringStepsCache[pokemonUuid] = steps
    }

    fun addScoringStep(evaluation: MoveEvaluation, step: ScoringStep) {
        evaluation.scoringSteps.add(step)
    }

    fun addScoringStep(evaluation: SwitchEvaluation, step: ScoringStep) {
        evaluation.scoringSteps.add(step)
    }

    fun addScoringStep(evaluation: ItemEvaluation, step: ScoringStep) {
        evaluation.scoringSteps.add(step)
    }

    /**
     * Retrieves cached scoring steps for a move and clears the cache entry.
     */
    fun getMoveScoringSteps(moveId: String): Pair<List<ScoringStep>, DamageCalcResult?>? {
        return moveScoringStepsCache.remove(moveId)
    }

    /**
     * Retrieves cached scoring steps for a switch and clears the cache entry.
     */
    fun getSwitchScoringSteps(pokemonUuid: String): List<ScoringStep>? {
        return switchScoringStepsCache.remove(pokemonUuid)
    }

    /**
     * Adds a personality scoring step to an existing move evaluation.
     */
    fun addPersonalityStep(moveId: String, step: ScoringStep) {
        log.moveEvaluations.find { it.moveId == moveId }?.scoringSteps?.add(step)
    }

    /**
     * Adds a personality scoring step to an existing switch evaluation.
     */
    fun addPersonalitySwitchStep(pokemonUuid: String, step: ScoringStep) {
        log.switchEvaluations.find { it.pokemonUuid == pokemonUuid }?.scoringSteps?.add(step)
    }

    /**
     * Adds an action priority scoring step to an existing move evaluation.
     */
    fun addActionPriorityStep(moveId: String, step: ScoringStep) {
        log.moveEvaluations.find { it.moveId == moveId }?.scoringSteps?.add(step)
    }

    /**
     * Adds an action priority scoring step to an existing switch evaluation.
     */
    fun addActionPrioritySwitchStep(pokemonUuid: String, step: ScoringStep) {
        log.switchEvaluations.find { it.pokemonUuid == pokemonUuid }?.scoringSteps?.add(step)
    }

    /**
     * Updates the final score of a move evaluation after all modifiers.
     */
    fun updateMoveFinalScore(moveId: String, finalScore: Double) {
        log.moveEvaluations.find { it.moveId == moveId }?.let { it.finalScore = finalScore }
    }

    /**
     * Updates the final score of a switch evaluation after all modifiers.
     */
    fun updateSwitchFinalScore(pokemonUuid: String, finalScore: Double) {
        log.switchEvaluations.find { it.pokemonUuid == pokemonUuid }?.let { it.finalScore = finalScore }
    }

    fun setFinalDecision(
        type: DecisionType,
        actionId: String,
        actionName: String,
        targetUuid: String?,
        targetName: String?,
        score: Double,
        reasoning: String,
        alternativesConsidered: Int,
        marginFromBest: Double
    ) {
        log.finalDecision = FinalDecision(
            type = type,
            actionId = actionId,
            actionName = actionName,
            targetUuid = targetUuid,
            targetName = targetName,
            score = score,
            reasoning = reasoning,
            alternativesConsidered = alternativesConsidered,
            marginFromBest = marginFromBest
        )
    }

    fun build(): AIDecisionLog {
        return log
    }

    fun buildAndRecord(): AIDecisionLog {
        val result = build()
        AIDebugServer.recordDecision(result)
        return result
    }

    companion object {
        fun createDamageCalcResult(
            basePower: Int,
            effectiveness: Double,
            stab: Boolean,
            minDamage: Int,
            maxDamage: Int,
            avgDamage: Int,
            defenderMaxHp: Int,
            defenderCurrentHp: Int
        ): DamageCalcResult {
            val minPercent = (minDamage.toDouble() / defenderMaxHp) * 100
            val maxPercent = (maxDamage.toDouble() / defenderMaxHp) * 100
            val avgPercent = (avgDamage.toDouble() / defenderMaxHp) * 100

            val effectivenessLabel = when {
                effectiveness == 0.0 -> "Immune"
                effectiveness < 0.5 -> "Very Not Effective"
                effectiveness < 1.0 -> "Not Very Effective"
                effectiveness == 1.0 -> "Neutral"
                effectiveness < 2.0 -> "Super Effective"
                else -> "Very Super Effective"
            }

            return DamageCalcResult(
                basePower = basePower,
                effectiveness = effectiveness,
                effectivenessLabel = effectivenessLabel,
                stab = stab,
                minDamage = minDamage,
                maxDamage = maxDamage,
                avgDamage = avgDamage,
                minPercent = minPercent,
                maxPercent = maxPercent,
                avgPercent = avgPercent,
                isOHKO = minDamage >= defenderCurrentHp,
                is2HKO = minDamage * 2 >= defenderCurrentHp
            )
        }
    }
}

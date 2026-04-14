/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.ai

import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.battles.interpreter.BattleContext
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.*
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility
import com.cobblemon.mod.common.battles.ai.strongBattleAI.ActiveTracker
import com.cobblemon.mod.common.battles.ai.strongBattleAI.BattleAITags
import com.cobblemon.mod.common.battles.ai.strongBattleAI.TrackerActor
import com.cobblemon.mod.common.battles.ai.strongBattleAI.TrackerPokemon
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * Competitive AI variant that prioritizes move and ability tags to evaluate actions.
 * StrongBattleAI remains untouched for fallback/testing.
 */
class NewStrongBattleAI(skill: Int) : BattleAI {
    private val skill = skill.coerceIn(0, 5)

    private val activeTracker = ActiveTracker()

    private val speedTierCoefficient = 4.0
    private var trickRoomCoefficient = 1.0
    private val typeMatchupWeightConsideration = 2.5
    private val moveDamageWeightConsideration = 0.9
    private val hpWeightConsideration = 0.25
    private val hpFractionCoefficient = 0.4
    private val randomProtectChance = 0.3

    private val switchImprovementBase = 2.5
    private val switchOutMatchupThreshold = -2.5
    private val lowHpSwitchThreshold = 0.3
    private val moveDamageScoreWeight = 1.0
    private val moveUtilityScoreWeight = 1.0
    private val friendlyFirePenalty = 1.3

    private val statusRegex = Regex("^(burn|paralysis|sleep|poison|freeze|confusion|flinch|partiallytrapped|attract)_(\\d+)$")
    private val secondaryChanceRegex = Regex("^secondary_chance_(\\d+)$")
    private val priorityRegex = Regex("^priority_(-?\\d+)$")
    private val healRegex = Regex("^heal_(\\d+)$")
    private val statRegex = Regex("^stat_(atk|def|spatk|spdef|speed|accuracy|evasion)_(-?\\d+)(?:_(\\d+))?$")
    private val recoilRegex = Regex("^recoil_(\\d+)$")

    private val selfTargetModes = setOf(
        MoveTarget.self,
        MoveTarget.adjacentAlly,
        MoveTarget.adjacentAllyOrSelf,
        MoveTarget.allyTeam,
        MoveTarget.allies,
        MoveTarget.allySide,
        MoveTarget.scripted,
        MoveTarget.randomNormal
    )

    private data class MoveEvaluation(
        val move: InBattleMove,
        val target: ActiveBattlePokemon?,
        val score: Double,
        val expectedDamage: Double,
        val expectedDamageFraction: Double,
        val canLikelyKo: Boolean
    )

    private data class ParsedStatTag(val stat: Stat, val stages: Int, val chance: Int)

    fun checkSkillLevel(): Boolean {
        if (skill == 5) {
            return true
        }
        return Random.nextInt(100) < skill * 20
    }

    fun checkSwitchOutSkill(): Boolean {
        val chance = when (skill) {
            in 0..2 -> 0.0
            3 -> 0.20
            4 -> 0.60
            5 -> 1.00
            else -> 0.0
        }
        return Random.nextDouble() <= chance
    }

    override fun choose(
        activeBattlePokemon: ActiveBattlePokemon,
        battle: PokemonBattle,
        aiSide: BattleSide,
        moveset: ShowdownMoveset?,
        forceSwitch: Boolean
    ): ShowdownActionResponse {
        updateActiveTracker(aiSide, battle)

        val actorTracker = activeTracker.alliedSide.actors.first { it.uuid == activeBattlePokemon.actor.uuid }
        val availableSwitches = buildLegalSwitches(activeBattlePokemon, actorTracker)

        if (forceSwitch || activeBattlePokemon.isGone()) {
            val bestSwitch = findBestSwitch(activeBattlePokemon, aiSide, battle, availableSwitches)
                ?: return PassActionResponse
            val battlePokemon = bestSwitch.second.second
            if (!isLegalSwitchOption(battlePokemon)) {
                return PassActionResponse
            }
            battlePokemon.willBeSwitchedIn = true
            return SwitchActionResponse(battlePokemon.uuid)
        }

        if (moveset == null) {
            return PassActionResponse
        }

        moveset.moves.firstOrNull { it.mustBeUsed() }?.let {
            return chooseMove(it, activeBattlePokemon)
        }

        val activeTrackerPokemon = activeTracker.alliedSide.activePokemon.first { it.id == activeBattlePokemon.battlePokemon!!.uuid }
        val opponents = activeTracker.opponentSide.activePokemon
        val availableMoves = moveset.moves.filter { it.canBeUsed() }.mapNotNull {
            val template = Moves.getByName(it.id) ?: return@mapNotNull null
            it to template
        }

        if (availableMoves.isEmpty()) {
            return MoveActionResponse("struggle")
        }

        if (activeTrackerPokemon.protectCount > 0) {
            activeTrackerPokemon.protectCount = if (Random.nextDouble() < randomProtectChance) {
                (activeTrackerPokemon.protectCount - 2).coerceAtLeast(0)
            } else {
                (activeTrackerPokemon.protectCount - 1).coerceAtLeast(0)
            }
        }

        if (!checkSkillLevel()) {
            val randomMove = availableMoves
                .filter { it.first.target.targetList(activeBattlePokemon)?.isEmpty() != true }
                .randomOrNull()
                ?: return MoveActionResponse("struggle")
            activeTrackerPokemon.firstTurn = false
            return chooseMove(randomMove.first, activeBattlePokemon)
        }

        val currentMatchup = estimateMatchup(activeBattlePokemon, aiSide, battle)
        val bestMove = findBestMove(
            activeBattlePokemon = activeBattlePokemon,
            activeTrackerPokemon = activeTrackerPokemon,
            opponents = opponents,
            availableMoves = availableMoves,
            battle = battle,
            aiSide = aiSide
        )

        val switchCandidate = findBestSwitch(activeBattlePokemon, aiSide, battle, availableSwitches)
        val shouldSwitch = checkSwitchOutSkill() && shouldSwitchOut(
            activeTrackerPokemon = activeTrackerPokemon,
            currentMatchup = currentMatchup,
            bestSwitchScore = switchCandidate?.first ?: currentMatchup,
            bestMove = bestMove,
            moveset = moveset,
            availableMoves = availableMoves
        )

        if (shouldSwitch && switchCandidate != null) {
            val battlePokemon = switchCandidate.second.second
            if (isLegalSwitchOption(battlePokemon)) {
                battlePokemon.willBeSwitchedIn = true
                return SwitchActionResponse(battlePokemon.uuid)
            }
        }

        if (bestMove != null) {
            activeTrackerPokemon.firstTurn = false
            if ("protect_like" in BattleAITags.getMoveTags(bestMove.move.id) || "invincible" in BattleAITags.getMoveTags(bestMove.move.id)) {
                activeTrackerPokemon.protectCount = (activeTrackerPokemon.protectCount + 2).coerceAtMost(4)
            }
            return chooseMove(bestMove.move, activeBattlePokemon, bestMove.target)
        }

        if (switchCandidate != null && switchCandidate.first > currentMatchup + switchImprovementBase) {
            val battlePokemon = switchCandidate.second.second
            if (isLegalSwitchOption(battlePokemon)) {
                battlePokemon.willBeSwitchedIn = true
                return SwitchActionResponse(battlePokemon.uuid)
            }
        }

        activeTrackerPokemon.firstTurn = false
        val fallback = availableMoves
            .filter { it.first.target.targetList(activeBattlePokemon)?.isEmpty() != true }
            .randomOrNull()
            ?: return MoveActionResponse("struggle")
        return chooseMove(fallback.first, activeBattlePokemon)
    }

    private fun findBestSwitch(
        activeBattlePokemon: ActiveBattlePokemon,
        aiSide: BattleSide,
        battle: PokemonBattle,
        availableSwitches: List<Pair<TrackerPokemon, BattlePokemon>>
    ): Pair<Double, Pair<TrackerPokemon, BattlePokemon>>? {
        return availableSwitches
            .map { estimateMatchup(activeBattlePokemon, aiSide, battle, it.first) to it }
            .maxByOrNull { it.first }
    }

    private fun buildLegalSwitches(
        activeBattlePokemon: ActiveBattlePokemon,
        actorTracker: TrackerActor
    ): List<Pair<TrackerPokemon, BattlePokemon>> {
        val activeIds = activeBattlePokemon.actor.activePokemon.mapNotNull { it.battlePokemon?.uuid }.toSet()
        val trackerPool = (actorTracker.party + actorTracker.activePokemon).filter { it.id != null }

        return activeBattlePokemon.actor.pokemonList
            .asSequence()
            .filter { it.uuid !in activeIds }
            .filter { isLegalSwitchOption(it) }
            .mapNotNull { battlePokemon ->
                val tracked = trackerPool.firstOrNull { it.id == battlePokemon.uuid } ?: return@mapNotNull null
                tracked to battlePokemon
            }
            .toList()
    }

    private fun isLegalSwitchOption(candidate: BattlePokemon): Boolean {
        if (candidate.health <= 0 || candidate.effectedPokemon.currentHealth <= 0) {
            return false
        }
        return try {
            candidate.canBeSentOut()
        } catch (_: Exception) {
            false
        }
    }

    private fun shouldSwitchOut(
        activeTrackerPokemon: TrackerPokemon,
        currentMatchup: Double,
        bestSwitchScore: Double,
        bestMove: MoveEvaluation?,
        moveset: ShowdownMoveset,
        availableMoves: List<Pair<InBattleMove, MoveTemplate>>
    ): Boolean {
        if (moveset.trapped || activeTrackerPokemon.firstTurn) {
            return false
        }

        val speed = statEstimationActive(activeTrackerPokemon, Stats.SPEED)
        val fasterOpponents = activeTracker.opponentSide.activePokemon.any { statEstimationActive(it, Stats.SPEED) > speed }
        if (activeTrackerPokemon.currentHpPercent < lowHpSwitchThreshold && fasterOpponents) {
            return false
        }

        val improvementNeeded = currentMatchup.absoluteValue * 0.5 + switchImprovementBase
        if (bestSwitchScore <= currentMatchup + improvementNeeded) {
            return false
        }

        if (bestMove?.canLikelyKo == true) {
            return false
        }
        if ((bestMove?.expectedDamageFraction ?: 0.0) >= 0.70 && currentMatchup > switchOutMatchupThreshold) {
            return false
        }

        val noUsefulDamageMove = availableMoves
            .filter { it.second.power > 0.0 && it.second.damageCategory != DamageCategories.STATUS }
            .all { pair ->
                activeTracker.opponentSide.activePokemon.all {
                    calculateDamage(pair.first, activeTrackerPokemon, it) <= 0.0
                }
            }
        if (noUsefulDamageMove) {
            return true
        }

        val currentAbility = activeTrackerPokemon.pokemon!!.ability
        val legalAbilities = activeTrackerPokemon.pokemon?.species?.abilities ?: activeTrackerPokemon.form?.abilities ?: activeTrackerPokemon.species!!.abilities
        val truantAbility = Abilities.get("truant")
        val slowStartAbility = Abilities.get("slowstart")
        if ((currentAbility.template == truantAbility && legalAbilities.none { it.template == truantAbility }) ||
            (currentAbility.template == slowStartAbility && legalAbilities.none { it.template == slowStartAbility })
        ) {
            return true
        }

        return currentMatchup < switchOutMatchupThreshold
    }

    private fun findBestMove(
        activeBattlePokemon: ActiveBattlePokemon,
        activeTrackerPokemon: TrackerPokemon,
        opponents: List<TrackerPokemon>,
        availableMoves: List<Pair<InBattleMove, MoveTemplate>>,
        battle: PokemonBattle,
        aiSide: BattleSide
    ): MoveEvaluation? {
        val currentMatchup = estimateMatchup(activeBattlePokemon, aiSide, battle)
        val availableSwitchCount = activeTracker.alliedSide.actors
            .first { activeTrackerPokemon in it.activePokemon }
            .party
            .count { (it.currentHp ?: 0) > 0 }

        return availableMoves.mapNotNull { move ->
            evaluateMove(
                move = move,
                activeBattlePokemon = activeBattlePokemon,
                activeTrackerPokemon = activeTrackerPokemon,
                opponents = opponents,
                currentMatchup = currentMatchup,
                availableSwitchCount = availableSwitchCount
            )
        }.maxByOrNull { it.score }
    }

    private fun evaluateMove(
        move: Pair<InBattleMove, MoveTemplate>,
        activeBattlePokemon: ActiveBattlePokemon,
        activeTrackerPokemon: TrackerPokemon,
        opponents: List<TrackerPokemon>,
        currentMatchup: Double,
        availableSwitchCount: Int
    ): MoveEvaluation? {
        val moveTags = BattleAITags.getMoveTags(move.first.id)
        if (failsContextGate(move.first, moveTags, activeTrackerPokemon)) {
            return null
        }

        val reliability = reliabilityMultiplier(move.second, moveTags)
        val riskPenalty = riskPenalty(moveTags, activeTrackerPokemon, opponents)
        val utilityForNoTarget = utilityScore(
            move = move,
            moveTags = moveTags,
            activeTrackerPokemon = activeTrackerPokemon,
            targetTracker = null,
            currentMatchup = currentMatchup,
            availableSwitchCount = availableSwitchCount
        )

        if (move.first.target in selfTargetModes || "target_self" in moveTags) {
            val score = utilityForNoTarget * moveUtilityScoreWeight * reliability - riskPenalty
            return MoveEvaluation(
                move = move.first,
                target = if (move.first.target == MoveTarget.self) activeBattlePokemon else null,
                score = score,
                expectedDamage = 0.0,
                expectedDamageFraction = 0.0,
                canLikelyKo = false
            )
        }

        val singleTargets = move.first.target.targetList(activeBattlePokemon)
            ?.map { it as ActiveBattlePokemon }
            ?.filter { it.battlePokemon != null }
        if (!singleTargets.isNullOrEmpty()) {
            val evaluated = singleTargets.mapNotNull { target ->
                val tracker = getTrackerPokemon(target.battlePokemon!!.uuid) ?: return@mapNotNull null
                val targetIsAlly = target.isAllied(activeBattlePokemon)
                val rawDamage = calculateDamage(move.first, activeTrackerPokemon, tracker)
                val expectedDamage = if (targetIsAlly) -rawDamage else rawDamage
                val damageFraction = if (targetIsAlly) 0.0 else estimateDamageFraction(rawDamage, tracker)
                val likelyKo = !targetIsAlly && damageFraction >= tracker.currentHpPercent
                val utility = utilityScore(
                    move = move,
                    moveTags = moveTags,
                    activeTrackerPokemon = activeTrackerPokemon,
                    targetTracker = tracker,
                    currentMatchup = currentMatchup,
                    availableSwitchCount = availableSwitchCount
                )
                val koBonus = if (likelyKo) 30.0 else 0.0
                val score = (expectedDamage * moveDamageScoreWeight + utility * moveUtilityScoreWeight + koBonus) * reliability - riskPenalty
                MoveEvaluation(
                    move = move.first,
                    target = target,
                    score = score,
                    expectedDamage = expectedDamage,
                    expectedDamageFraction = damageFraction,
                    canLikelyKo = likelyKo
                )
            }
            return evaluated.maxByOrNull { it.score }
        }

        val multiTargets = activeBattlePokemon.getMultiTargetList(move.first.target)
            ?.map { it as ActiveBattlePokemon }
            ?.filter { it.battlePokemon != null }
        if (!multiTargets.isNullOrEmpty()) {
            val (allies, enemyTargets) = multiTargets.partition { it.isAllied(activeBattlePokemon) }

            val allyDamage = activeTracker.alliedSide.activePokemon
                .filter { tracked -> allies.any { it.battlePokemon!!.uuid == tracked.id } }
                .sumOf { calculateDamage(move.first, activeTrackerPokemon, it) }
            val enemyDamage = activeTracker.opponentSide.activePokemon
                .filter { tracked -> enemyTargets.any { it.battlePokemon!!.uuid == tracked.id } }
                .sumOf { calculateDamage(move.first, activeTrackerPokemon, it) }

            val bestEnemyFraction = activeTracker.opponentSide.activePokemon
                .filter { tracked -> enemyTargets.any { it.battlePokemon!!.uuid == tracked.id } }
                .maxOfOrNull { estimateDamageFraction(calculateDamage(move.first, activeTrackerPokemon, it), it) } ?: 0.0
            val likelyKo = activeTracker.opponentSide.activePokemon
                .filter { tracked -> enemyTargets.any { it.battlePokemon!!.uuid == tracked.id } }
                .any { estimateDamageFraction(calculateDamage(move.first, activeTrackerPokemon, it), it) >= it.currentHpPercent }

            val utility = utilityForNoTarget
            val netDamage = enemyDamage - (allyDamage * friendlyFirePenalty)
            val koBonus = if (likelyKo) 20.0 else 0.0
            val score = (netDamage * moveDamageScoreWeight + utility * moveUtilityScoreWeight + koBonus) * reliability - riskPenalty
            return MoveEvaluation(
                move = move.first,
                target = null,
                score = score,
                expectedDamage = netDamage,
                expectedDamageFraction = bestEnemyFraction,
                canLikelyKo = likelyKo
            )
        }

        val score = utilityForNoTarget * moveUtilityScoreWeight * reliability - riskPenalty
        return MoveEvaluation(
            move = move.first,
            target = null,
            score = score,
            expectedDamage = 0.0,
            expectedDamageFraction = 0.0,
            canLikelyKo = false
        )
    }

    private fun utilityScore(
        move: Pair<InBattleMove, MoveTemplate>,
        moveTags: Set<String>,
        activeTrackerPokemon: TrackerPokemon,
        targetTracker: TrackerPokemon?,
        currentMatchup: Double,
        availableSwitchCount: Int
    ): Double {
        var score = 0.0
        val attackerAbility = activeTrackerPokemon.currentAbility?.name ?: activeTrackerPokemon.pokemon?.ability?.name
        val attackerAbilityTags = BattleAITags.getAbilityTags(attackerAbility)

        val priority = parsePriority(moveTags, move.second.priority)
        if (priority > 0) {
            val speed = statEstimationActive(activeTrackerPokemon, Stats.SPEED)
            val slowerThanOpp = activeTracker.opponentSide.activePokemon.count { statEstimationActive(it, Stats.SPEED) > speed }
            score += 5.0 + priority * 6.0 + slowerThanOpp * 2.0
            if ("priority_boost_status_moves" in attackerAbilityTags && "category_status" in moveTags) {
                score += 8.0
            }
            if ("priority_boost_healing_moves" in attackerAbilityTags && "healing_move" in moveTags) {
                score += 6.0
            }
            if ("priority_boost_flying_moves" in attackerAbilityTags && "type_flying" in moveTags &&
                activeTrackerPokemon.currentHpPercent >= 1.0
            ) {
                score += 6.0
            }
        }

        if ("healing_move" in moveTags) {
            val healAmount = parseHealPercent(moveTags)
            val missingHp = (1.0 - activeTrackerPokemon.currentHpPercent).coerceAtLeast(0.0)
            score += missingHp * (healAmount * 0.45)
            if (activeTrackerPokemon.currentHpPercent < 0.45) {
                score += 15.0
            }
            if (activeTrackerPokemon.currentHpPercent > 0.90) {
                score -= 10.0
            }
        }

        score += setupAndDebuffScore(
            move = move,
            moveTags = moveTags,
            activeTrackerPokemon = activeTrackerPokemon,
            targetTracker = targetTracker,
            currentMatchup = currentMatchup
        )

        score += statusUtilityScore(
            move = move,
            moveTags = moveTags,
            attacker = activeTrackerPokemon,
            target = targetTracker,
            attackerAbilityTags = attackerAbilityTags
        )

        score += fieldControlScore(
            move = move,
            moveTags = moveTags,
            availableSwitchCount = availableSwitchCount
        )

        score += weatherTerrainScore(
            moveTags = moveTags,
            attackerAbilityTags = attackerAbilityTags
        )

        if ("pivot" in moveTags) {
            score += if (currentMatchup < switchOutMatchupThreshold) 16.0 else 6.0
            if (availableSwitchCount <= 0) {
                score -= 8.0
            }
        }

        if ("invincible" in moveTags || "protect_like" in moveTags) {
            val protectPenalty = activeTrackerPokemon.protectCount * 6.5
            score += 7.0 - protectPenalty
            if (activeTrackerPokemon.currentHpPercent < 0.30) {
                score += 5.0
            }
        }

        if ("delay_1" in moveTags) {
            val speed = statEstimationActive(activeTrackerPokemon, Stats.SPEED)
            val fasterOpp = activeTracker.opponentSide.activePokemon.any { statEstimationActive(it, Stats.SPEED) > speed }
            if (fasterOpp) {
                score -= 5.0
            }
        }

        if ("context_weather_sensitive" in moveTags && activeTracker.currentWeather == null) {
            score -= 3.0
        }
        if ("context_terrain_sensitive" in moveTags && activeTracker.currentTerrain == null) {
            score -= 3.0
        }

        if ("conditional_hp_full" in attackerAbilityTags && activeTrackerPokemon.currentHpPercent < 1.0) {
            score -= 2.0
        }
        if ("conditional_hp_low" in attackerAbilityTags && activeTrackerPokemon.currentHpPercent < 0.33) {
            score += 4.0
        }
        if ("offense_influence" in attackerAbilityTags &&
            ("category_physical" in moveTags || "category_special" in moveTags)
        ) {
            score += 3.0
        }
        if ("defense_influence" in attackerAbilityTags &&
            ("healing_move" in moveTags || "invincible" in moveTags || "protect_like" in moveTags)
        ) {
            score += 2.5
        }

        return score
    }

    private fun setupAndDebuffScore(
        move: Pair<InBattleMove, MoveTemplate>,
        moveTags: Set<String>,
        activeTrackerPokemon: TrackerPokemon,
        targetTracker: TrackerPokemon?,
        currentMatchup: Double
    ): Double {
        val statTags = parseStatTags(moveTags)
        if (statTags.isEmpty()) {
            return 0.0
        }

        var score = 0.0
        val selfTarget = move.first.target in selfTargetModes || "target_self" in moveTags

        val positive = statTags.filter { it.stages > 0 }
        val negative = statTags.filter { it.stages < 0 }

        if (selfTarget && positive.isNotEmpty()) {
            val maxRelevantBoost = positive.maxOf { it.stages.coerceAtLeast(0) * it.chance / 100.0 }
            score += if (currentMatchup > 0) maxRelevantBoost * 7.0 else maxRelevantBoost * 2.5
            if (activeTrackerPokemon.currentHpPercent < 0.45) {
                score -= 6.0
            }
            positive.forEach { statTag ->
                val currentBoost = activeTrackerPokemon.boosts.getOrDefault(statTag.stat, 0)
                if (currentBoost >= 5) {
                    score -= 6.0
                }
            }
        }

        if (!selfTarget && negative.isNotEmpty() && targetTracker != null) {
            val expectedReduction = negative.sumOf { it.stages.absoluteValue * it.chance / 100.0 }
            val threat = offensiveThreatScore(targetTracker)
            score += expectedReduction * (2.0 + threat)
        }

        return score
    }

    private fun statusUtilityScore(
        move: Pair<InBattleMove, MoveTemplate>,
        moveTags: Set<String>,
        attacker: TrackerPokemon,
        target: TrackerPokemon?,
        attackerAbilityTags: Set<String>
    ): Double {
        if (target == null || target.currentHpPercent <= 0) {
            return 0.0
        }

        var score = 0.0
        val effectiveType = move.second.getEffectiveElementalType(attacker.pokemon)

        moveTags.mapNotNull { statusRegex.find(it) }.forEach { match ->
            val effect = match.groupValues[1]
            val baseChance = match.groupValues[2].toIntOrNull() ?: 0
            val chance = if ("secondary_chance_boost" in attackerAbilityTags) {
                (baseChance * 2).coerceAtMost(100)
            } else {
                baseChance
            }
            if (chance <= 0) {
                return@forEach
            }

            val statusValue = when (effect) {
                "burn" -> 12.0 + if (target.species?.baseStats?.getOrDefault(Stats.ATTACK, 0) ?: 0 > 90) 7.0 else 0.0
                "paralysis" -> 13.0 + if (target.species?.baseStats?.getOrDefault(Stats.SPEED, 0) ?: 0 > 90) 6.0 else 0.0
                "sleep" -> 16.0
                "poison" -> 10.0
                "freeze" -> 16.0
                "confusion" -> 7.0
                "flinch" -> {
                    val ourSpeed = statEstimationActive(attacker, Stats.SPEED)
                    val oppSpeed = statEstimationActive(target, Stats.SPEED)
                    if (ourSpeed >= oppSpeed || parsePriority(moveTags, move.second.priority) > 0) 9.0 else 3.0
                }
                "attract" -> 5.0
                "partiallytrapped" -> 6.0
                else -> 0.0
            }

            val canApply = when (effect) {
                "burn" -> AIUtility.canAffectWithStatus(Statuses.BURN.showdownName, target.species?.types ?: emptyList(), target.currentAbility)
                "paralysis" -> AIUtility.canAffectWithStatus(Statuses.PARALYSIS.showdownName, target.species?.types ?: emptyList(), target.currentAbility)
                "sleep" -> AIUtility.canAffectWithStatus(Statuses.SLEEP.showdownName, target.species?.types ?: emptyList(), target.currentAbility)
                "poison" -> AIUtility.canAffectWithStatus(Statuses.POISON.showdownName, target.species?.types ?: emptyList(), target.currentAbility)
                "freeze" -> AIUtility.canAffectWithStatus(Statuses.FROZEN.showdownName, target.species?.types ?: emptyList(), target.currentAbility)
                "confusion" -> AIUtility.canAffectWithStatus(Statuses.CONFUSE.showdownName, target.species?.types ?: emptyList(), target.currentAbility)
                "attract" -> AIUtility.canAffectWithStatus(Statuses.ATTRACT.showdownName, target.species?.types ?: emptyList(), target.currentAbility)
                else -> true
            }

            if (!canApply || hasMajorStatusImmunity(target)) {
                return@forEach
            }
            if (effect in setOf("burn", "paralysis", "sleep", "poison", "freeze") && target.currentStatus != null) {
                return@forEach
            }

            if ((effect == "paralysis" || effect == "burn") && move.second.damageCategory == DamageCategories.STATUS) {
                val multiplier = moveDamageMultiplier(attacker, move.second, target)
                if (multiplier == 0.0 && effectiveType == move.second.getEffectiveElementalType(attacker.pokemon)) {
                    return@forEach
                }
            }

            score += statusValue * (chance / 100.0)
        }

        moveTags.mapNotNull { secondaryChanceRegex.find(it) }.forEach { match ->
            val baseChance = match.groupValues[1].toIntOrNull() ?: 0
            if (baseChance <= 0) {
                return@forEach
            }
            val scaledChance = if ("secondary_chance_boost" in attackerAbilityTags) {
                (baseChance * 2).coerceAtMost(100)
            } else {
                baseChance
            }
            score += scaledChance * 0.06
        }

        return score
    }

    private fun fieldControlScore(
        move: Pair<InBattleMove, MoveTemplate>,
        moveTags: Set<String>,
        availableSwitchCount: Int
    ): Double {
        var score = 0.0
        val opponentsRemaining = activeTracker.opponentSide.actors.sumOf { actor -> actor.party.count { it.currentHpPercent > 0 } + actor.activePokemon.count { it.currentHpPercent > 0 } }
        val alliesRemaining = activeTracker.alliedSide.actors.sumOf { actor -> actor.party.count { it.currentHpPercent > 0 } + actor.activePokemon.count { it.currentHpPercent > 0 } }

        if ("hazard_set" in moveTags || moveTags.any { it.startsWith("sets_side_condition_") }) {
            val sideConditionTags = moveTags.filter { it.startsWith("sets_side_condition_") }
            val targetingOpposingSide = "target_opponents" in moveTags || move.first.target == MoveTarget.foeSide || move.first.target == MoveTarget.allAdjacentFoes
            val hazardList = if (targetingOpposingSide) activeTracker.opponentSide.sideHazards else activeTracker.alliedSide.sideHazards

            var hasNewCondition = false
            sideConditionTags.forEach {
                val condition = it.removePrefix("sets_side_condition_")
                if (condition.isNotBlank() && condition !in hazardList) {
                    hasNewCondition = true
                }
            }
            if ("hazard_set" in moveTags && hasNewCondition && opponentsRemaining >= 3) {
                score += 14.0
            } else if ("hazard_set" in moveTags && !hasNewCondition) {
                score -= 6.0
            }
        }

        if (move.first.id in AIUtility.antiHazardsMoves) {
            if (activeTracker.alliedSide.sideHazards.isNotEmpty() && alliesRemaining >= 2) {
                score += 12.0
            } else {
                score -= 4.0
            }
        }

        if ("screen_set" in moveTags) {
            if (activeTracker.alliedSide.screenCondition == null && availableSwitchCount > 0) {
                score += 10.0
            } else {
                score -= 3.0
            }
        }

        if ("field_effect" in moveTags && move.first.id == "courtchange") {
            val ownHazards = activeTracker.alliedSide.sideHazards.size
            val opponentHazards = activeTracker.opponentSide.sideHazards.size
            score += (ownHazards - opponentHazards) * 8.0
        }

        return score
    }

    private fun weatherTerrainScore(
        moveTags: Set<String>,
        attackerAbilityTags: Set<String>
    ): Double {
        var score = 0.0

        val weatherTag = moveTags.firstOrNull { it.startsWith("sets_weather_") }?.removePrefix("sets_weather_")
        if ("sets_weather" in moveTags || weatherTag != null) {
            if (activeTracker.currentWeather != weatherTag) {
                score += 6.0
            } else {
                score -= 3.0
            }
            if ("weather_interaction" in attackerAbilityTags || "weather_setter" in attackerAbilityTags) {
                score += 4.0
            }
            if (weatherTag != null && "conditional_weather_$weatherTag" in attackerAbilityTags) {
                score += 5.0
            }
        }

        val terrainTag = moveTags.firstOrNull { it.startsWith("sets_terrain_") }?.removePrefix("sets_terrain_")
        if ("sets_terrain" in moveTags || terrainTag != null) {
            if (activeTracker.currentTerrain != terrainTag) {
                score += 6.0
            } else {
                score -= 3.0
            }
            if ("terrain_interaction" in attackerAbilityTags || "terrain_setter" in attackerAbilityTags) {
                score += 4.0
            }
        }

        return score
    }

    private fun failsContextGate(
        move: InBattleMove,
        moveTags: Set<String>,
        attacker: TrackerPokemon
    ): Boolean {
        if ("requires_user_sleep" in moveTags && attacker.currentStatus != Statuses.SLEEP.showdownName) {
            return true
        }
        if ("blocked_by_gravity" in moveTags && activeTracker.currentRoom == "gravity") {
            return true
        }
        if (move.id == "fakeout" && !attacker.firstTurn) {
            return true
        }
        if ("healing_move" in moveTags && attacker.currentHpPercent >= 0.98) {
            return true
        }
        if ("target_self" in moveTags && parseStatTags(moveTags).any { it.stages > 0 && attacker.boosts.getOrDefault(it.stat, 0) >= 6 }) {
            return true
        }
        return false
    }

    private fun riskPenalty(moveTags: Set<String>, attacker: TrackerPokemon, opponents: List<TrackerPokemon>): Double {
        var penalty = 0.0
        val hpFactor = (1.0 - attacker.currentHpPercent).coerceAtLeast(0.1)

        if ("recoil_move" in moveTags) {
            penalty += 8.0 * hpFactor
        }
        moveTags.forEach {
            val recoil = recoilRegex.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (recoil != null && recoil > 0) {
                penalty += (recoil / 8.0) * hpFactor
            }
        }
        if ("crash_risk" in moveTags) {
            penalty += 10.0
        }
        if ("self_ko" in moveTags) {
            penalty += 25.0
        }
        if ("recharge_turn" in moveTags) {
            penalty += 15.0
        }
        if ("delay_1" in moveTags) {
            val ownSpeed = statEstimationActive(attacker, Stats.SPEED)
            val fasterOpp = opponents.any { statEstimationActive(it, Stats.SPEED) > ownSpeed }
            if (fasterOpp) {
                penalty += 4.0
            }
        }

        return penalty
    }

    private fun reliabilityMultiplier(move: MoveTemplate, moveTags: Set<String>): Double {
        if ("accuracy_true" in moveTags || "never_miss" in moveTags || "accuracy_perfect" in moveTags) {
            return 1.0
        }
        if ("reliability_high" in moveTags) {
            return 0.97
        }
        if ("reliability_medium" in moveTags) {
            return 0.86
        }
        if ("reliability_low" in moveTags) {
            return 0.72
        }
        return (move.accuracy / 100.0).coerceIn(0.5, 1.0)
    }

    private fun parsePriority(moveTags: Set<String>, fallback: Int): Int {
        return moveTags
            .mapNotNull { priorityRegex.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            .firstOrNull() ?: fallback
    }

    private fun parseHealPercent(moveTags: Set<String>): Int {
        val fromTag = moveTags.mapNotNull { healRegex.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }.maxOrNull()
        if (fromTag != null) {
            return fromTag
        }
        if ("heal_variable" in moveTags) {
            return 35
        }
        if ("healing_move" in moveTags) {
            return 25
        }
        return 0
    }

    private fun parseStatTags(moveTags: Set<String>): List<ParsedStatTag> {
        return moveTags.mapNotNull { tag ->
            val match = statRegex.find(tag) ?: return@mapNotNull null
            val stat = when (match.groupValues[1]) {
                "atk" -> Stats.ATTACK
                "def" -> Stats.DEFENCE
                "spatk" -> Stats.SPECIAL_ATTACK
                "spdef" -> Stats.SPECIAL_DEFENCE
                "speed" -> Stats.SPEED
                "accuracy" -> Stats.ACCURACY
                "evasion" -> Stats.EVASION
                else -> return@mapNotNull null
            }
            val stages = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val chance = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 100
            ParsedStatTag(stat, stages, chance.coerceIn(0, 100))
        }
    }

    private fun offensiveThreatScore(mon: TrackerPokemon): Double {
        val physical = mon.species?.baseStats?.getOrDefault(Stats.ATTACK, 0) ?: 0
        val special = mon.species?.baseStats?.getOrDefault(Stats.SPECIAL_ATTACK, 0) ?: 0
        val highest = maxOf(physical, special).toDouble()
        return (highest / 100.0).coerceAtLeast(0.5)
    }

    private fun getTrackerPokemon(id: java.util.UUID): TrackerPokemon? {
        return activeTracker.alliedSide.activePokemon.firstOrNull { it.id == id }
            ?: activeTracker.opponentSide.activePokemon.firstOrNull { it.id == id }
    }

    private fun estimateDamageFraction(damage: Double, defender: TrackerPokemon): Double {
        val maxHp = estimateMaxHp(defender).coerceAtLeast(1.0)
        return (damage / maxHp).coerceAtLeast(0.0)
    }

    private fun estimateMaxHp(mon: TrackerPokemon): Double {
        val baseHp = mon.pokemon?.species?.baseStats?.get(Stats.HP)
            ?: mon.species?.baseStats?.get(Stats.HP)
            ?: 80
        val level = mon.pokemon?.level ?: 50
        return ((2 * baseHp + 31) * level / 100.0) + level + 10
    }

    fun calculateDamage(move: Pair<InBattleMove, MoveTemplate>, attacker: ActiveBattlePokemon, attackerTracker: TrackerPokemon): Pair<ActiveBattlePokemon?, Double> {
        if (move.first.target in selfTargetModes) {
            return null to 0.0
        }
        val singleTargetList = move.first.target.targetList.invoke(attacker)?.map { it as ActiveBattlePokemon }?.filter { it.battlePokemon != null }
        val multiTargetList = attacker.getMultiTargetList(move.first.target)?.map { it as ActiveBattlePokemon }?.filter { it.battlePokemon != null }

        if (!singleTargetList.isNullOrEmpty()) {
            val potentialTargets = activeTracker.opponentSide.activePokemon
                .filter { opp -> singleTargetList.any { it.battlePokemon!!.uuid == opp.id } }
                .map { opp ->
                    val target = singleTargetList.first { it.battlePokemon!!.uuid == opp.id }
                    target to calculateDamage(move.first, attackerTracker, opp)
                }
            return potentialTargets.maxByOrNull { it.second } ?: (null to 0.0)
        }

        if (!multiTargetList.isNullOrEmpty()) {
            val (allies, opponents) = multiTargetList.partition { it.isAllied(attacker) }
            val totalOpponentDamage = activeTracker.opponentSide.activePokemon
                .filter { tracked -> opponents.any { it.battlePokemon!!.uuid == tracked.id } }
                .sumOf { calculateDamage(move.first, attackerTracker, it) }
            val totalAlliedDamage = activeTracker.alliedSide.activePokemon
                .filter { tracked -> allies.any { it.battlePokemon!!.uuid == tracked.id } }
                .sumOf { calculateDamage(move.first, attackerTracker, it) }

            return null to (totalOpponentDamage - (totalAlliedDamage * friendlyFirePenalty))
        }

        return null to 0.0
    }

    fun calculateDamage(move: InBattleMove, attacker: TrackerPokemon, opponent: TrackerPokemon): Double {
        val moveData = Moves.getByName(move.id) ?: return 0.0
        if (moveData.power <= 0 || moveData.damageCategory == DamageCategories.STATUS) {
            return 0.0
        }

        val moveTags = BattleAITags.getMoveTags(move.id)
        val attackerAbilityName = attacker.currentAbility?.name ?: attacker.pokemon?.ability?.name
        val attackerAbilityTags = BattleAITags.getAbilityTags(attackerAbilityName)
        val opponentAbilityName = opponent.currentAbility?.name ?: opponent.pokemon?.ability?.name
        val opponentAbilityTags = BattleAITags.getAbilityTags(opponentAbilityName)
        val moveType = moveData.getEffectiveElementalType(attacker.pokemon)

        if (AIUtility.typeImmuneAbilities[opponentAbilityName ?: "unknown"] == moveType ||
            ((opponentAbilityName == "suctioncup") && (move.id == "roar" || move.id == "whirlwind"))
        ) {
            return 0.0
        }

        if (moveBlockedByAbilityTags(
                moveTags = moveTags,
                moveType = moveType,
                attackerAbilityTags = attackerAbilityTags,
                defenderAbilityTags = opponentAbilityTags
            )
        ) {
            return 0.0
        }

        val physicalRatio = statEstimationActive(attacker, Stats.ATTACK) / statEstimationActive(opponent, Stats.DEFENCE).coerceAtLeast(1.0)
        val specialRatio = statEstimationActive(attacker, Stats.SPECIAL_ATTACK) / statEstimationActive(opponent, Stats.SPECIAL_DEFENCE).coerceAtLeast(1.0)
        val statRatio = if (moveData.damageCategory == DamageCategories.PHYSICAL) physicalRatio else specialRatio

        val pokemonLevel = attacker.pokemon?.level ?: 50
        val STAB = when {
            moveType in (attacker.pokemon?.types ?: emptyList()) && attacker.pokemon?.ability?.name == "adaptability" -> 2.0
            moveType in (attacker.pokemon?.types ?: emptyList()) -> 1.5
            else -> 1.0
        }

        val weather = when {
            activeTracker.currentWeather == "sunny" && (moveType == ElementalTypes.FIRE || moveData.name == "hydrosteam") -> 1.5
            activeTracker.currentWeather == "sunny" && moveType == ElementalTypes.WATER -> 0.5
            activeTracker.currentWeather == "raining" && moveType == ElementalTypes.WATER -> 1.5
            activeTracker.currentWeather == "raining" && moveType == ElementalTypes.FIRE -> 0.5
            else -> 1.0
        }

        val damageTypeMultiplier = moveDamageMultiplier(attacker, moveData, opponent)
        if (damageTypeMultiplier == 0.0) {
            return 0.0
        }

        val burn = when {
            (attacker.pokemon?.status?.status?.showdownName ?: attacker.currentStatus) == Statuses.BURN.showdownName &&
                moveData.damageCategory == DamageCategories.PHYSICAL &&
                attackerAbilityName != "guts" -> 0.5
            else -> 1.0
        }

        val abilityMoveTagMultiplier = BattleAITags.abilityMoveTagDamageMultiplier(attackerAbilityName, moveData.name)
        val hitsExpected = expectedHits(moveData)
        val reliability = reliabilityMultiplier(moveData, moveTags)

        var damage = (((((2 * pokemonLevel) / 5.0) + 2) * moveData.power * statRatio) / 50.0 + 2)
        damage *= weather
        damage *= STAB
        damage *= damageTypeMultiplier
        damage *= burn
        damage *= abilityMoveTagMultiplier
        damage *= hitsExpected
        damage *= reliability
        return damage.coerceAtLeast(0.0)
    }

    private fun moveBlockedByAbilityTags(
        moveTags: Set<String>,
        moveType: ElementalType,
        attackerAbilityTags: Set<String>,
        defenderAbilityTags: Set<String>
    ): Boolean {
        if ("ignores_ability" in moveTags || "ignores_ability_effects" in attackerAbilityTags) {
            return false
        }
        if ("immune_type_${moveType.name}" in defenderAbilityTags) {
            return true
        }
        defenderAbilityTags
            .filter { it.startsWith("immune_move_tag_") }
            .forEach { immunityTag ->
                val guardedTag = immunityTag.removePrefix("immune_move_tag_")
                if (guardedTag in moveTags) {
                    return true
                }
            }
        if ("blocks_opponent_priority" in defenderAbilityTags &&
            parsePriority(moveTags, 0) > 0
        ) {
            return true
        }
        return false
    }

    fun estimateMatchup(
        activeBattlePokemon: ActiveBattlePokemon,
        aiSide: BattleSide,
        battle: PokemonBattle,
        nonActiveMon: TrackerPokemon? = null
    ): Double {
        updateActiveTracker(aiSide, battle)
        val activeTrackerPokemon = nonActiveMon ?: activeTracker.alliedSide.activePokemon.first { it.id == activeBattlePokemon.battlePokemon!!.uuid }
        val speedEstimation = statEstimationActive(activeTrackerPokemon, Stats.SPEED)
        val opponentActiveTracker = activeTracker.opponentSide.activePokemon
        val ownAbilityTags = BattleAITags.getAbilityTags(activeTrackerPokemon.currentAbility?.name ?: activeTrackerPokemon.pokemon?.ability?.name)

        var score = 1.0
        for (opponent in opponentActiveTracker) {
            score += (bestDamageMultiplier(activeTrackerPokemon, opponent) * moveDamageWeightConsideration) +
                (typeMatchup(activeTrackerPokemon, opponent) * typeMatchupWeightConsideration)
            score -= (bestDamageMultiplier(opponent, activeTrackerPokemon) * moveDamageWeightConsideration) +
                (typeMatchup(opponent, activeTrackerPokemon) * typeMatchupWeightConsideration)

            if (speedEstimation > statEstimationActive(opponent, Stats.SPEED)) {
                score += speedTierCoefficient * trickRoomCoefficient
            } else if (statEstimationActive(opponent, Stats.SPEED) > speedEstimation) {
                score -= speedTierCoefficient * trickRoomCoefficient
            }

            score += (activeTrackerPokemon.currentHpPercent * hpFractionCoefficient) * hpWeightConsideration
            score -= (opponent.currentHpPercent * hpFractionCoefficient) * hpWeightConsideration

            val opponentAbilityTags = BattleAITags.getAbilityTags(opponent.currentAbility?.name ?: opponent.pokemon?.ability?.name)
            score += abilityTierScore(ownAbilityTags) * 0.4
            score -= abilityTierScore(opponentAbilityTags) * 0.35
            if ("ignore_opponent_stat_boosts" in ownAbilityTags &&
                (opponent.boosts.getOrDefault(Stats.ATTACK, 0) > 0 || opponent.boosts.getOrDefault(Stats.SPECIAL_ATTACK, 0) > 0)
            ) {
                score += 3.0
            }
        }

        return score
    }

    private fun abilityTierScore(tags: Set<String>): Double {
        return when {
            "tier_s" in tags -> 8.0
            "tier_a" in tags -> 6.0
            "tier_b" in tags -> 4.0
            "tier_c" in tags -> 2.0
            "tier_d" in tags -> 1.0
            else -> 0.0
        }
    }

    fun hasMajorStatusImmunity(target: TrackerPokemon): Boolean {
        val ability = target.pokemon?.ability?.name ?: target.currentAbility?.name ?: return false
        val abilityTags = BattleAITags.getAbilityTags(ability)
        return listOf("comatose", "purifyingsalt").contains(ability) ||
            "status_move_immunity" in abilityTags ||
            (activeTracker.currentWeather == "sunny" && ability == "leafguard")
    }

    fun statEstimationActive(mon: TrackerPokemon, stat: Stat): Double {
        val boost = mon.boosts[stat] ?: 0
        val actualBoost = if (boost > 1) {
            (2 + boost) / 2.0
        } else {
            2 / (2.0 - boost)
        }

        val baseStat = mon.pokemon?.species?.baseStats?.get(stat) ?: mon.species?.baseStats?.get(stat) ?: 0
        return ((2 * baseStat + 31) + 5) * actualBoost
    }

    fun moveDamageMultiplier(attacker: TrackerPokemon, move: MoveTemplate, defender: TrackerPokemon): Double {
        val defenderTypes = defender.pokemon?.types ?: defender.form?.types ?: defender.species?.types ?: emptyList()
        var multiplier = 1.0
        for (defenderType in defenderTypes) {
            multiplier *= AIUtility.getDamageMultiplier(move.getEffectiveElementalType(attacker.pokemon), defenderType)
        }
        return multiplier
    }

    fun bestDamageMultiplier(attacker: TrackerPokemon, defender: TrackerPokemon): Double {
        val attackerMoves = attacker.pokemon?.moveSet ?: attacker.moves
        val defenderTypes = defender.pokemon?.types ?: defender.form?.types ?: defender.species?.types ?: emptyList()

        var bestMultiplier = 1.0
        for (attackerMove in attackerMoves) {
            var multiplier = 1.0
            for (defenderType in defenderTypes) {
                multiplier *= AIUtility.getDamageMultiplier(attackerMove.type, defenderType)
            }
            if (multiplier > bestMultiplier) {
                bestMultiplier = multiplier
            }
        }
        return bestMultiplier
    }

    fun typeMatchup(attackingPokemon: TrackerPokemon, defendingPokemon: TrackerPokemon): Double {
        val attackerTypes = attackingPokemon.pokemon?.types ?: attackingPokemon.form?.types ?: attackingPokemon.species?.types ?: emptyList()
        val defenderTypes = defendingPokemon.pokemon?.types ?: defendingPokemon.form?.types ?: defendingPokemon.species?.types ?: emptyList()

        var multiplier = 1.0
        for (atkType in attackerTypes) {
            for (defType in defenderTypes) {
                multiplier *= AIUtility.getDamageMultiplier(atkType, defType)
            }
        }
        return multiplier
    }

    fun expectedHits(move: MoveTemplate): Int {
        val minMaxHits = AIUtility.multiHitMoves[move.name]
        if (move.name == "triplekick" || move.name == "tripleaxel") {
            return (1 + 2 * 0.9 + 3 * 0.81).toInt()
        }
        if (move.name == "populationbomb") {
            return 7
        }
        return if (minMaxHits == null) {
            1
        } else if (minMaxHits.first == minMaxHits.second) {
            minMaxHits.first
        } else {
            (2 + 3) / 3 + (4 + 5) / 6
        }
    }

    private fun updateActiveTracker(aiSide: BattleSide, battle: PokemonBattle) {
        if (!activeTracker.isInitialized) {
            activeTracker.initialize(aiSide)
        }
        activeTracker.currentWeather = battle.contextManager.get(BattleContext.Type.WEATHER)?.firstOrNull()?.id
        activeTracker.currentTerrain = battle.contextManager.get(BattleContext.Type.TERRAIN)?.firstOrNull()?.id
        activeTracker.currentRoom = battle.contextManager.get(BattleContext.Type.ROOM)?.firstOrNull()?.id

        trickRoomCoefficient = if (activeTracker.currentRoom == "trickroom") -1.0 else 1.0
        activeTracker.updateActiveState(aiSide)
    }

    private fun chooseMove(
        move: InBattleMove,
        activeBattlePokemon: ActiveBattlePokemon,
        target: ActiveBattlePokemon? = null
    ): MoveActionResponse {
        val targets = if (move.mustBeUsed()) null else move.target.targetList(activeBattlePokemon)
        if (targets == null) {
            return MoveActionResponse(move.id)
        }
        val chosenTarget = target ?: targets.filter { !it.isAllied(activeBattlePokemon) }.randomOrNull() ?: targets.random()
        return MoveActionResponse(move.id, chosenTarget.getPNX())
    }
}

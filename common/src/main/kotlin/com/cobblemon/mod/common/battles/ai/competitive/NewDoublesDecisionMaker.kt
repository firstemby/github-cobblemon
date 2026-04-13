package com.cobblemon.mod.common.battles.ai.competitive

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.battles.ActiveBattlePokemon
import com.cobblemon.mod.common.battles.InBattleMove
import com.cobblemon.mod.common.battles.ShowdownMoveset

class NewDoublesDecisionMaker(
    private val moveEvaluator: NewMoveEvaluator,
    private val damageCalculator: NewDamageCalculator
) {
    data class MoveTargetPair(
        val move: InBattleMove,
        val targetPNX: String?,
        val evaluation: NewMoveEvaluator.MoveEvaluation
    )

    fun findBestMoveAndTarget(
        moveset: ShowdownMoveset?,
        activeBattlePokemon: ActiveBattlePokemon,
        myPokemon: TrackedPokemon,
        opponents: List<TrackedPokemon>,
        partner: TrackedPokemon?,
        battleState: BattleState,
        gamePlan: GamePlan
    ): MoveTargetPair? {
        if (moveset == null) return null
        val usableMoves = moveset.moves.filter { it.canBeUsed() }
        if (usableMoves.isEmpty()) return null

        val allOptions = mutableListOf<MoveTargetPair>()
        for (move in usableMoves) {
            Moves.getByName(move.id) ?: continue
            val targetList = move.target.targetList(activeBattlePokemon)
            if (targetList == null) {
                val evaluation = moveEvaluator.evaluateMoveForDoubles(
                    move,
                    myPokemon,
                    opponents.firstOrNull() ?: TrackedPokemon(),
                    opponents,
                    partner,
                    battleState,
                    gamePlan
                )
                allOptions.add(MoveTargetPair(move, null, evaluation))
                continue
            }

            val opponentTargets = targetList.filterIsInstance<ActiveBattlePokemon>()
                .filter { !it.isAllied(activeBattlePokemon) }
            for (target in opponentTargets) {
                val targetPokemon = opponents.find { it.pokemon?.uuid == target.battlePokemon?.uuid } ?: continue
                val evaluation = moveEvaluator.evaluateMoveForDoubles(
                    move,
                    myPokemon,
                    targetPokemon,
                    opponents,
                    partner,
                    battleState,
                    gamePlan
                )
                allOptions.add(MoveTargetPair(move, target.getPNX(), evaluation))
            }
        }
        return allOptions.maxByOrNull { it.evaluation.totalScore }
    }

    fun selectBestTarget(
        move: InBattleMove,
        activeBattlePokemon: ActiveBattlePokemon,
        myPokemon: TrackedPokemon,
        opponents: List<TrackedPokemon>,
        battleState: BattleState
    ): String? {
        val targetList = move.target.targetList(activeBattlePokemon) ?: return null
        val opponentTargets = targetList.filterIsInstance<ActiveBattlePokemon>()
            .filter { !it.isAllied(activeBattlePokemon) }
        if (opponentTargets.isEmpty()) return null

        val targetScores = opponentTargets.mapNotNull { target ->
            val targetPokemon = opponents.find { it.pokemon?.uuid == target.battlePokemon?.uuid } ?: return@mapNotNull null
            val damage = damageCalculator.calculateDamage(move, myPokemon, targetPokemon, battleState.weather)
            target to (damage + scoreTarget(targetPokemon, myPokemon))
        }
        return targetScores.maxByOrNull { it.second }?.first?.getPNX()
    }

    private fun scoreTarget(target: TrackedPokemon, myPokemon: TrackedPokemon): Double {
        var score = target.threatLevel * 20.0
        if (target.cachedSpeed > myPokemon.cachedSpeed && target.currentHpPercent < 0.5) {
            score += 80.0
        }
        if (target.boosts.values.any { it >= 2 }) {
            score += 100.0
        }
        if (target.currentHpPercent < 0.35) {
            score += 50.0
        }
        return score
    }
}

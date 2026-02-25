package com.cobblemon.mod.common.battles.ai

import com.cobblemon.mod.common.api.battles.interpreter.BattleContext
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.*
import com.cobblemon.mod.common.battles.ai.competitive.*
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket
import kotlin.random.Random

class CompetitiveBattleAI(private val skill: Int) : BattleAI {

    companion object {
        private const val DEBUG_TOP_MOVES = 4
    }

    private val skillLevel = skill.coerceIn(0, 5)

    private val aiDebugEnabled = isAiDebugEnabled()

    private val battleTracker = EnhancedActiveTracker()
    private val opponentPredictor = OpponentPredictor()
    private val strategyAnalyzer = StrategyAnalyzer()
    private lateinit var damageCalculator: DamageCalculator
    private lateinit var moveEvaluator: MoveEvaluator
    private lateinit var switchDecisionMaker: SwitchDecisionMaker
    private lateinit var doublesDecisionMaker: DoublesDecisionMaker

    private var currentBattle: PokemonBattle? = null
    private var currentGamePlan: GamePlan? = null
    private var battleState: BattleState? = null
    private var lastMoveEvaluation: MoveEvaluator.MoveEvaluation? = null

    private var isDoublesMode: Boolean = false
    private var currentSlot: Int = 0
    private var currentTurn: Int = -1
    private val turnDecisions: MutableMap<Int, DoublesDecisionMaker.MoveTargetPair> = mutableMapOf()
    private var firstSlotDecided: Boolean = false

    override fun choose(
        activeBattlePokemon: ActiveBattlePokemon,
        battle: PokemonBattle,
        aiSide: BattleSide,
        moveset: ShowdownMoveset?,
        forceSwitch: Boolean
    ): ShowdownActionResponse {
        try {
            initializeComponents(battle)
            updateBattleState(activeBattlePokemon, battle)

            if (currentGamePlan == null) {
                val npcTeam = battle.side2.actors.first().pokemonList.map { it.effectedPokemon }
                currentGamePlan = strategyAnalyzer.analyzeTeamComposition(npcTeam)
            }

            if (battle.turn != currentTurn) {
                currentTurn = battle.turn
                turnDecisions.clear()
                firstSlotDecided = false
            }

            if (forceSwitch || activeBattlePokemon.isGone()) {
                return handleForcedSwitch(activeBattlePokemon, battle)
            }

            if (!passesSkillCheck()) {
                debugLog("Skill check failed; selecting random move.")
                return makeRandomMove(moveset, activeBattlePokemon)
            }

            updateOpponentModel(battle)

            if (isDoublesMode) {
                return chooseForDoubles(activeBattlePokemon, battle, moveset)
            }

            if (shouldAttemptSwitch() && shouldSwitch(activeBattlePokemon, battle)) {
                val switchTarget = findBestSwitch(activeBattlePokemon, battle)
                if (switchTarget != null) {
                    switchTarget.willBeSwitchedIn = true
                    logSwitchDecision("voluntary_switch", battle, listOf(switchTarget), switchTarget)
                    return SwitchActionResponse(switchTarget.uuid)
                }
            }

            val bestMove = chooseBestMove(moveset, activeBattlePokemon, battle)
            logMoveDecision("move_select", bestMove, activeBattlePokemon)
            return executeMove(bestMove, activeBattlePokemon)

        } catch (e: Exception) {
            e.printStackTrace()
            return PassActionResponse
        }
    }

    private fun chooseForDoubles(
        activeBattlePokemon: ActiveBattlePokemon,
        battle: PokemonBattle,
        moveset: ShowdownMoveset?
    ): ShowdownActionResponse {
        val state = battleState ?: return PassActionResponse
        val plan = currentGamePlan ?: return PassActionResponse
        val myPokemon = battleTracker.p2Active.getActiveAt(currentSlot) ?: return PassActionResponse
        val partnerSlot = 1 - currentSlot
        val partner = battleTracker.p2Active.getActiveAt(partnerSlot)
        val opponents = battleTracker.p1Active.getAllActive()

        val decision = doublesDecisionMaker.findBestMoveAndTarget(
            moveset,
            activeBattlePokemon,
            myPokemon,
            opponents,
            partner,
            state,
            plan
        )

        if (decision == null) {
            debugLog("Doubles: no evaluated option; selecting random move.")
            return makeRandomMove(moveset, activeBattlePokemon)
        }

        turnDecisions[currentSlot] = decision
        firstSlotDecided = true

        debugLog(
            "Doubles: chose move=${decision.move.id} target=${decision.targetPNX ?: "auto"} " +
                "score=${"%.2f".format(decision.evaluation.totalScore)} " +
                "expectedDamage=${"%.1f".format(decision.evaluation.expectedDamage)} " +
                "strategic=${"%.1f".format(decision.evaluation.strategicValue)} " +
                "risk=${"%.2f".format(decision.evaluation.riskFactor)}"
        )
        return executeDoublesMove(decision, activeBattlePokemon)
    }

    private fun executeDoublesMove(
        decision: DoublesDecisionMaker.MoveTargetPair,
        activeBattlePokemon: ActiveBattlePokemon
    ): ShowdownActionResponse {
        return if (decision.targetPNX != null) {
            MoveActionResponse(decision.move.id, decision.targetPNX)
        } else {
            MoveActionResponse(decision.move.id)
        }
    }

    private fun initializeComponents(battle: PokemonBattle) {
        if (currentBattle != battle) {
            currentBattle = battle
            isDoublesMode = battle.format.battleType.pokemonPerSide > 1
            damageCalculator = DamageCalculator(battle)
            moveEvaluator = MoveEvaluator(damageCalculator, opponentPredictor, isDoublesMode)
            switchDecisionMaker = SwitchDecisionMaker(damageCalculator, opponentPredictor, strategyAnalyzer)
            doublesDecisionMaker = DoublesDecisionMaker(moveEvaluator, damageCalculator)
        }
    }

    private fun updateBattleState(activeBattlePokemon: ActiveBattlePokemon, battle: PokemonBattle) {
        val request = activeBattlePokemon.actor.request ?: return

        // Determine current slot from PNX (e.g., "p2a" = slot 0, "p2b" = slot 1)
        val pnx = activeBattlePokemon.getPNX()
        currentSlot = if (pnx.length >= 3) pnx[2] - 'a' else 0

        if (isDoublesMode) {
            battle.side1.activePokemon.forEachIndexed { index, active ->
                active.battlePokemon?.let { pokemon ->
                    battleTracker.updatePokemonSlot(battleTracker.p1Active, index, pokemon)
                }
            }

            battle.side2.activePokemon.forEachIndexed { index, active ->
                active.battlePokemon?.let { pokemon ->
                    battleTracker.updatePokemonSlot(battleTracker.p2Active, index, pokemon)
                }
            }

            battleTracker.p1Active.activePokemon =
                battleTracker.p1Active.getActiveAt(currentSlot) ?: battleTracker.p1Active.activePokemon
            battleTracker.p2Active.activePokemon =
                battleTracker.p2Active.getActiveAt(currentSlot) ?: battleTracker.p2Active.activePokemon
        } else {
            battle.side1.activePokemon.firstOrNull()?.battlePokemon?.let { playerPokemon ->
                battleTracker.updatePokemon(battleTracker.p1Active.activePokemon, playerPokemon)
                battleTracker.updateParty(battleTracker.p1Active, battleTracker.p1Active.activePokemon)
            }

            activeBattlePokemon.battlePokemon?.let { aiPokemon ->
                battleTracker.updatePokemon(battleTracker.p2Active.activePokemon, aiPokemon)
                battleTracker.updateParty(battleTracker.p2Active, battleTracker.p2Active.activePokemon)
            }
        }

        updateRevealedMoves(battle)
        battleState = createBattleState(battle, activeBattlePokemon)
    }

    private fun createBattleState(battle: PokemonBattle, activeBattlePokemon: ActiveBattlePokemon): BattleState? {
        val p1Actor = battle.side1.actors.firstOrNull() ?: return null
        val p2Actor = battle.side2.actors.firstOrNull() ?: return null

        val playerActiveTracker = if (isDoublesMode) {
            battleTracker.p1Active.getActiveAt(currentSlot) ?: battleTracker.p1Active.activePokemon
        } else {
            battleTracker.p1Active.activePokemon
        }
        val npcActiveTracker = if (isDoublesMode) {
            battleTracker.p2Active.getActiveAt(currentSlot) ?: battleTracker.p2Active.activePokemon
        } else {
            battleTracker.p2Active.activePokemon
        }

        val playerActive = playerActiveTracker.pokemon ?: return null
        val npcActive = npcActiveTracker.pokemon ?: return null
        val playerActiveSlots = battleTracker.p1Active.getAllActive()
            .mapNotNull { it.pokemon }
            .ifEmpty { listOf(playerActive) }

        val npcActiveSlots = battleTracker.p2Active.getAllActive()
            .mapNotNull { it.pokemon }
            .ifEmpty { listOf(npcActive) }

        val partnerSlot = if (isDoublesMode) {
            val mySlot = currentSlot
            1 - mySlot
        } else {
            null
        }

        return BattleState(
            turn = battle.turn,
            playerTeam = p1Actor.pokemonList.map { it.effectedPokemon },
            npcTeam = p2Actor.pokemonList.map { it.effectedPokemon },
            playerActive = playerActive,
            npcActive = npcActive,
            weather = getWeather(battle),
            terrain = getTerrain(battle),
            playerHazards = getHazards(battle.side1),
            npcHazards = getHazards(battle.side2),
            playerBoosts = battleTracker.p1Active.activePokemon.boosts,
            npcBoosts = battleTracker.p2Active.activePokemon.boosts,
            battleContext = battle.contextManager,
            isDoubles = isDoublesMode,
            playerActiveSlots = playerActiveSlots,
            npcActiveSlots = npcActiveSlots,
            mySlotIndex = currentSlot,
            partnerSlotIndex = partnerSlot
        )
    }

    private fun handleForcedSwitch(activeBattlePokemon: ActiveBattlePokemon, battle: PokemonBattle): ShowdownActionResponse {
        val availableSwitches = getAvailableSwitches(activeBattlePokemon)

        if (availableSwitches.isEmpty()) {
            return ForfeitActionResponse()
        }

        val state = battleState
        val plan = currentGamePlan

        val bestSwitch = if (state != null && plan != null) {
            switchDecisionMaker.findBestSwitch(
                availableSwitches,
                battleTracker.p1Active.activePokemon,
                state,
                plan
            )
        } else {
            null
        }

        val switchTarget = bestSwitch ?: availableSwitches.first()
        switchTarget.willBeSwitchedIn = true
        logSwitchDecision("forced_switch", battle, availableSwitches, switchTarget)
        return SwitchActionResponse(switchTarget.uuid)
    }

    private fun shouldSwitch(activeBattlePokemon: ActiveBattlePokemon, battle: PokemonBattle): Boolean {
        val availableSwitches = getAvailableSwitches(activeBattlePokemon)
        if (availableSwitches.isEmpty()) return false

        val state = battleState ?: return false
        val plan = currentGamePlan ?: return false

        return switchDecisionMaker.shouldVoluntarilySwitch(
            battleTracker.p2Active.activePokemon,
            battleTracker.p1Active.activePokemon,
            state,
            plan,
            availableSwitches
        )
    }

    private fun findBestSwitch(activeBattlePokemon: ActiveBattlePokemon, battle: PokemonBattle): BattlePokemon? {
        val availableSwitches = getAvailableSwitches(activeBattlePokemon)

        val state = battleState ?: return null
        val plan = currentGamePlan ?: return null

        return switchDecisionMaker.findBestSwitch(
            availableSwitches,
            battleTracker.p1Active.activePokemon,
            state,
            plan
        )
    }

    private fun chooseBestMove(
        moveset: ShowdownMoveset?,
        activeBattlePokemon: ActiveBattlePokemon,
        battle: PokemonBattle
    ): InBattleMove? {
        if (moveset == null) return null

        val usableMoves = moveset.moves.filter { it.canBeUsed() }
        if (usableMoves.isEmpty()) return null

        val state = battleState ?: return null
        val plan = currentGamePlan ?: return null

        val evaluations = usableMoves.map { move ->
            moveEvaluator.evaluateMove(
                move,
                battleTracker.p2Active.activePokemon,
                battleTracker.p1Active.activePokemon,
                state,
                plan
            )
        }

        val bestEvaluation = evaluations.maxByOrNull { it.totalScore }
        logMoveRanking(evaluations)
        lastMoveEvaluation = bestEvaluation
        return bestEvaluation?.move
    }

    private fun executeMove(move: InBattleMove?, activeBattlePokemon: ActiveBattlePokemon): ShowdownActionResponse {
        if (move == null) {
            return makeRandomMove(null, activeBattlePokemon)
        }

        val target = if (move.mustBeUsed()) {
            null
        } else {
            move.target.targetList(activeBattlePokemon)
        }

        return if (target == null) {
            MoveActionResponse(move.id)
        } else {
            val chosenTarget = target.filter { !it.isAllied(activeBattlePokemon) }.randomOrNull()
                ?: target.random()
            MoveActionResponse(move.id, (chosenTarget as ActiveBattlePokemon).getPNX())
        }
    }

    private fun makeRandomMove(moveset: ShowdownMoveset?, activeBattlePokemon: ActiveBattlePokemon): ShowdownActionResponse {
        if (moveset == null) return PassActionResponse

        val usableMoves = moveset.moves.filter { it.canBeUsed() }
        val move = usableMoves.randomOrNull() ?: return PassActionResponse

        val target = if (move.mustBeUsed()) null else move.target.targetList(activeBattlePokemon)

        return if (target == null) {
            MoveActionResponse(move.id)
        } else {
            val chosenTarget = target.filter { !it.isAllied(activeBattlePokemon) }.randomOrNull()
                ?: target.random()
            MoveActionResponse(move.id, (chosenTarget as ActiveBattlePokemon).getPNX())
        }
    }

    private fun updateOpponentModel(battle: PokemonBattle) {
        val situation = categorizeSituation(battleState ?: return)

        val opponentActives = if (isDoublesMode) {
            battleTracker.p1Active.getAllActive()
        } else {
            listOf(battleTracker.p1Active.activePokemon)
        }

        for (opponentActive in opponentActives) {
            for (move in opponentActive.revealedMoves) {
                opponentPredictor.updateMoveHistory(
                    opponentActive.species ?: "unknown",
                    move,
                    situation.name.lowercase()
                )
            }
        }
    }

    override fun onHealthChange(packet: BattleHealthChangePacket) {
        try {
            val battle = currentBattle ?: return
            val pnx = packet.pnx

            val activePokemon = battle.activePokemon.find { it.getPNX() == pnx } ?: return
            val newHealth = packet.newHealth.toInt()
            val isPlayerPokemon = activePokemon.actor == battle.side1.actors.firstOrNull()
            val teamTracker = if (isPlayerPokemon) {
                battleTracker.p1Active
            } else {
                battleTracker.p2Active
            }
            val tracker = battleTracker.getTrackedPokemonByPNX(pnx, teamTracker) ?: teamTracker.activePokemon

            val oldHealth = tracker.currentHp
            val hpChange = newHealth - oldHealth

            tracker.previousHp = oldHealth
            tracker.currentHp = newHealth
            tracker.lastHpChange = hpChange
            tracker.currentHpPercent = if (tracker.maxHp > 0) newHealth.toDouble() / tracker.maxHp else 0.0

            updateMomentum(hpChange, isPlayerPokemon)

            detectCriticalThresholds(tracker, oldHealth, newHealth, isPlayerPokemon)

        } catch (e: Exception) {
        }
    }


    private fun updateMomentum(hpChange: Int, isPlayerPokemon: Boolean) {
        val momentumChange = when {
            hpChange > 0 -> -5.0  // Opponent healing shifts momentum toward them
            hpChange < 0 -> kotlin.math.abs(hpChange) / 10.0  // Damage shifts momentum
            else -> 0.0
        }

        if (isPlayerPokemon) {
            // Player took damage = AI momentum increases
            battleTracker.p2Active.momentum += momentumChange
            battleTracker.p1Active.momentum -= momentumChange
        } else {
            // AI took damage = Player momentum increases
            battleTracker.p2Active.momentum -= momentumChange
            battleTracker.p1Active.momentum += momentumChange
        }

        // Clamp momentum to -100 to 100
        battleTracker.p1Active.momentum = battleTracker.p1Active.momentum.coerceIn(-100.0, 100.0)
        battleTracker.p2Active.momentum = battleTracker.p2Active.momentum.coerceIn(-100.0, 100.0)
    }

    private fun detectCriticalThresholds(
        tracker: TrackedPokemon,
        oldHealth: Int,
        newHealth: Int,
        isPlayerPokemon: Boolean
    ) {
        if (newHealth <= 0 && oldHealth > 0) {
            if (isPlayerPokemon) {
                battleTracker.p2Active.momentum += 20.0
            } else {
                battleTracker.p1Active.momentum += 20.0
            }
        }
    }

    private fun categorizeSituation(battleState: BattleState): BattleSituation {
        val myHP = battleState.npcActive.currentHealth.toDouble() / battleState.npcActive.maxHealth
        val oppHP = battleState.playerActive.currentHealth.toDouble() / battleState.playerActive.maxHealth

        return when {
            myHP < 0.25 -> BattleSituation.DESPERATE
            oppHP < 0.25 -> BattleSituation.WINNING
            myHP > 0.8 && oppHP > 0.8 -> BattleSituation.EARLY_GAME
            battleState.npcTeam.count { it.currentHealth > 0 } <= 2 -> BattleSituation.ENDGAME
            battleState.playerBoosts.values.any { it >= 2 } -> BattleSituation.OPPONENT_SETUP
            battleState.npcBoosts.values.any { it >= 2 } -> BattleSituation.MY_SETUP
            battleState.weather != null -> BattleSituation.WEATHER_CONTROL
            else -> BattleSituation.NEUTRAL
        }
    }

    private fun getAvailableSwitches(activeBattlePokemon: ActiveBattlePokemon): List<BattlePokemon> {
        val currentUuid = activeBattlePokemon.battlePokemon?.uuid

        return activeBattlePokemon.actor.pokemonList.filter {
            it.health > 0 &&
            it.uuid != currentUuid &&
            try { it.canBeSentOut() } catch (e: Exception) { true }
        }
    }

    private fun passesSkillCheck(): Boolean {
        if (skillLevel == 5) return true
        if (skillLevel == 0) return false

        val baseChance = skillLevel * 20
        val turnBonus = if ((battleState?.turn ?: 0) > 10) 10 else 0

        return Random.nextInt(100) < (baseChance + turnBonus)
    }

    private fun shouldAttemptSwitch(): Boolean {
        return when (skillLevel) {
            in 0..2 -> false
            3 -> Random.nextDouble() < 0.25
            4 -> Random.nextDouble() < 0.70
            5 -> true
            else -> false
        }
    }

    private fun getWeather(battle: PokemonBattle): String? {
        return try {
            battle.contextManager.get(BattleContext.Type.WEATHER)
                ?.firstOrNull()?.id
        } catch (e: Exception) {
            null
        }
    }

    private fun getTerrain(battle: PokemonBattle): String? {
        return try {
            battle.contextManager.get(BattleContext.Type.TERRAIN)
                ?.firstOrNull()?.id
        } catch (e: Exception) {
            null
        }
    }

    private fun getHazards(side: BattleSide): List<String> {
        return try {
            side.contextManager.get(BattleContext.Type.HAZARD)
                ?.map { it.id } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun updateRevealedMoves(battle: PokemonBattle) {
        updateTeamRevealedMoves(battle, battleTracker.p1Active)
        updateTeamRevealedMoves(battle, battleTracker.p2Active)
    }

    private fun updateTeamRevealedMoves(battle: PokemonBattle, team: TeamTracker) {
        val active = team.getAllActive().ifEmpty { listOf(team.activePokemon) }
        for (tracked in active) {
            val pokemon = tracked.pokemon ?: continue
            val majorMessage = battle.majorBattleActions[pokemon.uuid]
            val minorMessage = battle.minorBattleActions[pokemon.uuid]
            val candidates = listOfNotNull(majorMessage, minorMessage)
            for (message in candidates) {
                val id = message.id.lowercase()
                if (id != "move" && id != "-singlemove" && id != "singlemove") continue
                val moveId = message.effectAt(1)?.id?.lowercase() ?: continue
                tracked.revealedMoves.add(moveId)
            }
        }
    }

    private fun logMoveDecision(
        reason: String,
        move: InBattleMove?,
        activeBattlePokemon: ActiveBattlePokemon
    ) {
        if (!aiDebugEnabled || move == null) return
        val eval = lastMoveEvaluation
        val self = activeBattlePokemon.battlePokemon?.effectedPokemon?.species?.name ?: "unknown"
        val opp = battleTracker.p1Active.activePokemon.species ?: "unknown"
        val plan = currentGamePlan?.winCondition?.name ?: "unknown"
        if (eval == null) {
            debugLog("Decision=$reason self=$self opp=$opp move=${move.id} plan=$plan")
            return
        }
        debugLog(
            "Decision=$reason self=$self opp=$opp move=${move.id} plan=$plan " +
                "score=${"%.2f".format(eval.totalScore)} " +
                "expectedDamage=${"%.1f".format(eval.expectedDamage)} " +
                "strategic=${"%.1f".format(eval.strategicValue)} " +
                "risk=${"%.2f".format(eval.riskFactor)}"
        )
    }

    private fun logMoveRanking(evaluations: List<MoveEvaluator.MoveEvaluation>) {
        if (!aiDebugEnabled || evaluations.isEmpty()) return
        val plan = currentGamePlan?.winCondition?.name ?: "unknown"
        val situation = battleState?.let { categorizeSituation(it).name } ?: "unknown"
        val top = evaluations.sortedByDescending { it.totalScore }.take(DEBUG_TOP_MOVES)
        val state = battleState
        val planData = currentGamePlan
        val attacker = battleTracker.p2Active.activePokemon
        val defender = battleTracker.p1Active.activePokemon
        val details = top.joinToString(", ") { eval ->
            val debug = if (state != null && planData != null) {
                moveEvaluator.buildDebugDetails(eval.move, attacker, defender, state, planData)
            } else {
                null
            }
            val flags = if (debug != null) {
                listOf(
                    "stab=${debug.hasStab}",
                    "eff=${"%.2f".format(debug.effectiveness)}",
                    "acc=${"%.2f".format(debug.accuracy)}",
                    "prio=${debug.priority}",
                    "status=${debug.isStatusMove}",
                    "setup=${debug.isSetup}",
                    "hazardSet=${debug.setsHazards}",
                    "hazardClear=${debug.removesHazards}",
                    "weather=${debug.setsWeather}",
                    "recovery=${debug.isRecovery}",
                    "pivot=${debug.isPivot}",
                    "risky=${debug.isRisky}"
                ).joinToString(" ")
            } else {
                "n/a"
            }
            "${eval.move.id}:" +
                "score=${"%.2f".format(eval.totalScore)} " +
                "raw=${"%.1f".format(eval.rawDamage)} " +
                "dmg=${"%.1f".format(eval.expectedDamage)} " +
                "strat=${"%.1f".format(eval.strategicValue)} " +
                "align=${"%.1f".format(eval.gamePlanAlignment)} " +
                "turn=${"%.1f".format(eval.turnAdvantage)} " +
                "risk=${"%.2f".format(eval.riskFactor)} " +
                flags
        }
        debugLog("MoveRank plan=$plan situation=$situation top=[$details]")
    }

    private fun logSwitchDecision(
        reason: String,
        battle: PokemonBattle,
        availableSwitches: List<BattlePokemon>,
        chosen: BattlePokemon?
    ) {
        if (!aiDebugEnabled) return
        val state = battleState
        val plan = currentGamePlan
        val opponent = battleTracker.p1Active.activePokemon
        val ranked = if (state != null && plan != null) {
            availableSwitches.map { candidate ->
                val score = switchDecisionMaker.evaluateSwitchOption(
                    candidate.effectedPokemon,
                    opponent,
                    state,
                    plan
                )
                candidate to score
            }.sortedByDescending { it.second }
                .take(3)
                .joinToString(", ") { (candidate, score) ->
                    "${candidate.effectedPokemon.species.name}:${"%.1f".format(score)}"
                }
        } else {
            "n/a"
        }

        val chosenName = chosen?.effectedPokemon?.species?.name ?: "none"
        debugLog("Decision=$reason chosen=$chosenName options=[$ranked]")
    }

    private fun debugLog(message: String) {
        if (aiDebugEnabled) {
            Cobblemon.LOGGER.info("[CompetitiveBattleAI] $message")
        }
    }

    private fun isAiDebugEnabled(): Boolean {
        return true
    }
}

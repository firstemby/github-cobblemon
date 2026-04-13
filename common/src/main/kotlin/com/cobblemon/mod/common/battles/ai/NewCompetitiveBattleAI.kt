package com.cobblemon.mod.common.battles.ai

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.interpreter.BattleContext
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI
import com.cobblemon.mod.common.battles.*
import com.cobblemon.mod.common.battles.ai.competitive.*
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket
import kotlin.random.Random

class NewCompetitiveBattleAI(private val skill: Int) : BattleAI {
    companion object {
        private const val DEBUG_TOP_MOVES = 4
    }

    private val skillLevel = skill.coerceIn(0, 5)
    private val aiDebugEnabled = isAiDebugEnabled()

    private val battleTracker = NewEnhancedActiveTracker()
    private val opponentPredictor = NewOpponentPredictor()
    private val strategyAnalyzer = NewStrategyAnalyzer()
    private lateinit var damageCalculator: NewDamageCalculator
    private lateinit var moveEvaluator: NewMoveEvaluator
    private lateinit var switchDecisionMaker: NewSwitchDecisionMaker
    private lateinit var doublesDecisionMaker: NewDoublesDecisionMaker

    private var currentBattle: PokemonBattle? = null
    private var currentGamePlan: GamePlan? = null
    private var battleState: BattleState? = null
    private var lastMoveEvaluation: NewMoveEvaluator.MoveEvaluation? = null

    private var currentAiSide: BattleSide? = null
    private var currentOpponentSide: BattleSide? = null

    private var isDoublesMode: Boolean = false
    private var currentSlot: Int = 0
    private var currentTurn: Int = -1
    private val turnDecisions: MutableMap<Int, NewDoublesDecisionMaker.MoveTargetPair> = mutableMapOf()
    private var firstSlotDecided: Boolean = false

    override fun choose(
        activeBattlePokemon: ActiveBattlePokemon,
        battle: PokemonBattle,
        aiSide: BattleSide,
        moveset: ShowdownMoveset?,
        forceSwitch: Boolean
    ): ShowdownActionResponse {
        try {
            currentAiSide = aiSide
            currentOpponentSide = aiSide.getOppositeSide()

            initializeComponents(battle)
            updateBattleState(activeBattlePokemon, battle, aiSide)

            if (currentGamePlan == null) {
                val npcTeam = aiSide.actors.first().pokemonList.map { it.effectedPokemon }
                currentGamePlan = strategyAnalyzer.analyzeTeamComposition(npcTeam)
            }

            if (battle.turn != currentTurn) {
                currentTurn = battle.turn
                turnDecisions.clear()
                firstSlotDecided = false
            }

            logDecisionContext(activeBattlePokemon, forceSwitch)

            if (forceSwitch || activeBattlePokemon.isGone()) {
                return handleForcedSwitch(activeBattlePokemon)
            }

            if (!passesSkillCheck()) {
                debugLog("Skill check failed; selecting random move.")
                return makeRandomMove(moveset, activeBattlePokemon)
            }

            updateOpponentModel()

            if (isDoublesMode) {
                return chooseForDoubles(activeBattlePokemon, moveset)
            }

            val switchGateOpen = shouldAttemptSwitch()
            if (switchGateOpen) {
                val shouldVoluntarySwitch = shouldSwitch(activeBattlePokemon)
                debugLog("Switch Gate: open | evaluator=$shouldVoluntarySwitch")
                if (shouldVoluntarySwitch) {
                    val switchTarget = findBestSwitch(activeBattlePokemon)
                    if (switchTarget != null) {
                        switchTarget.willBeSwitchedIn = true
                        logSwitchDecision("voluntary_switch", availableSwitches = getAvailableSwitches(activeBattlePokemon), chosen = switchTarget)
                        debugLog("Chosen Action: SWITCH -> ${switchTarget.effectedPokemon.species.name}")
                        return SwitchActionResponse(switchTarget.uuid)
                    }
                    debugLog("Switch Gate: evaluator requested switch, but no legal target found. Staying in.")
                } else {
                    debugLog("Switch Gate: staying in; move evaluation selected instead.")
                }
            } else {
                debugLog("Switch Gate: closed by tempo/skill gate; evaluating moves.")
            }

            val bestMove = chooseBestMove(moveset, activeBattlePokemon)
            logMoveDecision("move_select", bestMove, activeBattlePokemon)
            return executeMove(bestMove, activeBattlePokemon)
        } catch (e: Exception) {
            e.printStackTrace()
            return PassActionResponse
        }
    }

    private fun chooseForDoubles(
        activeBattlePokemon: ActiveBattlePokemon,
        moveset: ShowdownMoveset?
    ): ShowdownActionResponse {
        val state = battleState ?: return PassActionResponse
        val plan = currentGamePlan ?: return PassActionResponse
        val myPokemon = battleTracker.p2Active.getActiveAt(currentSlot) ?: return PassActionResponse
        val partner = battleTracker.p2Active.getActiveAt(1 - currentSlot)
        val opponents = battleTracker.p1Active.getAllActive()

        val decision = doublesDecisionMaker.findBestMoveAndTarget(
            moveset = moveset,
            activeBattlePokemon = activeBattlePokemon,
            myPokemon = myPokemon,
            opponents = opponents,
            partner = partner,
            battleState = state,
            gamePlan = plan
        ) ?: return makeRandomMove(moveset, activeBattlePokemon)

        turnDecisions[currentSlot] = decision
        firstSlotDecided = true
        debugLog(
            "Doubles Decision: move=${decision.move.id} target=${decision.targetPNX ?: "auto"} " +
                "score=${"%.2f".format(decision.evaluation.totalScore)}"
        )
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
            damageCalculator = NewDamageCalculator(battle)
            moveEvaluator = NewMoveEvaluator(damageCalculator, opponentPredictor, isDoublesMode)
            switchDecisionMaker = NewSwitchDecisionMaker(damageCalculator, opponentPredictor, strategyAnalyzer)
            doublesDecisionMaker = NewDoublesDecisionMaker(moveEvaluator, damageCalculator)
        }
    }

    private fun updateBattleState(activeBattlePokemon: ActiveBattlePokemon, battle: PokemonBattle, aiSide: BattleSide) {
        val pnx = activeBattlePokemon.getPNX()
        currentSlot = if (pnx.length >= 3) (pnx[2] - 'a').coerceAtLeast(0) else 0

        val opponentSide = aiSide.getOppositeSide()
        if (isDoublesMode) {
            opponentSide.activePokemon.forEachIndexed { index, active ->
                active.battlePokemon?.let { battleTracker.updatePokemonSlot(battleTracker.p1Active, index, it) }
            }
            aiSide.activePokemon.forEachIndexed { index, active ->
                active.battlePokemon?.let { battleTracker.updatePokemonSlot(battleTracker.p2Active, index, it) }
            }

            battleTracker.p1Active.activePokemon =
                battleTracker.p1Active.getActiveAt(currentSlot) ?: battleTracker.p1Active.activePokemon
            battleTracker.p2Active.activePokemon =
                battleTracker.p2Active.getActiveAt(currentSlot) ?: battleTracker.p2Active.activePokemon
        } else {
            opponentSide.activePokemon.firstOrNull()?.battlePokemon?.let { playerPokemon ->
                battleTracker.updatePokemon(battleTracker.p1Active.activePokemon, playerPokemon)
                battleTracker.updateParty(battleTracker.p1Active, battleTracker.p1Active.activePokemon)
            }
            activeBattlePokemon.battlePokemon?.let { aiPokemon ->
                battleTracker.updatePokemon(battleTracker.p2Active.activePokemon, aiPokemon)
                battleTracker.updateParty(battleTracker.p2Active, battleTracker.p2Active.activePokemon)
            }
        }

        updateRevealedMoves(battle)
        battleState = createBattleState(battle, aiSide)
    }

    private fun createBattleState(battle: PokemonBattle, aiSide: BattleSide): BattleState? {
        val opponentSide = aiSide.getOppositeSide()
        val playerActor = opponentSide.actors.firstOrNull() ?: return null
        val aiActor = aiSide.actors.firstOrNull() ?: return null

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

        val playerActiveSlots = battleTracker.p1Active.getAllActive().mapNotNull { it.pokemon }.ifEmpty { listOf(playerActive) }
        val npcActiveSlots = battleTracker.p2Active.getAllActive().mapNotNull { it.pokemon }.ifEmpty { listOf(npcActive) }
        val partnerSlot = if (isDoublesMode) 1 - currentSlot else null

        return BattleState(
            turn = battle.turn,
            playerTeam = playerActor.pokemonList.map { it.effectedPokemon },
            npcTeam = aiActor.pokemonList.map { it.effectedPokemon },
            playerActive = playerActive,
            npcActive = npcActive,
            weather = getWeather(battle),
            terrain = getTerrain(battle),
            playerHazards = getHazards(opponentSide),
            npcHazards = getHazards(aiSide),
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

    private fun handleForcedSwitch(activeBattlePokemon: ActiveBattlePokemon): ShowdownActionResponse {
        val availableSwitches = getAvailableSwitches(activeBattlePokemon)
        if (availableSwitches.isEmpty()) {
            debugLog("Forced Switch: no legal switches available; forfeiting.")
            return ForfeitActionResponse()
        }

        val state = battleState
        val plan = currentGamePlan
        val bestSwitch = if (state != null && plan != null) {
            switchDecisionMaker.findBestSwitch(
                availableSwitches = availableSwitches,
                opponentPokemon = battleTracker.p1Active.activePokemon,
                battleState = state,
                gamePlan = plan
            )
        } else {
            null
        }

        val switchTarget = bestSwitch ?: availableSwitches.first()
        switchTarget.willBeSwitchedIn = true
        logSwitchDecision("forced_switch", availableSwitches, switchTarget)
        debugLog("Chosen Action: FORCED SWITCH -> ${switchTarget.effectedPokemon.species.name}")
        return SwitchActionResponse(switchTarget.uuid)
    }

    private fun shouldSwitch(activeBattlePokemon: ActiveBattlePokemon): Boolean {
        val availableSwitches = getAvailableSwitches(activeBattlePokemon)
        if (availableSwitches.isEmpty()) return false
        val state = battleState ?: return false
        val plan = currentGamePlan ?: return false
        return switchDecisionMaker.shouldVoluntarilySwitch(
            myPokemon = battleTracker.p2Active.activePokemon,
            opponentPokemon = battleTracker.p1Active.activePokemon,
            battleState = state,
            gamePlan = plan,
            availableSwitches = availableSwitches
        )
    }

    private fun findBestSwitch(activeBattlePokemon: ActiveBattlePokemon): BattlePokemon? {
        val availableSwitches = getAvailableSwitches(activeBattlePokemon)
        val state = battleState ?: return null
        val plan = currentGamePlan ?: return null
        return switchDecisionMaker.findBestSwitch(
            availableSwitches = availableSwitches,
            opponentPokemon = battleTracker.p1Active.activePokemon,
            battleState = state,
            gamePlan = plan
        )
    }

    private fun chooseBestMove(
        moveset: ShowdownMoveset?,
        activeBattlePokemon: ActiveBattlePokemon
    ): InBattleMove? {
        if (moveset == null) return null
        val usableMoves = moveset.moves.filter { it.canBeUsed() }
        if (usableMoves.isEmpty()) return null

        val state = battleState ?: return null
        val plan = currentGamePlan ?: return null

        val evaluations = usableMoves.map { move ->
            moveEvaluator.evaluateMove(
                move = move,
                attacker = battleTracker.p2Active.activePokemon,
                defender = battleTracker.p1Active.activePokemon,
                battleState = state,
                gamePlan = plan
            )
        }

        val bestEvaluation = evaluations.maxByOrNull { it.totalScore }
        logMoveRanking(evaluations)
        lastMoveEvaluation = bestEvaluation
        return bestEvaluation?.move
    }

    private fun executeMove(move: InBattleMove?, activeBattlePokemon: ActiveBattlePokemon): ShowdownActionResponse {
        if (move == null) return makeRandomMove(null, activeBattlePokemon)
        val target = if (move.mustBeUsed()) null else move.target.targetList(activeBattlePokemon)
        return if (target == null) {
            debugLog("Chosen Action: MOVE ${move.id} (no target selection needed)")
            MoveActionResponse(move.id)
        } else {
            val chosenTarget = target.filter { !it.isAllied(activeBattlePokemon) }.randomOrNull() ?: target.random()
            debugLog("Chosen Action: MOVE ${move.id} -> target ${(chosenTarget as ActiveBattlePokemon).getPNX()}")
            MoveActionResponse(move.id, (chosenTarget as ActiveBattlePokemon).getPNX())
        }
    }

    private fun makeRandomMove(moveset: ShowdownMoveset?, activeBattlePokemon: ActiveBattlePokemon): ShowdownActionResponse {
        if (moveset == null) {
            debugLog("Fallback Action: PASS (no moveset available)")
            return PassActionResponse
        }
        val usableMoves = moveset.moves.filter { it.canBeUsed() }
        val move = usableMoves.randomOrNull() ?: run {
            debugLog("Fallback Action: PASS (no usable moves)")
            return PassActionResponse
        }
        debugLog("Fallback Action: random move '${move.id}'")
        val target = if (move.mustBeUsed()) null else move.target.targetList(activeBattlePokemon)
        return if (target == null) {
            MoveActionResponse(move.id)
        } else {
            val chosenTarget = target.filter { !it.isAllied(activeBattlePokemon) }.randomOrNull() ?: target.random()
            MoveActionResponse(move.id, (chosenTarget as ActiveBattlePokemon).getPNX())
        }
    }

    private fun updateOpponentModel() {
        val situation = categorizeSituation(battleState ?: return)
        val opponentActives = if (isDoublesMode) battleTracker.p1Active.getAllActive() else listOf(battleTracker.p1Active.activePokemon)
        for (opponentActive in opponentActives) {
            for (move in opponentActive.revealedMoves) {
                opponentPredictor.updateMoveHistory(
                    pokemon = opponentActive.species ?: "unknown",
                    move = move,
                    situation = situation.name.lowercase()
                )
            }
        }
    }

    override fun onHealthChange(packet: BattleHealthChangePacket) {
        try {
            val battle = currentBattle ?: return
            val activePokemon = battle.activePokemon.find { it.getPNX() == packet.pnx } ?: return
            val newHealth = packet.newHealth.toInt()
            val isOpponentPokemon = currentOpponentSide?.actors?.contains(activePokemon.actor) == true
            val teamTracker = if (isOpponentPokemon) battleTracker.p1Active else battleTracker.p2Active
            val tracker = battleTracker.getTrackedPokemonByPNX(packet.pnx, teamTracker) ?: teamTracker.activePokemon
            val oldHealth = tracker.currentHp
            val hpChange = newHealth - oldHealth
            tracker.previousHp = oldHealth
            tracker.currentHp = newHealth
            tracker.lastHpChange = hpChange
            tracker.currentHpPercent = if (tracker.maxHp > 0) newHealth.toDouble() / tracker.maxHp else 0.0
            updateMomentum(hpChange, isOpponentPokemon)
            detectCriticalThresholds(tracker, oldHealth, newHealth, isOpponentPokemon)
        } catch (_: Exception) {
        }
    }

    private fun updateMomentum(hpChange: Int, isOpponentPokemon: Boolean) {
        val momentumChange = when {
            hpChange > 0 -> -5.0
            hpChange < 0 -> kotlin.math.abs(hpChange) / 10.0
            else -> 0.0
        }

        if (isOpponentPokemon) {
            battleTracker.p2Active.momentum += momentumChange
            battleTracker.p1Active.momentum -= momentumChange
        } else {
            battleTracker.p2Active.momentum -= momentumChange
            battleTracker.p1Active.momentum += momentumChange
        }
        battleTracker.p1Active.momentum = battleTracker.p1Active.momentum.coerceIn(-100.0, 100.0)
        battleTracker.p2Active.momentum = battleTracker.p2Active.momentum.coerceIn(-100.0, 100.0)
    }

    private fun detectCriticalThresholds(
        tracker: TrackedPokemon,
        oldHealth: Int,
        newHealth: Int,
        isOpponentPokemon: Boolean
    ) {
        if (newHealth <= 0 && oldHealth > 0) {
            if (isOpponentPokemon) {
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
                try {
                    it.canBeSentOut()
                } catch (_: Exception) {
                    true
                }
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
        val weather = battle.contextManager.get(BattleContext.Type.WEATHER)?.firstOrNull()?.id
        return NewAiBattleUtils.normalizeWeatherId(weather)
    }

    private fun getTerrain(battle: PokemonBattle): String? {
        return battle.contextManager.get(BattleContext.Type.TERRAIN)?.firstOrNull()?.id
    }

    private fun getHazards(side: BattleSide): List<String> {
        return side.contextManager.get(BattleContext.Type.HAZARD)?.map { it.id } ?: emptyList()
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
        if (!shouldLogDecision() || move == null) return
        val eval = lastMoveEvaluation
        val self = activeBattlePokemon.battlePokemon?.effectedPokemon?.species?.name ?: "unknown"
        val opp = battleTracker.p1Active.activePokemon.species ?: "unknown"
        val plan = currentGamePlan?.winCondition?.name ?: "unknown"
        if (eval == null) {
            debugLog("Move Decision: reason=$reason | self=$self vs $opp | move=${move.id} | plan=$plan")
            return
        }
        debugLog(
            "Move Decision: ${move.id} selected | score=${"%.2f".format(eval.totalScore)} " +
                "| expectedDamage=${"%.1f".format(eval.expectedDamage)} | strategic=${"%.1f".format(eval.strategicValue)} " +
                "| risk=${"%.2f".format(eval.riskFactor)} | context=$self vs $opp | plan=$plan"
        )
    }

    private fun logMoveRanking(evaluations: List<NewMoveEvaluator.MoveEvaluation>) {
        if (!shouldLogDecision() || evaluations.isEmpty()) return
        val plan = currentGamePlan?.winCondition?.name ?: "unknown"
        val situation = battleState?.let { categorizeSituation(it).name } ?: "unknown"
        val top = evaluations.sortedByDescending { it.totalScore }.take(DEBUG_TOP_MOVES)
        if (!aiDebugEnabled) {
            val compact = top.joinToString(" | ") { eval ->
                "${eval.move.id}=${"%.1f".format(eval.totalScore)}"
            }
            debugLog("Move Ranking: plan=$plan, situation=$situation -> $compact")
            return
        }
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
        debugLog("Move Ranking (detailed): plan=$plan situation=$situation top=[$details]")
    }

    private fun logSwitchDecision(
        reason: String,
        availableSwitches: List<BattlePokemon>,
        chosen: BattlePokemon?
    ) {
        if (!shouldLogDecision()) return
        val state = battleState
        val plan = currentGamePlan
        val opponent = battleTracker.p1Active.activePokemon
        val ranked = if (state != null && plan != null) {
            availableSwitches.map { candidate ->
                val score = switchDecisionMaker.evaluateSwitchOption(
                    candidate = candidate.effectedPokemon,
                    opponent = opponent,
                    battleState = state,
                    gamePlan = plan
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
        debugLog("Switch Decision: reason=$reason | chosen=$chosenName | options=[$ranked]")
    }

    private fun debugLog(message: String) {
        if (!shouldLogDecision()) return
        val battleIdShort = currentBattle?.battleId?.toString()?.take(8) ?: "unknown"
        val turn = currentBattle?.turn ?: -1
        Cobblemon.LOGGER.info("[NewCompetitiveBattleAI battle=$battleIdShort turn=$turn] $message")
    }

    private fun isAiDebugEnabled(): Boolean {
        return System.getProperty("cobblemon.ai.debug", "false").toBooleanStrictOrNull() ?: false
    }

    private fun shouldLogDecision(): Boolean {
        val battle = currentBattle
        val isNpcVsNpc = battle?.actors?.all { it.type == ActorType.NPC } == true
        return aiDebugEnabled || isNpcVsNpc
    }

    private fun logDecisionContext(activeBattlePokemon: ActiveBattlePokemon, forceSwitch: Boolean) {
        if (!shouldLogDecision()) return
        val self = activeBattlePokemon.battlePokemon?.effectedPokemon?.species?.name ?: "unknown"
        val opp = battleTracker.p1Active.activePokemon.species ?: "unknown"
        val selfHp = formatPct(battleTracker.p2Active.activePokemon.currentHpPercent)
        val oppHp = formatPct(battleTracker.p1Active.activePokemon.currentHpPercent)
        val plan = currentGamePlan?.winCondition?.name ?: "unknown"
        val situation = battleState?.let { categorizeSituation(it).name } ?: "unknown"
        val gamePhase = battleState?.gamePhase?.name ?: "unknown"
        debugLog(
            "Context: $self ($selfHp) vs $opp ($oppHp) | plan=$plan | situation=$situation | phase=$gamePhase " +
                "| forceSwitch=$forceSwitch | doubles=$isDoublesMode | skill=$skillLevel"
        )
    }

    private fun formatPct(value: Double): String {
        val pct = (value.coerceIn(0.0, 1.0) * 100).toInt()
        return "$pct%"
    }
}

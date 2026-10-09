/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.battles

import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.battles.model.BattleEndResult
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.util.asArrayValue

/**
 * Event fired whenever a [PokemonBattle] ends, regardless of how it ended (victory, fleeing, being stopped, etc).
 *
 * [PokemonBattle.winners] and [PokemonBattle.losers] are only populated if the [result] has a winner,
 * that is [BattleEndResult.VICTORY], [BattleEndResult.CAPTURE] or [BattleEndResult.FORFEIT].
 *
 * @param result How the battle ended.
 * @param forfeitedBy The actor that forfeited, if the [result] is [BattleEndResult.FORFEIT].
 */
data class BattleEndedEvent(
    override val battle: PokemonBattle,
    val result: BattleEndResult,
    val forfeitedBy: BattleActor?
) : BattleEvent {
    /** Whether one side won the battle, which includes captures and forfeits. */
    val hasWinner = battle.winners.isNotEmpty()
    val wasVictory = result == BattleEndResult.VICTORY
    val wasCapture = result == BattleEndResult.CAPTURE
    val wasForfeit = result == BattleEndResult.FORFEIT
    val wasFled = result == BattleEndResult.FLED
    val wasStopped = result == BattleEndResult.STOPPED

    /** Whether the battle was between players and wild Pokémon. */
    val isPvW = battle.isPvW
    /** Whether the battle was between players and NPCs. */
    val isPvN = battle.isPvN
    /** Whether the battle was between players. */
    val isPvP = battle.isPvP

    val context = mutableMapOf<String, MoValue>(
        "battle" to battle.struct,
        "result" to StringValue(result.name.lowercase()),
        "has_winner" to DoubleValue(hasWinner),
        "was_victory" to DoubleValue(wasVictory),
        "was_capture" to DoubleValue(wasCapture),
        "was_forfeit" to DoubleValue(wasForfeit),
        "was_fled" to DoubleValue(wasFled),
        "was_stopped" to DoubleValue(wasStopped),
        "is_pvw" to DoubleValue(isPvW),
        "is_pvn" to DoubleValue(isPvN),
        "is_pvp" to DoubleValue(isPvP),
        "winners" to battle.winners.asArrayValue { it.struct },
        "losers" to battle.losers.asArrayValue { it.struct },
        "players" to battle.actors.filter { it.type == ActorType.PLAYER }.asArrayValue { it.struct },
        "npcs" to battle.actors.filter { it.type == ActorType.NPC }.asArrayValue { it.struct },
        "wild_pokemon" to battle.actors.filter { it.type == ActorType.WILD }.asArrayValue { it.struct }
    ).apply {
        forfeitedBy?.let { put("forfeited_by", it.struct) }
    }
}

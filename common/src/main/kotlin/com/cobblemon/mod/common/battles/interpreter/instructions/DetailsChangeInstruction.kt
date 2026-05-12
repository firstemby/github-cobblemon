/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.interpreter.instructions

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.text.lightPurple
import com.cobblemon.mod.common.battles.dispatch.InterpreterInstruction
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket
import com.cobblemon.mod.common.net.messages.client.battle.BattleSwitchPokemonPacket
import com.cobblemon.mod.common.util.battleLang
import kotlin.math.roundToInt

/**
 * Format: |detailschange|POKEMON|DETAILS|HP STATUS
 *
 * POKEMON has changed formes permanently (i.e. Mega Evolution) to DETAILS.
 * @author Segfault Guy
 * @since September 10th, 2023
 */
class DetailsChangeInstruction(val message: BattleMessage): InterpreterInstruction {

  override fun invoke(battle: PokemonBattle) {
    val (pnx, _) = message.pnxAndUuid(0) ?: return
    val battlePokemon = message.battlePokemon(0, battle) ?: return
    val heldItemName = battlePokemon.effectedPokemon.heldItem().hoverName.string
    val formName = message.argumentAt(1)?.split(',')?.get(0)?.substringAfter('-')?.lowercase() ?: return

    val pokemonName = battlePokemon.getName()
    val effectiveForm = when (formName) {
      "mega-x" -> "mega_x"
      "mega-y" -> "mega_y"
      else -> formName
    }

    val delay = if (effectiveForm in setOf("mega_x", "mega_y", "mega")) 3.5f else 1f

    battle.dispatchWaiting(delaySeconds = delay) {
      battlePokemon.setBattleFeature(effectiveForm, true)
      battlePokemon.sendUpdate()

      val flatHp = message.argumentAt(1)?.split("/")?.getOrNull(0)?.toFloatOrNull() ?: return@dispatchWaiting
      val ratioHp = message.argumentAt(1)?.split("/")?.getOrNull(0)?.toFloatOrNull()?.times(0.01F) ?: return@dispatchWaiting
      battlePokemon.effectedPokemon.currentHealth = flatHp.roundToInt()
      battle.sendSidedUpdate(battlePokemon.actor, BattleHealthChangePacket(pnx, flatHp), BattleHealthChangePacket(pnx, ratioHp))
      battle.sendUpdate(BattleSwitchPokemonPacket(pnx, battlePokemon, true, battlePokemon.getIllusion()))

      battle.broadcastChatMessage(battleLang("detailschange.$formName", pokemonName, heldItemName).lightPurple())
      battle.majorBattleActions[battlePokemon.uuid] = message
    }
  }
}
/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.battle.preview

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.net.messages.client.battle.BattlePokemonDTO
import com.cobblemon.mod.common.net.messages.server.battle.TeamPreviewSelectPokemonPacket
import net.minecraft.network.chat.Component
import java.time.Instant

class ClientBattleTeamPreview(
    val selections: Int,
    val team: List<BattlePokemonDTO?>,
    val opponent: List<BattlePokemonDTO?>,
    val mustPickBy: Instant,
    val opponentName: Component
) {
    val selection = mutableListOf<BattlePokemonDTO>()

    fun select(pokemon: BattlePokemonDTO) {
        selection.add(pokemon)
        sendChangePacket()
    }

    fun unselect(pokemon: BattlePokemonDTO) {
        selection.remove(pokemon)
        sendChangePacket()
    }

    private fun sendChangePacket() {
        val indexes = selection.map { team.indexOf(it) }
        if (indexes.any { it == -1 }) return
        CobblemonNetwork.sendToServer(TeamPreviewSelectPokemonPacket(indexes.toSet()))
    }
}
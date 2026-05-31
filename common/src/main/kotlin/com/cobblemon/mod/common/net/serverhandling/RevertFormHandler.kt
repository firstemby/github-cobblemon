/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.server.pokemon.update.form.RevertFormPacket
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm
import com.cobblemon.mod.common.util.party
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RevertFormHandler : ServerNetworkPacketHandler<RevertFormPacket> {
    override fun handle(packet: RevertFormPacket, server: MinecraftServer, player: ServerPlayer) {
        val pokemon = player.party().get(packet.pokemonUUID) ?: return
        val form = pokemon.form
        if (form is TemporaryForm) {
            form.revert(pokemon)
        }
    }
}
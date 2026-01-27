/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.data

import com.cobblemon.mod.common.api.interaction.PokemonInteractions
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.client.data.PokemonInteractionsSyncPacket
import net.minecraft.client.Minecraft

object PokemonInteractionsSyncHandler : ClientNetworkPacketHandler<PokemonInteractionsSyncPacket> {

    override fun handle(
        packet: PokemonInteractionsSyncPacket,
        client: Minecraft,
    ) {
        PokemonInteractions.speciesInteractions.clear()
        PokemonInteractions.speciesInteractions.addAll(packet.speciesInteractions)
        PokemonInteractions.generalInteractions.clear()
        PokemonInteractions.generalInteractions.addAll(packet.generalInteractions)
    }
}
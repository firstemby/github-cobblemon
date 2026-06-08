/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.block

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.moves.BenchedMove
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.storage.PokemonStore
import com.cobblemon.mod.common.block.tmmachine.TMMachineMenu
import com.cobblemon.mod.common.item.components.TMMoveComponent
import com.cobblemon.mod.common.net.messages.server.block.TMMachineTeachMovePacket
import com.cobblemon.mod.common.util.party
import com.cobblemon.mod.common.util.pc
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

object TMMachineTeachMoveHandler : ServerNetworkPacketHandler<TMMachineTeachMovePacket> {

    override fun handle(packet: TMMachineTeachMovePacket, server: MinecraftServer, player: ServerPlayer) {
        val pokemonStore: PokemonStore<*> = if (packet.isParty) player.party() else player.pc()
        val pokemon = pokemonStore[packet.uuid] ?: return
        val moveTemplate = packet.moveTemplate ?: return
        val menu = player.containerMenu as? TMMachineMenu ?: return
        val inventory = menu.inventory ?: return

        if (ItemStack.isSameItemSameComponents(packet.heldStack, menu.carried) &&
            moveTemplate == TMMoveComponent.getTMMove(packet.heldStack) &&
            packet.heldStack.count == menu.carried.count &&
            !pokemon.moveSet.getMoveTemplates().contains(moveTemplate) &&
            !pokemon.allAccessibleMoves.contains(moveTemplate)
        ) {
            if (!player.isCreative && !Cobblemon.config.infiniteTmUses) menu.carried.shrink(1)

            if (pokemon.moveSet.hasSpace()) {
                pokemon.moveSet.add(moveTemplate.create())
            } else {
                pokemon.benchedMoves.add(BenchedMove(moveTemplate, 0))
            }

            inventory.setChanged()
            menu.broadcastChanges()

            menu.tmMachineEntity?.playSound(CobblemonSounds.TM_USE, volume = 0.25F)
        }
    }
}

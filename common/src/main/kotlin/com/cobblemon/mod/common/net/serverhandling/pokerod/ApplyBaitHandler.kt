/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.pokerod

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.item.interactive.PokerodItem
import com.cobblemon.mod.common.net.messages.server.pokerod.ApplyBaitPacket
import com.cobblemon.mod.common.net.messages.server.pokerod.CastPokeRodPacket
import com.cobblemon.mod.common.util.isServerSide
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

//TODO: Remove Logger once this is all functional
object ApplyBaitHandler : ServerNetworkPacketHandler<ApplyBaitPacket> {

    override fun handle(packet: ApplyBaitPacket, server: MinecraftServer, player: ServerPlayer) {
        Cobblemon.LOGGER.debug("Received ApplyBaitPacket from player={} mainHand={}", player.uuid, packet.mainHand)

        val hand = if (packet.mainHand) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND
        val primaryStack = player.getItemInHand(hand)
        val secondaryStack =
            player.getItemInHand(if (hand == InteractionHand.MAIN_HAND) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND)

        Cobblemon.LOGGER.debug(
            "Selected hand: {} | primaryItem={} secondaryItem={}",
            hand,
            primaryStack.item,
            secondaryStack.item
        )

        (primaryStack.item as? PokerodItem)?.let { pokerod ->
            if (player.level().isServerSide()) {
                Cobblemon.LOGGER.info(
                    "Player {} attempting to apply offhand bait with primary={} secondary={}",
                    player.uuid,
                    primaryStack,
                    secondaryStack
                )
                pokerod.attemptApplyOffhandBait(
                    player.level(),
                    player,
                    primaryStack,
                    secondaryStack
                )
            } else {
                Cobblemon.LOGGER.debug("Skipping apply bait: not on server side for player={}", player.uuid)
            }
        } ?: run {
            Cobblemon.LOGGER.warn(
                "Player {} tried to apply bait but primary item is not a Pokerod: {}",
                player.uuid,
                primaryStack.item
            )
        }
    }
}
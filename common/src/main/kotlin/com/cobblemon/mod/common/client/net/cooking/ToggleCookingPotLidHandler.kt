/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.cooking

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.block.campfirepot.CampfireBlock
import com.cobblemon.mod.common.block.campfirepot.CookingPotMenu
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity.Companion.IS_LID_OPEN_INDEX
import com.cobblemon.mod.common.net.messages.client.cooking.ToggleCookingPotLidPacket
import com.cobblemon.mod.common.util.activateNearbyObservers
import com.cobblemon.mod.common.util.playSoundServer
import com.cobblemon.mod.common.util.raycast
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ClipContext

object ToggleCookingPotLidHandler : ServerNetworkPacketHandler<ToggleCookingPotLidPacket> {
    override fun handle(
        packet: ToggleCookingPotLidPacket,
        server: MinecraftServer,
        player: ServerPlayer
    ) {
        if (player.containerMenu !is CookingPotMenu) {
            Cobblemon.LOGGER.debug("Player {} interacted with invalid menu {}", player, player.containerMenu);
            return
        }

        val menu = player.containerMenu as? CookingPotMenu ?: return
        val isLidOpen = if (packet.value) 1 else 0
        menu.containerData.set(IS_LID_OPEN_INDEX, isLidOpen)

        val raycastBlockPos =
            player.raycast(player.blockInteractionRange().toFloat() + 1F, ClipContext.Fluid.ANY).blockPos
        val blockEntity = player.level().getBlockEntity(raycastBlockPos) as? CampfireBlockEntity ?: return
        blockEntity.toggleLid(isLidOpen == 1, raycastBlockPos)
    }
}
/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.pokedex.scanner

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.block.entity.PrintedPhotoDisplayBlockEntity
import com.cobblemon.mod.common.net.messages.server.pokedex.scanner.FinishPrintedPhotoScanningPacket
import com.cobblemon.mod.common.pokedex.photo.PrintedPhotoPokedexScanner
import com.cobblemon.mod.common.pokedex.scanner.PokedexUsageContext
import com.cobblemon.mod.common.pokedex.scanner.PlayerScanningDetails
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object FinishPrintedPhotoScanningHandler : com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler<FinishPrintedPhotoScanningPacket> {
    override fun handle(
        packet: FinishPrintedPhotoScanningPacket,
        server: MinecraftServer,
        player: ServerPlayer
    ) {
        val progressTick = PlayerScanningDetails.playerToTickMap[player.uuid]
        val ticksScan = progressTick?.let { server.tickCount - it } ?: PokedexUsageContext.SUCCESS_SCAN_SERVER_TICKS
        if (ticksScan < PokedexUsageContext.SUCCESS_SCAN_SERVER_TICKS) return
        if (!isLookingAtPrintedPhoto(player, packet)) return

        val component = (player.level().getBlockEntity(packet.blockPos) as? PrintedPhotoDisplayBlockEntity)?.component ?: return
        val newlySeen = PrintedPhotoPokedexScanner.grantSeenEntries(player, component)
        PrintedPhotoPokedexScanner.sendResultMessage(player, newlySeen, component.detectedSpecies.distinct().size)
    }

    private fun isLookingAtPrintedPhoto(player: ServerPlayer, packet: FinishPrintedPhotoScanningPacket): Boolean {
        val maxDistance = Cobblemon.config.maxPokedexScanningDetectionRange +
            (packet.zoomLevel * PokedexUsageContext.BLOCK_LENGTH_PER_ZOOM_STAGE)
        if (player.position().distanceTo(Vec3.atCenterOf(packet.blockPos)) > maxDistance) {
            return false
        }

        val eye = player.getEyePosition(1.0F)
        val end = eye.add(player.getViewVector(1.0F).scale(maxDistance.toDouble()))
        val hit = player.level().clip(
            ClipContext(
                eye,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
            )
        )
        if (hit.type != HitResult.Type.BLOCK) return false
        val blockHit = hit as? BlockHitResult ?: return false
        if (blockHit.blockPos != packet.blockPos) return false
        return player.level().getBlockEntity(packet.blockPos) is PrintedPhotoDisplayBlockEntity
    }
}

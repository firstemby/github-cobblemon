/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item

import com.cobblemon.mod.common.entity.scan.ScanNodeEntity
import com.cobblemon.mod.common.net.messages.client.pokedex.OpenScanNodeConfigPacket
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.phys.AABB

/**
 * Worldbuilding tool: right-clicking a block places (or re-opens) an invisible [ScanNodeEntity]
 * anchored to that block, making the spot scannable by the Pokedex scanner. Creative/op only.
 * Sneak-attacking a node while holding this stick removes it (handled in [ScanNodeEntity.hurt]).
 */
class ScanNodeStickItem(properties: Item.Properties) : CobblemonItem(properties) {

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        if (!player.abilities.instabuild && !(player is ServerPlayer && player.hasPermissions(2))) {
            return InteractionResult.PASS
        }

        val level = context.level
        if (level.isClientSide) return InteractionResult.SUCCESS
        val serverLevel = level as? ServerLevel ?: return InteractionResult.PASS

        val pos = context.clickedPos
        val searchBox = AABB(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x + 1.0, pos.y + 1.0, pos.z + 1.0
        ).inflate(0.6)

        val existing = serverLevel.getEntitiesOfClass(ScanNodeEntity::class.java, searchBox) { it.anchorPos == pos }
            .firstOrNull()
        if (existing != null) {
            openConfig(player, existing)
            return InteractionResult.CONSUME
        }

        val node = ScanNodeEntity(serverLevel)
        node.setPos(pos.x + 0.5, pos.y + 0.5 - (ScanNodeEntity.SIZE / 2.0), pos.z + 0.5)
        node.anchorPos = pos.immutable()
        node.anchorBlock = BuiltInRegistries.BLOCK.getKey(serverLevel.getBlockState(pos).block)
        serverLevel.addFreshEntity(node)
        openConfig(player, node)
        return InteractionResult.CONSUME
    }

    private fun openConfig(player: Player, node: ScanNodeEntity) {
        (player as? ServerPlayer)?.let { serverPlayer ->
            OpenScanNodeConfigPacket(node.id, node.category.id, node.scanId, node.frameFields).sendToPlayer(serverPlayer)
        }
    }
}
